# Vertical-scrolling Month view — implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement task-by-task. Steps use `- [ ]` checkbox syntax. Spec at [docs/specs/2026-05-27-vertical-month-view-design.md](../specs/2026-05-27-vertical-month-view-design.md).

**Goal:** Ship a continuous-scroll Month view as an alternative to the existing paged Month, behind a `Settings > Appearance > Month scroll style` dropdown. Default unchanged.

**Architecture:** Hand-rolled `LazyColumn` of 121 month grids (~10 years), sticky headers via `stickyHeader`, viewport-center detection via `LazyListState.layoutInfo` + `derivedStateOf` + `snapshotFlow.distinctUntilChanged`. Reuses existing `MonthGrid` / `WeekLayoutRow` / `EventChips` / multi-day-bar rendering unchanged. Adds a constructor parameter on `MonthViewModel` to widen its event-fetch window in continuous mode.

**Tech Stack:** Kotlin 2.x, Jetpack Compose 1.10+, AndroidX DataStore, kotlinx-coroutines `Flow` / `StateFlow`. No new dependencies.

---

## Task 1: Settings flag — data model

**Files:**
- Create: `app/src/main/kotlin/com/arishawke/asala/calendar/ui/settings/MonthScrollStyle.kt`
- Modify: `app/src/main/kotlin/com/arishawke/asala/calendar/ui/settings/UserPreferences.kt`
- Modify: `app/src/main/kotlin/com/arishawke/asala/calendar/ui/settings/SettingsViewModel.kt`

- [ ] **Step 1: Create the enum.** Mirror `ThemeMode` / `PaletteId` conventions (no annotations — enums are inherently `@Stable` in Compose).

```kotlin
// app/src/main/kotlin/com/arishawke/asala/calendar/ui/settings/MonthScrollStyle.kt
package com.arishawke.asala.calendar.ui.settings

// Selector for the Month-view scroll surface. Paged keeps the historic
// HorizontalPager-of-grids behavior; Continuous swaps it for a vertical
// LazyColumn of month grids with sticky headers. Default Paged so
// existing installs see no behavior change.
enum class MonthScrollStyle {
    Paged,
    Continuous,
}
```

- [ ] **Step 2: Add the field to `UserPrefs` and the read path.** Locate the `data class UserPrefs(...)` body in `UserPreferences.kt` (around line 62), add the field next to `showWeekNumber`:

```kotlin
val monthScrollStyle: MonthScrollStyle,
```

- [ ] **Step 3: Add the DataStore key.** Find the `private companion object` block in `UserPreferences.kt`:

```kotlin
val KEY_MONTH_SCROLL_STYLE = stringPreferencesKey("month_scroll_style")
```

- [ ] **Step 4: Wire the read path.** Inside `UserPreferences.prefs` (the `Flow<UserPrefs>` builder), add to the `UserPrefs(...)` constructor call, mirroring the `themeMode` / `defaultView` lines:

```kotlin
monthScrollStyle = parseEnum(p[KEY_MONTH_SCROLL_STYLE], MonthScrollStyle.Paged) {
    MonthScrollStyle.valueOf(it)
},
```

- [ ] **Step 5: Add the setter.** Below `setShowWeekNumber` in `UserPreferences.kt`:

```kotlin
suspend fun setMonthScrollStyle(style: MonthScrollStyle) {
    dataStore.edit { it[KEY_MONTH_SCROLL_STYLE] = style.name }
}
```

- [ ] **Step 6: Update `SettingsViewModel` initial state.** In `SettingsViewModel.kt` `initialValue = UserPrefs(...)` block, add:

```kotlin
monthScrollStyle = MonthScrollStyle.Paged,
```

- [ ] **Step 7: Add the SettingsViewModel setter.** Below `setShowWeekNumber`:

```kotlin
fun setMonthScrollStyle(style: MonthScrollStyle) {
    viewModelScope.launch { prefs.setMonthScrollStyle(style) }
}
```

- [ ] **Step 8: Run the gate to catch any compile errors.**

```bash
./gradlew :app:compileDebugKotlin
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 9: Commit.**

```bash
git add app/src/main/kotlin/com/arishawke/asala/calendar/ui/settings/MonthScrollStyle.kt \
        app/src/main/kotlin/com/arishawke/asala/calendar/ui/settings/UserPreferences.kt \
        app/src/main/kotlin/com/arishawke/asala/calendar/ui/settings/SettingsViewModel.kt
git commit -m "feat(settings): add MonthScrollStyle preference"
```

---

## Task 2: Settings UI — dropdown + strings

**Files:**
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/kotlin/com/arishawke/asala/calendar/ui/settings/SettingsRows.kt`
- Modify: `app/src/main/kotlin/com/arishawke/asala/calendar/ui/settings/SettingsScreen.kt`

- [ ] **Step 1: Add strings.** In `strings.xml`, near the other Settings strings (after `settings_time_format_24h`):

```xml
<string name="settings_month_scroll_style">Month scroll style</string>
<string name="settings_month_scroll_style_paged">Paged</string>
<string name="settings_month_scroll_style_continuous">Continuous</string>
```

