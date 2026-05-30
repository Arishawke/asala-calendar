# Code-quality remediation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Land the audit's actionable findings as small, verified changes: close the one real test gap in the recurring-edit logic, harden a rare data-loss window, and apply a set of cheap maintainability / performance / convention cleanups.

**Architecture:** No new architecture. Changes follow patterns already in the codebase: pure `Map<String, Any?>` builders crossed to `ContentValues` only at the provider edge (as `EventDraft.toMap` / `EventCancellation.buildMap` already do), file-level `internal` testable functions, and `remember`-based Compose memoization. One new constants file removes duplicated literals.

**Tech Stack:** Kotlin, Jetpack Compose, Material 3, Android CalendarContract, JUnit4 unit tests (`runBlocking`, no Robolectric, no coroutine-test lib), detekt + ktlint(spotless) + Android Lint gate, Timber logging.

---

## Ground rules (apply to every task)

- **Gate before every push (not just tests):** `./gradlew :app:spotlessKotlinCheck detekt :app:lintDebug :app:testDebugUnitTest`. `testDebugUnitTest` alone does NOT run spotless/detekt/lint; spotless will reflow code.
- **No em dashes anywhere** (code, comments, commits). No emojis.
- **Conventional Commits**, small and single-purpose. **CHANGELOG.md** `[Unreleased]` entry in the same commit only for user-visible changes (noted per task). No AI self-reference / no `Co-Authored-By`.
- **Branch hygiene:** serialize PRs, do not stack. Land PR1 on main (push feature branch -> open PR -> wait for CI -> fast-forward push main from local), then branch PR2 off the new main. Never `git merge main` into a feature branch. Never use `gh pr merge --merge/--squash/--rebase` or the web UI merge button.
- **Calibration:** solo / offline-first. Keep it surgical. Do not refactor adjacent code.

## What changes (file map)

- Create: `app/src/main/kotlin/com/arishawke/asala/calendar/data/TimeUnits.kt` (F4)
- Create: `app/src/test/kotlin/com/arishawke/asala/calendar/data/EventMutationsTest.kt` (F1)
- Create: `app/src/test/kotlin/com/arishawke/asala/calendar/data/EscapeLikePatternTest.kt` (F3)
- Create: `NOTICE` at repo root (F10, optional)
- Modify: `data/EventMutations.kt` (F1 extract + F2 reorder), `data/EventRepository.kt` (F3 extract + F7 log), `data/RemindersRepository.kt` (F7), `data/ContentResolverFlow.kt` (F7), `ui/settings/UserPreferences.kt` (F7 + F4 + F6), `ui/week/TimelineGrid.kt` (F5 + F11 + F4), `ui/components/EventVisual.kt` (F6), and the F4/F6 pattern files listed inline.

---

# PR 1 - tests + the data-loss hardening  (F1, F2, F3)

Branch: `quality-recurring-tests`. One PR, three commits.

### Task 1: Extract and test the this-instance exception map  (F1)

**Files:**
- Modify: `app/src/main/kotlin/com/arishawke/asala/calendar/data/EventMutations.kt`
- Test: `app/src/test/kotlin/com/arishawke/asala/calendar/data/EventMutationsTest.kt` (create)

Context: the `ThisInstance` branch of `updateEventScoped` builds its exception
`ContentValues` inline, so the field logic (bind to parent slot, strip
recurrence) is the one untested seam. The delete path already uses the tested
`EventCancellation.buildMap`. Mirror that pattern: pull a pure `Map` builder out,
reuse the existing `Map<String, Any?>.toCalendarEventContentValues()`.

- [x] **Step 1: Write the failing test**

Create `app/src/test/kotlin/com/arishawke/asala/calendar/data/EventMutationsTest.kt`
(match `EventCancellationTest`/`EventDraftTest` assertion style):

