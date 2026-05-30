# Debuggability assessment for Asala Calendar

Date: 2026-05-23
Status: Draft for owner review

## Summary

The codebase is in good shape for its size. It is small, well-split,
and uses a consistent set of patterns end to end. The single biggest
debuggability gap is the complete absence of any logging: when
something misbehaves on a real device, there is no trail to follow.
Three priorities follow at the end.

## What I looked at

- Module structure
- File sizes and split discipline
- Logging
- Error handling
- Tests
- Dependency injection

## Findings

### 1. Single-module is the right call (for now)

The `app` module contains 67 Kotlin files totalling 8,207 lines of
production code (`find app/src/main -name "*.kt" | wc -l`, then
`-exec cat {} + | wc -l`). Multi-module Gradle setups start paying
back somewhere above the 30K-line mark or once a second form factor
(Wear OS, tablet, automotive) lands. Asala has neither.

Splitting the app into `:core`, `:data`, `:ui` modules today would
add Gradle wiring, slow incremental builds on a low-spec laptop,
and force `internal` visibility decisions for code that does not yet
need them. The current package layout (`data/`, `ui/<surface>/`,
`notifications/`, `prefs/`) already gives us the boundary clarity a
multi-module split would enforce, without the build-time tax.

Verdict: stay single-module. Reassess when total LoC crosses ~25K
or when we add a second surface.

### 2. Zero logging is the biggest gap

`grep -rn "Log.d\|Log.i\|Log.w\|Log.e\|Timber" app/src/main` returns
nothing. Not a single log line in 67 files. This is the biggest
single debuggability issue and the only one I would call urgent.

The paths most likely to fail silently on a real device are exactly
the ones with no instrumentation:

- Notification scheduling (AlarmManager set, fired, snoozed,
  cancelled, rescheduled at boot).
- Content-observer fan-out from `CalendarContract.Instances`.
- Permission-grant transitions (calendar, notifications, exact
  alarms).
- Calendar-provider failures (insert returning null, update
  returning 0).

When v0.7 reminders failed on user devices, the only artifact we
had was the user's verbal description. With Timber wired in we
would have had timestamped lines from the receiver and the
scheduler, on the user's own logcat.

What Timber gives us, in plain language: it is a tiny wrapper
around Android's built-in `Log` calls. You write `Timber.d("alarm
set for %d", millis)` instead of `Log.d("Scheduler", ...)`. Timber
fills in the class name automatically, and in release builds the
tree we plant is a no-op so nothing ships to user devices. Cost:
one dependency line, one `Application.onCreate` call, ~30 minutes
to wire up and seed the noisy paths with log calls.

### 3. Error handling is sparse and silent

Across `data/EventRepository.kt` and `data/CalendarRepository.kt`
there are no try/catch blocks at all. Across the whole app there
are five (`grep -rn "try {" app/src/main/kotlin`), and four of
those are around `startActivity` calls in `SettingsScreen.kt` and
broadcast-receiver `goAsync` blocks.

What the repositories do instead: short-circuit on null cursors
with `?: return emptyList()`, and return `null`/`false` on insert
or update failure. From `EventRepository.kt`:

    val cursor = contentResolver.query(...) ?: return emptyList()

    suspend fun insertEvent(draft: EventDraft): Long? =
        withContext(Dispatchers.IO) {
            val uri = contentResolver.insert(
                CalendarContract.Events.CONTENT_URI,
                draft.toContentValues(),
            ) ?: return@withContext null
            ContentUris.parseId(uri)
        }

A ViewModel calling `insertEvent` gets back `null` and cannot tell
why: was it a permission revocation, a calendar that no longer
exists, a provider crash, or a write to a read-only sync account?
The user sees the save sheet close with no event added and no
error shown.

Recommended direction: an `Outcome<T>` sealed class returned from
repository boundaries (`Outcome.Success(value)` /
`Outcome.Failure(reason)`), with a small enum of failure reasons
the UI can map to a snackbar. Not a full Either / Result library,
just a project-local type. ViewModels then have something to react
to other than `null`.

### 4. Tests are math-heavy, screen-light

Eight unit tests live under `app/src/test`:

- `data/DayRangeMathTest.kt`
- `data/RecurrenceRuleTest.kt`
- `data/RecurringExceptionMathTest.kt`
- `data/EventDraftTest.kt`
- `notifications/ReminderSchedulerDiffTest.kt`
- `notifications/ReminderTimeMathTest.kt`
- `ui/timeline/OverlapLayoutTest.kt`
- `ui/eventedit/StartShiftDurationTest.kt`

All eight are pure-function tests of date math, recurrence math,
overlap layout math, or scheduler diff math. They are good tests.
None of them are ViewModel tests, repository tests, or Compose UI
tests.

