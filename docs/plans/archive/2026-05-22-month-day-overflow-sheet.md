# Month-view "+N more" day-overflow sheet implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Surface every event on a dense day from Month view by making the existing `+N more` label open a Material 3 modal bottom sheet listing all events for that date, each tappable to open the standard event detail sheet.

**Architecture:** One new composable (`DayOverflowSheet`) plus three small modifications (`DayCell`, `MonthScreen`, `MainActivity`). Zero ViewModel changes; the sheet reads from the existing `MonthUiState.eventsByDate` map and reuses the existing `AppViewModel.openEventDetail(eventId, instanceMillis)` flow. Sequential sheet transition (await `hide()` before opening detail) so no two bottom sheets are ever on screen at once.

**Tech Stack:** Kotlin 2.x, Compose BOM 2026.05.00, Material 3 (`ModalBottomSheet`, `ListItem`, `rememberModalBottomSheetState`, `Icons.Filled.ExpandMore`), JUnit 4 for JVM tests where needed.

**Spec reference:** [docs/specs/2026-05-22-month-day-overflow-sheet-design.md](../specs/2026-05-22-month-day-overflow-sheet-design.md)

---

## File structure

### New files

```
app/src/main/kotlin/com/arishawke/asala/calendar/ui/month/
  DayOverflowSheet.kt         ; ModalBottomSheet composable; the overflow date formatter
                              ; lives here as an internal top-level val so DayCell can reuse it
```

### Modified files

```
app/src/main/res/values/strings.xml                    ; +2 string resources
app/src/main/kotlin/com/arishawke/asala/calendar/
  ui/month/DayCell.kt                                  ; +onOverflowClick param; +N more becomes a Row
  ui/month/MonthScreen.kt                              ; +overflowDate state; renders DayOverflowSheet
  MainActivity.kt                                      ; wires MonthScreen.onEventClick to vm.openEventDetail
CHANGELOG.md                                           ; new entry under [Unreleased] / Added
```

### Notes on data flow