```kotlin
/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.data

import android.provider.CalendarContract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class EventMutationsTest {
    private fun draft(rrule: String? = "FREQ=DAILY") = EventDraft(
        calendarId = 3L,
        title = "Standup",
        description = null,
        location = null,
        startMillis = 1_700_000_000_000L,
        endMillis = 1_700_003_600_000L,
        allDay = false,
        eventTimezone = "America/New_York",
        rrule = rrule,
    )

    // A this-instance edit becomes a non-recurring exception bound to the
    // parent series' slot. Pre-fix this field assembly lived inline in
    // updateEventScoped and was untested.
    @Test
    fun `this-instance exception binds to parent slot and drops recurrence`() {
        val map = thisInstanceExceptionMap(
            draft = draft(rrule = "FREQ=DAILY"),
            parentEventId = 7L,
            instanceMillis = 1_700_000_000_000L,
            parentAllDay = false,
        )
        assertEquals(7L, map[CalendarContract.Events.ORIGINAL_ID])
        assertEquals(1_700_000_000_000L, map[CalendarContract.Events.ORIGINAL_INSTANCE_TIME])
        assertEquals(0, map[CalendarContract.Events.ORIGINAL_ALL_DAY])
        assertEquals(1_700_003_600_000L, map[CalendarContract.Events.DTEND])
        assertFalse(map.containsKey(CalendarContract.Events.RRULE))
        assertFalse(map.containsKey(CalendarContract.Events.DURATION))
    }

    // All-day parents need ORIGINAL_ALL_DAY=1 or the provider cannot match the
    // exception against the UTC-midnight slot and the original still shows.
    @Test
    fun `all-day this-instance exception marks ORIGINAL_ALL_DAY`() {
        val map = thisInstanceExceptionMap(
            draft = draft(rrule = "FREQ=WEEKLY"),
            parentEventId = 9L,
            instanceMillis = 1_700_000_000_000L,
            parentAllDay = true,
        )
        assertEquals(1, map[CalendarContract.Events.ORIGINAL_ALL_DAY])
    }
}
```

- [x] **Step 2: Run it; verify it fails to compile (function not defined)**

Run: `./gradlew :app:testDebugUnitTest --tests "*EventMutationsTest*"`
Expected: FAIL - unresolved reference `thisInstanceExceptionMap`.

- [x] **Step 3: Add the pure builder and call it from the extension function**

In `data/EventMutations.kt`, add this file-level function (after the imports,
before `updateEventScoped`):

```kotlin
// Field assembly for a "this occurrence only" edit: start from the draft, bind
// it to the parent series' slot, and strip recurrence so the exception is a
// one-off. Pure + map-shaped so it unit-tests without a ContentResolver, like
// EventCancellation.buildMap.
internal fun thisInstanceExceptionMap(
    draft: EventDraft,
    parentEventId: Long,
    instanceMillis: Long,
    parentAllDay: Boolean,
): Map<String, Any?> = buildMap {
    putAll(draft.toMap())
    put(CalendarContract.Events.ORIGINAL_ID, parentEventId)
    put(CalendarContract.Events.ORIGINAL_INSTANCE_TIME, instanceMillis)
    put(CalendarContract.Events.ORIGINAL_ALL_DAY, if (parentAllDay) 1 else 0)
    remove(CalendarContract.Events.RRULE)
    remove(CalendarContract.Events.DURATION)
    put(CalendarContract.Events.DTEND, draft.endMillis)
}
```

Then replace the `ThisInstance` branch body of `updateEventScoped` (currently
builds `cv` via `draft.toContentValues().apply { ... }`) with:

```kotlin
        RecurringEditScope.ThisInstance -> {
            require(instanceMillis != null)
            val cv = thisInstanceExceptionMap(draft, eventId, instanceMillis, parentAllDay)
                .toCalendarEventContentValues()
            val uri = insert(CalendarContract.Events.CONTENT_URI, cv) ?: return@withContext null
            ContentUris.parseId(uri).takeIf { it > 0L }
        }
```

(`toCalendarEventContentValues` is already `internal` in `EventCancellation.kt`,
same package, no import needed. `ContentValues` import in `EventMutations.kt` is
still used by the `ThisAndFollowing` branch, so leave it.)

- [x] **Step 4: Run the test; verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "*EventMutationsTest*"`
Expected: PASS (2 tests).

- [x] **Step 5: Full gate, then commit**

```bash
./gradlew :app:spotlessKotlinCheck detekt :app:lintDebug :app:testDebugUnitTest
git add app/src/main/kotlin/com/arishawke/asala/calendar/data/EventMutations.kt \
        app/src/test/kotlin/com/arishawke/asala/calendar/data/EventMutationsTest.kt
