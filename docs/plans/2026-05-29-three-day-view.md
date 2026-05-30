# 3-Day View Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a Google-Calendar-style 3-day timeline that pages three days at a time, sitting between Week and Day in the view list, rolling from a date (not snapped to a week boundary), with no crowded-overflow chip and a +/-60-day swipe window.

**Architecture:** A new `ui/threeday/` package owns its own pager and viewmodel but renders through Week's existing composables. A pure `ThreeDayPageMath` maps page <-> date by rolling 3 days from an anchor (`Math.floorDiv` for negative offsets). `ThreeDayViewModel` copies `DayViewModel`'s fixed-window single-subscription model. `ThreeDayScreen` drives a `HorizontalPager` and renders each page via the existing `WeekPage` with a 3-element day list. `WeekPage` and `formatWeekRange` widen `private -> internal`; an additive `enableOverflow` param (default `true`) lets 3-day opt out of the "+N" chip while leaving Week byte-identical. The `CalendarView` enum gains `ThreeDay`; three compiler-enforced exhaustive `when`s are the safety net for switch-site coverage.

**Tech Stack:** Kotlin, Jetpack Compose, Material 3, JUnit (JVM unit tests).

**Design spec:** [docs/specs/2026-05-29-three-day-view-design.md](../specs/2026-05-29-three-day-view-design.md)

**Branch:** `feat/three-day-view` (create off current `main` before Task 1: `git switch -c feat/three-day-view`).

---

## File structure

- Create `app/src/main/kotlin/com/arishawke/asala/calendar/ui/threeday/ThreeDayPageMath.kt`: pure `pageStart` / `pageForDate` helpers + `ThreeDayPageSize`.
- Create `app/src/test/kotlin/com/arishawke/asala/calendar/ui/threeday/ThreeDayPageMathTest.kt`: unit tests for the page math.
- Create `app/src/main/kotlin/com/arishawke/asala/calendar/ui/threeday/ThreeDayViewModel.kt`: fixed-window viewmodel modeled on `DayViewModel`.
- Create `app/src/main/kotlin/com/arishawke/asala/calendar/ui/threeday/ThreeDayScreen.kt`: pager + jump handling, renders `WeekPage`.
- Modify `app/src/main/kotlin/com/arishawke/asala/calendar/ui/week/WeekScreen.kt`: `WeekPage` and `formatWeekRange` `private -> internal`; add `enableOverflow` param to `WeekPage`, thread to `TimelineGrid`.
- Modify `app/src/main/kotlin/com/arishawke/asala/calendar/ui/week/TimelineGrid.kt`: add `enableOverflow` param; gate the `onOverflow` callback.
- Modify `app/src/main/kotlin/com/arishawke/asala/calendar/AppViewModel.kt`: add `ThreeDay` enum value.
- Modify `app/src/main/kotlin/com/arishawke/asala/calendar/CalendarViewLabel.kt`: add label branch.
- Modify `app/src/main/kotlin/com/arishawke/asala/calendar/ui/CalendarViewSwitcher.kt`: add `ThreeDay` route + import.
- Modify `app/src/main/kotlin/com/arishawke/asala/calendar/ui/header/HeaderDropdownPanel.kt`: add `ThreeDay` to the mini-month branch.
- Modify `app/src/main/res/values/strings.xml`: add `view_three_day`.
- Modify `CHANGELOG.md`, `docs/ROADMAP.md`.

**Not modified (verified auto-pickup):** `ui/month/CalendarDrawer.kt` and `ui/settings/SettingsRows.kt` build their lists from `CalendarView.entries.filter { it.isAlwaysVisible() || tasksEnabled }` + `view.label()`, so 3-Day appears with no edit. `ui/settings/UserPreferences.kt` persists by name with a `Month` fallback (`CalendarView.valueOf`), so no migration. `AppShell.kt` routes mini-month taps via `requestJumpTo(date, state.currentView)`, so the cross-view jump targets ThreeDay automatically. The per-screen pending-jump filters in `WeekScreen`/`DayScreen`/`ScheduleScreen`/month screens already ignore foreign jumps; no change.

---