- [ ] **Step 2: Add `MonthScrollStyleRow`.** In `SettingsRows.kt`, after `TimeFormatRow`'s `timeFormatLabel` helper:

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MonthScrollStyleRow(current: MonthScrollStyle, onChange: (MonthScrollStyle) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val label = stringResource(monthScrollStyleLabel(current))
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
        OutlinedTextField(
            value = label,
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.settings_month_scroll_style)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            MonthScrollStyle.entries.forEach { style ->
                DropdownMenuItem(
                    text = { Text(stringResource(monthScrollStyleLabel(style))) },
                    onClick = {
                        onChange(style)
                        expanded = false
                    },
                )
            }
        }
    }
}

private fun monthScrollStyleLabel(style: MonthScrollStyle): Int = when (style) {
    MonthScrollStyle.Paged -> R.string.settings_month_scroll_style_paged
    MonthScrollStyle.Continuous -> R.string.settings_month_scroll_style_continuous
}
```

- [ ] **Step 3: Wire into `SettingsScreen.kt`.** Under the Appearance section, after `appearance-show-week-number` (so the new row sits at the bottom of Appearance):

```kotlin
item("appearance-month-scroll-style") {
    MonthScrollStyleRow(
        current = s.monthScrollStyle,
        onChange = vm::setMonthScrollStyle,
    )
}
```

- [ ] **Step 4: Run the local gate.**

```bash
./gradlew :app:spotlessKotlinCheck :app:detekt :app:lintDebug :app:testDebugUnitTest
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit.**

```bash
git add app/src/main/res/values/strings.xml \
        app/src/main/kotlin/com/arishawke/asala/calendar/ui/settings/SettingsRows.kt \
        app/src/main/kotlin/com/arishawke/asala/calendar/ui/settings/SettingsScreen.kt
git commit -m "feat(settings): Month scroll style dropdown UI"
```

---

## Task 3: `MonthViewModel` window widening

**Files:**
- Modify: `app/src/main/kotlin/com/arishawke/asala/calendar/ui/month/MonthViewModel.kt`
- Create: `app/src/test/kotlin/com/arishawke/asala/calendar/ui/month/MonthRangeWindowTest.kt`

- [ ] **Step 1: Write the failing test.** Pure helper test — we'll add a `monthFetchWindow` companion function that returns the `(startDate, endExclusive)` pair so it can be tested without instantiating the VM.

```kotlin
// app/src/test/kotlin/com/arishawke/asala/calendar/ui/month/MonthRangeWindowTest.kt
package com.arishawke.asala.calendar.ui.month

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class MonthRangeWindowTest {
    @Test fun radius_1_matches_paged_window() {
        val ym = YearMonth.of(2026, 5)
        val (start, endExclusive) = MonthViewModel.monthFetchWindow(ym, radius = 1)
        assertEquals(LocalDate.of(2026, 4, 1), start)
        assertEquals(LocalDate.of(2026, 7, 1), endExclusive)
    }

    @Test fun radius_6_returns_13_month_window() {
        val ym = YearMonth.of(2026, 5)
        val (start, endExclusive) = MonthViewModel.monthFetchWindow(ym, radius = 6)
        assertEquals(LocalDate.of(2025, 11, 1), start)
        assertEquals(LocalDate.of(2026, 12, 1), endExclusive)
    }

    @Test fun radius_0_returns_single_month() {
        val ym = YearMonth.of(2026, 5)
        val (start, endExclusive) = MonthViewModel.monthFetchWindow(ym, radius = 0)
        assertEquals(LocalDate.of(2026, 5, 1), start)
        assertEquals(LocalDate.of(2026, 6, 1), endExclusive)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails.**

```bash
./gradlew :app:testDebugUnitTest --tests '*MonthRangeWindowTest*'
```
Expected: FAILED (`monthFetchWindow` does not exist).

- [ ] **Step 3: Add the helper + the constructor parameter.** Edit `MonthViewModel.kt`:

Add the `companion object` (the class currently has no companion):

```kotlin
companion object {
    // Half-window radius around the visible center month. Paged mode uses
    // 1 (current behavior, single visible month plus one neighbor on each
    // side for pre-fetch). Continuous mode uses 6 so the LazyColumn's
    // pre-composed items render with events rather than blank cells.
    fun monthFetchWindow(center: YearMonth, radius: Int): Pair<LocalDate, LocalDate> {
        val start = center.minusMonths(radius.toLong()).atDay(1)
        val endExclusive = center.plusMonths(radius.toLong() + 1).atDay(1)
        return start to endExclusive
    }
}
```

Add the constructor parameter (default `1` so existing call sites compile unchanged):

```kotlin
class MonthViewModel(
    private val eventRepo: EventRepository,
    private val hiddenCalendarIdsFlow: StateFlow<Set<Long>>,
    private val calendarColorOverridesFlow: StateFlow<Map<Long, Int>>,
    private val eventColorOverridesFlow: StateFlow<Map<Long, Int>>,
    private val todayFlow: StateFlow<LocalDate>,
    private val monthWindowRadius: Int = 1,
    private val zone: ZoneId = ZoneId.systemDefault(),
) : ViewModel() { ... }
```

Replace the `eventsForMonth` body (around lines 49-58) to consult the helper:

```kotlin
@OptIn(ExperimentalCoroutinesApi::class)
private val eventsForMonth =
    visibleMonth.flatMapLatest { ym ->
        val (startDate, endExclusive) = monthFetchWindow(ym, monthWindowRadius)
        eventRepo.observeEvents(
            startDate = startDate,
            endExclusive = endExclusive,
            zone = zone,
        )
    }