git commit -m "refactor(data): extract testable this-instance exception map"
```
No CHANGELOG (internal refactor + tests, no behavior change).

### Task 2: Insert the split before truncating the parent  (F2)

**Files:** Modify `data/EventMutations.kt` (the `ThisAndFollowing` branch of
`updateEventScoped`).

Decision (per your "use best practice"): currently the parent RRULE is truncated
first, then the split event is inserted; a failed insert silently loses the
following occurrences. Reorder to insert first. Worst case becomes a visible,
recoverable duplicate instead of silent loss, matching the save layer's
documented "better that data survives" stance (see `EventSaveTest`). This cannot
be unit-tested (it is ContentResolver orchestration); verify on device.

- [x] **Step 1: Reorder the branch**

Replace the `ThisAndFollowing` branch body of `updateEventScoped` with:

```kotlin
        RecurringEditScope.ThisAndFollowing -> {
            require(instanceMillis != null && parentRrule != null)
            val newRrule =
                RecurrenceExceptionMath.appendUntil(
                    parentRrule,
                    RecurrenceExceptionMath.untilUtcForTruncation(instanceMillis, parentAllDay),
                )
            // Insert the split series first. If the parent truncation then
            // fails the user sees a recoverable duplicate rather than silently
            // losing the following occurrences (survive-over-rollback, as in
            // the save layer).
            val uri = insert(CalendarContract.Events.CONTENT_URI, draft.toContentValues())
                ?: return@withContext null
            val newId = ContentUris.parseId(uri).takeIf { it > 0L } ?: return@withContext null
            val parentUri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
            val parentCv =
                ContentValues().apply {
                    put(CalendarContract.Events.RRULE, newRrule)
                }
            if (update(parentUri, parentCv, null, null) <= 0) {
                Timber.e(
                    "ThisAndFollowing: split %d inserted but parent %d truncation failed",
                    newId,
                    eventId,
                )
            }
            newId
        }
```

- [x] **Step 2: Add the Timber import**

In `data/EventMutations.kt` imports, add (keep alphabetical with existing imports):

```kotlin
import timber.log.Timber
```

- [x] **Step 3: Full gate**

Run: `./gradlew :app:spotlessKotlinCheck detekt :app:lintDebug :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL (existing `RecurringExceptionMathTest` still green).

- [x] **Step 4: CHANGELOG + commit**

Add under `CHANGELOG.md` `[Unreleased]`:
```markdown
### Fixed
- Editing "this and following" on a recurring event no longer risks dropping the
  later occurrences if the underlying provider write is interrupted.
```
```bash
git add app/src/main/kotlin/com/arishawke/asala/calendar/data/EventMutations.kt CHANGELOG.md
git commit -m "fix(data): insert split before truncating parent on this-and-following edit"
```

- [x] **Step 5: Device smoke (before PR)** - create a daily recurring event, edit
  "this and following" from a middle occurrence (change the title), confirm the
  earlier occurrences keep the old title and this-onward show the new one, with no
  duplicates and no missing days. See PR1 verification.

### Task 3: Make `escapeLikePattern` testable and test it  (F3)

**Files:**
- Modify: `data/EventRepository.kt` (move `escapeLikePattern` to file scope)
- Test: `app/src/test/kotlin/com/arishawke/asala/calendar/data/EscapeLikePatternTest.kt` (create)