## Task 1: Pure 3-day page math

**Files:**
- Create: `app/src/main/kotlin/com/arishawke/asala/calendar/ui/threeday/ThreeDayPageMath.kt`
- Test: `app/src/test/kotlin/com/arishawke/asala/calendar/ui/threeday/ThreeDayPageMathTest.kt`

- [x] **Step 1: Write the failing test**

Create `ThreeDayPageMathTest.kt`:

```kotlin
/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.ui.threeday

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ThreeDayPageMathTest {
    private val anchor: LocalDate = LocalDate.of(2026, 3, 2)
    private val center = 20

    @Test fun `center page starts at the anchor`() {
        assertEquals(anchor, pageStart(anchor, center, center))
    }

    @Test fun `next page is anchor plus three days`() {
        assertEquals(anchor.plusDays(3), pageStart(anchor, center + 1, center))
    }

    @Test fun `previous page is anchor minus three days`() {
        assertEquals(anchor.minusDays(3), pageStart(anchor, center - 1, center))
    }

    @Test fun `consecutive pages tile contiguously without overlap`() {
        val p0 = pageStart(anchor, center, center)
        val p1 = pageStart(anchor, center + 1, center)
        // page center covers [p0, p0+3); the next page begins exactly at p0+3.
        assertEquals(p0.plusDays(3), p1)
    }

    @Test fun `pageForDate inverts pageStart at the anchor`() {
        assertEquals(center, pageForDate(anchor, anchor, center))
    }

    @Test fun `every day of a page maps to that page`() {
        assertEquals(center, pageForDate(anchor, anchor, center))
        assertEquals(center, pageForDate(anchor, anchor.plusDays(1), center))
        assertEquals(center, pageForDate(anchor, anchor.plusDays(2), center))
        // The day after rolls into the next page.
        assertEquals(center + 1, pageForDate(anchor, anchor.plusDays(3), center))
    }

    @Test fun `pageForDate floors negative offsets to the earlier page`() {
        // A date before the anchor belongs to an earlier page. Without
        // Math.floorDiv these would round toward center and land wrong.
        assertEquals(center - 1, pageForDate(anchor, anchor.minusDays(1), center))
        assertEquals(center - 1, pageForDate(anchor, anchor.minusDays(3), center))
        assertEquals(center - 2, pageForDate(anchor, anchor.minusDays(4), center))
    }

    @Test fun `the page pageForDate names always contains the date`() {
        // Round-trip invariant across a spread of offsets, both signs.
        for (offset in -10..10) {
            val date = anchor.plusDays(offset.toLong())
            val page = pageForDate(anchor, date, center)
            val start = pageStart(anchor, page, center)
            assertTrue(
                "date=$date page=$page start=$start",
                !date.isBefore(start) && date.isBefore(start.plusDays(3)),
            )
        }
    }
}
```

- [x] **Step 2: Run the test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.arishawke.asala.calendar.ui.threeday.ThreeDayPageMathTest"`
Expected: FAIL to compile (`pageStart`, `pageForDate` undefined).

- [x] **Step 3: Implement the page math**

Create `ThreeDayPageMath.kt`:

```kotlin
/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.ui.threeday

import java.time.LocalDate
import java.time.temporal.ChronoUnit

// 3-day pages roll a fixed stride from an anchor (rolling, not snapped to
// a calendar boundary). page == center shows [anchor, anchor + 3).
internal const val ThreeDayPageSize = 3

// First date shown on a given page.
internal fun pageStart(anchor: LocalDate, page: Int, center: Int): LocalDate =
    anchor.plusDays((page - center).toLong() * ThreeDayPageSize)

// Page whose 3-day span contains the date. floorDiv (not integer / which
// truncates toward zero) so dates before the anchor map to the correct
// earlier page rather than rounding back toward center.
internal fun pageForDate(anchor: LocalDate, date: LocalDate, center: Int): Int {
    val days = ChronoUnit.DAYS.between(anchor, date).toInt()
    return center + Math.floorDiv(days, ThreeDayPageSize)
}
```