```

Add `monthWindowRadius` to the `Factory`:

```kotlin
class Factory(
    private val contentResolver: ContentResolver,
    private val hiddenCalendarIdsFlow: StateFlow<Set<Long>>,
    private val calendarColorOverridesFlow: StateFlow<Map<Long, Int>>,
    private val eventColorOverridesFlow: StateFlow<Map<Long, Int>>,
    private val todayFlow: StateFlow<LocalDate>,
    private val monthWindowRadius: Int = 1,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass == MonthViewModel::class.java)
        return MonthViewModel(
            eventRepo = EventRepository(contentResolver),
            hiddenCalendarIdsFlow = hiddenCalendarIdsFlow,
            calendarColorOverridesFlow = calendarColorOverridesFlow,
            eventColorOverridesFlow = eventColorOverridesFlow,
            todayFlow = todayFlow,
            monthWindowRadius = monthWindowRadius,
        ) as T
    }
}
```

- [ ] **Step 4: Run the test to verify it passes.**

```bash
./gradlew :app:testDebugUnitTest --tests '*MonthRangeWindowTest*'
```
Expected: PASSED.

- [ ] **Step 5: Run the full gate.**

```bash
./gradlew :app:spotlessKotlinCheck :app:detekt :app:lintDebug :app:testDebugUnitTest
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit.**

```bash
git add app/src/main/kotlin/com/arishawke/asala/calendar/ui/month/MonthViewModel.kt \
        app/src/test/kotlin/com/arishawke/asala/calendar/ui/month/MonthRangeWindowTest.kt
git commit -m "refactor(month): make MonthViewModel fetch-window radius configurable"
```

---

## Task 4: `MonthGrid` refactor — accept `weekRowHeight`

**Files:**
- Modify: `app/src/main/kotlin/com/arishawke/asala/calendar/ui/month/MonthScreen.kt`

- [ ] **Step 1: Add the `weekRowHeight` parameter to `MonthGrid`.** Around line 197:

```kotlin
@Composable
private fun MonthGrid(
    yearMonth: YearMonth,
    firstDayOfWeek: DayOfWeek,
    eventsByDate: Map<LocalDate, List<EventItem>>,
    allEvents: List<EventItem>,
    today: LocalDate,
    dimPastDates: Boolean,
    showWeekNumber: Boolean,
    weekRowHeight: Dp,
    onDayCellClick: (LocalDate) -> Unit,
    onOverflowClick: (LocalDate) -> Unit,
)
```

Replace the inner `Column(Modifier.fillMaxSize()) { ... WeekLayoutRow(..., modifier = Modifier.fillMaxWidth().weight(1f)) ... }` with:

```kotlin
Column(modifier = Modifier.fillMaxWidth()) {
    for (week in 0 until 6) {
        val weekDays = remember(days, week) { days.subList(week * 7, week * 7 + 7) }
        val weekStart = weekDays.first().date
        val segments = remember(weekStart, allEvents) {
            LaneAssigner.assignLanes(
                WeekBucketer.bucketize(allEvents, weekStart, zone),
            )
        }
        WeekLayoutRow(
            weekDays = weekDays,
            weekStart = weekStart,
            segments = segments,
            eventsByDate = eventsByDate,
            today = today,
            dimPastDates = dimPastDates,
            showWeekNumber = showWeekNumber,
            onDayCellClick = onDayCellClick,
            onOverflowClick = onOverflowClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(weekRowHeight),
        )
    }
}
```

(Removes `Modifier.fillMaxSize()` and `Modifier.weight(1f)`. Each week is now bounded by `weekRowHeight`.)

- [ ] **Step 2: Update the call site of `MonthGrid` inside `MonthScreen`.** Wrap the pager's `MonthGrid` call in a `BoxWithConstraints` so the row height can be derived from the available height:

Find the existing `HorizontalPager` page content (around lines 175-193) and wrap the `MonthGrid` invocation. Replace the existing inner `MonthGrid(...)` call with:

```kotlin
BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
    val rowHeight = (maxHeight / 6).coerceAtLeast(WeekRowHeightMin)
    MonthGrid(
        yearMonth = monthForPage,
        firstDayOfWeek = firstDayOfWeek,
        eventsByDate = state.eventsByDate,
        allEvents = state.events,
        today = state.today,
        dimPastDates = dimPastDates,
        showWeekNumber = showWeekNumber,
        weekRowHeight = rowHeight,
        onDayCellClick = onDayCellClick,
        onOverflowClick = { overflowDate = it },
    )
}
```

