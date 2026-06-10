# Asala Calendar code tour

## What this doc is

- Single-file orientation for someone auditing this codebase.
- Audience: working Android devs, debuggers, vibe coders reviewing their own diffs.
- Names the places that have actually had bugs and points at the tests that pin behavior down.
- Not a tutorial. For change history see [CHANGELOG.md](../CHANGELOG.md).

> Heads up: much of this code was written with AI assistance. The README's disclaimer is the audit trail; this tour is how you verify rather than trust.

## The 60-second tour

Single Gradle module: `:app`. All Kotlin lives under `app/src/main/kotlin/com/arishawke/asala/calendar/`.

| Package | What it contains |
| --- | --- |
| [`data/`](../app/src/main/kotlin/com/arishawke/asala/calendar/data) | Repositories wrapping the Android Calendar Provider, plus pure-function helpers (RRULE, day-range math, recurring exceptions). |
| [`notifications/`](../app/src/main/kotlin/com/arishawke/asala/calendar/notifications) | Reminder scheduling, boot rescheduler, alarm and action receivers, snooze picker Activity. |
| [`ui/`](../app/src/main/kotlin/com/arishawke/asala/calendar/ui) | Compose screens, ViewModels, themed components. Sub-packages mirror screen surfaces: `year/`, `month/`, `week/`, `threeday/`, `day/`, `schedule/`, `eventedit/`, `eventdetail/`, `settings/`, `permissions/`, `calendars/`, `search/`, `notifications/`, `timeline/`, `multidaybars/`, `header/`, `theme/`, `accessibility/`, `components/`, `widget/`. |
| Root | App shell and top-level state: [MainActivity.kt](../app/src/main/kotlin/com/arishawke/asala/calendar/MainActivity.kt), [AppViewModel.kt](../app/src/main/kotlin/com/arishawke/asala/calendar/AppViewModel.kt), [AsalaCalendarApplication.kt](../app/src/main/kotlin/com/arishawke/asala/calendar/AsalaCalendarApplication.kt), [CalendarViewLabel.kt](../app/src/main/kotlin/com/arishawke/asala/calendar/CalendarViewLabel.kt). |

No `:core` / `:data` / `:ui` split. The reasoning: package layout already enforces the boundary clarity, and the codebase is still below the size where modularization pays back.

## Where to start reading

Ranked. Open these in order and the rest should not surprise.

| # | File | Why it matters |
| --- | --- | --- |
| 1 | [MainActivity.kt](../app/src/main/kotlin/com/arishawke/asala/calendar/MainActivity.kt) + [ui/App.kt](../app/src/main/kotlin/com/arishawke/asala/calendar/ui/App.kt) / [ui/AppShell.kt](../app/src/main/kotlin/com/arishawke/asala/calendar/ui/AppShell.kt) | App entry, theme + permission gate, drawer + scaffold + view switcher. Owns the header-dropdown panel (clickable title + chevron + `AnimatedVisibility` panel hosting [ui/header/](../app/src/main/kotlin/com/arishawke/asala/calendar/ui/header)). Overlay surfaces live in [ui/AppOverlays.kt](../app/src/main/kotlin/com/arishawke/asala/calendar/ui/AppOverlays.kt). |
| 2 | [AppViewModel.kt](../app/src/main/kotlin/com/arishawke/asala/calendar/AppViewModel.kt) | Top-level state owner. Every screen subscribes to its flows. `hiddenCalendarIdsFlow` is the user-toggle union with drawer-hidden accounts (`drawerHiddenAccountKeys`) and [StorageModeFilter](../app/src/main/kotlin/com/arishawke/asala/calendar/data/StorageModeFilter.kt) mode hides. `viewedMonth` is pushed up from `MonthScreen` so the header dropdown's chip strip can highlight the current month. |
| 3 | [data/EventRepository.kt](../app/src/main/kotlin/com/arishawke/asala/calendar/data/EventRepository.kt) | Only file that talks to `CalendarContract` for events. |
| 4 | [data/CalendarRepository.kt](../app/src/main/kotlin/com/arishawke/asala/calendar/data/CalendarRepository.kt) | Same idea for the calendars table. Smaller. |
| 5 | [data/ContentResolverFlow.kt](../app/src/main/kotlin/com/arishawke/asala/calendar/data/ContentResolverFlow.kt) | Whole change-notification mechanism in one tiny file. |
| 6 | [notifications/ReminderScheduler.kt](../app/src/main/kotlin/com/arishawke/asala/calendar/notifications/ReminderScheduler.kt) | Diff-based plan for `AlarmManager`. The logic lives here. |
| 7 | [ui/permissions/CalendarPermissionGate.kt](../app/src/main/kotlin/com/arishawke/asala/calendar/ui/permissions/CalendarPermissionGate.kt) | Gates everything else behind `READ_CALENDAR` / `WRITE_CALENDAR`. |
| 8 | [ui/eventedit/EventEditViewModel.kt](../app/src/main/kotlin/com/arishawke/asala/calendar/ui/eventedit/EventEditViewModel.kt) | Biggest user-facing flow with real logic. Save orchestration lives in [EventSave.kt](../app/src/main/kotlin/com/arishawke/asala/calendar/ui/eventedit/EventSave.kt). |
| 9 | [ui/settings/UserPreferences.kt](../app/src/main/kotlin/com/arishawke/asala/calendar/ui/settings/UserPreferences.kt) | DataStore pattern every preference uses, in one file. |
| 10 | [notifications/BootRescheduler.kt](../app/src/main/kotlin/com/arishawke/asala/calendar/notifications/BootRescheduler.kt) | Small file, recent permission bug. Worked example of receiver awkwardness. |

