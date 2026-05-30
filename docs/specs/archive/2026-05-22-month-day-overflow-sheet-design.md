# Design: Month view "+N more" day-overflow sheet

Status: Draft (not yet implemented).
Target release: v0.5.0.

## Context

Since v0.3.0, Month view has been a glance-and-navigate surface. A cell
can show at most three event chips. Any overflow is summarised as
`+N more` plain text, with no way to reach the hidden events from the
Month view itself. The previous design routed every cell tap to Day
view (commit 378ba6d), on the reasoning that Day view is the place to
see every event for a given date.

That works for sparse days. It fails for dense days. A user with eight
events on Friday cannot see events 4 through 8 from Month view at all
without first opening Day view, and even there has to scroll past the
ones already visible as chips. The `+N more` label is the visible
symptom of an interaction that does not exist.

This spec adds the missing interaction. It is intentionally surgical:
no layout reshuffle, no competing detail surface, no settings toggle.
The cell stays as it is; the `+N more` label becomes the tappable
affordance that opens a bottom sheet listing every event for that
date.

## Goal

Let the user reach every event on a dense day without leaving Month
view, while preserving the existing cell-tap-jumps-to-Day behaviour
for navigation.

## Explicit non-goals

- A hybrid month-plus-list layout (the grid-plus-day-list pattern, "B"
  in the brainstorm). Reserved for a possible v0.6+ Settings toggle.
- Density tiers (a Compact / Stacked / Details pinch zoom, "C"
  in the brainstorm).
- Making the visible chips inside a cell directly clickable. Chips
  remain visual; the user reaches event detail via Day view (sparse
  cells) or the overflow sheet (dense cells).
- Drag-to-reschedule from inside the sheet. Sheet is read-and-navigate
  only.
- Any change to Week, Day, or Schedule views.
- Any change to the Calendar Provider read or write path.

## Prior art consulted

- Tap-cell-jumps-to-day, same as our current behaviour. No per-day
  overflow surface inside Month.
- A two-layered approach: a density toggle (Compact / Stacked /
  Details) for the grid, plus a "list button" that turns Month view
  into a hybrid: grid on top, scrollable day list below. We adopt
  neither; both are out of scope here.
- The hybrid pattern as a default. Out of scope.
- Peer overview-only Month views: no per-day overflow affordance,
  Month kept as a pure overview.

Pattern picked: tap-to-expand bottom sheet on the overflow label only.
Not represented in the surveyed apps; chosen because it is the
smallest change that fixes the surfaced complaint and does not
re-architect Month view.

Material 3 caveat consulted: NN/g warns against stacked bottom sheets.
This design avoids stacking by awaiting the list sheet's `hide()`
before opening the existing event-detail sheet. Sequential, never
nested.

## Interaction rules

- Tap a date number, an empty area inside the cell, or a visible chip:
  jump to Day view at that date. Current behaviour, unchanged.
- Tap the `+N more` affordance: open a Material 3 `ModalBottomSheet`
  listing every event on that date.
- Tap an event row in the sheet: dismiss the sheet (await `hide()`),
  then open the existing `EventDetailSheet` for that event.
- Swipe down or system back on the sheet: dismiss, return to Month
  grid. Same gesture set as the existing `EventDetailSheet`.

Chips inside the cell stay non-clickable. Reasoning: the user's
earlier decision to remove per-chip click stands; the touch target is
small (~14dp) and a separate Item 1 review point. This spec does not
revisit chip clickability.

## Bottom sheet content

Material 3 `ModalBottomSheet` with `wrap-content` height (per M3
guidance), `rememberModalBottomSheetState(skipPartiallyExpanded = true)`
so the sheet opens to its full natural size and is not draggable to a
half-state.

- Header: long-format date (`EEEE, MMMM d, yyyy`, e.g. "Friday,
  May 22, 2026"), `titleMedium`, padding 16dp horizontal / 12dp
  vertical. Year is included because the sheet can be opened from any
  month / year the pager has scrolled to.
- Body: vertical `Column` (not a `LazyColumn`; a single day rarely has
  more than ~20 events).
  - All-day events first, then timed events sorted ascending by
    `startMillis`. Implementer should verify whether
    `MonthViewModel.eventsByDate` already groups all-day first; if so,
    keep the upstream order. If not, partition in `DayOverflowSheet`
    before rendering.
- Each row: a Material 3 `ListItem` with
  - a 4dp colored leading bar (`event.displayColor`) drawn as a
    rounded rectangle, fillMaxHeight,
  - headline text: `event.title.ifBlank { stringResource(event_no_title) }`,
  - supporting text: localised time range, or `stringResource(schedule_all_day)`
    for all-day events.
  - Full-row tap target (>= 48dp), Material ripple, semantics so
    TalkBack announces "title, time, button".
- Empty state: defensive only. Should not occur in normal use (the
  sheet only opens when overflow > 0). If the upstream state changed
  between tap and open (sync deleted events), render a single
  `Text(stringResource(sheet_no_events))` in `onSurfaceVariant`.

## Visual change in the cell

The `+N more` element gains both a colour change and an icon to
satisfy Material 3's "make a link look like a link" guidance (color
alone is borderline).

- Text colour: `MaterialTheme.colorScheme.primary`.
- Trailing icon: `Icons.Filled.ExpandMore`, 14dp, same primary colour,
  4dp leading spacing.
