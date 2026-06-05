# 0006. Single-occurrence recurrence edits use EXDATE, not provider exception rows

Date: 2026-06-05

## Status

Accepted. Refines ADR-0002 (CalendarProvider write conventions) for the
single-occurrence ("this event only") delete and edit paths.

## Context

"This event only" delete and "this event only" edit both made the entire
recurring series disappear from the calendar. The parent `Events` row survived
(rrule intact, `deleted=0`), but the provider's `Instances` expansion returned
nothing for the series.

Reproduced on-device (Pixel 10 Pro XL, Android 16) with an instrumented test
against the real CalendarProvider on a throwaway LOCAL calendar. We tried, in
order:

1. The original hand-rolled approach: insert a zero-length `STATUS_CANCELED`
   event into `Events.CONTENT_URI` with `ORIGINAL_ID`. Result: whole series
   vanishes.
2. The documented mechanism: insert via
   `Events.CONTENT_EXCEPTION_URI/<parentId>`. Result: whole series still
   vanishes. The inserted exception's `lastDate` is computed as the parent
   series' end, so the provider treats the exception as spanning and
   suppressing every remaining occurrence.

So on this provider, attaching *any* exception row to a recurring parent breaks
the parent's instance expansion. This is not something the app can fix in the
provider.

`adb shell content` gave inconsistent results for the same operations because
each `content` invocation is isolated and the provider keeps a single global
instances-expansion range that gets churned across out-of-order calls. The
trustworthy signal is an instrumented test running in a real app process.

## Decision

Model single-occurrence operations on the parent's recurrence rather than with
exception rows:

- **Delete one occurrence:** append the occurrence to the parent's `EXDATE`.
- **Edit one occurrence:** insert the edited occurrence as a standalone,
  non-recurring event, then append the original occurrence to the parent's
  `EXDATE`. The insert runs first so a failed `EXDATE` write rolls the one-off
  back (deletes it) and reports a clean failure, rather than leaving the
  original occurrence excluded with no replacement. This mirrors the
  insert-before-truncate ordering the "this and following" split uses.

Both update the parent with **DTSTART and RRULE re-sent in the same delta**. The
provider only rebuilds the `Instances` table when DTSTART is present, and only
recomputes the series end (`lastDate`) correctly when RRULE is present too; an
EXDATE-only update truncates the series at the excluded date (verified on-device:
it kept only the first occurrence).

EXDATE values follow the parent's value type, matching `untilUtcForTruncation`:
timed occurrences exclude by UTC datetime (`yyyyMMddTHHmmssZ`), all-day by date
(`yyyyMMdd`). See `RecurrenceExceptionMath.exdateValue` / `mergeExdate`.

## Consequences

- Single-occurrence delete and edit work: only the targeted occurrence changes,
  the rest of the series survives (instrumented test
  `RecurringSingleOccurrenceTest`).
- An edited occurrence becomes an independent one-off event, not a provider
  recurrence exception. Trade-offs:
  - A later "edit all" on the series will not retroactively include the detached
    occurrence.
  - On synced (DAVx5/Google) calendars the detached event does not round-trip as
    an `RECURRENCE-ID` exception; it syncs as its own event plus an EXDATE on the
    series. This is acceptable for an offline-first local-calendar app and avoids
    the data-loss-shaped series-vanish bug entirely.
  - Editing the same slot "this occurrence only" twice via the parent series
    would insert a second one-off; the EXDATE is deduped (`mergeExdate` skips a
    value already present), so it is not appended twice. This is largely
    unreachable from the UI: once excluded, the slot no longer renders as part of
    the series, so the user edits the detached one-off directly (a normal
    whole-event edit) rather than the series occurrence.
- `STATUS_CANCELED` exception rows are no longer written, so the read path does
  not need to filter them for our own deletions. (Foreign/synced cancellations
  can still arrive; filtering those is tracked separately.)
- Verification of recurrence/exception behavior must be done on-device against
  the real provider (instrumented tests), not via `adb content` or JVM/Robolectric
  unit tests. The pure helpers (EXDATE formatting, COUNT split math) remain
  JVM-unit-tested.
