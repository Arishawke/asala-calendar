# M3 design: event CRUD + recurrence

Status: Shipped in v0.4.0 on 2026-05-21. Archived; do not edit.
Refer to `CHANGELOG.md` for what actually landed, and to
`notes/v0.4-review-findings.md` for the post-release review.

## Context

v0.3.0 shipped as a polish release on top of v0.2.0. The app is
currently read-only: it renders events from the Android Calendar
Provider across four views (Month / Week / Day / Schedule) and
exposes nothing writable.

M3 adds the write path: tap to view, FAB to create, edit, delete,
basic recurrence. Reminders are written but not yet fired;
notification firing is M4.

## Goal

Make the app useful as a daily-driver calendar
on read **and** write, minus drag-to-reschedule, notifications,
trash bin, and per-event color. Those land in M4 or later.

## Explicit non-goals for M3

- Notification firing, AlarmManager scheduling, snooze actions
- Trash bin / 30-day undo
- Drag-to-reschedule on Week or Day
- Per-event color override (events use their calendar's color)
- Multiple reminders per event
- Custom RRULE beyond the four basic frequencies
- Per-event time zone picker (uses device current TZ)
- Attendees, conferencing, attachments

These are explicitly deferred. Some land in M4, some later.

## Architecture

Three additions on top of the existing layout:

```
data/
  EventRepository.kt        ; gain insertEvent / updateEvent / deleteEvent
  RemindersRepository.kt    ; NEW; wraps CalendarContract.Reminders
  RecurrenceRule.kt         ; NEW; small RRULE builder
ui/
  eventdetail/              ; NEW; bottom sheet, read path
  eventedit/                ; NEW; full-screen form, write path
  settings/                 ; NEW; full settings screen
prefs/                      ; existing DataStore module gains 3 keys
```

The four view ViewModels do not change. They already observe
`CalendarContract.Instances` via a `ContentObserver`; provider writes
trigger re-emit automatically, so the UI updates on save without
explicit signaling.

## Data layer

### EventRepository writes

Three new functions, each performing a single `ContentResolver`
operation on `CalendarContract.Events.CONTENT_URI`:

- `insertEvent(draft: EventDraft): Long` returns the new event ID.
- `updateEvent(eventId: Long, draft: EventDraft, scope: RecurringEditScope)`.
- `deleteEvent(eventId: Long, scope: RecurringEditScope)`.

`EventDraft` is the new write-side data class. It carries: title,
description, location, calendarId, dtstart, dtend, allDay,
eventTimezone, rrule (nullable), reminderMinutesBefore (nullable).

`RecurringEditScope` is an enum: `ThisInstance`, `ThisAndFollowing`,
`AllEvents`. Used only for recurring events; non-recurring events
ignore the scope.

### Recurring edit semantics

The Calendar Provider models recurring-event modifications as
"exceptions" on a parent event. The exception is itself a row in
`CalendarContract.Events` with `original_id` and
`originalInstanceTime` pointing at the parent.

- **ThisInstance**: insert a new exception event with the edited
  fields. The original instance is suppressed via the provider's
  built-in `original_id` link.
- **ThisAndFollowing**: truncate the parent's RRULE with
  `UNTIL=<instance-start - 1ms>`, then insert a new recurring event
  starting at the edited instance.
- **AllEvents**: update the parent event directly. All instances
  (past and future) reflect the change.

For deletes, the same scopes apply but with the delete path:
- **ThisInstance**: insert an exception with
  `eventStatus = STATUS_CANCELED`.
- **ThisAndFollowing**: truncate parent's RRULE.
- **AllEvents**: delete the parent event (provider cascades to
  instances and reminders).

### Time zone rule

- **Timed events**: `eventTimezone = TimeZone.getDefault().id`. The
  editor's date and time pickers run in the device's current zone.
  `dtstart` and `dtend` are absolute UTC millis derived from the
  picker's local-time selection in the current zone.
- **All-day events**: `eventTimezone = "UTC"`, `allDay = 1`,
  `dtstart` at 00:00 UTC of the chosen date, `dtend` at 00:00 UTC of
  the day after the last day (RFC 5545 exclusive end).

The editor does not expose a TZ picker in M3. If a user travels and
edits an existing event, the existing event's `eventTimezone` is
preserved; only fields the user changed are written.

### RecurrenceRule builder

Four-frequency builder with end-by-date or count. Produces RFC 5545
RRULE strings:
- Daily: `FREQ=DAILY[;INTERVAL=n][;UNTIL=...|COUNT=...]`
- Weekly: `FREQ=WEEKLY[;BYDAY=MO,WE,FR][;UNTIL=...|COUNT=...]`
- Monthly: `FREQ=MONTHLY[;BYMONTHDAY=15][;UNTIL=...|COUNT=...]`
- Yearly: `FREQ=YEARLY[;BYMONTH=3;BYMONTHDAY=15][;UNTIL=...|COUNT=...]`

INTERVAL is 1 unless the user changes it. Custom BYDAY/BYMONTHDAY
patterns beyond defaults are not in M3; the form exposes only the
four frequencies and the end-condition.

### RemindersRepository

Writes to `CalendarContract.Reminders`. M3 supports one reminder per
event. API:
- `setReminder(eventId: Long, minutesBefore: Int?)`: deletes all
  existing reminders for the event, then inserts one if non-null.
- Notifications subsystem is M4; reminders rows exist but nothing
  fires.

## UI layer

### Event detail bottom sheet (`ui/eventdetail/`)

Modal bottom sheet shown when the user taps any event chip / block
in any view. Renders read-only:
- Title, calendar name and color swatch
- Start and end (formatted per locale, "All day" if applicable)
- Location and description if non-empty
- Recurrence summary if recurring ("Repeats weekly", etc.)
- Reminder summary if set ("15 minutes before")

Three actions in the sheet's bottom bar:
- **Edit** (opens `EventEditScreen` with the event prefilled)
- **Delete** (opens confirm dialog; if recurring, opens 3-option
  scope picker; otherwise simple confirm)
- **Close** (or scrim tap / back press)

`onClick` callback added to: `MonthScreen.DayCell.EventChips`,
`WeekScreen.EventBlock`, `DayScreen` (reuses the Week column),
`ScheduleScreen.EventRow`. All four receive `(eventId, instanceTime)`.

### Event edit screen (`ui/eventedit/`)

Full-screen Compose form (not a bottom sheet). Reached by:
- FAB on the four views (creates a new event)
- Edit button in the detail sheet (edits an existing event)

Fields:
- Title (TextField, required, empty allowed as "(No title)")
- Start date + time (DatePicker, TimePicker, side-by-side)
- End date + time
- All-day switch (toggles time pickers off, normalizes dates)
- Calendar selector (dropdown of visible writable calendars)
- Location (TextField, optional)
- Description (TextField, multiline, optional)
- Reminder (dropdown: None, At time, 5 min, 10 min, 15 min, 30 min,
  1 hour, 1 day before)
- Recurrence (compact dropdown: Does not repeat / Daily / Weekly /
  Monthly / Yearly; "Custom" deferred to later milestone)

Validation:
- End must be after start (inline error on the End field; Save
  disabled until valid)
- Title trims; if empty after trim, save as "(No title)"

Save flow:
- New event: `insertEvent(draft)`, then `setReminder(id, mins)`
- Edit non-recurring: `updateEvent(id, draft, AllEvents)` (scope
  ignored), then `setReminder(id, mins)`
- Edit recurring: open `RecurringEditScopeDialog`, then route to
  `updateEvent(id, draft, picked)`

### Recurring edit scope dialog

Material 3 AlertDialog. Three radio options, none preselected:
- "Only this event"
- "This and following events"
- "All events"

Buttons: "Cancel" (left) and "OK" (right; disabled until a radio is
picked). Shown on both edit and delete of recurring events.

### Settings screen (`ui/settings/`)

Full-screen Compose, reached from drawer footer gear icon. Replaces
the current theme-only dialog.

Sections:
- **Appearance**
  - Theme override (System / Light / Dark) — relocated from
    existing dialog
- **Week**
  - Week starts on (Sunday / Monday / Saturday / System default)
  - Show ISO 8601 week numbers (switch)
- **General**
  - Default view (Month / Week / Day / Schedule)

The drawer's "Settings" entry now navigates to the screen instead of
opening the dialog. Theme dialog and its dedicated composable
removed.

### Permissions

`WRITE_CALENDAR` is already declared in the manifest (added in
v0.2.0 prep per `b814b9b`). Permission gate currently requests both
READ and WRITE together; verify on a fresh-install path before
shipping slice 2.

## Preferences (DataStore)

Existing `themeMode` DataStore extended with three new keys, all
`enumPreferenceKey` patterns:
- `defaultView: CalendarView` (default: Month)
- `weekStartsOn: DayOfWeek?` (default: null = follow locale)
- `isoWeekNumbers: Boolean` (default: false)

`AppViewModel.Factory` reads all four synchronously on first
composition (same pattern as theme today) to avoid first-frame
flash.

## Slices, in order

Each slice is a single commit with a `[Unreleased]` CHANGELOG entry,
matching the v0.2.0 → v0.3.0 cadence. After all 7 land, cut v0.4.0
as a prerelease.

| # | Slice | Touches |
|---|-------|---------|
| 1 | `feat(perm): request WRITE_CALENDAR with READ` | `AndroidManifest.xml` (verify), `CalendarPermissionGate.kt` |
| 2 | `feat(event): tap-to-view detail bottom sheet` | new `ui/eventdetail/`, onClick wired in 4 views |
| 3 | `feat(event): create non-recurring events from FAB` | new `ui/eventedit/`, `EventRepository.insertEvent`, FAB in `MainActivity` |
| 4 | `feat(event): edit and delete non-recurring events` | `EventRepository.updateEvent`, `deleteEvent`; confirm dialog |
| 5 | `feat(event): recurrence end-to-end (create + scope dialog for edits and deletes)` | `RecurrenceRule.kt`, recurrence section in edit form, `RecurringEditScopeDialog`, exception/truncate logic in repo |
| 6 | `feat(event): single reminder offset in editor` | `RemindersRepository`, reminder dropdown in form |
| 7 | `feat(settings): full settings screen with view + week + theme` | new `ui/settings/`, DataStore key extensions, drawer wiring |

Slice 5 combines recurrence-create with the scope dialog so the
app never carries a state where recurring events exist but cannot
be edited safely.

## Testing

Unit-testable logic (`testDebugUnitTest`):
- `RecurrenceRule.build()` for the four frequencies and both end
  conditions
- `EventDraft.toContentValues()` for new and updated events
- DST-edge calculation of `originalInstanceTime` for recurring
  exceptions
- All-day `dtstart`/`dtend` normalization to 00:00 UTC

UI verified manually on the Pixel 10 Pro XL per the working
agreement: each slice gets a smoke test after install. Fresh-install
path (uninstall first) verified before slice 2 ships, per
`feedback-test-fresh-install`.

## Risks

1. **Recurring exception math**. The provider's exception model is
   subtle. `originalInstanceTime` must be the ORIGINAL planned
   start in the parent's TZ, not the edited start. Get this wrong
   and edits create orphan exceptions that show duplicated in
   month/week. Mitigation: dedicated unit tests in slice 5.
2. **DST around recurring events**. A "weekly Tuesday 9am" event
   that crosses a DST transition must still display at 9am local
   in both DST states. Provider handles this if `eventTimezone` is
   set correctly; failure mode is a 1-hour shift. Mitigation: test
   matrix in slice 5.
3. **Permission revoked mid-session**. User can revoke WRITE
   permission from Settings while the app is alive. Saves fail with
   `SecurityException`. Mitigation: catch in repo, surface as a
   snackbar, do not crash.
4. **Settings screen vs theme dialog migration**. Existing users
   currently access theme via the dialog. The drawer footer entry
   re-routes silently; verify no broken state during slice 8.

## What's queued after M3

- **M4**: notifications + AlarmManager + snooze + `POST_NOTIFICATIONS`
  permission (on-demand at first reminder-set).
- **M5 or later**: trash bin, drag-to-reschedule, per-event color,
  multiple reminders, search.
- See `docs/ROADMAP.md` for the longer-term list.