## How the app is wired

Startup and render path:

```
launcher tap
    |
    v
MainActivity.onCreate
    |  reads opening Intent (deep link from a notification)
    |  enableEdgeToEdge()
    v
setContent { App() }
    |
    v
AsalaCalendarTheme (themeMode from AppViewModel)
    |
    v
CalendarPermissionGate (READ_CALENDAR + WRITE_CALENDAR)
    |
    +-- not granted ---> rationale + system permission dialog
    |
    +-- granted -------> notify Application, render below
                         |
                         v
                  StorageModeOnboarding (first run only)
                         |
                         v
                       AppShell
                         |  ModalNavigationDrawer (calendar list, visibility)
                         |  TopAppBar (Today, search, settings, view label)
                         |  FloatingActionButton (create event)
                         |  AnimatedContent on currentView:
                         |    Year / Month / Week / 3-Day / Day / Schedule
                         v
                  per-view Screen + ViewModel
                         |  subscribes to:
                         |    EventRepository.observeEvents(range)
                         |    AppViewModel.hiddenCalendarIdsFlow
                         |    UserPreferences.prefs
                         v
                  EventRepository / CalendarRepository
                         |  ContentResolver -> CalendarContract
                         |  ContentResolverFlow registers a ContentObserver
                         |  observer tick -> re-query -> new list emitted
                         v
                  Android Calendar Provider
                  (system ContentProvider, shared across apps)
```

Reminder side path:

- Provider change fires the application observer.
- Observer calls `ReminderScheduler.rescheduleAll`.
- Scheduler arms and cancels alarms via `AlarmManager`.
- After boot, package replace, or timezone change, [BootRescheduler.kt](../app/src/main/kotlin/com/arishawke/asala/calendar/notifications/BootRescheduler.kt) does the same job.

## Permission gating

Decision tree:

```
app launches
    |
    v
READ_CALENDAR + WRITE_CALENDAR granted?
    |
    +-- no --> CalendarPermissionGate shows rationale + system dialog
    |          (everything below this point does not render)
    |
    +-- yes -> render UI
               |
               v
        Android 13+ POST_NOTIFICATIONS granted?
               |
               +-- no --> reminders post but are silent (degraded UX, no crash)
               |
               +-- yes -> reminders post normally
               |
               v
        SCHEDULE_EXACT_ALARM granted?
               |
               +-- no --> fall back to inexact alarms (degraded, no crash)
               |
               +-- yes -> exact alarms armed
```

> Heads up: any path running outside the gate (receivers, background work, application observers) must check `READ_CALENDAR` itself or the provider call throws `SecurityException`.

