# Midnight-crossing event presentation

Date: 2026-05-30
Status: approved (design), implementing

## Context

A timed event that crosses midnight (e.g. 11:55pm to 12:55am) is already
split into one chip/row per covered day by `clipToDay`. The problem is the
labels on the second-day piece, which make it read like a separate event:

- Timeline grid (Week / 3-Day): piece 2 sits at the top of the next day's
  column showing the original start time with no continuation marker.
- Day view: piece 1 shows `23:30 - 00:00` (end clamped to midnight) and
  piece 2 shows `23:30 - 00:30`, neither marked as a continuation.
- Schedule (agenda): `ScheduleScreen` overrides the row start with the
  clipped `displayStartMillis`, so piece 2 shows `12:00 AM` with no badge.

Google Calendar's pattern (confirmed on device): each piece keeps the title
plus a `N/total` fraction, and shows the meaningful boundary for that piece.
For "Test" 11:55pm to 12:55am: day 1 reads `Test 1/2` at `11:55pm`, day 2
reads `Test 2/2` ending `12:55am`. Neither piece shows a bare `00:00`.

## Goal

Make midnight-crossing timed events read as one event in two (or more) parts,
matching Google: a segment fraction on the title and one meaningful time per
piece. Single-day events are unchanged. All-day events are out of scope (they
go through `WeekBucketer`, not `clipToDay`).

## Approach

Compute segment numbers once where the split already happens, then render
them on both surfaces.

### 1. Segment math on `DayClippedEvent` (pure, tested)

[ui/timeline/DayClippedEvent.kt](../../app/src/main/kotlin/com/arishawke/asala/calendar/ui/timeline/DayClippedEvent.kt)

- Add `segmentIndex: Int` and `segmentCount: Int` to `DayClippedEvent`.
- Compute in `clipToDay` from the event's own span (window-independent, so a
  piece whose first day is off-screen still reads `2/2`):
  - `startDay = startMillis -> LocalDate(zone)`
  - `lastDay  = (maxOf(endMillis - 1, startMillis)) -> LocalDate(zone)`
    (the `-1ms` keeps an event ending exactly at midnight on the prior day,
    matching the existing clip null-boundary behavior)
  - `segmentCount = lastDay - startDay + 1`
  - `segmentIndex = date - startDay + 1`
- Add a pure helper for the per-piece anchor time:
  `segmentAnchorMillis(clip): Long?` returns `startMillis` for the first
  piece, `endMillis` for the last, `null` for a single-day event or a middle
  piece (which fills a whole column and shows only the fraction).

### 2. Grid rendering

[ui/components/EventVisual.kt](../../app/src/main/kotlin/com/arishawke/asala/calendar/ui/components/EventVisual.kt),
[ui/week/EventBlock.kt](../../app/src/main/kotlin/com/arishawke/asala/calendar/ui/week/EventBlock.kt)

- Thread `segmentIndex` / `segmentCount` from `EventBlock` (already holds the
  `DayClippedEvent`) through `EventChipBlock` (new params, default `1`) into
  `EventBlockLabels`.
- In `EventBlockLabels`, when `segmentCount > 1`:
  - Append ` N/total` to the title via a new compact string
    `event_segment_badge` = `%1$d/%2$d` (Google's form: `Test 1/2`).
  - Time row uses `segmentAnchorMillis`: first piece -> real start, last
    piece -> real end, middle piece -> no time row. This replaces the
    misleading `- 00:00` / repeated-start and ignores `showEndTime` for
    multi-day pieces only.
- Single-day events keep the existing `showEndTime` behavior untouched.
- Day / Week / 3-Day all route through `EventBlock` -> `EventChipBlock`, so
  one change covers all three.

### 3. Schedule rendering

[ui/schedule/ScheduleViewModel.kt](../../app/src/main/kotlin/com/arishawke/asala/calendar/ui/schedule/ScheduleViewModel.kt)

- In `expandTimed`, set `dayIndex = clip.segmentIndex` and
  `totalDays = clip.segmentCount` on the `ScheduleRow`. The existing
  `row.totalDays > 1` badge path in `ScheduleScreen` then renders the
  established `(Day N/M)` badge on timed crossers, matching how the agenda
  already shows multi-day all-day events. No new schedule string, no
  `EventChipRow` change.

Note: the grid uses the compact `N/total` form (space-constrained chips,
matches Google) while the agenda reuses its existing `(Day N/M)` badge
(consistent with the agenda's own multi-day all-day rows). Within-view
consistency is preserved on both surfaces.

## Testing

- Extend
  [DayClippedEventTest](../../app/src/test/kotlin/com/arishawke/asala/calendar/ui/timeline/DayClippedEventTest.kt):
  assert `segmentIndex` / `segmentCount` for same-day (1/1), the 2-piece
  crosser (1/2, 2/2), the 3-day event (1/3, 2/3, 3/3), and the
  ends-exactly-at-midnight boundary (1/1). Add `segmentAnchorMillis` cases.
- Extend
  [ScheduleRowExpansionTest](../../app/src/test/kotlin/com/arishawke/asala/calendar/ui/schedule/ScheduleRowExpansionTest.kt):
  the timed crosser now yields rows with `dayIndex`/`totalDays` = 1/2 and 2/2.
- Compose rendering is not unit-tested in this project (no Robolectric);
  device smoke covers it.

## Verification

- `./gradlew :app:spotlessKotlinCheck detekt :app:lintDebug :app:testDebugUnitTest`.
- Device smoke on a real 11:55pm -> 12:55am event across Week, Day, 3-Day,
  and Schedule: each shows the title with `1/2` / `2/2` (or `(Day 1/2)` in the
  agenda), piece 1 the start time, piece 2 the end time, no bare `00:00`.

## Out of scope

- All-day multi-day events (already handled by `WeekBucketer`).
- A "tiny spillover" threshold. Match Google: any crossing draws piece 2; the
  grid's existing 15-minute minimum height keeps a sliver tappable.