- [x] **Step 4: Run the test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.arishawke.asala.calendar.ui.threeday.ThreeDayPageMathTest"`
Expected: PASS (all eight tests).

- [x] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/arishawke/asala/calendar/ui/threeday/ThreeDayPageMath.kt \
        app/src/test/kotlin/com/arishawke/asala/calendar/ui/threeday/ThreeDayPageMathTest.kt
git commit -m "feat(threeday): add rolling 3-day page math"
```

---

## Task 2: 3-day viewmodel

**Files:**
- Create: `app/src/main/kotlin/com/arishawke/asala/calendar/ui/threeday/ThreeDayViewModel.kt`

Modeled byte-for-byte on `DayViewModel` (fixed window captured at init, single `observeEvents`, `filteredAndRecolored` applied in the `combine`). Only the window size differs: `WindowPagesEachSide * 3` days each side, with `+ 3` (not `+ 1`) on the exclusive end so the furthest forward page's three days are all covered.

- [x] **Step 1: Implement the viewmodel**

Create `ThreeDayViewModel.kt`:

```kotlin
/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.ui.threeday

import android.content.ContentResolver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.arishawke.asala.calendar.data.EventItem
import com.arishawke.asala.calendar.data.EventRepository
import com.arishawke.asala.calendar.data.filteredAndRecolored
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.LocalDate
import java.time.ZoneId

data class ThreeDayUiState(val today: LocalDate, val selectedDate: LocalDate, val events: List<EventItem>)

