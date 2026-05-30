# Spec: Week view crowded-overlap overflow

Date: 2026-05-29.
Status: approved design, not yet implemented.

## Problem

In Week view, overlapping timed events split into equal side-by-side
columns (`layOutOverlaps` assigns each a column index within a cluster;
`TimelineGrid.DayColumn` renders each at `columnWidth / clusterWidth`).
Week packs seven days across the screen, so a day column is roughly
50dp on a phone. Two overlapping events read fine; three shrink to
unreadable slivers, and more are worse. The user reported this directly.

## Goal

When a Week day column gets crowded, keep one event readable and move
the rest into the app's existing "+N more" overflow sheet, instead of
slicing the column into unreadable strips.

## Decisions

- **Trigger: cluster max-concurrency of 3 or more.** A cluster whose
  column count (`clusterWidth`) is 1 or 2 renders exactly as today
  (equal columns, which stay readable). At `clusterWidth >= 3` the
  cluster switches to the overflow rendering below.
- **Keep column 0, collapse the rest.** Events assigned to column 0
  render at full column width (column 0 is a non-overlapping time
  sequence, so a long event there stays fully visible). Events in
  columns 1+ are not drawn individually; they collapse into a single
  "+N" overflow chip for that cluster, where N is the count of
  collapsed events. A light decorative stacked-card shadow sits behind
  the column-0 card(s) to signal there is more underneath.
- **Tap the chip to open the overflow sheet.** Reuse Month view's
  `DayOverflowSheet` (`ui/month/DayOverflowSheet.kt`), passing the
  cluster's events. Each row shows color dot, title, and time and is
  tappable to open the event detail, exactly as in Month.
- **Week only.** Day view shares the same `DayColumn` renderer, but a
  single day fills the screen (~110dp+ per column), where three
  overlaps are perfectly readable. The overflow behavior is gated on so
  it applies to Week and NOT Day. Day view keeps equal columns.
- **No Settings toggle.** Since uncrowded days (0-2 overlaps) look
  unchanged, the common case is untouched and a toggle would mostly add
  Settings surface and a second layout path to test. Revisit only if
  requested.

## Non-goals

- No change to 1-2 overlap rendering, all-day events, or Day view.
- No Settings toggle.
- No new overlap algorithm: `layOutOverlaps` keeps its greedy
  column-assignment behavior and its existing test.
- No declined / not-accepted event styling (tracked separately on the
  roadmap under response indicators).

## Approach

### Layout

`layOutOverlaps` (`ui/timeline/OverlapLayout.kt`) gains a `clusterId`
(or `clusterIndex`) on `LaidOutEvent` so consumers can group a cluster's
events. This is additive: the existing `columnIndex` / `clusterWidth`
fields and `OverlapLayoutTest` assertions are unchanged.

A new pure function partitions a day's laid-out events for the crowded
case, e.g.:

```
data class CrowdedDayLayout(
    val visible: List<LaidOutEvent>,        // clusterWidth < 3, or column 0 of a crowded cluster
    val overflow: List<OverflowGroup>,      // one per crowded cluster
)
data class OverflowGroup(
    val clusterId: Int,
    val collapsedCount: Int,                // events in columns >= 1
    val events: List<EventItem>,            // full cluster, for the sheet
    val anchorStartMillis: Long,            // for vertical placement of the chip
)
```

This function is unit-tested in isolation (no Compose).

### Rendering (`TimelineGrid.DayColumn`)

`DayColumn` gains a flag, e.g. `crowdedOverflow: Boolean` (true from
Week, false from Day) plus an `onOverflow: (List<EventItem>) -> Unit`
callback (null/no-op for Day).

- `crowdedOverflow == false`: current behavior unchanged.
- `crowdedOverflow == true`: render `visible` events as today
  (column-0 events at full width for crowded clusters; 1-2 clusters
  unchanged), then render one "+N" chip per `OverflowGroup` anchored at
  the group's vertical position, calling `onOverflow(group.events)` on
  tap. A subtle shadow/elevation behind the crowded column-0 card gives
  the "deck" hint.

### Overflow sheet host (`WeekScreen` / `WeekPage`)

Hoist sheet state next to the pager:

```
var overflowEvents by remember { mutableStateOf<List<EventItem>?>(null) }
...
overflowEvents?.let { events ->
    DayOverflowSheet(
        date = <the visible day>,
        events = events,
        onDismiss = { overflowEvents = null },
        onEventClick = onEventClick,   // already routed to openEventDetail
    )
}
```

`WeekScreen` already has `zone` and the `onEventClick` route to the
detail sheet; `DayColumn` already receives `onEventClick`.

### Time format and locale (verified)

No work required. `DayOverflowSheet` formats each row's time via
`rememberTimeFormatter()` (`ui/theme/TimeFormatters.kt`), which reads
`LocalIs24Hour` (the tri-state Follow-system / 12h / 24h setting,
re-read on resume) and `LocalLocale`. This is the same formatter the
Week event chips use, so 12h/24h, the AM/PM marker, and locale all
carry through automatically and identically.

Minor plan decision: `DayOverflowSheet`'s header shows the day's date.
For an overlap group, an optional time-range subtitle ("1:00 - 3:30
PM") would read better. Either reuse the sheet as-is (date header) or
add an optional subtitle parameter. Defer to the plan.

### Drag, z-order, all-day

- Drag-to-reschedule is unchanged. It operates on the column-0 card
  via `RescheduleDragState` and is independent of the column layout.
  Collapsed events are rescheduled by opening them from the sheet.
- The deck shadow is decorative (drawn behind the top card), not real
  stacked composables, so it stays cheap.
- All-day events render on their own surface, untouched.

## Testing

- `OverlapLayoutTest` stays; the `clusterId` addition is additive.
- New unit tests for the partition function: a 1-event day, a 2-overlap
  cluster (stays visible, no overflow), a 3-overlap cluster (column 0
  visible + overflow count 2), a chain cluster with max-concurrency 2
  but many events (stays as columns, no overflow), and a day mixing a
  crowded cluster with separate uncrowded events.
- Device smoke test (per fresh-install practice): a day with 3+ truly
  concurrent events in Week shows the primary card + "+N"; tapping
  opens the sheet; times render in the active 12h/24h + locale setting;
  Day view of the same day still shows equal columns; drag still works.

## Risk

Low. The overlap algorithm is unchanged except for an additive field.
The new rendering path is gated to Week, so Day view is unaffected. The
overflow sheet is reused as-is. The main care points are correct
vertical anchoring of the "+N" chip and counting N per cluster, both
covered by unit tests on the partition function.

## File-size note

`TimelineGrid.kt` is ~270 lines today. The crowded-overflow rendering
and the partition helper should land in their own files (e.g.
`CrowdedOverflow.kt` for the pure partition, a small composable for the
chip) to stay within the ~200-line convention rather than growing
`TimelineGrid.kt` further.

## Verification

Plan file: to be created at
`docs/plans/2026-05-29-week-overlap-crowded-overflow.md`.
