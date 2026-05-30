# Spec: 3-Day view

Date: 2026-05-29.
Status: approved design, not yet implemented.

## Problem

The app offers Month, Week, and Day timelines. Week packs seven days
across a phone screen (~50dp per column), and Day fills the screen with
one. There is no middle option: a few days at readable width that still
shows adjacent-day context. Google Calendar's 3-day view fills exactly
this gap, and it is a common request for a calendar that bills itself as
a Google-Calendar-quality peer.

## Goal

A 3-day timeline that pages three days at a time, sitting between Week
and Day in the view list. It reuses Week's timeline rendering (header,
grid, columns, event blocks, now-line, working-hours and working-days
dimming) so a 3-element day list produces three equal, readable columns.

## Decisions

- **Rolling anchor.** Column 0 of a page is the date you switched in on;
  swiping steps by exactly 3 days from there. This matches how Day view
  rolls from a date and is what Google Calendar's 3-day does. We do NOT
  snap to fixed 3-day epochs.
- **No crowded-overflow chip.** 3-day columns are ~2.3x wider than
  Week's, so overlap crowding is rare. The view shows all overlapping
  events side by side, matching Day's "wide column, no overflow"
  behavior. Week keeps its "+N" overflow; 3-day opts out.
- **Window: +/-60 days.** Same reach Day view already uses (~2 months
  each way), held in a single event subscription. Smallest footprint,
  consistent with Day.
- **Label: `3-Day`.** Title-Case, matching the sibling labels (Month,
  Week, Day, Schedule).
- **Position: between Week and Day** in the `CalendarView` enum. Enum
  order drives the drawer and settings-list position.

## Non-goals

- No change to Week, Day, Month, or Schedule.
- No new overlap or event-layout algorithm: the reused renderers keep
  their existing behavior and tests.
- No Settings toggle for anchoring or overflow.
- No new persistence migration (see Persistence below).
- No new drawable: the view switcher renders a label, not an icon.

## Approach: thin sibling screen, reuse Week's rendering

Week's pager math (page <-> date) is private inside `WeekScreen.kt`: it
snaps the anchor to a week boundary and steps by whole weeks. 3-day must
roll from a date and step by 3 days. That is genuinely different paging
math, so a flag through `WeekScreen` would tangle two models and break
the ~200-line file convention. Instead, a new `ui/threeday/` package
owns its own pager and viewmodel but renders through Week's existing
composables.

### New files

- **`ui/threeday/ThreeDayPageMath.kt`** - pure, testable helpers:
  - `pageStart(anchor: LocalDate, page: Int, center: Int): LocalDate`
    returns `anchor.plusDays((page - center) * 3L)`.
  - `pageForDate(anchor: LocalDate, date: LocalDate, center: Int): Int`
    returns `center + Math.floorDiv(daysBetween(anchor, date), 3)`.
  `Math.floorDiv` is required so dates before the anchor (negative
  offsets) round toward the correct earlier page rather than toward
  zero. This is the only real logic in the feature, so it is isolated
  and unit-tested directly (no Compose).

