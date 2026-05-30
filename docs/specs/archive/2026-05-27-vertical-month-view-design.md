# Vertical-scrolling Month view — design

Status: approved 2026-05-27. Spec author and implementer: Claude under
Arishawke's direction. Ships behind a Settings toggle alongside the
existing paged Month.

## Why

The paged Month view (HorizontalPager of 6x7 grids) is fine for "what
events fit on this month" but forces a discrete swipe per month
boundary. A continuous-scroll mode is on the roadmap as a long-standing
alternative-axis ask. Adds one Settings dropdown; default behavior
unchanged for existing users.

## What ships

A new `Settings > Appearance` entry, **Month scroll style**, with two
options:
- **Paged** (default) — current HorizontalPager behavior, unchanged.
- **Continuous** — a single vertical scroll across ~10 years, sticky
  month headers between grids, same per-day chip rendering as paged.

No new bottom-nav entry. Same Month icon, same destination; the
Settings flag picks the surface.

## Surface

The continuous surface stacks 121 month grids (±60 from today, matching
the paged range) inside a `LazyColumn`. Before each grid: a sticky
header rendering "May 2026" in the user's locale. Each grid is the
existing `MonthGrid` composable rendering its 6 week rows. Day cells,
multi-day all-day bars, +N more overflow, today highlight, and
luminance-aware chip colors are reused unchanged from the paged path.

ASCII shape at the May/June boundary:
```
... weeks of May ...
| Sun | Mon | Tue | Wed | Thu | Fri | Sat |
|  25 |  26 |  27 |  28 |  29 |  30 |  31 |
[ chip rows for May 25-31 ]
========================================
[ STICKY HEADER: June 2026                ]
========================================
| Sun | Mon | Tue | Wed | Thu | Fri | Sat |
|  31 |   1 |   2 |   3 |   4 |   5 |   6 |  <- weekday header inside grid
|     |   1 |   2 |   3 |   4 |   5 |   6 |
... weeks of June ...
```
The May 25-31 row is May's last week (it uses `outDateStyle = EndOfRow`
implicitly: 5 weeks for May 2026). June's grid renders its own 6 rows.
The Sun May 31 / Mon Jun 1 split is acceptable because the sticky
header makes the boundary unambiguous.

## Architecture

### Settings flag

New enum `MonthScrollStyle { Paged, Continuous }` next to `ThemeMode`
in `ui/settings/`. New field on `UserPrefs`:
```kotlin
val monthScrollStyle: MonthScrollStyle = MonthScrollStyle.Paged
```
DataStore key `month_scroll_style` (string), parsed via the existing
`parseEnum` helper. New `setMonthScrollStyle(style)` on
`UserPreferences`. Matches the convention of `ThemeMode`, `CalendarView`,
`StorageMode`, `PaletteId`.

### Settings UI

New `MonthScrollStyleRow` composable in `ui/settings/SettingsRows.kt`
(mirrors `ThemeRow`'s dropdown shape). Wired into `SettingsScreen.kt`
under `## Appearance`, after the working-hours / working-days toggles
and before the show-week-number toggle. Three strings added to
`res/values/strings.xml`:
- `settings_month_scroll_style` — section label
- `settings_month_scroll_style_paged` — "Paged"
- `settings_month_scroll_style_continuous` — "Continuous"

### Routing

`ui/month/MonthScreen.kt` becomes a thin dispatcher:
1. Hoist the `viewModel<MonthViewModel>(factory = ...)` call here so
   both subscreens share the instance regardless of which renders.
2. Read `prefs.monthScrollStyle` from the AppViewModel prefs flow.
3. Route to `PagedMonthScreen` (existing body lifted verbatim) or
   `ContinuousMonthScreen` (new).

Both subscreens receive the same parameters and callbacks the current
`MonthScreen` does.

### `MonthGrid` refactor

Currently `MonthGrid` uses `Modifier.weight(1f)` per week row inside a
`Column(Modifier.fillMaxSize())`, so it divides the parent's height
into six equal rows. In a `LazyColumn` item the parent has unbounded
height; `weight` does not work.

Refactor: `MonthGrid` accepts a `weekRowHeight: Dp` parameter and
applies `Modifier.height(weekRowHeight)` on each `WeekLayoutRow`.
- `PagedMonthScreen` computes
  `weekRowHeight = (availableHeight / 6).coerceAtLeast(WeekRowHeightMin)`
  from a `BoxWithConstraints` wrapping the pager.
- `ContinuousMonthScreen` uses a fixed `WeekRowHeightContinuous = 96.dp`.

`WeekLayoutRowCore`'s inner `Modifier.weight(1f)` (chip-area resolution
within a now-bounded week row) stays unchanged.