Context: search escaping (`%`, `_`, `\` are SQL LIKE metacharacters) is untested
and the function is a `private` member, so it cannot be unit-tested in isolation.
Move it to a file-level `internal fun` (same idiom as
`toCalendarEventContentValues`). The recolor transform is already covered by
`VisibilityAndOverridesTest`; the debounce flow is a stable library operator and
is deferred (see "Deferred").

- [x] **Step 1: Write the failing test**

Create `app/src/test/kotlin/com/arishawke/asala/calendar/data/EscapeLikePatternTest.kt`:

```kotlin
/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.data

import org.junit.Assert.assertEquals
import org.junit.Test

class EscapeLikePatternTest {
    // LIKE metacharacters must be escaped with the ESCAPE '\' char so a search
    // for "50%" matches the literal, not "any suffix".
    @Test
    fun `escapes percent underscore and backslash`() {
        assertEquals("""50\% off""", escapeLikePattern("50% off"))
        assertEquals("""a\_b""", escapeLikePattern("a_b"))
        assertEquals("""x\\y""", escapeLikePattern("""x\y"""))
    }

    @Test
    fun `escapes a run of only metacharacters`() {
        assertEquals("""\%\_\\""", escapeLikePattern("""%_\"""))
    }

    @Test
    fun `passes ordinary text through unchanged`() {
        assertEquals("dentist", escapeLikePattern("dentist"))
    }
}
```

- [x] **Step 2: Run it; verify it fails (private/unresolved)**

Run: `./gradlew :app:testDebugUnitTest --tests "*EscapeLikePatternTest*"`
Expected: FAIL - `escapeLikePattern` not accessible / unresolved.

- [x] **Step 3: Move the function to file scope**

In `data/EventRepository.kt`, delete the `private fun escapeLikePattern(...)`
method from inside the `EventRepository` class body and add it at file level
(below the class, alongside other top-level data helpers), changing `private`
to `internal`:

```kotlin
internal fun escapeLikePattern(query: String): String {
    val sb = StringBuilder(query.length + 8)
    for (c in query) {
        when (c) {
            '\\', '%', '_' -> sb.append('\\').append(c)
            else -> sb.append(c)
        }
    }
    return sb.toString()
}
```

The existing call inside `searchEvents` (`escapeLikePattern(query.trim())`) is
unchanged - it resolves to the file-level function.

- [x] **Step 4: Run the test; verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "*EscapeLikePatternTest*"`
Expected: PASS (3 tests).

- [x] **Step 5: Full gate + commit**

```bash
./gradlew :app:spotlessKotlinCheck detekt :app:lintDebug :app:testDebugUnitTest
git add app/src/main/kotlin/com/arishawke/asala/calendar/data/EventRepository.kt \
        app/src/test/kotlin/com/arishawke/asala/calendar/data/EscapeLikePatternTest.kt
git commit -m "refactor(data): extract escapeLikePattern to a testable function"
```
No CHANGELOG (internal).

### PR1 verification + publish
- [x] Full gate green.
- [x] Device smoke for Task 2 (recurring this-and-following edit) on the Pixel:
  `adb uninstall com.arishawke.asala.calendar` then `./gradlew :app:installDebug`,
  reproduce the edit, confirm no lost/duplicate occurrences.
- [x] Push branch -> open PR -> wait for CI green -> fast-forward push `main` from
  local. Verify GitHub's `main` advanced (origin multi-pushes; a Forgejo-only
  success can mask a GitHub rejection).

---

# PR 2 - cleanups  (F4, F6, F11, F7)

Branch `quality-cleanups` off the new main. Four commits. No behavior change.

### Task 4: Centralize duplicated time constants  (F4)

**Files:**
- Create: `app/src/main/kotlin/com/arishawke/asala/calendar/data/TimeUnits.kt`
- Modify (pattern, one per file): remove the local `private const` and use
  `TimeUnits.<name>` instead, adding `import com.arishawke.asala.calendar.data.TimeUnits`:
  - `ui/eventdetail/EventWhenFormatter.kt` (`MinutesPerHour`)
  - `ui/timeline/DragReschedule.kt` (`MinutesPerHour`, `MillisPerMinute`)
  - `ui/timeline/NowLineMarker.kt` (`MinutesPerHour`)
  - `ui/week/TimelineGrid.kt` (`HoursPerDay`, `MaxStartHour`, `MinutesPerHour`)
  - `ui/settings/WorkingHoursRangeRow.kt` (`HoursPerDay`, `MaxStartHour`)
  - `ui/settings/UserPreferences.kt` (`HoursPerDay` in the companion)
  - `notifications/SnoozeApplier.kt` (`MillisPerMinute`)
  - `ui/schedule/ScheduleScreen.kt` (`MillisPerMinute`)
- Leave as-is: `ui/week/RescheduleDragState.kt` `private const val MinutesPerHour = 60f`
  (a `Float`; different type, isolated, not worth widening the shared object). Note
  it in the commit body.

- [x] **Step 1: Create the constants file**

```kotlin
/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.data

// Single source of truth for wall-clock unit conversions used across the
// timeline, schedule, settings, and reminder code.
internal object TimeUnits {
    const val MinutesPerHour = 60
    const val MillisPerMinute = 60_000L
    const val HoursPerDay = 24
    const val MaxStartHour = HoursPerDay - 1
}
```

- [x] **Step 2: Apply the pattern per file**

For each file above: delete the listed `private const val ...` declaration(s),
add the `TimeUnits` import, and replace each bare usage of the removed name with
`TimeUnits.<name>` (e.g. `MinutesPerHour` -> `TimeUnits.MinutesPerHour`). Grep
within each file to find usages: `grep -n "MinutesPerHour\|MillisPerMinute\|HoursPerDay\|MaxStartHour" <file>`.

- [x] **Step 3: Full gate (spotless will reformat; that is expected)**

Run: `./gradlew :app:spotlessKotlinCheck detekt :app:lintDebug :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL. Detekt's `MagicNumber` baseline may now have stale
entries for the removed consts - if detekt reports an unused-baseline warning,
regenerate the baseline with `./gradlew detektBaseline` and stage the updated
`config/detekt/baseline.xml`.

- [x] **Step 4: Commit**

```bash
git add app/src/main/kotlin/com/arishawke/asala/calendar/data/TimeUnits.kt <modified files> config/detekt/baseline.xml
git commit -m "refactor: centralize duplicated time-unit constants"
```
No CHANGELOG (internal).

### Task 5: Remove em dashes; normalize time-range hyphen  (F6)

**Files (em dash `—` in comments):** `ui/components/EventVisual.kt` (3),
`data/EventDetail.kt`, `ui/eventdetail/Linkify.kt`,
`ui/eventedit/CustomReminderDialog.kt`, `ui/multidaybars/WeekBucketer.kt`,
`ui/settings/UserPreferences.kt`, `app/src/test/.../ui/timeline/OverlapLayoutTest.kt`.
**Files (en dash `–` in user-visible time-range strings):**
`ui/components/EventVisual.kt:220`, `ui/settings/WorkingHoursRangeRow.kt:57`.

- [x] **Step 1: Replace the dashes**

In each comment, replace `—` with a hyphen `-` or rephrase to a period so it
reads naturally. In the two time-range strings replace the en dash with a hyphen:
`"${...} – ${...}"` -> `"${...} - ${...}"`.

- [x] **Step 2: Verify none remain**

Run: `git grep -nP '[\x{2013}\x{2014}]' -- '*.kt'`
Expected: no output.

- [x] **Step 3: Full gate + commit**

```bash
./gradlew :app:spotlessKotlinCheck detekt :app:lintDebug :app:testDebugUnitTest
git add -A
git commit -m "style: remove em dashes and normalize time-range hyphen"
```
No CHANGELOG (cosmetic; the visible change is one dash glyph in the time label).

### Task 6: Replace the dead loop variable  (F11)

**Files:** Modify `ui/week/TimelineGrid.kt` (`HourGuideLines`, ~line 262).

- [x] **Step 1: Swap the loop**

Replace `for (h in 0..23) {` with `repeat(24) {` (the body never uses `h`). The
closing brace and body are unchanged.

- [x] **Step 2: Gate.** If detekt's `UnusedPrivateProperty` baseline entry for
  `$h` is now stale, regenerate: `./gradlew detektBaseline` and stage
  `config/detekt/baseline.xml`.

Run: `./gradlew :app:spotlessKotlinCheck detekt :app:lintDebug :app:testDebugUnitTest`

- [x] **Step 3: Commit**

```bash
git add app/src/main/kotlin/com/arishawke/asala/calendar/ui/week/TimelineGrid.kt config/detekt/baseline.xml
git commit -m "refactor(week): use repeat over an unused loop index"
```

### Task 7: Log silent provider failures  (F7)

**Files:** `data/EventRepository.kt`, `data/RemindersRepository.kt`,
`data/ContentResolverFlow.kt`, `ui/settings/UserPreferences.kt`. Add `Timber.w`
at each swallow point. Observability only, no behavior change. Do NOT touch the
receiver-level crash guards in `notifications/` (those are correct).

- [x] **Step 1: `EventRepository.queryInstances` null cursor**

Replace `) ?: return emptyList()` (the `contentResolver.query(...)` in
`queryInstances`) with:

```kotlin
            ) ?: run {
                Timber.w("queryInstances: null cursor for %s..%s", startDate, endExclusive)
                return emptyList()
            }
```
Add `import timber.log.Timber` to the file.

- [x] **Step 2: `RemindersRepository.setReminder` rejected insert**

Replace the final line `contentResolver.insert(CalendarContract.Reminders.CONTENT_URI, cv) != null` with:

```kotlin
        val inserted = contentResolver.insert(CalendarContract.Reminders.CONTENT_URI, cv) != null
        if (!inserted) Timber.w("setReminder: provider rejected reminder insert for event %d", eventId)
        inserted
```
Add `import timber.log.Timber`.

- [x] **Step 3: `ContentResolverFlow` SecurityException**

In the `catch (_: SecurityException)` block, log before yielding `false`:

```kotlin
        } catch (e: SecurityException) {
            Timber.w(e, "registerContentObserver denied for %s", uri)
            false
        }
```
Add `import timber.log.Timber` if not present.

- [x] **Step 4: `UserPreferences.parseEnum` fallback**

Replace the body of `parseEnum` with a logged fallback:

```kotlin
    private inline fun <T> parseEnum(raw: String?, default: T, of: (String) -> T): T =
        raw?.let {
            runCatching { of(it) }.getOrElse { e ->
                Timber.w(e, "discarding unparseable stored enum value %s", it)
                null
            }
        } ?: default
```
Add `import timber.log.Timber` if not present. (Leave the two inline
`DayOfWeek.valueOf` sites; they are low value and parseEnum covers the pattern.)

- [x] **Step 5: Full gate + commit**

```bash
./gradlew :app:spotlessKotlinCheck detekt :app:lintDebug :app:testDebugUnitTest
git add -A
git commit -m "refactor(data): log swallowed provider read/write failures"
```
No CHANGELOG (internal observability).

### PR2 verification + publish
- [x] Full gate green; `git grep -nP '[\x{2013}\x{2014}]' -- '*.kt'` empty.
- [x] Push -> PR -> CI -> FF push main. Verify GitHub main advanced.

---

# PR 3 - timeline memoization  (F5)

Branch `quality-timeline-perf` off the new main. One commit. Measure on a release
build per project policy.

### Task 8: Memoize per-day clipping and overlap layout

**Files:** Modify `ui/week/TimelineGrid.kt`.

Context: `events` reaches `TimelineGrid` as an (unstable) `List`. Today line 117
re-filters/clips per column and `DayColumn` (line ~203) runs `crowdedLayout`
un-`remember`ed, so the now-line tick and drags re-do O(n log n) work for every
visible column. Precompute the per-day clipped lists once (stable references),
then memoize the overlap layout inside each column.

- [x] **Step 1: Precompute the per-day clipped events**

In `TimelineGrid`, immediately before the `Box(` (currently ~line 104), add:

```kotlin
    // Clip events to each day once per data change so columns get stable list
    // references; without this the now-line tick re-filters every column.
    val timedByDay = remember(events, days, zone) {
        val timed = events.filter { !it.allDay }
        days.associateWith { d -> timed.mapNotNull { clipToDay(it, d, zone) } }
    }
```

Then in the `days.forEachIndexed { index, date -> ... }` loop, replace the
`DayColumn(...)` argument

```kotlin
                    events = events.filter { !it.allDay }.mapNotNull { clipToDay(it, date, zone) },
```
with
```kotlin
                    events = timedByDay.getValue(date),
```

- [x] **Step 2: Memoize the overlap layout inside the column**

In `DayColumn`, replace `val crowded = crowdedLayout(events, threshold)` with:

```kotlin
        val crowded = remember(events, threshold) { crowdedLayout(events, threshold) }
```
(`remember` is already imported.)

- [x] **Step 3: Gate**

Run: `./gradlew :app:spotlessKotlinCheck detekt :app:lintDebug :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL.

- [x] **Step 4: Release-build smoke**

Build the release APK and confirm Week/Day timeline scroll + drag are smooth and
visually unchanged (event positions, overflow chips, now-line identical):
`./gradlew :app:assembleRelease` then install
`app/build/outputs/apk/release/app-release.apk`. (Perf verdicts are measured on
R8 builds, not debug.)

- [x] **Step 5: CHANGELOG + commit**

Add under `[Unreleased]`:
```markdown
### Changed
- Smoother Week and Day timelines: event clipping and overlap layout are now
  cached and no longer recomputed on every frame.
```
```bash
git add app/src/main/kotlin/com/arishawke/asala/calendar/ui/week/TimelineGrid.kt CHANGELOG.md
git commit -m "perf(week): memoize per-day clipping and overlap layout"
```

### PR3 verification + publish
- [x] Gate green; release smoke confirms identical rendering.
- [x] Push -> PR -> CI -> FF push main.

---

# PR 4 - optional polish  (F9, F10)  - confirm before doing

Lower value; only if you want them. Branch `quality-polish` off the new main.

### Task 9 (optional): a11y touch target + mini-month cue  (F9)

**Files:** `ui/week/OverflowChip.kt`, `ui/header/MiniMonthPanel.kt`.

- [x] In `OverflowChip.kt:37` raise `.heightIn(min = 24.dp)` to
  `.heightIn(min = 40.dp)` (the chip sits inside a dense column; 40dp is a
  reasonable middle ground that does not overlap neighboring chips - verify
  visually it does not clip in a crowded cluster).
- [x] In `MiniMonthPanel.kt`, expose event presence to TalkBack as a non-color
  cue: add a `stateDescription` (or contentDescription suffix) on the day cell
  when it has events, using a new plural/string resource. (Read the exact dot/cell
  code at execution time; this is a semantics addition, not a layout change.)
- [x] Gate; verify with TalkBack on device. CHANGELOG `### Changed` (accessibility).
- [x] Commit `a11y(week,header): enlarge overflow chip target and announce mini-month events`.

### Task 10 (optional): third-party OSS attribution  (F10)

**Files:** Create `NOTICE` at repo root; modify `ui/settings/SettingsScreen.kt`
(About section) + a new string resource.

- [x] Create `NOTICE` listing each direct dependency and its license (AndroidX /
  Compose / DataStore / kotlinx-serialization / Timber = Apache-2.0;
  kizitonwose/calendar = MIT) with copyright holders.
- [x] Add an "Open source licenses" `ListItem` to the About section (mirror the
  existing `about-source` row) that opens the GitHub `NOTICE` URL or shows the
  text. Add the `settings_about_licenses` string.
- [x] Gate. CHANGELOG `### Added`. Commit `docs: add third-party license attribution`.

---

# Deferred (do NOT do without a decision)

- **Full `SearchViewModel` debounce/flow test.** Requires adding the
  `kotlinx-coroutines-test` dependency (new dep -> stop-and-confirm). The 200ms
  debounce and `flatMapLatest`/`combine` are stable library operators; the
  app-specific logic (escaping, recolor) is now covered by F3 + the existing
  `VisibilityAndOverridesTest`. Recommendation at this scale: skip unless we add
  coroutine-test for other reasons.
- **AppViewModel / UserPreferences / EventEditViewModel split (audit F8).**
  Cohesive; the over-engineering lens counter-voted. Split opportunistically when
  already editing, using the existing `AppViewModelSheetState.kt` extension
  pattern. Not a scheduled task.

---

## Self-review (done at write time)

- **Coverage vs audit:** F1, F2, F3 -> PR1; F4, F6, F11, F7 -> PR2; F5 -> PR3;
  F9, F10 -> optional PR4; F3c + F8 -> Deferred with rationale. All audit findings
  are routed.
- **Placeholders:** none in the committed tasks; the only "read at execution time"
  notes are confined to the explicitly-optional Task 9 mini-month semantics and
  Task 10 string wiring.
- **Type/name consistency:** `thisInstanceExceptionMap` (Task 1) is referenced
  identically in Task 1 only; `TimeUnits.<name>` (Task 4) matches the object
  defined in Task 4 Step 1; `escapeLikePattern` (Task 3) keeps its existing
  signature; `timedByDay` (Task 8) is defined and used within `TimelineGrid`.
- **Calibration:** no new runtime deps in the committed scope; no enterprise
  ceremony; every change is small and traceable to an audit finding.