- `MonthUiState.eventsByDate` is built by `MonthViewModel.uiState` via `events.groupBy { it.startDate(zone) }` ([MonthViewModel.kt:63-65](../../app/src/main/kotlin/com/arishawke/asala/calendar/ui/month/MonthViewModel.kt#L63-L65)). The list per date is NOT sorted, and all-day events are NOT grouped first. The sheet must partition and sort before rendering.
- Multi-day events appear only on their start date (same behaviour as the chips), so the sheet content matches what the cell summarised.
- `AppViewModel.openEventDetail(eventId: Long, instanceMillis: Long)` is the existing entry point used by Week, Day, and Schedule ([MainActivity.kt:229,237,243](../../app/src/main/kotlin/com/arishawke/asala/calendar/MainActivity.kt)).

---

## Task 1: Add string resources

**Files:**
- Modify: `app/src/main/res/values/strings.xml`

- [ ] **Step 1.1: Add the two new string entries**

Insert after the existing `<!-- Event display -->` block (after line 44):

```xml
    <!-- Month view day-overflow sheet -->
    <string name="cd_show_overflow">Show all %1$d events for %2$s</string>
    <string name="sheet_no_events">No events</string>
```

- [ ] **Step 1.2: Compile to verify the new ids resolve**

Run: `./gradlew :app:processDebugResources`
Expected: `BUILD SUCCESSFUL`. The generated `R.java` will now contain `R.string.cd_show_overflow` and `R.string.sheet_no_events`.

---

## Task 2: Create `DayOverflowSheet` composable

**Files:**
- Create: `app/src/main/kotlin/com/arishawke/asala/calendar/ui/month/DayOverflowSheet.kt`

- [ ] **Step 2.1: Write the full file**

```kotlin
/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.ui.month

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.arishawke.asala.calendar.R
import com.arishawke.asala.calendar.data.EventItem
import com.arishawke.asala.calendar.ui.theme.LocalIs24Hour
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// Shared between this sheet and DayCell's overflow-row contentDescription so
// TalkBack reads the same date string that the user sees in the sheet header.
internal val OverflowDateFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DayOverflowSheet(
    date: LocalDate,
    events: List<EventItem>,
    onDismiss: () -> Unit,
    onEventClick: (eventId: Long, instanceMillis: Long) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val zone = ZoneId.systemDefault()

    // upstream eventsByDate is unsorted and does not group all-day first
    val (allDay, timed) = events.partition { it.allDay }
    val ordered = allDay + timed.sortedBy { it.startMillis }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Text(
            text = date.format(OverflowDateFormatter),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )

        if (ordered.isEmpty()) {
            Text(
                text = stringResource(R.string.sheet_no_events),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp),
            )
        } else {
            Column {
                ordered.forEach { ev ->
                    EventRow(
                        event = ev,
                        zone = zone,
                        onClick = { dismissThenOpen(scope, sheetState, ev, onDismiss, onEventClick) },
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
private fun dismissThenOpen(
    scope: CoroutineScope,
    sheetState: SheetState,
    event: EventItem,
    onDismiss: () -> Unit,
    onEventClick: (Long, Long) -> Unit,
) {
    scope.launch {
        // wait for the slide-down animation so the user never sees two sheets
        // on screen at once (NN/g stacked-sheet antipattern).
        sheetState.hide()
        onDismiss()
        onEventClick(event.eventId, event.startMillis)
    }
}

@Composable
private fun EventRow(
    event: EventItem,
    zone: ZoneId,
    onClick: () -> Unit,
) {
    val is24Hour = LocalIs24Hour.current
    val timeFmt = if (is24Hour) DateTimeFormatter.ofPattern("HH:mm")
    else DateTimeFormatter.ofPattern("h:mm a")
    val supporting = if (event.allDay) {
        stringResource(R.string.schedule_all_day)
    } else {
        val start = Instant.ofEpochMilli(event.startMillis).atZone(zone).toLocalTime()
        val end = Instant.ofEpochMilli(event.endMillis).atZone(zone).toLocalTime()
        "${start.format(timeFmt)} - ${end.format(timeFmt)}"
    }

    ListItem(
        leadingContent = {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(32.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(event.displayColor)),
            )
        },
        headlineContent = {
            Text(
                text = event.title.ifBlank { stringResource(R.string.event_no_title) },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        },
        supportingContent = {
            Text(
                text = supporting,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    )
}
```

- [ ] **Step 2.2: Compile to verify the new file builds**

Run: `./gradlew :app:compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`. Compile errors at this stage are usually missing imports; let the compiler tell you which.

---

## Task 3: Modify `DayCell` so `+N more` becomes a clickable Row

**Files:**
- Modify: `app/src/main/kotlin/com/arishawke/asala/calendar/ui/month/DayCell.kt`

- [ ] **Step 3.1: Add the new parameter to `DayCell`**

Find the `DayCell` function signature (around line 45):

```kotlin
@Composable
fun DayCell(
    day: CalendarDay,
    events: List<EventItem>,
    isToday: Boolean,
    modifier: Modifier = Modifier,
    isPast: Boolean = false,
    onClick: (() -> Unit)? = null,
    onEventClick: ((eventId: Long, instanceMillis: Long) -> Unit)? = null,
) {
```

Replace it with:

```kotlin
@Composable
fun DayCell(
    day: CalendarDay,
    events: List<EventItem>,
    isToday: Boolean,
    modifier: Modifier = Modifier,
    isPast: Boolean = false,
    onClick: (() -> Unit)? = null,
    onEventClick: ((eventId: Long, instanceMillis: Long) -> Unit)? = null,
    onOverflowClick: (() -> Unit)? = null,
) {
```

- [ ] **Step 3.2: Add the required imports at the top of DayCell.kt**

Add (alphabetical order with the existing imports):

```kotlin
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
```

- [ ] **Step 3.3: Update the `EventChips` function signature**

Find (around line 92):

```kotlin
@Composable
private fun EventChips(events: List<EventItem>, onEventClick: ((eventId: Long, instanceMillis: Long) -> Unit)?) {
```

Replace with:

```kotlin
@Composable
private fun EventChips(
    date: LocalDate,
    events: List<EventItem>,
    onEventClick: ((eventId: Long, instanceMillis: Long) -> Unit)?,
    onOverflowClick: (() -> Unit)?,
) {
```

- [ ] **Step 3.4: Update the `+overflow more` rendering**

Find inside `EventChips` (around lines 103-110):

```kotlin
        if (overflow > 0) {
            Text(
                text = "+$overflow more",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
```

Replace with:

```kotlin
        if (overflow > 0) {
            val totalCount = events.size
            val dateLabel = date.format(OverflowDateFormatter)
            val rowCd = stringResource(R.string.cd_show_overflow, totalCount, dateLabel)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (onOverflowClick != null) Modifier
                            .clickable(onClick = onOverflowClick)
                            .semantics { contentDescription = rowCd }
                        else Modifier
                    )
                    .padding(horizontal = 4.dp, vertical = 1.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "+$overflow more",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (onOverflowClick != null) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (onOverflowClick != null) {
                    Spacer(modifier = Modifier.width(2.dp))
                    Icon(
                        imageVector = Icons.Filled.ExpandMore,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }
```

- [ ] **Step 3.5: Add the `semantics` and `contentDescription` imports if not already present**

Check the top of DayCell.kt; you should already have these from the P1 bundle:

```kotlin
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
```

If `contentDescription` is missing (the P1 bundle added only `stateDescription`), add it.

- [ ] **Step 3.6: Pass `date` and `onOverflowClick` from `DayCell` into `EventChips`**

Find the `EventChips(...)` call inside `DayCell` (around line 86):

```kotlin
        if (isInMonth && events.isNotEmpty()) {
            EventChips(events = events, onEventClick = onEventClick)
        }
```

Replace with:

```kotlin
        if (isInMonth && events.isNotEmpty()) {
            EventChips(
                date = day.date,
                events = events,
                onEventClick = onEventClick,
                onOverflowClick = onOverflowClick,
            )
        }
```

- [ ] **Step 3.7: Compile to verify**

Run: `./gradlew :app:compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`. If the compiler complains about `OverflowDateFormatter`, it means the import from Task 2 is needed. Since both files are in the same package (`com.arishawke.asala.calendar.ui.month`), no import statement is required.

---

## Task 4: Wire `MonthScreen` with the overflow sheet

**Files:**
- Modify: `app/src/main/kotlin/com/arishawke/asala/calendar/ui/month/MonthScreen.kt`

- [ ] **Step 4.1: Add the `onEventClick` parameter to `MonthScreen`**

Find the function signature (around line 57):

```kotlin
@Composable
fun MonthScreen(
    hiddenCalendarIdsFlow: StateFlow<Set<Long>>,
    onTitleChange: (String) -> Unit,
    todayJumpCounter: StateFlow<Int>,
    modifier: Modifier = Modifier,
    firstDayOfWeekOverride: DayOfWeek? = null,
    dimPastDates: Boolean = false,
    onDayCellClick: (LocalDate) -> Unit = {},
) {
```

Replace with:

```kotlin
@Composable
fun MonthScreen(
    hiddenCalendarIdsFlow: StateFlow<Set<Long>>,
    onTitleChange: (String) -> Unit,
    todayJumpCounter: StateFlow<Int>,
    modifier: Modifier = Modifier,
    firstDayOfWeekOverride: DayOfWeek? = null,
    dimPastDates: Boolean = false,
    onDayCellClick: (LocalDate) -> Unit = {},
    onEventClick: (eventId: Long, instanceMillis: Long) -> Unit = { _, _ -> },
) {
```

- [ ] **Step 4.2: Add overflow state and the sheet renderer**

Add at the top of `MonthScreen`'s body, right after `val state by vm.uiState.collectAsStateWithLifecycle()` (around line 71):

```kotlin
    var overflowDate by remember { mutableStateOf<LocalDate?>(null) }
```

You'll need this import (alphabetised among existing runtime imports):

```kotlin
import androidx.compose.runtime.mutableStateOf
```

(`getValue`, `setValue`, `remember` are already imported in MonthScreen.kt.)

- [ ] **Step 4.3: Plumb `onOverflowClick` into the `DayCell` call**

Find (around line 142):

```kotlin
                        DayCell(
                            day = gd,
                            events = eventsByDate[gd.date].orEmpty(),
                            isToday = gd.date == today,
                            isPast = dimPastDates && gd.date.isBefore(today),
                            onClick = { onDayCellClick(gd.date) },
                            modifier = Modifier
                                .weight(1f)
                                .height(rowHeight),
                        )
```

Replace with:

```kotlin
                        DayCell(
                            day = gd,
                            events = eventsByDate[gd.date].orEmpty(),
                            isToday = gd.date == today,
                            isPast = dimPastDates && gd.date.isBefore(today),
                            onClick = { onDayCellClick(gd.date) },
                            onOverflowClick = { overflowDate = gd.date },
                            modifier = Modifier
                                .weight(1f)
                                .height(rowHeight),
                        )
```

But notice this `DayCell(...)` call is inside `MonthGrid`, not directly in `MonthScreen`. So you also need to plumb `onOverflowClick` and the overflow-set lambda from `MonthScreen` down into `MonthGrid`. Update `MonthGrid`'s signature (around line 120):

```kotlin
@Composable
private fun MonthGrid(
    yearMonth: YearMonth,
    firstDayOfWeek: DayOfWeek,
    eventsByDate: Map<LocalDate, List<EventItem>>,
    today: LocalDate,
    dimPastDates: Boolean,
    onDayCellClick: (LocalDate) -> Unit,
) {
```

Replace with:

```kotlin
@Composable
private fun MonthGrid(
    yearMonth: YearMonth,
    firstDayOfWeek: DayOfWeek,
    eventsByDate: Map<LocalDate, List<EventItem>>,
    today: LocalDate,
    dimPastDates: Boolean,
    onDayCellClick: (LocalDate) -> Unit,
    onOverflowClick: (LocalDate) -> Unit,
) {
```

Then update the `DayCell(...)` call inside `MonthGrid` (the same lines as above) to pass `onOverflowClick = { onOverflowClick(gd.date) }` instead of `onOverflowClick = { overflowDate = gd.date }`.

Final `DayCell` call inside `MonthGrid`:

```kotlin
                        DayCell(
                            day = gd,
                            events = eventsByDate[gd.date].orEmpty(),
                            isToday = gd.date == today,
                            isPast = dimPastDates && gd.date.isBefore(today),
                            onClick = { onDayCellClick(gd.date) },
                            onOverflowClick = { onOverflowClick(gd.date) },
                            modifier = Modifier
                                .weight(1f)
                                .height(rowHeight),
                        )
```

- [ ] **Step 4.4: Update the `MonthGrid(...)` call site inside `HorizontalPager`**

Find (around line 107):

```kotlin
            MonthGrid(
                yearMonth = pageToYearMonth(page, anchorMonth),
                firstDayOfWeek = firstDayOfWeek,
                eventsByDate = state.eventsByDate,
                today = state.today,
                dimPastDates = dimPastDates,
                onDayCellClick = onDayCellClick,
            )
```

Replace with:

```kotlin
            MonthGrid(
                yearMonth = pageToYearMonth(page, anchorMonth),
                firstDayOfWeek = firstDayOfWeek,
                eventsByDate = state.eventsByDate,
                today = state.today,
                dimPastDates = dimPastDates,
                onDayCellClick = onDayCellClick,
                onOverflowClick = { date -> overflowDate = date },
            )
```

- [ ] **Step 4.5: Render the sheet under the pager**

Find the end of the outer `Column(modifier = modifier.fillMaxSize()) { ... }` block (around line 116). Right BEFORE the closing brace, add the sheet:

The block currently ends like:

```kotlin
        ) { page ->
            MonthGrid(...)
        }
    }
}
```

Replace with:

```kotlin
        ) { page ->
            MonthGrid(
                yearMonth = pageToYearMonth(page, anchorMonth),
                firstDayOfWeek = firstDayOfWeek,
                eventsByDate = state.eventsByDate,
                today = state.today,
                dimPastDates = dimPastDates,
                onDayCellClick = onDayCellClick,
                onOverflowClick = { date -> overflowDate = date },
            )
        }
    }

    overflowDate?.let { date ->
        DayOverflowSheet(
            date = date,
            events = state.eventsByDate[date].orEmpty(),
            onDismiss = { overflowDate = null },
            onEventClick = onEventClick,
        )
    }
}
```

- [ ] **Step 4.6: Compile**

Run: `./gradlew :app:compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`.

---

## Task 5: Wire `MainActivity` to pass `onEventClick` to `MonthScreen`

**Files:**
- Modify: `app/src/main/kotlin/com/arishawke/asala/calendar/MainActivity.kt`

- [ ] **Step 5.1: Add the `onEventClick` argument to the `MonthScreen` call**

Find (around line 215):

```kotlin
                    CalendarView.Month -> MonthScreen(
                        hiddenCalendarIdsFlow = vm.hiddenCalendarIdsFlow,
                        onTitleChange = { title = it },
                        todayJumpCounter = vm.todayJumpCounter,
                        firstDayOfWeekOverride = prefs.weekStartsOn,
                        dimPastDates = prefs.dimPastDates,
                        onDayCellClick = { date -> vm.requestJumpTo(date, CalendarView.Day) },
                    )
```

Replace with:

```kotlin
                    CalendarView.Month -> MonthScreen(
                        hiddenCalendarIdsFlow = vm.hiddenCalendarIdsFlow,
                        onTitleChange = { title = it },
                        todayJumpCounter = vm.todayJumpCounter,
                        firstDayOfWeekOverride = prefs.weekStartsOn,
                        dimPastDates = prefs.dimPastDates,
                        onDayCellClick = { date -> vm.requestJumpTo(date, CalendarView.Day) },
                        onEventClick = { eid, millis -> vm.openEventDetail(eid, millis) },
                    )
```

- [ ] **Step 5.2: Build the debug APK to confirm the full graph compiles**

Run: `./gradlew :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`.

---

## Task 6: Lint + tests + CHANGELOG

**Files:**
- Modify: `CHANGELOG.md`

- [ ] **Step 6.1: Run lint and unit tests**

Run: `./gradlew :app:lintDebug :app:testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`. Lint may flag a new contentDescription pattern; review the report at `app/build/reports/lint-results-debug.html` if so.

- [ ] **Step 6.2: Add the CHANGELOG entry**

Open `CHANGELOG.md`. Under the existing `## [Unreleased]` block, add a bullet under `### Added` (right after the "Dim past dates" entry):

```markdown
- Tap "+N more" on a dense Month-view day to open a bottom
  sheet listing every event on that day. Each row taps
  through to the event detail sheet. Fixes the case where
  more than three events on one day were hidden behind the
  "+N more" label. Cell-tap navigation to Day view is
  unchanged.
```

---

## Task 7: Manual verification

Per the spec's verification section. The implementer should walk through these on the paired phone before declaring the feature complete.

- [ ] **Step 7.1: Fresh install**

Uninstall the existing debug build (calendar permission flow needs to be exercised fresh):

```bash
adb uninstall com.arishawke.asala.calendar
./gradlew :app:installDebug
adb shell am start -n com.arishawke.asala.calendar/.MainActivity
```

- [ ] **Step 7.2: Seed a dense day**

Use another calendar app on the phone or device emulator to add 5+ events on a single date so the Month cell shows "+N more".

- [ ] **Step 7.3: Walk through the interactions**

In Asala:

1. Open Month view; navigate to the dense date.
2. Confirm the cell shows three chips and `+N more` in primary colour with a small chevron icon.
3. Tap the date number, then tap an empty area of the cell. Both should jump to Day view at that date. Use the back button to return to Month.
4. Tap `+N more`. A bottom sheet should slide up, titled with the long-form date including year (e.g., "Friday, May 22, 2026").
5. Confirm every event for that day appears, with all-day events first, then timed events sorted by start time.
6. Tap an event row. The bottom sheet should slide down completely, then the event detail sheet should slide up.
7. Close the detail sheet (system back or X). You should land back on the Month grid, NOT back on the overflow sheet.
8. Open the overflow sheet again, then swipe down. Sheet dismisses, Month grid is shown.
9. Open the overflow sheet again, then press system back. Same behaviour as swipe-down.

- [ ] **Step 7.4: TalkBack pass**

Enable TalkBack via Settings > Accessibility. Focus the `+N more` element with explore-by-touch. The announcement should read: "Show all N events for Friday, May 22, 2026, button" (where N matches the total event count, NOT just the overflow count).

- [ ] **Step 7.5: Dark mode pass**

Switch the device to dark theme. Re-open the dense day. Confirm:
- `+N more` and the chevron remain visible against the cell background.
- The sheet's header and ListItem text have adequate contrast.

---

## Suggested commit checkpoints (user-initiated)

Per CLAUDE.md, do not run `git commit` without explicit user go-ahead. When the user gives the go-ahead, these are the suggested logical commits:

```bash
# Commit 1: the spec itself (already drafted)
git add docs/specs/2026-05-22-month-day-overflow-sheet-design.md
git commit -m "docs: spec for Month-view '+N more' day-overflow sheet"

# Commit 2: the plan (this file)
git add docs/plans/2026-05-22-month-day-overflow-sheet.md
git commit -m "docs: implementation plan for Month-view overflow sheet"

# Commit 3: the feature, all code + strings + CHANGELOG together
git add app/src/main/res/values/strings.xml \
        app/src/main/kotlin/com/arishawke/asala/calendar/ui/month/DayOverflowSheet.kt \
        app/src/main/kotlin/com/arishawke/asala/calendar/ui/month/DayCell.kt \
        app/src/main/kotlin/com/arishawke/asala/calendar/ui/month/MonthScreen.kt \
        app/src/main/kotlin/com/arishawke/asala/calendar/MainActivity.kt \
        CHANGELOG.md
git commit -m "feat(month): '+N more' opens day-overflow sheet"
```

The P1 bundle from the prior session (a11y semantics + RemindersRepository fix + CHANGELOG accessibility entry) is a separate logical commit, suggested separately by the user.

---

## Out-of-scope (do NOT touch in this plan)

- Week view chip touch targets (separate spec).
- Hybrid month + day list layout (a possible v0.6+ feature; spec lists this explicitly).
- Per-event chip click in Month view (the existing decision stands; chips remain visual).
- Drag-to-reschedule from inside the sheet.
- Any ViewModel changes.