### Minimum row height

`WeekRowHeightMin = 96.dp`. Below that, `EventChips.BoxWithConstraints.maxHeight`
falls under one chip's slot, and `capacityByHeight` collapses to zero —
silently hiding chips behind a "+N more". 96 dp on a typical device
fits 2 chips + the day-number badge + the +N more affordance with a
safety margin. Empirically verified during smoke (see test plan).

### `MonthViewModel` widened window

Today: `eventsForMonth = visibleMonth.flatMapLatest { ym -> observeEvents(ym.minusMonths(1), ym.plusMonths(2)) }` —
fixed `±1` month buffer around `visibleMonth`.

Continuous mode pre-composes many month items at once; a fixed `±1`
window leaves most composed cells empty during scroll. Fix: add a
`monthWindowRadius: Int` constructor parameter on `MonthViewModel`.
- Paged passes `1` (current behavior, no change).
- Continuous passes `6` (~13-month window — covers a fast fling
  without re-querying every month boundary).

Inside `flatMapLatest` the window becomes
`observeEvents(ym.minusMonths(radius), ym.plusMonths(radius + 1))`.
`visibleMonth` in continuous mode tracks the viewport-center yearMonth.

### Visible-month detection

`ContinuousMonthScreen` derives the center yearMonth via:
```kotlin
val centerMonth by remember(state) {
    derivedStateOf {
        val info = state.layoutInfo
        val viewportCenter = (info.viewportStartOffset + info.viewportEndOffset) / 2
        val centerItem = info.visibleItemsInfo.minByOrNull {
            kotlin.math.abs(it.offset + it.size / 2 - viewportCenter)
        } ?: return@derivedStateOf origin
        indexToYearMonth(centerItem.index, origin)
    }
}
LaunchedEffect(state) {
    snapshotFlow { centerMonth }
        .distinctUntilChanged()
        .collect { ym ->
            vm.showMonth(ym)
            onViewedMonthChange(ym)
            onTitleChange(ym.format(titleFmt))
            onViewedDateChange(if (today in ym) today else ym.atDay(1))
        }
}
```
`derivedStateOf` throttles to value changes; `snapshotFlow` +
`distinctUntilChanged` throttles to actual month transitions, not
per-frame fires.

### `pendingDateJump` integration

`origin = today.minusMonths(60)`. Target item index:
```kotlin
val targetIndex = ChronoUnit.MONTHS.between(
    origin.atDay(1),
    jump.date.withDayOfMonth(1),
).toInt().coerceIn(0, TotalMonths - 1)
```
Use `state.scrollToItem(targetIndex)` (synchronous snap — matches the
"show me this date now" intent of a search-result tap). Consume the
jump immediately after via `onConsumePendingDateJump()`. The
`it.view == CalendarView.Month` filter from
`AppViewModel.consumePendingDateJump` carries over unchanged; both
subscreens read the same `pendingDateJump` channel and filter to Month.

### Header dropdown chip strip

Already routes via `pendingDateJump` (`AppShell.kt:256` →
`requestJumpTo(ym.atDay(1), CalendarView.Month)`). Works in continuous
mode for free once the jump handler above is wired. No `MonthChipsPanel`
change needed.

### Today highlight across midnight

`MonthViewModel.todayFlow` is already in place from v0.12.0; the today
highlight refreshes live. In continuous mode, every composed month item
re-highlights on the midnight rollover — a small recomposition wave,
but functionally correct.

### Sticky header

`stickyHeader { ... }` lambda renders a `Surface(color =
colorScheme.surface)` with an opaque background so day cells below
don't bleed through during the sticky transition. Header content:
month name + year per locale, `MaterialTheme.typography.titleMedium`,
left-aligned, 12 dp vertical padding.

### Initial scroll position