## The tricky parts (audit checklist)

Each item names the gotcha, then links the file.

- Theme is collected separately from `uiState` so it can apply before calendar permission is granted. The combined `uiState` flow includes a `ContentObserver` registration that crashes pre-grant. See the `themeMode` declaration in [AppViewModel.kt](../app/src/main/kotlin/com/arishawke/asala/calendar/AppViewModel.kt).
- `BootRescheduler.onReceive` must check `READ_CALENDAR` before touching the provider. `MY_PACKAGE_REPLACED` fires after a fresh install, before any permission grant. See [BootRescheduler.kt](../app/src/main/kotlin/com/arishawke/asala/calendar/notifications/BootRescheduler.kt) and commit `866d894`.
- Receivers use `goAsync()` for off-thread work. Without it the system can kill the process mid-write. See [BootRescheduler.kt](../app/src/main/kotlin/com/arishawke/asala/calendar/notifications/BootRescheduler.kt) and [NotificationActionReceiver.kt](../app/src/main/kotlin/com/arishawke/asala/calendar/notifications/NotificationActionReceiver.kt), commit `f499709`.
- Snooze picker is an `AlertDialog` inside a translucent Activity, not a `ModalBottomSheet`. The earlier sheet-in-floating-Activity combo had layout oddities and crashed on selection. See [SnoozePickerActivity.kt](../app/src/main/kotlin/com/arishawke/asala/calendar/notifications/SnoozePickerActivity.kt).
- Day-range math uses a half-open interval `[startOfDay, startOfNextDay)`. Events at 23:59 must still belong to the "begin" day. DST days must yield 23h or 25h spans, not 24h. See [DayRangeMath.kt](../app/src/main/kotlin/com/arishawke/asala/calendar/data/DayRangeMath.kt) and [DayRangeMathTest.kt](../app/src/test/kotlin/com/arishawke/asala/calendar/data/DayRangeMathTest.kt).
- "Only this event" delete/edit on a recurring series is modeled on the parent's `EXDATE`, not a provider exception row. Attaching any exception row to a recurring parent makes the provider drop the whole series from its `Instances` expansion. Delete appends the occurrence to the parent `EXDATE`; edit inserts a standalone one-off first (so a failed `EXDATE` write rolls back cleanly), then appends to `EXDATE`. Both re-send `DTSTART` and `RRULE` in the same delta or the series truncates at the excluded date. See [ADR-0006](adr/0006-single-occurrence-recurrence-edits-via-exdate.md), [EventMutations.kt](../app/src/main/kotlin/com/arishawke/asala/calendar/data/EventMutations.kt), and the instrumented [RecurringSingleOccurrenceTest.kt](../app/src/androidTest/kotlin/com/arishawke/asala/calendar/data/RecurringSingleOccurrenceTest.kt).
- Editing the start time auto-shifts the end to preserve duration. Edge cases (midnight crossings, all-day toggling, backward shifts) are pinned by [StartShiftDurationTest.kt](../app/src/test/kotlin/com/arishawke/asala/calendar/ui/eventedit/StartShiftDurationTest.kt), commit `ed918a0`.
- Editor uses a fresh `ViewModelStore` per open so re-opening does not inherit the previous draft's title. See [ui/AppShell.kt](../app/src/main/kotlin/com/arishawke/asala/calendar/ui/AppShell.kt) (`ScopedViewModelStore`).
- A single `Mutex` inside [ReminderScheduler.kt](../app/src/main/kotlin/com/arishawke/asala/calendar/notifications/ReminderScheduler.kt) serializes reschedules. Bursts (e.g. CalDAV touching 50 rows) queue rather than race.
- One `runBlocking { userPrefs.prefs.first() }` on the main thread, in `AppViewModel.Factory`. Justified to avoid a first-paint flash. See [AppViewModel.kt](../app/src/main/kotlin/com/arishawke/asala/calendar/AppViewModel.kt). The editor reads its storage mode off `appViewModel.prefs.value` at construction so a second `runBlocking` is no longer needed. Adding new ones needs a review-worthy justification.

