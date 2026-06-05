# 0002 — Calendar Provider write conventions

## Status

Accepted, 2026-05-22. Partially superseded by
[ADR-0006](0006-single-occurrence-recurrence-edits-via-exdate.md): the
single-occurrence delete/edit conventions in this ADR (STATUS_CANCELED
cancellation rows, exception rows carrying the parent CALENDAR_ID, the
`parentCalendarId` write chain, all-day exception rows) are replaced by an
EXDATE-on-parent approach, because exception rows drop the whole series from
the provider's instance expansion on-device. The non-exception conventions
here (null-not-throw, DTSTART-re-send for instance rebuilds, DURATION/RRULE
round-trip quirks) still hold.

## Context

[ADR-0001](0001-data-layer-is-calendarcontract.md) committed Asala to writing
directly through `CalendarContract`. M3 (event CRUD, shipped in v0.4.0) was
the first time we exercised that decision. The provider is documented but
several real-world behaviours are not in the docs and only show up under
load against a multi-account device:

- AOSP `CalendarProvider2` silently rejects inserts (returns `null`) instead
  of throwing, including when a required column is missing or when the
  caller's `CALENDAR_ACCESS_LEVEL` is below `CAL_ACCESS_CONTRIBUTOR` (500).
- Some sync adapters rewrite Asala-written rows on the server round-trip.
  `DURATION` "P0DT1H0M0S" becomes "P3600S" (no T separator, not strict
  RFC 5545). RRULE gains `;WKST=MO`. Recurring rows end up with
  `DTEND = NULL` because they store `DURATION` instead.
- Exception rows (`ORIGINAL_ID + ORIGINAL_INSTANCE_TIME`) need explicit
  parent-derived fields. The provider does **not** infer `CALENDAR_ID` from
  the parent, despite a comment in the original M3 implementation
  asserting it did.
- `CALLER_IS_SYNCADAPTER` looks tempting for "make writes go through" but
  is the wrong flag for a user-facing app: it bypasses dirty-tracking and
  breaks the Google / DAVx5 sync queue.

The v0.4.0 verify cycle plus the v0.4.1 post-release review surfaced seven
bugs all rooted in these behaviours. This ADR captures the conventions
those fixes settled on, so future write paths don't relitigate them.

## Decision

The following are binding rules for any code that reads or writes events.

### 1. Pre-flight the calendar's writability at the editor boundary

Filter the editor's calendar dropdown to writable calendars before showing
it: `visible && accessLevel >= CAL_ACCESS_CONTRIBUTOR (500)`. The flag
lives on `CalendarItem.isWritable` and is sourced from
`CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL` in `CalendarRepository`.
Subscription, holiday, and foreign-attendee calendars never reach the
user-facing dropdown.

Follows the Calendar Provider's documented access-level convention
(`CAL_ACCESS_CONTRIBUTOR` = 500 is the minimum level a calendar
needs to be writable).

### 2. Every Events insert needs `DTSTART` plus `DTEND` or `DURATION`

The provider rejects inserts that omit both. The cancellation-exception
write path (delete one instance of a recurring event) is the easy one to
get wrong: a hand-built `ContentValues` with `ORIGINAL_ID`,
`ORIGINAL_INSTANCE_TIME`, `STATUS_CANCELED`, and `DTSTART` is not enough.

Convention: cancellation rows set `DTEND = DTSTART` as a zero-duration
marker. The row is hidden anyway, so the duration doesn't matter.

### 3. Exception rows carry the parent's `CALENDAR_ID` explicitly

The provider does not back-fill `CALENDAR_ID` from `ORIGINAL_ID`. Pass it
through: `MainActivity → AppViewModel.deleteEvent →
EventRepository.deleteEvent(..., parentCalendarId)`. Same rule for any
future exception-write code.

### 4. `ORIGINAL_INSTANCE_TIME` is `Instances.BEGIN` pass-through, not arithmetic

The Calendar Provider's recurrence expansion gives us the DST-correct UTC
millis for every occurrence via `CalendarContract.Instances.BEGIN`. When we
write an exception row, pass that value verbatim as
`ORIGINAL_INSTANCE_TIME`. Do not compute it as `parentDtstart + n × week`
arithmetic; that drifts across DST. The `RecurrenceExceptionMath` wrapper
that pretended to need `parentDtstart` was removed in
[commit `4639565`](../../../commit/4639565) for this reason.

(The only exception is all-day recurring exceptions, which the
established pattern shifts to UTC via a helper analogous to
`Formatter.getShiftedUtcTS`. We don't currently write all-day
exceptions; if we add that, follow the established UTC-shift pattern.)

### 5. Editing a recurring event from an instance uses the instance's time, not the parent's

When the editor opens from a tapped instance, prefill the form with the
instance's clock time (preserving the parent's duration), not the parent
series's DTSTART. Lets "Only this event" and "This and following" land on
the right occurrence; "All events" still updates the whole series at the
shown time, which matches the established recurring-edit-scope
conventions. Wired through `EventEditViewModel.Factory(eventId,
instanceMillis)`.

The detail-sheet display has the same shape and is on the v0.4.2 list
(`notes/v0.4-review-findings.md` N7).

### 6. The DURATION parser must tolerate the `P{n}S` form

`fetchEventDetail` reads `DURATION` whenever `DTEND` is NULL (recurring
events store one or the other). The parser accepts:

- Our writes: `P{d}DT{h}H{m}M{s}S`
- Other RFC 5545 shorter forms: `P1D`, `PT1H`, `P1W`
- Post-sync form some adapters use: `P3600S` (no T)

Live in `EventDraft.parseIso8601DurationMs`. Eight tests cover the shapes
including malformed input.

### 7. Do **not** use `CALLER_IS_SYNCADAPTER`

Setting that query parameter on `Events.CONTENT_URI` operations is for
sync adapters (DAVx5, corporate account adapters, etc.). A user-facing
app like Asala writing through the plain URI lets the provider mark rows
`DIRTY=1`; the platform sync adapter then pushes them upstream. Using
the flag from Asala bypasses dirty tracking and breaks any installed
sync adapter's upstream push. Confirmed by web-search and by the absence
of `caller_is_syncadapter` usage in established Android calendar apps.

### 8. Do **not** manually set `HAS_ALARM`

`CalendarProvider2` auto-updates `HAS_ALARM` when a `CalendarContract.Reminders`
row is inserted with a valid `EVENT_ID`. Behaviour is stable Android
12-16. Manually setting it is redundant; the established Android
calendar apps never write it either.

## Consequences

- New write paths (drag-to-reschedule in M4, multi-reminder support,
  CalDAV writes, etc.) start from a known-good baseline.
- The `fetchEventDetail` DURATION parser absorbs any future provider
  quirks of the same flavour without changing call sites.
- Cross-app interop is verified on hardware against multiple installed
  Android calendar apps; the M3 review notes document the verification.
- `CALLER_IS_SYNCADAPTER` is out of scope unless we ever build a
  proper sync adapter (CalDAV). At that point it'll need its own ADR.
- Anyone tempted to relax the writability filter (e.g., to show the
  user "you can't write here" inline) must keep the save-time
  permission check too — the dropdown filter is convenience, not the
  trust boundary.