- **`ui/threeday/ThreeDayScreen.kt`** (~120 lines) - the pager. Center
  page = today; window is `WindowPagesEachSide` pages each side
  (`41`-page total span covering ~+/-60 days, mirroring Day's reach).
  Each page's three dates come from `pageStart`. Renders each page via
  the existing `WeekPage(days = pageDays, enableOverflow = false, ...)`.
  Carries its own:
  - pending-jump filter `it.view == CalendarView.ThreeDay` (the other
    screens already ignore foreign jumps; no change to them),
  - today-jump (`todayJumpCounter` -> animate to center page),
  - `onViewedDateChange` reporting the page's column-0 date.

- **`ui/threeday/ThreeDayViewModel.kt`** - modeled on `DayViewModel`
  (fixed window computed once at init + a single
  `eventRepo.observeEvents(...)` subscription), NOT `WeekViewModel`
  (whose per-week `flatMapLatest` re-subscribes on every page and would
  churn the ContentObserver when paging by 3). With
  `WindowPagesEachSide = 20` (~+/-60 days, matching Day), the furthest
  forward page starts at `today + 60` and covers days 60-62, so the
  fetch window runs start-inclusive `today.minusDays(60)` to end-exclusive
  `today.plusDays(63)`, i.e. `today.plusDays(WindowPagesEachSide * 3 + 3)`.
  The `+ 3` covers the three days of the last page (a one-day-per-page
  view like Day uses `+ 1`; this view must not). Reuses
  `EventRepository.observeEvents` and `dayRangeMillis` unchanged.

- **`app/src/test/kotlin/.../ui/threeday/ThreeDayPageMathTest.kt`** -
  see Testing.

### Touched existing files

- **`ui/week/WeekScreen.kt`** - two `private -> internal` widenings, no
  structural change:
  - `WeekPage` so the new package can render a page. Despite the name it
    is already a general N-day timeline page (it iterates `days` of any
    length); we keep the name rather than rename to stay surgical.
  - `formatWeekRange` so the 3-day toolbar title can reuse it as
    `formatWeekRange(start, start.plusDays(2), locale)`. It is not
    week-specific: it ICU-formats any date interval ("Mar 2 - 4, 2026"),
    which is exactly the title a 3-day page wants.
  `WeekDayHeader`, `TimelineGrid`, `DayColumn`, `AllDayRow`, and
  `DayOverflowSheet` are already `internal` and reused unchanged.
  `WeekDayHeader` lays columns out with `weight(1f)`, so a 3-element list
  yields three equal columns for free.

- **Overflow opt-out (additive).** Add `enableOverflow: Boolean = true`
  to `WeekPage` and to `TimelineGrid`. `TimelineGrid` currently passes an
  unconditional `onOverflow` lambda to `DayColumn`; gate it so it passes
  `onOverflow = null` when `enableOverflow` is false. A null `onOverflow`
  makes `DayColumn` use threshold `Int.MAX_VALUE` (nothing collapses, no
  "+N" chip), and the `DayOverflowSheet` host stays composed but never
  fires. Week calls with the default `true`; 3-day passes `false`. Gated
  behind a defaulted parameter, so Week's behavior is byte-identical.

### Rendering parity: 3-day follows Week's chips, not Day's

"3-day is a wider Day" describes the screen-level params (see View
registration), NOT the chip rendering. Because the view renders through
`WeekPage -> TimelineGrid -> DayColumn`, it inherits Week's chip
treatment, which differs from Day's hand-rolled `Timeline` in two ways.
Both are deliberate and correct for three medium-width columns:

- **`showEndTime = false`** (Week's default). Day passes `showEndTime =
  true` because its single column is wide enough for an end-time label;
  three columns are not, so no end time on the chip. Matches Week.
- **`dimPastDates = false`.** Day's screen takes no `dimPastDates` and
  never dims; we mirror Day and pass `false` to `WeekPage`. (3-day shows
  past days alongside today, so enabling the dim later is reasonable, but
  it is out of scope here and off by default to match Day.)

### Event filtering and recolor (must replicate)

`ThreeDayViewModel` must apply
`evs.filteredAndRecolored(hidden, calOverrides, evtOverrides)` in its
`combine`, exactly as `DayViewModel` does. This is what honors hidden
calendars and per-calendar / per-event color overrides; it is easy to
omit when copying the window logic and is load-bearing.

## View registration

The main risk is a missed switch site. `ThreeDay` is added to the
`CalendarView` enum at `AppViewModel.kt` (currently
`{ Month, Week, Day, Schedule, Tasks }`), positioned between `Week` and
`Day`. `isAlwaysVisible()` (`!= Tasks`) covers it automatically.

Three exhaustive `when`s have no `else`, so the compiler flags any miss:

- `CalendarViewLabel.kt` - add `CalendarView.ThreeDay -> R.string.view_three_day`.
- `ui/CalendarViewSwitcher.kt` - add `CalendarView.ThreeDay -> ThreeDayScreen(...)`,
  mirroring the params **Day** receives (hidden-calendar + color-override
  flows, `onTitleChange`, `todayJumpCounter`, `pendingDateJump` +
  `onConsumePendingDateJump`, the four working-hours params,
  `workingDaysEnabled`/`workingDaysMask`, `onEventClick`, `onReschedule`,
  `onViewedDateChange`) plus the import. Day passes working-hours but NOT
  `showWeekNumber`; 3-day follows Day.
- `ui/header/HeaderDropdownPanel.kt` - add `ThreeDay` to the
  `Week, Day, Schedule ->` mini-month branch.

Auto-pickup (verify only, no edit): the drawer
(`ui/month/CalendarDrawer.kt`) and the Settings default-view dropdown
(`ui/settings/SettingsRows.kt`) both build their list from
`CalendarView.entries.filter { it.isAlwaysVisible() || tasksEnabled }`
and render `view.label()`. ThreeDay appears in both with no edit.

Final audit before declaring done: `grep -rn "CalendarView\." app/src/main/kotlin`
and `grep -rn "selectedView\|defaultView" app/src/main/kotlin`.

## Persistence

Default-view persistence is name-based with a `Month` fallback
(`UserPreferences.kt`, `CalendarView.valueOf(it)`), so adding an enum
value round-trips with NO migration. A user who sets 3-Day as default
has `"ThreeDay"` stored and restored; older stored names still resolve.

## New resources

- String `view_three_day` = `3-Day` in
  `app/src/main/res/values/strings.xml`, beside the existing
  `view_month/week/day/schedule` block. HardcodedText is a lint ERROR,
  so the switcher's `Text` must use `stringResource`.
- No new drawable (label-only switcher).

## Testing

- **`ThreeDayPageMathTest.kt`** (pure, no Compose):
  - center page maps to the anchor; `center + 1` -> anchor + 3 days;
    `center - 1` -> anchor - 3 days.
  - a page covers a contiguous `[start, start + 3)` span that does not
    overlap its neighbors.
  - `pageForDate` is the inverse of `pageStart`, including a date BEFORE
    the anchor (negative offset, the `Math.floorDiv` case) and the
    not-a-multiple-of-3 offsets that must floor to the page containing
    the date.
- Event-fetch window math is already covered by `DayRangeMathTest` (the
  VM reuses `EventRepository.observeEvents` / `dayRangeMillis`); no new
  repo-window test.
- Device smoke (per fresh-install practice): pick 3-Day from the drawer;
  three equal columns with correct header dates; swipe steps by exactly
  3 days; today-jump recenters; FAB opens the editor on a sensible date;
  drag-reschedule works; Week and Day are visually unchanged; relaunch
  with 3-Day set as default restores it.

## Risk

Low. The feature is additive: one new package plus three compiler-forced
`when` edits and two visibility widenings. The reused renderers are
unchanged except for a single defaulted `enableOverflow` param that
leaves Week's default behavior identical. The only real logic
(`ThreeDayPageMath`) is isolated and unit-tested. The named-persistence
fallback means no migration. The compiler's exhaustiveness checks are
the safety net against a missed switch site.

## Roadmap and changelog

- Add 3-Day view to `docs/ROADMAP.md` Now/Next when work starts.
- CHANGELOG `[Unreleased]` Added entry when it lands (user-visible).

## Verification

Plan file: to be created at
`docs/plans/2026-05-29-three-day-view.md`.

1. `./gradlew :app:compileDebugKotlin` first - surfaces any
   non-exhaustive `when`.
2. Full gate: `./gradlew :app:spotlessKotlinCheck :app:detekt
   :app:lintDebug :app:testDebugUnitTest`.
3. Fresh-install device smoke as above.