### Notification action wiring

```
notification posted by ReminderAlarmReceiver
    |
    +-- user taps body -----> Activity (deep link to event)
    |
    +-- user taps Dismiss --> NotificationActionReceiver
    |                          |  goAsync()
    |                          v
    |                         cancel the system notification
    |
    +-- user taps Snooze ---> (depends on default snooze pref)
                               |
                               +-- default set ----> NotificationActionReceiver
                               |                      |  goAsync()
                               |                      v
                               |                     schedule new alarm at now + default
                               |
                               +-- no default -----> SnoozePickerActivity
                                                      |  AlertDialog in translucent theme
                                                      v
                                                     user picks duration
                                                      |
                                                      v
                                                     schedule new alarm
```

## What's tested and what isn't

### Tested (pure-function unit tests under `app/src/test/kotlin/`)

| Test | Covers |
| --- | --- |
| [DayRangeMathTest.kt](../app/src/test/kotlin/com/arishawke/asala/calendar/data/DayRangeMathTest.kt) | Day-range math, including DST 23h / 25h days. |
| [RecurrenceRuleTest.kt](../app/src/test/kotlin/com/arishawke/asala/calendar/data/RecurrenceRuleTest.kt) | RRULE parsing and serialization. |
| [RecurringExceptionMathTest.kt](../app/src/test/kotlin/com/arishawke/asala/calendar/data/RecurringExceptionMathTest.kt) | "This and following" cut math for recurring edits. |
| [EventDraftTest.kt](../app/src/test/kotlin/com/arishawke/asala/calendar/data/EventDraftTest.kt) | Draft serialization. |
| [EventEndMillisTest.kt](../app/src/test/kotlin/com/arishawke/asala/calendar/data/EventEndMillisTest.kt) | DTEND-or-DURATION fallback for recurring detail rows. |
| [StorageModeFilterTest.kt](../app/src/test/kotlin/com/arishawke/asala/calendar/data/StorageModeFilterTest.kt) | Mode-driven calendar hides (Local only blanks sync). |
| [EventEditCalendarPickerTest.kt](../app/src/test/kotlin/com/arishawke/asala/calendar/data/EventEditCalendarPickerTest.kt) | Event-editor picker filter: writable plus storage-mode constraint. |
| [ReminderSchedulerDiffTest.kt](../app/src/test/kotlin/com/arishawke/asala/calendar/notifications/ReminderSchedulerDiffTest.kt) | Scheduler diff logic (arm vs cancel decisions). |
| [ReminderTimeMathTest.kt](../app/src/test/kotlin/com/arishawke/asala/calendar/notifications/ReminderTimeMathTest.kt) | Reminder trigger time math. |
| [SnoozeResolutionTest.kt](../app/src/test/kotlin/com/arishawke/asala/calendar/notifications/SnoozeResolutionTest.kt) | Snooze alert-id fallback when CalendarAlerts lookup misses. |
| [OverlapLayoutTest.kt](../app/src/test/kotlin/com/arishawke/asala/calendar/ui/timeline/OverlapLayoutTest.kt) | Day/week overlap layout. |
| [StartShiftDurationTest.kt](../app/src/test/kotlin/com/arishawke/asala/calendar/ui/eventedit/StartShiftDurationTest.kt) | Duration-preserving start shifts. |
| [EventSaveTest.kt](../app/src/test/kotlin/com/arishawke/asala/calendar/ui/eventedit/EventSaveTest.kt) | Event-save orchestration: partial failure on rejected reminder leaves event row in place. |

### Not tested

| Area | Notes |
| --- | --- |
| ViewModels | No tests. |
| Repositories | No tests; they touch the provider directly. |
| Receivers | No tests. |
| Application object | No tests. |
| Compose screens | A few previews exist. No Compose UI tests and no Robolectric. The one instrumented test is [RecurringSingleOccurrenceTest.kt](../app/src/androidTest/kotlin/com/arishawke/asala/calendar/data/RecurringSingleOccurrenceTest.kt), which exercises single-occurrence recurrence edits against the real provider. |

> Heads up: a known testing gap is screen tests for the storage-mode onboarding picker, the event-save flow, and the snooze picker.

