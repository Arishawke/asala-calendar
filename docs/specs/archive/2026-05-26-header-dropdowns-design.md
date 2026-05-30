# Spec: header dropdowns for in-place date navigation

Date: 2026-05-26.
Status: design.

## Problem

Switching between months / weeks / dates today means swiping the pager
N times or jumping back to "today" first. A familiar, fast pattern
common in other calendar apps: tap the title and an inline panel slides
down with either month chips (in Month view) or a mini-month calendar
(in Week / Day / Schedule). The user wants the same affordance, with the
panel **pushing the rest of the UI down** rather than overlaying it.

## Goals

- Tap the title in the top app bar to expand an inline panel below it.
  Title text is followed by a chevron that rotates 180deg when
  expanded. Tapping again (or tapping a target inside the panel)
  collapses it.
- **Month view panel**: horizontal scroller of `MMM yy` chips
  (e.g. "Jan '26"), centered on the currently viewed month. Tap a
  chip -> pager jumps to that month and the panel collapses.
- **Week / Day / Schedule panel**: mini month calendar with
  `< Month Year >` navigation, weekday header, and a 7x6 grid of
  date cells. Each cell shows the date number plus up to three small
  colored dots (one per distinct calendar that has events that day).
  Tap a cell -> the current view jumps to that date and the panel
  collapses.
- Panel pushes the rest of the UI down (not an overlay). Layout uses
  `AnimatedVisibility` inside the Scaffold's `topBar` slot so the
  innerPadding under the bar grows with the panel.
- Back gesture closes the panel before any other handler.

## Non-goals

- Date range picker style multi-select.
- Pinch-to-zoom or other gestures inside the mini-month.
- A separate "year view" panel for jumping years at a time. Month-chip
  scroller covers 12 each side of the current month; year jumps would
  be a follow-up.
- Per-event preview tooltip on dot hover.

## Approach

### Trigger

`AppShell.kt` owns local `headerExpanded: Boolean` state. The
TopAppBar's `title` slot becomes a clickable Row of title text +
chevron icon (rotation via `animateFloatAsState`). The chevron only
appears when a panel is available for the current view (always for
Month / Week / Day / Schedule; suppressed for Tasks since there is
nothing to navigate yet).

### Push-down layout

The Scaffold's `topBar` slot wraps the TopAppBar in a Column:

```
topBar = {
    Column {
        TopAppBar(title = clickable + chevron)
        AnimatedVisibility(visible = headerExpanded) {
            HeaderDropdownPanel(currentView, ...)
        }
    }
}
```

Material 3 Scaffold uses `SubcomposeLayout` for `topBar`, so when the
Column's measured height changes the innerPadding under the bar grows
with it and the main content area shifts down naturally.

### MonthChipsPanel

`MonthChipsPanel(currentMonth, today, onSelectMonth)` renders a
`LazyRow` of `FilterChip`s across a 25-month window
(12 months before the viewed month, the viewed month, 12 after).
Format `MMM yy` via `DateTimeFormatter.ofPattern("MMM yy")` so the
year is two-digit and chips stay compact. The viewed month is
selected (filled tone); today's month gets a subtle accent border if
distinct. Initial scroll positions the viewed-month chip centered.

### MiniMonthPanel

`MiniMonthPanel(displayedMonth, today, firstDayOfWeek, eventsByDate,
onSelectDate, onChangeMonth)` lays out:

1. A 3-column Row at the top: `<` icon, `Month Year` text (clickable
   to reset to the parent view's current month), `>` icon.
2. 7-column weekday header.
3. 6 rows of 7 cells (date number + 0-3 colored dots).

Cell visual rules:

- In-month days render with `onSurface` text; out-of-month days use
  `onSurfaceVariant`.
- Today gets a primary-filled circle behind the number.
- Tapping any cell calls `onSelectDate(date)`. The host calls
  `vm.requestJumpTo(date, currentView)` and closes the panel.

Dot rules:

- Group `eventsByDate[date]` by `calendarId`, keep the first three
  groups in insertion order, render a small `Box(size=4.dp,
  CircleShape)` per group colored by that calendar's
  `displayColor`.
- All-day events count too. The mini-month is just a density map; the
  view itself is where the user sees details.

### MiniMonthViewModel

New `MiniMonthViewModel` scoped to AppShell. Owns:

- `displayedMonth: MutableStateFlow<YearMonth>` (defaults to today's
  month).
- `uiState: StateFlow<MiniMonthUiState>` deriving
  `eventsByDate: Map<LocalDate, List<EventItem>>` for the displayed
  month by combining `eventRepo.observeEvents(...)` with the existing
  hidden + color override flows.

Resets `displayedMonth` to today's month each time the panel opens
(simplest contract; user can navigate from there).

### Navigation plumbing

`AppViewModel.requestJumpTo(date, view)` already exists and is used
by Month -> Day. It sets `_pendingDateJump` and switches view. The
existing consumers cover Day view. New consumers needed:

- **Week**: add `pendingDateJump` collection in `WeekScreen.kt` that
  scrolls the pager to the week containing the date.
- **Schedule**: add `pendingDateJump` collection in
  `ScheduleScreen.kt` that scrolls `listState` to the index of that
  date (or the next available day if the exact date is empty).
- **Month**: add `pendingDateJump` collection in `MonthScreen.kt`
  that scrolls the pager to the date's `YearMonth`.

Each consumer drains `pendingDateJump` via
`vm.consumePendingDateJump()` after the scroll.

### Back gesture

`AppShell` adds `BackHandler(enabled = headerExpanded) { headerExpanded = false }`
ahead of the existing `previousView` and `drawerState` handlers so
it takes priority.

## Decisions

- **Push-down panel inside Scaffold's topBar slot**: simplest layout
  that lets Scaffold's existing innerPadding flow handle the shift.
- **Default panel month = today's month**: clean reset on each open;
  matches the common convention in other calendar apps.
- **25-month chip window**: enough to scan a year either direction
  without crowding the LazyRow. Outside that range, user can switch
  view to Month and swipe.
- **Up to 3 dots per day**: covers most calendar densities; matches
  the user's pick over a single-dot or count-badge alternative.
- **One bundled PR**: trigger + both panels + per-view jump plumbing.
  Mini-month and chips share enough wiring that splitting would just
  add churn.

## Risk

- Scaffold + dynamic topBar height: the Compose Material 3 Scaffold
  remeasures `topBar` via SubcomposeLayout, but verify on device that
  the animation looks smooth (no janky jump at the start/end of the
  expand). Fallback: hoist the panel out of `topBar` into the parent
  Column and apply `Modifier.padding(top = ...)` to the content
  manually.
- Loading events for an arbitrary mini-month: the existing
  `EventRepository.observeEvents` is already month-aware. Adding a
  third ContentObserver subscription is cheap (it deduplicates with
  the existing ones via CalendarProvider's URI subscription).

## Verification

Plan file:
[2026-05-26-header-dropdowns.md](../plans/2026-05-26-header-dropdowns.md).
