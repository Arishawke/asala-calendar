# Multi-day all-day continuous bars (Month + Week)

## Context

User report: a multi-day all-day event (22nd-25th) only renders as a single
chip on the start day in Month view. Should render as a continuous bar
across all covered days, wrapping at week boundaries. Same pattern wanted in
Week view's `AllDayRow`.

Asala uses `kizitonwose/Calendar` for Month view paging; that library has
no built-in multi-day support, so we render the bars on top via an overlay
aligned to each week row.

The Local Only onboarding text is misleading by omission: it doesn't
mention that calendar permission is still required because Asala stores
events in Android's system Calendar Provider regardless of mode. Three-line
copy fix bundled in this PR.

## Design

### Pure helpers (testable without Compose)

`WeekBucketer.bucketize(events: List<EventItem>, weekStart: LocalDate): List<WeekSegment>`
- Split each event into per-week segments. Multi-week events become N
  segments, one per week.
- Each segment carries: eventId, lane (assigned next), startCol (0-6),
  endCol (0-6 inclusive), isContinuedLeft, isContinuedRight, title, color.
- Only multi-day all-day events get bucketed; single-day events stay on
  the existing per-cell rendering path.

`LaneAssigner.assignLanes(segments: List<WeekSegment>): List<WeekSegment>`
- Sort by longest span first, then start day.
- Greedy lowest-free-lane sweep: for each segment, find the smallest lane
  index where no other already-assigned segment overlaps.
- Lane stays sticky across the segment's days. Result is segments with
  `lane` field populated.

### Render

`MultiDayBarRow` Compose composable.
- Takes a `List<WeekSegment>` and a row width.
- Lays out each segment as a `Box` with `Modifier.offset(x = startCol *
  cellWidth)` and `Modifier.width((endCol - startCol + 1) * cellWidth)`.
- Background = segment color, rounded corners on natural ends, square on
  cut ends per `isContinuedLeft` / `isContinuedRight`.
- Title rendered inside the bar, truncated with ellipsis if it overflows.

### Month view integration

Currently the Month view uses kizitonwose's paged calendar with `DayCell`
per day. Multi-day all-day events bypass the per-cell event list and
instead render as an overlay positioned over each week row.

Approach: render the kizitonwose `HorizontalCalendar` as today, then on top
overlay a `Column` of week rows, each containing a `MultiDayBarRow` for the
week's bucketed segments. Use `Modifier.onGloballyPositioned` on the day
cells to capture the week row's vertical position (or compute it from the
calendar's known fixed cell height).

The existing per-cell event list in `DayCell` continues to handle:
- Single-day all-day events (still rendered as a chip on that day)
- Timed events (still as a chip)
- "+N more" overflow (still per-day)

Multi-day all-day events are removed from the per-cell list to avoid
double rendering.

### Week view integration

`AllDayRow.kt` already exists as a dedicated horizontal strip above the
timeline. Today it renders all-day events as per-day chips. Replace with
`MultiDayBarRow` driven by the same `WeekBucketer.bucketize` (called with
the visible week's start). Simpler than Month view because the row is
already dedicated to all-day content.

### Overflow

`maxLanesPerWeek` constant. For Month view, ~3 lanes per week before
"+N more" pill. For Week view, more generous (no timed chips to compete
with), ~4 lanes. The "+N more" pill on a row taps into the existing
`DayOverflowSheet` for the day under the pill.

## Files

### New
- `app/src/main/kotlin/com/arishawke/asala/calendar/ui/multidaybars/WeekBucketer.kt`
- `app/src/main/kotlin/com/arishawke/asala/calendar/ui/multidaybars/LaneAssigner.kt`
- `app/src/main/kotlin/com/arishawke/asala/calendar/ui/multidaybars/MultiDayBarRow.kt`
- `app/src/main/kotlin/com/arishawke/asala/calendar/ui/multidaybars/WeekSegment.kt`
- `app/src/test/kotlin/com/arishawke/asala/calendar/ui/multidaybars/WeekBucketerTest.kt`
- `app/src/test/kotlin/com/arishawke/asala/calendar/ui/multidaybars/LaneAssignerTest.kt`

### Modified
- `app/src/main/kotlin/com/arishawke/asala/calendar/ui/month/MonthScreen.kt`
  (overlay the bar layer over each week row)
- `app/src/main/kotlin/com/arishawke/asala/calendar/ui/month/DayCell.kt`
  (skip multi-day all-day events; they're handled by the overlay)
- `app/src/main/kotlin/com/arishawke/asala/calendar/ui/week/AllDayRow.kt`
  (rewrite to use MultiDayBarRow)
- `app/src/main/res/values/strings.xml` (Local-Only copy fix: add a
  sentence about calendar permission)
- `CHANGELOG.md` ([Unreleased] entries under Added)

## Verification

- Unit tests for `WeekBucketer` and `LaneAssigner` cover: single-week
  span, week wrap, multi-week wrap, lane stickiness, overflow.
- `./gradlew :app:lintDebug :app:testDebugUnitTest spotlessCheck detekt
  --continue` green.
- Fresh-install device verification:
  - Create an all-day event spanning a week boundary (Sat-Tue). Month
    view should show two bars (Sat-Sat row N, Sun-Tue row N+1) on the
    same lane. Both bars same color, title in the first segment, cut
    edge square on the right of segment 1 and left of segment 2.
  - Create two overlapping multi-day events. Stack on separate lanes.
  - Create 5 overlapping. See "+N more" overflow appear.
  - Week view's all-day row shows the same bars (continuous, not per-day).
  - Settings -> Storage Mode -> Local Only on first launch; the copy now
    mentions permission is still required.

## Calibration note

Solo / hobby. One PR bundling Month + Week + copy fix per user request.
Internal commits split for review readability: (1) pure helpers + tests,
(2) Month view rendering, (3) Week view rendering, (4) copy fix.