Run the full suite: `./gradlew :app:testDebugUnitTest`.

## Red flags for auditors

- Silent error swallowing in repositories. Zero `try` / `catch`. Nulls short-circuit with `?: return emptyList()`. A ViewModel cannot distinguish a permission revocation from a read-only sync account.
- New code touching the Calendar Provider without a `READ_CALENDAR` check. Anything outside the gate (receivers, observers, background work) must check `checkSelfPermission` first.
- `runBlocking` on the UI thread. Exactly one today and it is justified in source. Any new one needs a strong reason.
- Lint baseline. `app/lint-baseline.xml` silences pre-existing findings. "Lint clean" means "no new findings beyond the baseline." Open the baseline before trusting the green check.
- No DI framework. Manual `Factory` companion objects everywhere. Fine at this scale; the trap is a future contributor adding Hilt halfway and leaving two patterns.
- Single-module Gradle. Boundaries are convention only; nothing in the build prevents a UI file importing the provider directly. Flag cross-layer imports in review.

## How to verify

| Command | What it checks | Pass criteria |
| --- | --- | --- |
| `./gradlew :app:spotlessKotlinCheck :app:detekt :app:lintDebug :app:testDebugUnitTest` | Format, static analysis, lint (with baseline), and the JVM unit-test suite. | All green. Must pass before any push. |
| `./gradlew :app:assembleRelease` | Release APK with R8 minify and resource shrink. | Build succeeds. Any perf verdict must be measured on this APK, not debug. |
| `adb uninstall com.arishawke.asala.calendar` then `./gradlew :app:installDebug` | Fresh-install path. | Permission gate, storage-mode onboarding, notification permission dialog, and OEM battery advisory all appear and complete cleanly. Required for permission-gated changes per [CONTRIBUTING.md](../CONTRIBUTING.md). |

## Logging

- Debug builds plant `Timber.DebugTree()` in [AsalaCalendarApplication.onCreate](../app/src/main/kotlin/com/arishawke/asala/calendar/AsalaCalendarApplication.kt). Release builds plant nothing and `Timber.*` calls become no-ops at runtime.
- Logs use the calling class as the tag automatically. Example: `AsalaCalendarApplication: onCreate`, `NotificationActionReceiver: applySnooze alertId=14 ...`.
- Watch only this app's logs in real time:
  ```
  adb logcat --pid=$(adb shell pidof com.arishawke.asala.calendar)
  ```
- Capture a snapshot of the last 1000 lines:
  ```
  adb logcat -d -t 1000 > /tmp/asala.log
  ```
- Strategic log points already in place: app startup, calendar permission grant, storage mode switches (LocalOnly/SyncOnly/Hybrid), alarm fire, snooze receiver dispatch, snooze applied, and any caught throwable in the notification action handlers.
- Never log PII (event titles, locations, attendees) or auth tokens. The lines I have today only log ids and counts.

Architectural decisions: [docs/adr/](adr/). Conventions (commit style, lint policy, file-size discipline): [CONTRIBUTING.md](../CONTRIBUTING.md).

## Where to ask for help

- The codebase first. Open the file, then open the sibling test file. Test names encode the rules.
- ADRs under [docs/adr/](adr/) answer "why is it this way." [0001](adr/0001-data-layer-is-calendarcontract.md) on `CalendarContract` as the data layer; [0002](adr/0002-calendar-provider-write-conventions.md) on write conventions; [0003](adr/0003-launcher-icon-source-and-density.md) on the launcher icon; [0004](adr/0004-quality-gates.md) on the quality-gates initiative; [0005](adr/0005-github-native-dependency-scanning.md) on the migration to GitHub-native dependency scanning; [0006](adr/0006-single-occurrence-recurrence-edits-via-exdate.md) on EXDATE-based single-occurrence recurrence edits.
- [CHANGELOG.md](../CHANGELOG.md) records every released change with a one-line reason.
- File an issue on [GitHub](https://github.com/Arishawke/asala-calendar/issues) if something looks wrong. The README disclaimer is the audit trail; bug reports are how it gets corrected.