- Both wrapped in a single `Row` with `Modifier.clickable(onClick = ...)`
  so the tap target covers text and icon.
- `contentDescription` on the Row: `stringResource(R.string.cd_show_overflow,
  totalEventCount, headerDateString)`, e.g. "Show all 8 events for
  Friday, May 22, 2026". `totalEventCount` is `events.size`, not
  `overflowCount`, so the announcement matches what the sheet will
  display. `headerDateString` is the same formatted date the sheet
  header uses.
- No background pill, no border, no extra height. Cell layout
  unchanged.

Before / after:

```
Cell, dense day (today):
  22
  | Standup
  | 1:1 Sam
  | Design rvw
  +5 more         <- before: muted gray, not tappable

  22
  | Standup
  | 1:1 Sam
  | Design rvw
  +5 more v       <- after: primary color + chevron, tappable button
```

## Implementation outline

New file:

```
ui/month/DayOverflowSheet.kt     ; ModalBottomSheet composable
```

Modified files:

```
ui/month/DayCell.kt              ; +onOverflowClick parameter; +N more
                                 ; becomes a Row with text + icon,
                                 ; clickable when onOverflowClick != null
ui/month/MonthScreen.kt          ; +overflowDate state; passes
                                 ; onOverflowClick down; renders
                                 ; DayOverflowSheet when set
res/values/strings.xml           ; +cd_show_overflow, +sheet_date_format
                                 ; (date format goes via DateTimeFormatter,
                                 ; not strings; only the cd string is new)
```

State ownership: `overflowDate: LocalDate?` lives in `MonthScreen` as
a local `remember { mutableStateOf<LocalDate?>(null) }`. Non-null shows
the sheet. The sheet's events come from
`state.eventsByDate[overflowDate].orEmpty()` (no new ViewModel work,
no new query, no new data path).

Sequential sheet handoff: when an event row is tapped, the screen
launches a coroutine that calls `listSheetState.hide()` (returns when
the slide-down animation finishes), then sets `overflowDate = null` and
calls `AppViewModel.openEventDetail(eventId, instanceMillis)`. The
existing event-detail flow handles the rest. At most one bottom sheet
is visible at any moment.

`DayCell.onOverflowClick` is plumbed only when MonthGrid passes a
non-null lambda. The preview composables in `DayCell.kt` continue to
pass null (no behaviour to preview).

## Strings

Three new entries in `res/values/strings.xml`:

```xml
<!-- Month view overflow sheet -->
<string name="cd_show_overflow">Show all %1$d events for %2$s</string>
<string name="sheet_no_events">No events</string>
```

The header date format reuses
`DateTimeFormatter.ofPattern("EEEE, MMMM d")` for the visible header
(localised via system locale) and the same pattern feeding the
contentDescription, so screen reader and visible header stay in sync.

## Accessibility

- The overflow Row gets a single `contentDescription` describing it as
  a button with the event count and date.
- The cell's existing `stateDescription` (today / past, added in the
  earlier P1 bundle) is preserved.
- Sheet rows use the default Material 3 `ListItem` semantics, which
  already announce as button when given an `onClick`. Time and title
  are read together via `Modifier.semantics(mergeDescendants = true)`.
- The sheet itself receives focus on open (Material 3 ModalBottomSheet
  default behaviour). Closing returns focus to the `+N more` Row.

## Testing

- `EventDraft`, `RecurrenceRule`, etc. tests remain untouched.
- New unit tests are not strictly required for this change; the
  behaviour is composition + state, not data math. Manual verification
  per the section below is sufficient for v0.5.0.
- If, later, the sort order is moved into a pure function in
  `MonthViewModel`, that function would warrant a unit test.

## Verification

For the implementer to confirm the spec landed:

1. `./gradlew :app:lintDebug :app:testDebugUnitTest` green.
2. Install debug build, fresh launch (CLAUDE.md test-fresh-install
   rule applies; uninstall first if calendar permission has already
   been granted on this device).
3. Pick a date with > 3 events (or create three more on today's date
   to seed). Confirm in Month view that:
   - The cell shows three chips and `+N more` in primary colour with
     a small chevron.
   - Tapping the cell anywhere other than the `+N more` Row jumps to
     Day view at that date.
   - Tapping `+N more` opens a bottom sheet titled with the long-form
     date.
   - The sheet lists every event for that day, all-day first, then
     timed events in start-time order.
   - Tapping an event row dismisses the sheet, then opens the event
     detail sheet for that event.
   - Closing the event detail sheet returns to Month grid (not to the
     overflow sheet).
   - Swipe down or system back on the overflow sheet closes it and
     returns to Month grid.
4. Enable TalkBack. Walk through the overflow Row; confirm the
   announcement reads "Show all 8 events for Friday, May 22, button".
5. Switch to dark mode. Confirm the primary-coloured `+N more` and
   chevron retain visible contrast against the cell background.

## Open questions

None at design time. The author and reviewer accept the sequential
sheet pattern (list sheet then event detail sheet) as not-stacked
within the meaning of the NN/g warning.

## Out of scope, captured for future specs

- v0.6+: a Settings toggle "Hybrid Month view" that, when on,
  replaces this overflow-sheet behaviour with the hybrid layout
  (grid on top, day list below). Out of scope here.
- Future: investigate Week view's all-day chip touch target as a
  separate spec. See review item 1 in an internal plan for context.