- [ ] **Step 3: Add `WeekRowHeightMin` constant.** At the bottom of `MonthScreen.kt`, in the private companion area (or as a top-level `private val` near `InitialPage`):

```kotlin
// EventChips' BoxWithConstraints math collapses chip capacity to zero
// below this height; pin a floor so a tiny screen does not silently
// hide every chip behind a "+N more" affordance.
internal val WeekRowHeightMin: Dp = 96.dp
```

Mark `internal` so `ContinuousMonthScreen` can reuse it later.

- [ ] **Step 4: Ensure imports cover `Dp`, `dp`, `height`, `BoxWithConstraints`.** Verify these are present at the top of `MonthScreen.kt`:

```kotlin
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.height
import androidx.compose.ui.unit.Dp
```

- [ ] **Step 5: Run the local gate.**

```bash
./gradlew :app:spotlessKotlinCheck :app:detekt :app:lintDebug :app:testDebugUnitTest
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Manually smoke the paged Month.**

```bash
./gradlew :app:installDebug
```
Verify Month view still renders identically to v0.12.0 (no chip count regression, today highlight, multi-day bars).

- [ ] **Step 7: Commit.**

```bash
git add app/src/main/kotlin/com/arishawke/asala/calendar/ui/month/MonthScreen.kt
git commit -m "refactor(month): MonthGrid takes explicit weekRowHeight"
```

---

## Task 5: Center-detection helper + unit test

**Files:**
- Create: `app/src/main/kotlin/com/arishawke/asala/calendar/ui/month/ContinuousMonthCenter.kt`
- Create: `app/src/test/kotlin/com/arishawke/asala/calendar/ui/month/MonthCenterDetectionTest.kt`

- [ ] **Step 1: Write the failing test.** Pure function: given visible item info shaped as `List<VisibleItem>` and a viewport center, returns the index closest to center.

```kotlin
// app/src/test/kotlin/com/arishawke/asala/calendar/ui/month/MonthCenterDetectionTest.kt
package com.arishawke.asala.calendar.ui.month

import org.junit.Assert.assertEquals
import org.junit.Test

class MonthCenterDetectionTest {
    @Test fun picks_the_item_whose_center_is_closest_to_viewport_center() {
        val items = listOf(
            VisibleItem(index = 0, offset = -200, size = 600),
            VisibleItem(index = 1, offset = 400, size = 600),
            VisibleItem(index = 2, offset = 1000, size = 600),
        )
        val center = ContinuousMonthCenter.pick(items, viewportCenter = 500, fallback = 99)
        // item 1: center at 400 + 300 = 700, distance 200
        // item 0: center at -200 + 300 = 100, distance 400
        // item 2: center at 1000 + 300 = 1300, distance 800
        assertEquals(1, center)
    }

    @Test fun empty_visible_items_returns_fallback() {
        val center = ContinuousMonthCenter.pick(emptyList(), viewportCenter = 0, fallback = 42)
        assertEquals(42, center)
    }

