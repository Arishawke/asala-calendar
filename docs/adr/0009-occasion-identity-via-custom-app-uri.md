# 0009. Occasion identity via CUSTOM_APP_URI

Date: 2026-07-01

## Status

Accepted.

## Context

The contact-occasions feature (v0.23.0) generates one yearly all-day event per
contact birthday or anniversary into two app-provisioned local calendars. Sync
needs a stable per-contact identity on each generated row so reconcile can
insert, update, and delete without touching anything else in those calendars.

The provider's natural identity column, `_SYNC_ID`, is writable only through
sync-adapter URIs (`CALLER_IS_SYNCADAPTER`), which ADR-0002 rule 7 forbids: the
app writes as an ordinary client. Two further constraints shaped the design:
a contact date may carry no year (vCard `--MM-DD`), yet the recurring event
still needs a concrete `DTSTART`; and the render layer derives ages at display
time, so the row must carry the contact's display name somewhere machine-readable.

Users can also hand-add ordinary events into the provisioned calendars: the
app's own editor offers them as writable chips. Any identity scheme must leave
those rows alone on every path, including deletes.

## Decision

- Identity is `CUSTOM_APP_URI = asala://occasion/<contactId>/<Type>` (with
  `CUSTOM_APP_PACKAGE` set to the app package), standing in for `_SYNC_ID`.
  `parseOccasionUri` is the single parser; `isOwnedOccasionUri` is the single
  ownership predicate, shared by the sync reconcile and the read paths.
- Ownership is ROW-scoped, never calendar-scoped. A row without a parseable
  occasion URI is hand-added: reconcile skips it, teardown leaves it to the
  calendar delete, and the render layer keeps its own title and notes. The
  read paths (`EventRepository` Instances projection, `EventDetailReader`)
  carry an `isOwnedOccasion` flag from the same predicate; the Instances view
  exposes `CUSTOM_APP_URI` to ordinary queries (device-verified).
- A no-year date uses the sentinel year 1604 in `DTSTART`. 1604 is a leap year
  (so `--02-29` builds a valid date), far enough back that no real contact
  birth year collides, and the render layer suppresses the age when it sees
  the sentinel. A Feb-29 date with a concrete non-leap year normalizes to the
  sentinel as well instead of throwing.
- The event `DESCRIPTION` carries the contact display name. The age/ordinal
  title ("Alice turns 30") is derived at render time from the parent DTSTART
  year, so no yearly rename writes are needed. For owned rows the detail sheet
  suppresses the description as "Notes"; for hand-added rows it shows normally.

## Consequences

- No sync-adapter surface is needed; ADR-0002's write conventions hold.
- Another app holding WRITE_CALENDAR could forge or strip the URI; that only
  affects rows inside the app's own generated calendars and crosses no
  privilege boundary (audit-verified), so it is accepted.
- The DESCRIPTION field is not usable as free-form notes on generated rows;
  hand-added rows keep full Notes behavior.
- `_SYNC_ID` remains free if a real sync adapter ever arrives; migration would
  be a one-time rewrite of the URI-stamped rows.