The flows we have zero coverage on are the ones most likely to
break in user-visible ways:

- Storage-mode onboarding picker (the v0.6 work).
- Event save flow (insert, update, recurring-edit scope choice).
- Snooze picker (the v0.7 fix source).
- Calendar-drawer create/delete local calendar.
- Permission-grant cold paths (notification denied, exact alarm
  denied).

`createComposeRule()` plus Robolectric or a real instrumented
runner would let us pin these as regression tests instead of
catching them in user reports.

### 5. File-split discipline is working

`find app/src/main -name "*.kt" -exec wc -l {} + | sort -rn | head
-10` gives the longest five files:

- `MainActivity.kt`: 459 lines. Hosts the NavHost, snackbar host,
  permission state, and the storage-mode onboarding gate. Long
  because it is the app shell, not because it grew organically.
- `ui/month/CalendarDrawer.kt`: 421 lines. Drawer + create-local-
  calendar dialog + delete confirmation. Could split if it grows
  again, fine where it is.
- `ui/settings/SettingsScreen.kt`: 358 lines. One screen, many
  preference rows. Linear, no nested logic.
- `data/EventRepository.kt`: 339 lines. One class, several CRUD
  methods. Splitting by operation would not buy anything.
- `AppViewModel.kt`: 309 lines. Owns top-level app state. Could
  shrink if the storage-mode logic moved to its own use case, but
  not urgent.

Everything else is under 300 lines and most of the screen files
are 200-260. The `WeekScreen.kt` decomposition called out in
`CLAUDE.md` is genuinely clean: `WeekScreen.kt` is 260 lines, with
`TimelineGrid.kt` (216) and several smaller composables alongside
it. No file feels like a god object.

### 6. Manual DI is fine at this scale

No Hilt. ViewModels are constructed via `Factory` companion
objects passed to `viewModel(factory = ...)`. Verified across
eight ViewModels (`grep -rn "Factory" app/src/main/kotlin
--include="*ViewModel*.kt"`): every one uses the same pattern.

Adding Hilt today would mean annotation processors in the build,
a new entry-point ceremony, and another thing for a future
contributor to learn before they can add a screen. The current
pattern is verbose but obvious. Stick with it.

## What I am NOT recommending

- Multi-module Gradle migration. Adds build complexity that does
  not pay back below ~30K lines. Revisit when we add Wear OS or a
  tablet variant.
- Hilt or Dagger. Manual factories work; adding a DI framework now
  trades surface for ceremony.
- A larger architecture rewrite (MVI, Redux-style, etc.). MVVM
  with `StateFlow` is working fine and the screen sizes confirm
  it.
- Static-analysis tooling beyond what we already run. Spotless or
  detekt can wait until something concrete asks for it.

## Recommended next steps (prioritized)

1. **Add Timber.** Plant a `DebugTree` in debug builds, a no-op
   tree in release. About 30 minutes of wiring plus a sweep to add
   log lines at the noisy paths: notification scheduling and
   firing, content-observer events, permission-state transitions,
   and the repository methods that currently return null on
   failure. Biggest debuggability win for the smallest effort. Do
   this first.

2. **Introduce an `Outcome<T>` sealed class at repository
   boundaries.** Replace the silent `?: return emptyList()` and
   `?: return@withContext null` patterns with `Outcome.Failure`
   carrying a reason. ViewModels then surface "could not load
   calendars" or "could not save event" to the user via a
   snackbar, instead of an empty screen or a no-op save. Plays
   well with Timber: log the failure once at the repository
   boundary, react to the typed result in the ViewModel.

3. **Screen tests for the critical paths.** `createComposeRule()`
   tests for: the storage-mode onboarding picker, the event-save
   flow (including recurring-edit scope), and the snooze picker
   (the v0.7 bug source). One Saturday of focused work; future
   regressions get caught in CI instead of in user reports. Order
   chosen so the highest-traffic flows are covered first.

## Out of scope for the next phase

- Refactoring the ViewModel layer. No obvious gains right now.
- UI tests beyond the three flows above.
- Adopting a new architecture pattern (MVI, etc.). MVVM is fine.
- Logging in release builds beyond a no-op tree. Crashlytics-style
  remote reporting is a separate decision.

## Where to look in the code

Future debuggability work should start from these files:

- `app/src/main/kotlin/com/arishawke/asala/calendar/data/EventRepository.kt`
- `app/src/main/kotlin/com/arishawke/asala/calendar/data/CalendarRepository.kt`
- `app/src/main/kotlin/com/arishawke/asala/calendar/notifications/ReminderScheduler.kt`
- `app/src/main/kotlin/com/arishawke/asala/calendar/notifications/ReminderAlarmReceiver.kt`
- `app/src/main/kotlin/com/arishawke/asala/calendar/MainActivity.kt`