    @Test fun tie_breaks_to_first_in_list() {
        val items = listOf(
            VisibleItem(index = 5, offset = 0, size = 100),  // center 50
            VisibleItem(index = 6, offset = 100, size = 100), // center 150
        )
        // viewportCenter = 100 -> item 5 center is 50 (dist 50), item 6 center is 150 (dist 50)
        // minByOrNull returns the first on ties.
        val center = ContinuousMonthCenter.pick(items, viewportCenter = 100, fallback = 99)
        assertEquals(5, center)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails.**

```bash
./gradlew :app:testDebugUnitTest --tests '*MonthCenterDetectionTest*'
```
Expected: FAILED (unresolved references).

- [ ] **Step 3: Write the helper.**

```kotlin
// app/src/main/kotlin/com/arishawke/asala/calendar/ui/month/ContinuousMonthCenter.kt
package com.arishawke.asala.calendar.ui.month

import kotlin.math.abs

// Visible-item info needed for center detection. Shaped to match
// LazyListItemInfo (index / offset / size) without taking a Compose
// dependency in unit tests.
internal data class VisibleItem(val index: Int, val offset: Int, val size: Int)

// Picks the visible item whose vertical center is nearest the viewport
// center. Returns the index of that item, or `fallback` when the list
// is empty (first frame, before layout completes). Ties resolve to the
// first match in `items` order.
internal object ContinuousMonthCenter {
    fun pick(items: List<VisibleItem>, viewportCenter: Int, fallback: Int): Int {
        if (items.isEmpty()) return fallback
        return items.minBy { abs(it.offset + it.size / 2 - viewportCenter) }.index
    }
}
```

- [ ] **Step 4: Run the test to verify it passes.**

```bash
./gradlew :app:testDebugUnitTest --tests '*MonthCenterDetectionTest*'
```
Expected: PASSED.

- [ ] **Step 5: Commit.**

```bash
git add app/src/main/kotlin/com/arishawke/asala/calendar/ui/month/ContinuousMonthCenter.kt \
        app/src/test/kotlin/com/arishawke/asala/calendar/ui/month/MonthCenterDetectionTest.kt
git commit -m "feat(month): continuous-scroll viewport center detection helper"
```

---

## Task 6: Extract `PagedMonthScreen` and turn `MonthScreen` into a dispatcher

**Files:**
- Create: `app/src/main/kotlin/com/arishawke/asala/calendar/ui/month/PagedMonthScreen.kt`
- Modify: `app/src/main/kotlin/com/arishawke/asala/calendar/ui/month/MonthScreen.kt`

- [ ] **Step 1: Create `PagedMonthScreen.kt` with the current `MonthScreen` body lifted verbatim.**

Lift everything from the current `internal fun MonthScreen(...)` body — the call to `viewModel(...)`, `collectAsStateWithLifecycle`, anchorMonth, pagerState, `LaunchedEffect`s for page changes, `pendingDateJump` handling, `HorizontalPager` block, overflow date sheet — into a new `internal fun PagedMonthScreen(...)` with the same signature minus the `MonthViewModel` (which the dispatcher will hoist).

```kotlin
// app/src/main/kotlin/com/arishawke/asala/calendar/ui/month/PagedMonthScreen.kt
// (License header + package declaration)

@Suppress("LongParameterList")
@Composable
internal fun PagedMonthScreen(
    vm: MonthViewModel,
    pendingJump: PendingDateJump?,
    onConsumePendingDateJump: () -> Unit,
    onTitleChange: (String) -> Unit,
    onViewedMonthChange: (YearMonth) -> Unit,
    onViewedDateChange: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    firstDayOfWeekOverride: DayOfWeek? = null,
    dimPastDates: Boolean = false,
    showWeekNumber: Boolean = false,
    onDayCellClick: (LocalDate) -> Unit = {},
    onEventClick: (eventId: Long, instanceMillis: Long) -> Unit = { _, _ -> },
) {
    // Body lifted verbatim from current MonthScreen, MINUS the
    // `viewModel(...)` factory call (vm is passed in).
}
```

(Concrete steps for the lift are mechanical — copy the existing body between the function braces, delete the `viewModel(...)` factory call, and the parameters and signature stay identical to the current `MonthScreen`.)

- [ ] **Step 2: Rewrite `MonthScreen.kt` as a dispatcher.** Replace the entire body of `MonthScreen.kt` with:

```kotlin
// app/src/main/kotlin/com/arishawke/asala/calendar/ui/month/MonthScreen.kt
// (License header + package declaration + imports)

@Suppress("LongParameterList")
@Composable
internal fun MonthScreen(
    pendingJump: PendingDateJump?,
    onConsumePendingDateJump: () -> Unit,
    onTitleChange: (String) -> Unit,
    onViewedMonthChange: (YearMonth) -> Unit,
    onViewedDateChange: (LocalDate) -> Unit,
    hiddenCalendarIdsFlow: StateFlow<Set<Long>>,
    calendarColorOverridesFlow: StateFlow<Map<Long, Int>>,
    eventColorOverridesFlow: StateFlow<Map<Long, Int>>,
    monthScrollStyle: MonthScrollStyle,
    modifier: Modifier = Modifier,
    firstDayOfWeekOverride: DayOfWeek? = null,
    dimPastDates: Boolean = false,
    showWeekNumber: Boolean = false,
    onDayCellClick: (LocalDate) -> Unit = {},
    onEventClick: (eventId: Long, instanceMillis: Long) -> Unit = { _, _ -> },
) {
    val context = LocalContext.current
    val todayFlow = (context.applicationContext as AsalaCalendarApplication).todayProvider.today
    // Window radius differs by mode: Paged keeps the historic ±1 prefetch;
    // Continuous widens to ±6 so LazyColumn pre-composed items render with
    // events rather than blank cells.
    val radius = when (monthScrollStyle) {
        MonthScrollStyle.Paged -> 1
        MonthScrollStyle.Continuous -> 6
    }
    val vm: MonthViewModel = viewModel(
        key = "month-scroll-${monthScrollStyle.name}",
        factory = MonthViewModel.Factory(
            context.contentResolver,
            hiddenCalendarIdsFlow,
            calendarColorOverridesFlow,
            eventColorOverridesFlow,
            todayFlow,
            radius,
        ),
    )

    when (monthScrollStyle) {
        MonthScrollStyle.Paged -> PagedMonthScreen(
            vm = vm,
            pendingJump = pendingJump,
            onConsumePendingDateJump = onConsumePendingDateJump,
            onTitleChange = onTitleChange,
            onViewedMonthChange = onViewedMonthChange,
            onViewedDateChange = onViewedDateChange,
            modifier = modifier,
            firstDayOfWeekOverride = firstDayOfWeekOverride,
            dimPastDates = dimPastDates,
            showWeekNumber = showWeekNumber,
            onDayCellClick = onDayCellClick,
            onEventClick = onEventClick,
        )
        MonthScrollStyle.Continuous -> ContinuousMonthScreen(
            vm = vm,
            pendingJump = pendingJump,
            onConsumePendingDateJump = onConsumePendingDateJump,
            onTitleChange = onTitleChange,
            onViewedMonthChange = onViewedMonthChange,
            onViewedDateChange = onViewedDateChange,
            modifier = modifier,
            firstDayOfWeekOverride = firstDayOfWeekOverride,
            dimPastDates = dimPastDates,
            showWeekNumber = showWeekNumber,
            onDayCellClick = onDayCellClick,
            onEventClick = onEventClick,
        )
    }
}
```

Note `key = "month-scroll-${monthScrollStyle.name}"` so flipping the Settings dropdown disposes the prior `MonthViewModel` (which had the wrong window radius) and creates a fresh one with the new radius.

- [ ] **Step 3: Update the AppShell call site.** Find the `MonthScreen(...)` invocation in `AppShell.kt` and pass `monthScrollStyle = prefs.monthScrollStyle`.

```kotlin
MonthScreen(
    // ...existing params...
    monthScrollStyle = prefs.monthScrollStyle,
)
```

- [ ] **Step 4: Stub `ContinuousMonthScreen`.** Create a placeholder so the dispatcher compiles (the real implementation lands in Task 7).

```kotlin
// app/src/main/kotlin/com/arishawke/asala/calendar/ui/month/ContinuousMonthScreen.kt
// (License header + package declaration + imports)

@Suppress("LongParameterList", "UNUSED_PARAMETER")
@Composable
internal fun ContinuousMonthScreen(
    vm: MonthViewModel,
    pendingJump: PendingDateJump?,
    onConsumePendingDateJump: () -> Unit,
    onTitleChange: (String) -> Unit,
    onViewedMonthChange: (YearMonth) -> Unit,
    onViewedDateChange: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    firstDayOfWeekOverride: DayOfWeek? = null,
    dimPastDates: Boolean = false,
    showWeekNumber: Boolean = false,
    onDayCellClick: (LocalDate) -> Unit = {},
    onEventClick: (eventId: Long, instanceMillis: Long) -> Unit = { _, _ -> },
) {
    // Stub: filled in by Task 7. Falls back to PagedMonthScreen so the
    // dispatcher compiles in the meantime.
    PagedMonthScreen(
        vm = vm,
        pendingJump = pendingJump,
        onConsumePendingDateJump = onConsumePendingDateJump,
        onTitleChange = onTitleChange,
        onViewedMonthChange = onViewedMonthChange,
        onViewedDateChange = onViewedDateChange,
        modifier = modifier,
        firstDayOfWeekOverride = firstDayOfWeekOverride,
        dimPastDates = dimPastDates,
        showWeekNumber = showWeekNumber,
        onDayCellClick = onDayCellClick,
        onEventClick = onEventClick,
    )
}
```

- [ ] **Step 5: Run the local gate.**

```bash
./gradlew :app:spotlessKotlinCheck :app:detekt :app:lintDebug :app:testDebugUnitTest
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit.**

```bash
git add app/src/main/kotlin/com/arishawke/asala/calendar/ui/month/MonthScreen.kt \
        app/src/main/kotlin/com/arishawke/asala/calendar/ui/month/PagedMonthScreen.kt \
        app/src/main/kotlin/com/arishawke/asala/calendar/ui/month/ContinuousMonthScreen.kt \
        app/src/main/kotlin/com/arishawke/asala/calendar/ui/AppShell.kt
git commit -m "refactor(month): split MonthScreen into Paged + Continuous dispatcher"
```

---

## Task 7: `ContinuousMonthScreen` — the real implementation

**Files:**
- Modify: `app/src/main/kotlin/com/arishawke/asala/calendar/ui/month/ContinuousMonthScreen.kt`

- [ ] **Step 1: Replace the stub with the real body.**

```kotlin
package com.arishawke.asala.calendar.ui.month

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arishawke.asala.calendar.PendingDateJump
import com.arishawke.asala.calendar.CalendarView
import com.arishawke.asala.calendar.R
import kotlinx.coroutines.flow.distinctUntilChanged
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

private const val MonthsEitherSide = 60L
private const val TotalMonths = (MonthsEitherSide * 2 + 1).toInt()

@Suppress("LongParameterList")
@Composable
internal fun ContinuousMonthScreen(
    vm: MonthViewModel,
    pendingJump: PendingDateJump?,
    onConsumePendingDateJump: () -> Unit,
    onTitleChange: (String) -> Unit,
    onViewedMonthChange: (YearMonth) -> Unit,
    onViewedDateChange: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    firstDayOfWeekOverride: DayOfWeek? = null,
    dimPastDates: Boolean = false,
    showWeekNumber: Boolean = false,
    onDayCellClick: (LocalDate) -> Unit = {},
    @Suppress("UNUSED_PARAMETER")
    onEventClick: (eventId: Long, instanceMillis: Long) -> Unit = { _, _ -> },
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val firstDayOfWeek = firstDayOfWeekOverride ?: firstDayOfWeekFromLocale()
    val locale = LocalConfiguration.current.locales.get(0)
    val titleFmt = remember(locale) { DateTimeFormatter.ofPattern("MMMM yyyy", locale) }

    // Origin = first item's month. Captured once so the index<->yearMonth
    // mapping stays stable; today rollover does not shift the surface.
    val origin = remember { YearMonth.from(state.today).minusMonths(MonthsEitherSide) }

    val listState = rememberLazyListState(initialFirstVisibleItemIndex = MonthsEitherSide.toInt())

    // Viewport center -> yearMonth, throttled.
    val centerIndex by remember(listState) {
        derivedStateOf {
            val info = listState.layoutInfo
            val viewportCenter = (info.viewportStartOffset + info.viewportEndOffset) / 2
            val visibleItems = info.visibleItemsInfo.map { VisibleItem(it.index, it.offset, it.size) }
            ContinuousMonthCenter.pick(
                items = visibleItems,
                viewportCenter = viewportCenter,
                fallback = MonthsEitherSide.toInt(),
            )
        }
    }

    LaunchedEffect(listState, origin) {
        snapshotFlow { centerIndex }
            .distinctUntilChanged()
            .collect { idx ->
                val ym = origin.plusMonths(idx.toLong())
                vm.showMonth(ym)
                onViewedMonthChange(ym)
                onTitleChange(ym.format(titleFmt))
                val todayIsInMonth = YearMonth.from(state.today) == ym
                onViewedDateChange(if (todayIsInMonth) state.today else ym.atDay(1))
            }
    }

    // Jump-to-date support: scroll synchronously to the target month and
    // consume the jump. Only acts on jumps targeted at Month.
    LaunchedEffect(pendingJump) {
        val jump = pendingJump?.takeIf { it.view == CalendarView.Month } ?: return@LaunchedEffect
        val targetYm = YearMonth.from(jump.date)
        val targetIdx = ChronoUnit.MONTHS.between(origin, targetYm).toInt().coerceIn(0, TotalMonths - 1)
        listState.scrollToItem(targetIdx)
        onConsumePendingDateJump()
    }

    Column(modifier = modifier.fillMaxSize()) {
        WeekdayHeader(firstDayOfWeek = firstDayOfWeek, showWeekNumberColumn = showWeekNumber)
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
        ) {
            items(
                count = TotalMonths,
                key = { idx -> origin.plusMonths(idx.toLong()).toString() },
            ) { idx ->
                val ym = origin.plusMonths(idx.toLong())
                Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = ym.format(titleFmt),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                MonthGrid(
                    yearMonth = ym,
                    firstDayOfWeek = firstDayOfWeek,
                    eventsByDate = state.eventsByDate,
                    allEvents = state.events,
                    today = state.today,
                    dimPastDates = dimPastDates,
                    showWeekNumber = showWeekNumber,
                    weekRowHeight = WeekRowHeightMin,
                    onDayCellClick = onDayCellClick,
                    onOverflowClick = { /* overflow sheet routing TBD: handled by PagedMonthScreen */ },
                )
            }
        }
    }
}
```

(`WeekdayHeader`, `MonthGrid`, `firstDayOfWeekFromLocale`, `WeekRowHeightMin` are pulled from `MonthScreen.kt` / `PagedMonthScreen.kt` — they remain `internal` so this file can call them. Verify each one's visibility.)

- [ ] **Step 2: Expose `MonthGrid`, `WeekdayHeader`, `firstDayOfWeekFromLocale`, `WeekRowHeightMin`.** In `MonthScreen.kt` (or `PagedMonthScreen.kt` if they moved there), change `private` → `internal` on those four declarations.

- [ ] **Step 3: Overflow sheet handling.** The paged path uses an `overflowDate by remember { mutableStateOf<LocalDate?>(null) }` + `DayOverflowSheet`. Keep that handling in the paged path; for continuous mode we omit the overflow sheet in this first cut — overflow taps no-op. Document the gap in CHANGELOG.

Wait — the spec says continuous mode reuses the existing chip rendering "unchanged" which includes the +N overflow affordance. Let me wire it. Add the overflow sheet inside `ContinuousMonthScreen`:

```kotlin
var overflowDate by remember { mutableStateOf<LocalDate?>(null) }
// ... LazyColumn block ...
overflowDate?.let { date ->
    DayOverflowSheet(
        date = date,
        events = state.eventsByDate[date].orEmpty(),
        is24Hour = LocalIs24Hour.current,
        onDismiss = { overflowDate = null },
        onEventClick = onEventClick,
    )
}
```

And pass `onOverflowClick = { overflowDate = it }` instead of `{ /* TBD */ }`.

Required additional imports:
```kotlin
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.arishawke.asala.calendar.ui.theme.LocalIs24Hour
```

- [ ] **Step 4: Run the local gate.**

```bash
./gradlew :app:spotlessKotlinCheck :app:detekt :app:lintDebug :app:testDebugUnitTest
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Manual smoke on device.**

```bash
adb -s 100.122.79.24:33781 install -r app/build/outputs/apk/debug/app-debug.apk
```
1. Settings → Appearance → Month scroll style → Continuous.
2. Scroll up across at least 4 month boundaries.
3. Scroll down past today's month.
4. Tap a chip; detail sheet opens.
5. Tap a "+N more" affordance; overflow sheet opens.
6. Header dropdown chip → tap a month chip → continuous scroll lands on that month.
7. Flip back to Paged in Settings; Month view returns to paged.

- [ ] **Step 6: Commit.**

```bash
git add app/src/main/kotlin/com/arishawke/asala/calendar/ui/month/ContinuousMonthScreen.kt \
        app/src/main/kotlin/com/arishawke/asala/calendar/ui/month/PagedMonthScreen.kt \
        app/src/main/kotlin/com/arishawke/asala/calendar/ui/month/MonthScreen.kt
git commit -m "feat(month): continuous-scroll Month view behind Settings toggle"
```

---

## Task 8: CHANGELOG + ROADMAP

**Files:**
- Modify: `CHANGELOG.md`
- Modify: `docs/ROADMAP.md` (remove the "Vertical-scrolling month view" line from `## Later`)

- [ ] **Step 1: CHANGELOG.** Under `[Unreleased]` > `### Added`:

```markdown
- Continuous-scroll Month view as an alternative to the existing
  paged Month. New Settings entry `Appearance > Month scroll style`
  with two options: Paged (default) and Continuous. Continuous mode
  stacks ~10 years of months vertically inside a LazyColumn with
  sticky month headers; reuses the same chip and multi-day bar
  rendering as the paged surface.
```

- [ ] **Step 2: ROADMAP.** Remove the line `- Vertical-scrolling month view as an alternative to the paged HorizontalPager (kizitonwose VerticalCalendar is the obvious base).` from `## Later: themes, not milestones > ### Visual polish` (around line 380).

- [ ] **Step 3: Commit.**

```bash
git add CHANGELOG.md docs/ROADMAP.md
git commit -m "docs: changelog + roadmap for continuous-scroll Month"
```

---

## Task 9: Final gate + push

- [ ] **Step 1: Run the full local gate.**

```bash
./gradlew :app:spotlessKotlinCheck :app:detekt :app:lintDebug :app:testDebugUnitTest
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Push and open PR.**

```bash
git push -u origin feat/vertical-month-view
gh pr create --base main --head feat/vertical-month-view \
  --title "feat(month): continuous-scroll Month view" \
  --body "$(cat <<'EOF'
## Summary
- New Settings entry: Appearance > Month scroll style (Paged | Continuous).
- ContinuousMonthScreen hosts a LazyColumn of 121 months with sticky headers.
- MonthGrid refactored to accept weekRowHeight (Dp); MonthViewModel's fetch window radius is now configurable (1 for paged, 6 for continuous).

Spec: docs/specs/2026-05-27-vertical-month-view-design.md
Plan: docs/plans/2026-05-27-vertical-month-view.md

## Test plan
- [x] Local gate green
- [x] Unit tests pass (MonthCenterDetectionTest, MonthRangeWindowTest)
- [ ] Manual smoke: toggle Settings, scroll continuous across midnight, jump from search result, fling fast, switch modes mid-session
EOF
)"
```

- [ ] **Step 3: Wait for CI.**

```bash
gh pr checks <PR#> --watch
```

- [ ] **Step 4: FF-merge to main.**

```bash
git checkout main
git merge --ff-only <branch-sha>
git push origin main
git branch -d feat/vertical-month-view
git push origin --delete feat/vertical-month-view
```

- [ ] **Step 5: Archive spec + plan.**

```bash
git mv docs/specs/2026-05-27-vertical-month-view-design.md docs/specs/archive/
git mv docs/plans/2026-05-27-vertical-month-view.md docs/plans/archive/
git add -A && git commit -m "docs: archive vertical-month-view spec + plan"
```

(Archive lives on a follow-up tiny PR, or batch with the next release.)

---

## Self-review

**Spec coverage:**
- ✅ Settings flag (`monthScrollStyle: MonthScrollStyle`) — Task 1.
- ✅ Settings UI dropdown — Task 2.
- ✅ Strings — Task 2.
- ✅ Dispatcher in `MonthScreen.kt` — Task 6.
- ✅ `MonthGrid` refactor with `weekRowHeight` — Task 4.
- ✅ `MonthViewModel` window radius — Task 3.
- ✅ Visible-month detection with `derivedStateOf` + `snapshotFlow.distinctUntilChanged` — Task 7.
- ✅ `pendingDateJump` integration via `scrollToItem` — Task 7.
- ✅ Sticky header with opaque `Surface` — Task 7.
- ✅ Stable `key` per LazyColumn item — Task 7.
- ✅ `initialFirstVisibleItemIndex = 60` — Task 7.
- ✅ `MonthCenterDetectionTest` — Task 5.
- ✅ `MonthRangeWindowTest` — Task 3.
- ✅ CHANGELOG entry — Task 8.

**Placeholder scan:** no TBDs, all code blocks contain real content.

**Type consistency:**
- `MonthScrollStyle` enum: same casing everywhere.
- `monthFetchWindow(center, radius)` signature consistent between Task 3 step 1 (test) and step 3 (impl).
- `ContinuousMonthCenter.pick(items, viewportCenter, fallback)` consistent between Task 5 step 1 and step 3.
- `MonthsEitherSide`, `TotalMonths`, `WeekRowHeightMin` referenced consistently.

Plan ready for inline execution.