`rememberLazyListState(initialFirstVisibleItemIndex = 60)` — start on
today's month. Compose's built-in `LazyListState.Saver` preserves
position across config changes (rotation, etc.) for free; we don't
persist across process death (opening Asala always starts on today's
month, matching paged mode's `InitialPage = 60`).

### Stable item key

```kotlin
items(count = TotalMonths, key = { idx -> indexToYearMonth(idx, origin).toString() }) { idx -> ... }
```
Stable across recomposition; survives prefs-flip re-renders.

## Edge cases

- **Mode switch at runtime.** Hoisted `MonthViewModel` survives the
  flip; both subscreens reuse it. The disposed subscreen's scroll
  position is lost; on re-entry to that mode we re-seed to today's
  month.
- **Empty `visibleItemsInfo`** (during first composition before layout):
  `centerMonth` defaults to the `origin` value; the
  `LaunchedEffect`'s `distinctUntilChanged` makes the first real
  emission once layout completes.
- **Fast fling across many months.** `snapshotFlow` debounces; we
  collect only the final stopped position. `vm.showMonth` does not
  re-fetch unless the new center is outside the current window
  (`flatMapLatest` cancels in-flight queries).
- **Far-future / far-past chip taps from the header chip strip.** The
  chip strip is `MonthsEitherSide = 12`. Anything beyond that requires
  manual scroll. Parity with paged mode (same chip range either way).

## Out of scope

- Variable-height week rows (deferred; would unblock the multi-line
  titles backlog item but breaks the chip-capacity math).
- True week-row-streaming surface (Approach C; bigger refactor).
- Persisting scroll position across process death.
- Animated jump-to-month (uses synchronous `scrollToItem`; if users
  ask for a smooth animation, switch to `animateScrollToItem` later).
- `kizitonwose/Calendar` library adoption (Approach A; not needed for
  this design).

## Verification

### Unit tests

- `MonthCenterDetectionTest` — pure function: given a list of
  `(itemIndex, itemOffset, itemSize)` tuples and a viewport range,
  returns the correct center yearMonth.
- `MonthRangeWindowTest` — pin that with `monthWindowRadius = 6`, the
  VM's `eventsForMonth` queries `[ym - 6, ym + 7)`.

Existing pure tests (`WeekBucketer`, `LaneAssigner`, `buildMonthGrid`,
`StartShiftDuration`, etc.) stay green — none of them touch the
pager or LazyColumn surface.

### Manual smoke

Fresh-install device:
1. Open Asala; Month view renders in paged mode (default).
2. Settings → Appearance → Month scroll style → Continuous.
3. Month view re-renders as continuous; scroll up + down across at
   least 4 month boundaries; chips and bars match the paged density.
4. Tap a search result that targets a Month-view date; scroll lands
   on that month.
5. Tap a header-dropdown month chip; scroll lands on that month.
6. Set device clock to 23:58 with the continuous Month open; wait
   past midnight; today highlight advances.
7. Flip the Settings dropdown back to Paged; Month view returns to
   paged with no visual artifacts.
8. Rotate the device with continuous Month open and mid-scroll;
   scroll position survives.
9. Verify chip count at 96 dp row height matches paged density on
   a busy month with 4+ events per day.

### Local gate

```
./gradlew :app:spotlessKotlinCheck :app:detekt :app:lintDebug :app:testDebugUnitTest
```
Must pass before push.

## File-level inventory

New:
- `app/src/main/kotlin/com/arishawke/asala/calendar/ui/month/PagedMonthScreen.kt` (existing `MonthScreen.kt` body lifted here)
- `app/src/main/kotlin/com/arishawke/asala/calendar/ui/month/ContinuousMonthScreen.kt`
- `app/src/main/kotlin/com/arishawke/asala/calendar/ui/settings/MonthScrollStyle.kt` (or inline next to existing enums)
- `app/src/test/kotlin/com/arishawke/asala/calendar/ui/month/MonthCenterDetectionTest.kt`
- `app/src/test/kotlin/com/arishawke/asala/calendar/ui/month/MonthRangeWindowTest.kt`

Modified:
- `app/src/main/kotlin/com/arishawke/asala/calendar/ui/month/MonthScreen.kt` (becomes dispatcher; existing body removed)
- `app/src/main/kotlin/com/arishawke/asala/calendar/ui/month/MonthViewModel.kt` (add `monthWindowRadius`)
- `app/src/main/kotlin/com/arishawke/asala/calendar/ui/settings/UserPreferences.kt` (add enum field, DataStore key, setter)
- `app/src/main/kotlin/com/arishawke/asala/calendar/ui/settings/SettingsViewModel.kt` (initial state, setter)
- `app/src/main/kotlin/com/arishawke/asala/calendar/ui/settings/SettingsRows.kt` (new `MonthScrollStyleRow`)
- `app/src/main/kotlin/com/arishawke/asala/calendar/ui/settings/SettingsScreen.kt` (insert row under Appearance)
- `app/src/main/res/values/strings.xml` (3 new strings)
- `CHANGELOG.md` (`[Unreleased]` Added entry)
