# Calendar selector redesign (account + chip row)

Date: 2026-05-30
Status: approved (design), pending user spec review

## Context

The event editor's "which calendar does this event save to" control is
`CalendarDropdown`, a private composable in
[EventForm.kt](../../app/src/main/kotlin/com/arishawke/asala/calendar/ui/eventedit/EventForm.kt).
It is a stock Material 3 `ExposedDropdownMenu` showing each calendar's
`displayName` over its `accountName`, with no color dot and no indicator on the
selected calendar. The user wants it to match Google Calendar, which they use
and prefer.

The selectable calendar list is already built and filtered upstream by the
tested contract `EventEditCalendarPicker.filter(...)`
([EventEditCalendarPicker.kt](../../app/src/main/kotlin/com/arishawke/asala/calendar/data/EventEditCalendarPicker.kt)):
writable, visible, storage-mode-appropriate, non-hidden. `CalendarItem` already
carries `accountName` and `displayColor`, so this redesign is **presentation
only**: no data-layer, repository, provider, or save-path changes, and the
`onSelect(id: Long)` contract is unchanged.

## Goal

Replace the dropdown with Google Calendar's 2025 pattern: an **account
selector** plus a **horizontal, side-scrolling row of calendar chips** for the
selected account. Each chip carries the calendar's color dot and name; the
chosen calendar's chip is visibly selected. Selecting a calendar must always
leave the event pointed at a valid, writable calendar.

Non-goals: the date picker, time picker, color picker, recurrence, and reminder
controls are untouched. No change to which calendars are selectable.

## Approach

### 1. Pure grouping seam (tested)

[EventEditCalendarPicker.kt](../../app/src/main/kotlin/com/arishawke/asala/calendar/data/EventEditCalendarPicker.kt)

Add to the existing picker-contract object (keeps the testable logic in one
place, reuses `EventEditCalendarPickerTest`):

- `data class CalendarAccountGroup(val accountName: String, val calendars: List<CalendarItem>)`
- `fun groupByAccount(calendars: List<CalendarItem>): List<CalendarAccountGroup>`
  - Groups by `accountName`, preserving first-seen account order and the input
    order of calendars within each account (the repo already orders the list;
    grouping must not reshuffle it).
  - Empty input -> empty list.

The "do we show the account selector?" rule is simply `groups.size > 1`.

### 2. New `CalendarSelector` composable

New file `ui/eventedit/CalendarSelector.kt` (extracting the selector out of
EventForm.kt also keeps that file near the ~200-line split convention).

Signature mirrors the current call site so EventForm changes by one line:

```
@Composable
fun CalendarSelector(
    calendars: List<CalendarItem>,
    selectedId: Long?,
    onSelect: (Long) -> Unit,
    modifier: Modifier = Modifier,
)
```

Behavior:

- Compute `groups = EventEditCalendarPicker.groupByAccount(calendars)`.
- Track `shownAccount` UI state, initialized to the group containing
  `selectedId` (falling back to the first group). `remember` keyed on
  `selectedId` so an externally-changed selection re-syncs the shown account.
- **Single account (`groups.size <= 1`):** no account selector. Render the
  "Calendar" section label and the chip row only.
- **Multiple accounts:** render an `ExposedDropdownMenuBox` "Account" selector
  (reusing the existing dropdown idiom from the old `CalendarDropdown`) above
  the chip row. Choosing an account sets `shownAccount` and calls
  `onSelect(group.calendars.first().id)` so the event immediately points at a
  valid calendar in the chosen account.
- **Chip row:** a `LazyRow` of `FilterChip`s, one per calendar in
  `shownAccount`:
  - `selected = (cal.id == selectedId)`.
  - `leadingIcon` = a color dot: `Box(Modifier.size(...).clip(CircleShape).background(Color(cal.displayColor)))`,
    matching the existing ColorRow dot pattern.
  - `label` = `cal.displayName`.
  - `onClick = { onSelect(cal.id) }`.
  - `FilterChip`'s built-in selected container color carries the selection
    visually; selection state is exposed to TalkBack by `FilterChip` itself.

### 3. Wire into the form

[EventForm.kt](../../app/src/main/kotlin/com/arishawke/asala/calendar/ui/eventedit/EventForm.kt)

- Replace the `CalendarDropdown(...)` call (lines ~109-113) with
  `CalendarSelector(...)`, same arguments.
- Delete the now-unused private `CalendarDropdown` composable.

### 4. Strings

`res/values/strings.xml`: add `field_account` ("Account") for the account
selector label. Reuse the existing `field_calendar` ("Calendar") for the
section label. No hardcoded UI text.

## Accessibility

- Color is never the sole signal: every chip pairs its dot with the calendar
  name (WCAG 1.4.1). The dot is decorative (no `contentDescription`).
- `FilterChip` provides a >=48dp touch target and selected-state semantics, so
  TalkBack announces the chosen calendar as selected.
- Known tradeoff of this surface: calendars past the right edge of the row are
  reachable only by scrolling/swiping, so a long list is less discoverable than
  a vertical list. Acceptable for the typical handful of calendars; noted so we
  revisit if users report many-calendar accounts.

## Testing

- Extend
  [EventEditCalendarPickerTest](../../app/src/test/kotlin/com/arishawke/asala/calendar/data/EventEditCalendarPickerTest.kt)
  for `groupByAccount`: empty -> empty; one account preserves order; two
  accounts produce two groups in first-seen order with intra-account order
  preserved; a calendar list already sorted stays sorted (no reshuffle).
- Compose UI is not unit-tested in this project (no Robolectric); the chip row,
  account switching, and selection visuals are covered by device smoke.

## Verification

- Full gate: `./gradlew :app:spotlessKotlinCheck detekt :app:lintDebug :app:testDebugUnitTest`.
- Device smoke (read-only on the real synced calendars, no event inserted):
  open "New event" and confirm:
  - The calendar control shows a color dot + name per calendar as chips.
  - The currently-selected calendar's chip reads as selected.
  - Tapping a chip changes the selection.
  - With more than one account, the account selector appears and switching it
    swaps the chip row and selects that account's first calendar; with a single
    account the account selector is absent.

## Out of scope

- The other event-editor pickers (date, time, color, recurrence, reminder).
- Changing which calendars are offered (still `EventEditCalendarPicker.filter`).
- The collapsed-field treatment from earlier options 1/2: this surface shows the
  chips inline, so there is no separate collapsed field to restyle.
