# Plan: header dropdowns for in-place date navigation

Date: 2026-05-26.
Spec:
[2026-05-26-header-dropdowns-design.md](../specs/2026-05-26-header-dropdowns-design.md).

## Steps

- [ ] `ui/AppShell.kt`: change the `TopAppBar.title` slot to a
  clickable Row of `Text` + chevron icon (rotation animated via
  `animateFloatAsState`). Wrap the `topBar` lambda's content in a
  `Column` and append an `AnimatedVisibility(visible = headerExpanded)`
  block hosting the panel composable. Add a `BackHandler` for the
  expanded state.
- [ ] New file `ui/header/HeaderDropdownPanel.kt`: dispatcher
  composable that picks `MonthChipsPanel` when
  `currentView == CalendarView.Month` and `MiniMonthPanel` otherwise.
  Hides itself for Tasks.
- [ ] New file `ui/header/MonthChipsPanel.kt`: `LazyRow` of
  `FilterChip`s across a 25-month window centered on the viewed
  month. Initial scroll positions the viewed month chip near the
  center. Selected chip = viewed month. Tap -> `onSelectMonth`.
- [ ] New file `ui/header/MiniMonthPanel.kt`: top nav row
  (`<` / `Month Year` / `>`), weekday header, 6x7 cell grid. Cells
  show date number plus up to three colored dots derived from
  `eventsByDate[date]` (group by `calendarId`, take first three).
  Today gets a primary-filled circle. Out-of-month days dim.
- [ ] New file `ui/header/MiniMonthViewModel.kt`: holds
  `displayedMonth` and exposes `eventsByDate` for that month by
  combining `EventRepository.observeEvents(startOfMonth, startOfNextMonth, zone)`
  with the existing `hiddenCalendarIdsFlow` +
  `calendarColorOverridesFlow` + `eventColorOverridesFlow`. Resets
  `displayedMonth` on a `resetToCurrent()` call (invoked on panel
  open).
- [ ] `AppViewModel.kt`: no API surface change beyond
  `requestJumpTo`; verify it works for all four target views. Add a
  `viewedMonthForHeader: StateFlow<YearMonth>` only if Month view
  needs to know its own currently visible month for chip selection
  highlighting (probably yes; thread it through `MonthScreen` via a
  callback to `AppViewModel`).
- [ ] `ui/month/MonthScreen.kt`: add `pendingDateJump` collection
  that animates the pager to the target `YearMonth`. Push the
  currently visible `YearMonth` up to `AppViewModel` via a setter so
  the chip panel knows which chip is selected.
- [ ] `ui/week/WeekScreen.kt`: add `pendingDateJump` collection that
  animates the pager to the week containing the target date.
- [ ] `ui/schedule/ScheduleScreen.kt`: add `pendingDateJump`
  collection that scrolls `listState` to the index of that date in
  `state.daysInOrder` (or the next available day).
- [ ] `res/values/strings.xml`: `cd_header_expand` /
  `cd_header_collapse` for the chevron content descriptions;
  `cd_mini_month_prev` / `cd_mini_month_next` for the panel nav
  arrows; `cd_mini_month_reset` for tapping the month-year header.
- [ ] `CHANGELOG.md` `[Unreleased]`: Added entry for header
  dropdowns.
- [ ] CI: `./gradlew :app:lintDebug :app:testDebugUnitTest spotlessCheck detekt :app:assembleDebug`
  green; regenerate baselines if signature drift hits.
- [ ] Device verify on Pixel 10 Pro XL:
  - Month: tap title -> chips slide down (content pushes down, not
    overlay). Tap "Mar '26" -> month pager jumps; panel collapses.
  - Day: tap title -> mini-month slides down with dots. Tap any
    day -> Day view scrolls to it. `<` / `>` navigate panel months
    without leaving Day view.
  - Week: same as Day but jump scrolls Week pager to that week.
  - Schedule: tap a date -> Schedule scrolls to that day's section.
  - Back gesture closes the panel before navigating views.
  - Theme flip: chevron + dots flip with theme.

## Files touched

- `app/src/main/kotlin/com/arishawke/asala/calendar/ui/AppShell.kt`
- **NEW** `app/src/main/kotlin/com/arishawke/asala/calendar/ui/header/HeaderDropdownPanel.kt`
- **NEW** `app/src/main/kotlin/com/arishawke/asala/calendar/ui/header/MonthChipsPanel.kt`
- **NEW** `app/src/main/kotlin/com/arishawke/asala/calendar/ui/header/MiniMonthPanel.kt`
- **NEW** `app/src/main/kotlin/com/arishawke/asala/calendar/ui/header/MiniMonthViewModel.kt`
- `app/src/main/kotlin/com/arishawke/asala/calendar/AppViewModel.kt` (if month-view tracking is needed)
- `app/src/main/kotlin/com/arishawke/asala/calendar/ui/month/MonthScreen.kt`
- `app/src/main/kotlin/com/arishawke/asala/calendar/ui/week/WeekScreen.kt`
- `app/src/main/kotlin/com/arishawke/asala/calendar/ui/schedule/ScheduleScreen.kt`
- `app/src/main/res/values/strings.xml`
- `CHANGELOG.md`

## Reuse

- `AppViewModel.requestJumpTo(date, view)` + `pendingDateJump` flow
  ([AppViewModel.kt:287-292](../../app/src/main/kotlin/com/arishawke/asala/calendar/AppViewModel.kt#L287-L292),
  already covers cross-view navigation).
- `EventRepository.observeEvents(startDate, endExclusive, zone)`
  for the mini-month's data window.
- `filteredAndRecolored` pipeline
  ([EventItem.kt:71-76](../../app/src/main/kotlin/com/arishawke/asala/calendar/data/EventItem.kt#L71-L76))
  for hidden + override application on the mini-month events.
- `firstDayOfWeekFromLocale()` and the `weekStartsOn` pref for the
  mini-month weekday order.
- `CalendarTokens.todayHighlight` / `onTodayHighlight` for the
  today-cell circle.