class ThreeDayViewModel(
    private val eventRepo: EventRepository,
    private val hiddenCalendarIdsFlow: StateFlow<Set<Long>>,
    private val calendarColorOverridesFlow: StateFlow<Map<Long, Int>>,
    private val eventColorOverridesFlow: StateFlow<Map<Long, Int>>,
    private val todayFlow: StateFlow<LocalDate>,
    private val zone: ZoneId = ZoneId.systemDefault(),
) : ViewModel() {

    // Window is captured once from the initial today; shifting it at
    // midnight would re-subscribe the upstream observer, churning the
    // ContentObserver. The today highlight refreshes via todayFlow
    // regardless. Same model as DayViewModel.
    private val initialToday: LocalDate = todayFlow.value
    private val selectedDate = MutableStateFlow(initialToday)
    private val windowStart = initialToday.minusDays((WindowPagesEachSide * ThreeDayPageSize).toLong())

    // +ThreeDayPageSize (not +1): the furthest forward page spans three
    // days, so the exclusive end must clear all three. A one-day-per-page
    // view (Day) uses +1; this view must not.
    private val windowEndExclusive =
        initialToday.plusDays((WindowPagesEachSide * ThreeDayPageSize + ThreeDayPageSize).toLong())

    private val events = eventRepo.observeEvents(
        startDate = windowStart,
        endExclusive = windowEndExclusive,
        zone = zone,
    )

    val uiState: StateFlow<ThreeDayUiState> =
        combine(
            selectedDate,
            events,
            hiddenCalendarIdsFlow,
            calendarColorOverridesFlow,
            eventColorOverridesFlow,
        ) { d, evs, hidden, calOverrides, evtOverrides ->
            ThreeDayUiState(
                today = todayFlow.value,
                selectedDate = d,
                events = evs.filteredAndRecolored(hidden, calOverrides, evtOverrides),
            )
        }.combine(todayFlow) { state, today ->
            if (state.today == today) state else state.copy(today = today)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ThreeDayUiState(
                today = initialToday,
                selectedDate = initialToday,
                events = emptyList(),
            ),
        )

    fun selectDate(date: LocalDate) {
        selectedDate.update { date }
    }

    class Factory(
        private val contentResolver: ContentResolver,
        private val hiddenCalendarIdsFlow: StateFlow<Set<Long>>,
        private val calendarColorOverridesFlow: StateFlow<Map<Long, Int>>,
        private val eventColorOverridesFlow: StateFlow<Map<Long, Int>>,
        private val todayFlow: StateFlow<LocalDate>,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass == ThreeDayViewModel::class.java)
            return ThreeDayViewModel(
                eventRepo = EventRepository(contentResolver),
                hiddenCalendarIdsFlow = hiddenCalendarIdsFlow,
                calendarColorOverridesFlow = calendarColorOverridesFlow,
                eventColorOverridesFlow = eventColorOverridesFlow,
                todayFlow = todayFlow,
            ) as T
        }
    }

    companion object {
        // 20 pages * 3 days = +/-60 days each side, matching Day's reach.
        const val WindowPagesEachSide: Int = 20
    }
}
```

- [x] **Step 2: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL. (The viewmodel is not referenced yet; this just typechecks it.)

- [x] **Step 3: Commit**

```bash
git add app/src/main/kotlin/com/arishawke/asala/calendar/ui/threeday/ThreeDayViewModel.kt
git commit -m "feat(threeday): add fixed-window 3-day viewmodel"
```

---

## Task 3: Widen Week renderers and add the overflow opt-out

**Files:**
- Modify: `app/src/main/kotlin/com/arishawke/asala/calendar/ui/week/WeekScreen.kt`
- Modify: `app/src/main/kotlin/com/arishawke/asala/calendar/ui/week/TimelineGrid.kt`

All changes are additive and keep Week's behavior byte-identical (`enableOverflow` defaults to `true`, and `WeekScreen`'s existing `WeekPage` call omits it).

- [x] **Step 1: Make `WeekPage` and `formatWeekRange` internal**

In `WeekScreen.kt`, change the `WeekPage` declaration (currently `private fun WeekPage(`) to:

```kotlin
internal fun WeekPage(
```

And change `formatWeekRange` (currently `private fun formatWeekRange(`) to:

```kotlin
internal fun formatWeekRange(first: LocalDate, last: LocalDate, locale: Locale): String {
```

- [x] **Step 2: Add `enableOverflow` to `WeekPage` and thread it down**

In the `WeekPage` parameter list, add `enableOverflow` after `showWeekNumber: Boolean,`:

```kotlin
    showWeekNumber: Boolean,
    enableOverflow: Boolean = true,
    onEventClick: (eventId: Long, instanceMillis: Long) -> Unit,
```

In the `TimelineGrid(...)` call inside `WeekPage`, add the argument (after `workingDaysMask = workingDaysMask,`):

```kotlin
            workingDaysMask = workingDaysMask,
            enableOverflow = enableOverflow,
            onEventClick = onEventClick,
```

- [x] **Step 3: Add `enableOverflow` to `TimelineGrid` and gate the callback**

In `TimelineGrid.kt`, add the parameter to the signature after `workingDaysMask: Long = 0L,`:

```kotlin
    workingDaysMask: Long = 0L,
    enableOverflow: Boolean = true,
    onEventClick: ((eventId: Long, instanceMillis: Long) -> Unit)? = null,
```

Immediately after the existing `var overflowSheet by remember { mutableStateOf<Pair<LocalDate, List<EventItem>>?>(null) }` line, add a gated callback:

```kotlin
    // 3-day opts out of crowded overflow (wide columns, like Day). A null
    // callback makes DayColumn use threshold Int.MAX_VALUE: nothing
    // collapses and no "+N" chip is drawn.
    val overflowCallback: ((date: LocalDate, events: List<EventItem>) -> Unit)? =
        if (enableOverflow) {
            { date, evs -> overflowSheet = date to evs }
        } else {
            null
        }
```

Then in the `DayColumn(...)` call, replace the existing line:

```kotlin
                    onOverflow = { d, evs -> overflowSheet = d to evs },
```

with:

```kotlin
                    onOverflow = overflowCallback,
```

- [x] **Step 4: Verify the build and existing tests (Week unchanged)**

Run: `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL. Week still passes `enableOverflow`'s default `true`, so `overflowCallback` is non-null and Week renders exactly as before. `OverlapLayoutTest` / `CrowdedOverflowTest` are unaffected.

- [x] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/arishawke/asala/calendar/ui/week/WeekScreen.kt \
        app/src/main/kotlin/com/arishawke/asala/calendar/ui/week/TimelineGrid.kt
git commit -m "refactor(week): make WeekPage reusable with an overflow opt-out"
```

---

## Task 4: 3-day screen and view registration

**Files:**
- Create: `app/src/main/kotlin/com/arishawke/asala/calendar/ui/threeday/ThreeDayScreen.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/kotlin/com/arishawke/asala/calendar/AppViewModel.kt`
- Modify: `app/src/main/kotlin/com/arishawke/asala/calendar/CalendarViewLabel.kt`
- Modify: `app/src/main/kotlin/com/arishawke/asala/calendar/ui/header/HeaderDropdownPanel.kt`
- Modify: `app/src/main/kotlin/com/arishawke/asala/calendar/ui/CalendarViewSwitcher.kt`

This is one atomic task: adding `ThreeDay` to the enum makes three exhaustive `when`s non-exhaustive (compile error) and the switcher route needs the screen to exist, so these land together. There is no unit test (the screen is Compose UI and the page math is already covered by Task 1); the gate is a clean compile + the full local checks.

- [x] **Step 1: Add the string resource**

In `strings.xml`, add after `<string name="view_week">Week</string>` (so resource order matches enum order):

```xml
    <string name="view_three_day">3-Day</string>
```

- [x] **Step 2: Create the 3-day screen**

Create `ThreeDayScreen.kt`:

```kotlin
/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.ui.threeday

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.arishawke.asala.calendar.AsalaCalendarApplication
import com.arishawke.asala.calendar.CalendarView
import com.arishawke.asala.calendar.PendingDateJump
import com.arishawke.asala.calendar.ui.theme.rememberCalendarPagerFling
import com.arishawke.asala.calendar.ui.week.WeekPage
import com.arishawke.asala.calendar.ui.week.formatWeekRange
import kotlinx.coroutines.flow.StateFlow
import java.time.LocalDate
import java.time.ZoneId

@Composable
// Same shape as DayScreen: pager state + today jump + cross-view jump +
// render dispatch, plus the workingHours / workingDays params, push this
// just over detekt's 60-line LongMethod default.
@Suppress("LongParameterList", "LongMethod")
fun ThreeDayScreen(
    hiddenCalendarIdsFlow: StateFlow<Set<Long>>,
    calendarColorOverridesFlow: StateFlow<Map<Long, Int>>,
    eventColorOverridesFlow: StateFlow<Map<Long, Int>>,
    onTitleChange: (String) -> Unit,
    todayJumpCounter: StateFlow<Int>,
    pendingDateJump: StateFlow<PendingDateJump?>,
    onConsumePendingDateJump: () -> Unit,
    modifier: Modifier = Modifier,
    workingHoursEnabled: Boolean = false,
    workingHoursStartHour: Int = 9,
    workingHoursEndHour: Int = 17,
    workingDaysEnabled: Boolean = false,
    workingDaysMask: Long = 0L,
    onEventClick: (eventId: Long, instanceMillis: Long) -> Unit = { _, _ -> },
    onReschedule: (eventId: Long, instanceMillis: Long, newStartMillis: Long) -> Unit = { _, _, _ -> },
    onViewedDateChange: (LocalDate) -> Unit = {},
) {
    val context = LocalContext.current
    val todayFlow = (context.applicationContext as AsalaCalendarApplication).todayProvider.today
    val vm: ThreeDayViewModel = viewModel(
        factory = ThreeDayViewModel.Factory(
            context.contentResolver,
            hiddenCalendarIdsFlow,
            calendarColorOverridesFlow,
            eventColorOverridesFlow,
            todayFlow,
        ),
    )
    val state by vm.uiState.collectAsStateWithLifecycle()

    val center = ThreeDayViewModel.WindowPagesEachSide
    val pageCount = center * 2 + 1
    val pagerState = rememberPagerState(initialPage = center) { pageCount }

    val zone = remember { ZoneId.systemDefault() }
    val locale = LocalConfiguration.current.locales.get(0)
    // Rolling anchor: today sits in column 0 of the center page.
    val anchor = state.today

    // Push the visible page into the VM (drives the event filter), set the
    // toolbar title to the page's 3-day range, and report the FAB's
    // default date.
    LaunchedEffect(pagerState, anchor) {
        snapshotFlow { pagerState.currentPage }.collect { page ->
            val start = pageStart(anchor, page, center)
            val end = start.plusDays((ThreeDayPageSize - 1).toLong())
            vm.selectDate(start)
            onTitleChange(formatWeekRange(start, end, locale))
            // FAB default: today if it is in the visible page, else the
            // page's first day so a future add lands inside it.
            val viewedDate = if (state.today in start..end) state.today else start
            onViewedDateChange(viewedDate)
        }
    }

    // Jump-to-today.
    val jumpCounter by todayJumpCounter.collectAsStateWithLifecycle()
    var lastHandledJump by remember { mutableIntStateOf(jumpCounter) }
    LaunchedEffect(jumpCounter) {
        if (jumpCounter != lastHandledJump) {
            lastHandledJump = jumpCounter
            pagerState.animateScrollToPage(center)
        }
    }

    // Cross-view jump from the header mini-month. Map the date to the page
    // that contains it, clamped to the window. Filtered by target view so
    // a jump meant for Day / Week / Month / Schedule does not scroll here.
    val pendingJump by pendingDateJump.collectAsStateWithLifecycle()
    LaunchedEffect(pendingJump, anchor) {
        val jump = pendingJump?.takeIf { it.view == CalendarView.ThreeDay } ?: return@LaunchedEffect
        val target = pageForDate(anchor, jump.date, center).coerceIn(0, pageCount - 1)
        pagerState.animateScrollToPage(target)
        onConsumePendingDateJump()
    }

    HorizontalPager(
        state = pagerState,
        modifier = modifier.fillMaxSize(),
        beyondViewportPageCount = 1,
        flingBehavior = rememberCalendarPagerFling(pagerState),
    ) { page ->
        val days = remember(page, anchor, center) {
            val start = pageStart(anchor, page, center)
            (0 until ThreeDayPageSize).map { start.plusDays(it.toLong()) }
        }
        WeekPage(
            days = days,
            today = state.today,
            events = state.events,
            zone = zone,
            dimPastDates = false,
            workingHoursEnabled = workingHoursEnabled,
            workingHoursStartHour = workingHoursStartHour,
            workingHoursEndHour = workingHoursEndHour,
            workingDaysEnabled = workingDaysEnabled,
            workingDaysMask = workingDaysMask,
            showWeekNumber = false,
            enableOverflow = false,
            onEventClick = onEventClick,
            onReschedule = onReschedule,
        )
    }
}
```

- [x] **Step 3: Add the enum value**

In `AppViewModel.kt`, change the enum (currently `enum class CalendarView { Month, Week, Day, Schedule, Tasks }`) to insert `ThreeDay` between `Week` and `Day`:

```kotlin
enum class CalendarView { Month, Week, ThreeDay, Day, Schedule, Tasks }
```

- [x] **Step 4: Add the label branch**

In `CalendarViewLabel.kt`, add the `ThreeDay` case between the `Week` and `Day` lines:

```kotlin
        CalendarView.Week -> R.string.view_week
        CalendarView.ThreeDay -> R.string.view_three_day
        CalendarView.Day -> R.string.view_day
```

- [x] **Step 5: Add `ThreeDay` to the header mini-month branch**

In `HeaderDropdownPanel.kt`, add `CalendarView.ThreeDay` to the grouped branch (currently `CalendarView.Week, CalendarView.Day, CalendarView.Schedule ->`):

```kotlin
        CalendarView.Week, CalendarView.ThreeDay, CalendarView.Day, CalendarView.Schedule -> {
```

- [x] **Step 6: Add the switcher route**

In `CalendarViewSwitcher.kt`, add the import (next to the other screen imports):

```kotlin
import com.arishawke.asala.calendar.ui.threeday.ThreeDayScreen
```

Then add the `ThreeDay` branch to the `when (view)` between `CalendarView.Week -> WeekScreen(...)` and `CalendarView.Day -> DayScreen(...)`. It mirrors the **Day** branch's parameters (Day passes working-hours but not `firstDayOfWeekOverride` / `dimPastDates` / `showWeekNumber`):

```kotlin
            CalendarView.ThreeDay -> ThreeDayScreen(
                hiddenCalendarIdsFlow = vm.hiddenCalendarIdsFlow,
                calendarColorOverridesFlow = vm.calendarColorOverridesFlow,
                eventColorOverridesFlow = vm.eventColorOverridesFlow,
                onTitleChange = onTitleChange,
                todayJumpCounter = vm.todayJumpCounter,
                pendingDateJump = vm.pendingDateJump,
                onConsumePendingDateJump = vm::consumePendingDateJump,
                workingHoursEnabled = prefs.workingHoursEnabled,
                workingHoursStartHour = prefs.workingHoursStartHour,
                workingHoursEndHour = prefs.workingHoursEndHour,
                workingDaysEnabled = prefs.workingDaysEnabled,
                workingDaysMask = workingDaysMask,
                onEventClick = { eid, millis -> vm.openEventDetail(eid, millis) },
                onReschedule = { eid, instMillis, newStart ->
                    vm.rescheduleEvent(eid, instMillis, newStart)
                },
                onViewedDateChange = vm::setViewedDate,
            )
```

- [x] **Step 7: Verify the build (exhaustiveness now satisfied)**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL. If it fails with "when expression must be exhaustive", a switch site was missed; the error names the file and `when`.

- [x] **Step 8: Run the full local gate**

Run: `./gradlew :app:spotlessKotlinCheck :app:detekt :app:lintDebug :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL. (`ThreeDayScreen` already carries `@Suppress("LongParameterList", "LongMethod")`; keep it. If lint flags a missing `view_three_day` translation, it is a warning, not an error, consistent with the other `view_*` strings.)

- [x] **Step 9: Final switch-site audit**

Run:

```bash
grep -rn "CalendarView\." app/src/main/kotlin
grep -rn "selectedView\|defaultView" app/src/main/kotlin
```

Expected: every `when (view)` / `when (this)` / `when (currentView)` over `CalendarView` now lists `ThreeDay` or routes it through a grouped branch / `entries` filter. Confirm no bare `CalendarView.valueOf` site needs a migration (persistence falls back to `Month`).

- [x] **Step 10: Commit**

```bash
git add app/src/main/kotlin/com/arishawke/asala/calendar/ui/threeday/ThreeDayScreen.kt \
        app/src/main/res/values/strings.xml \
        app/src/main/kotlin/com/arishawke/asala/calendar/AppViewModel.kt \
        app/src/main/kotlin/com/arishawke/asala/calendar/CalendarViewLabel.kt \
        app/src/main/kotlin/com/arishawke/asala/calendar/ui/header/HeaderDropdownPanel.kt \
        app/src/main/kotlin/com/arishawke/asala/calendar/ui/CalendarViewSwitcher.kt
git commit -m "feat(threeday): add 3-day view and register it between Week and Day"
```

---

## Task 5: Roadmap, changelog, and device smoke test

**Files:**
- Modify: `docs/ROADMAP.md`
- Modify: `CHANGELOG.md`

- [x] **Step 1: Add the changelog entry**

Under `## [Unreleased]` in `CHANGELOG.md`, add an `### Added` entry (create the section if absent):

```markdown
### Added
- 3-Day view. A timeline between Week and Day that pages three days at a
  time, rolling from the day you open it on. It shows three readable
  columns with the same event blocks, now-line, and working-hours and
  working-days shading as Week, and sits between Week and Day in the view
  switcher and settings. Set it as the default view to open into it.
```

- [x] **Step 2: Move 3-Day view to a shipped/Now state on the roadmap**

In `docs/ROADMAP.md`, add a `Now` bullet (or move any existing placeholder) noting the 3-day view is implemented this cycle, mirroring the style of the existing entries:

```markdown
- **3-Day view.** A Week/Day-hybrid timeline that pages three days at a
  time, rolling from the opened date. Renders through Week's columns with
  no crowded-overflow chip (wide columns, like Day). Design:
  [docs/specs/2026-05-29-three-day-view-design.md](specs/2026-05-29-three-day-view-design.md).
```

- [x] **Step 3: Commit the docs**

```bash
git add CHANGELOG.md docs/ROADMAP.md
git commit -m "docs: changelog and roadmap for 3-day view"
```

- [x] **Step 4: Device smoke test (fresh install)**

```bash
adb uninstall com.arishawke.asala.calendar
./gradlew :app:installDebug
```

Then on the device, verify:
- Open the drawer: **3-Day** appears between Week and Day. Pick it.
- Three equal-width columns render with the correct header dates; the title shows the 3-day range (e.g. "Mar 2 - 4, 2026").
- Swiping left/right steps by **exactly 3 days** (no week snapping, no single-day step).
- Tap "today" (toolbar) from a swiped-away page: it recenters so today is column 0.
- The FAB opens the editor defaulting to a sensible date (today if visible, else the page's first day).
- Drag-to-reschedule a timed event works.
- A day with 3+ overlapping events shows them **side by side with no "+N" chip** (overflow off).
- Open the header mini-month while in 3-Day and tap a date: the pager jumps to the page containing it.
- Switch to **Week** and **Day**: both look and behave exactly as before (no regressions).
- Set **3-Day as the default view** in Settings, fully close and relaunch the app: it opens into 3-Day (persistence round-trip, no crash, no reset to Month).

Record results in the PR description. Note any failures rather than marking the task done.

---

## Self-review

- **Spec coverage:**
  - Rolling anchor: Task 1 `pageStart`/`pageForDate` (rolling stride from anchor), Task 4 screen uses `anchor = state.today`. Covered.
  - No crowded-overflow chip: Task 3 `enableOverflow` opt-out, Task 4 screen passes `enableOverflow = false`. Covered.
  - +/-60-day window: Task 2 `WindowPagesEachSide = 20` * 3 with the `+ 3` end bound. Covered.
  - Label `3-Day`, position between Week and Day: Task 4 string + enum order. Covered.
  - Reuse Week renderers (`WeekPage`, header, grid, columns, now-line, working-hours/days): Task 3 widening + Task 4 render call. Covered.
  - Viewmodel modeled on Day (single subscription) with `filteredAndRecolored`: Task 2. Covered.
  - Three exhaustive `when` edits + two visibility widenings + no migration + auto-pickup sites: Tasks 3-4 + File structure notes. Covered.
  - Title reuses `formatWeekRange`: Task 3 widening + Task 4 `onTitleChange`. Covered.
  - Rendering parity (`showEndTime = false`, `dimPastDates = false` via WeekPage): Task 4 render call passes `dimPastDates = false`; `showEndTime` stays Week's default by routing through `WeekPage`/`TimelineGrid` (neither passes `showEndTime`, so `DayColumn`'s default `false` applies). Covered.
  - Tests for page math incl. negative offset / floorDiv: Task 1. Covered.
  - Changelog + roadmap: Task 5. Covered.
- **Type consistency:** `pageStart(anchor, page, center): LocalDate` and `pageForDate(anchor, date, center): Int` used identically in Task 1 and Task 4. `ThreeDayPageSize` (Int = 3) used in Tasks 1, 2, 4. `ThreeDayViewModel.WindowPagesEachSide` (Int = 20) used in Tasks 2 and 4. `ThreeDayUiState(today, selectedDate, events)` and `selectDate(date)` consistent. `WeekPage(..., enableOverflow: Boolean = true, ...)` and `TimelineGrid(..., enableOverflow: Boolean = true, ...)` match between Tasks 3 and 4. `ThreeDayScreen(...)` parameter list matches the call site in Task 4 Step 6.
- **Placeholder scan:** none; every code step shows complete code. The only descriptive (non-code) step is the roadmap bullet wording, which is given verbatim.
- **Ordering / build-green invariant:** Task 1 (pure, tested) -> Task 2 (VM, compiles unreferenced) -> Task 3 (Week no-op refactor, existing tests green) -> Task 4 (atomic enum + screen + registration, compiles + full gate) -> Task 5 (docs + device). Each task ends on a green build; Task 4 is necessarily atomic because the enum change and its exhaustive-`when` fixes and the screen route are mutually dependent for compilation.
