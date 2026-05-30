# 0001 — Data layer is CalendarContract, no local cache

## Status

Accepted, 2026-05-20.

## Context

Asala needs to display and (in M3) edit events from every calendar source the
device knows about: synced accounts (CalDAV via DAVx5 and other Android account providers) and local accounts.
Two ways to structure the data layer:

1. **CalendarContract directly.** Use `android.provider.CalendarContract` for
   every read and write. No app-owned database.
2. **Room cache.** Sync events into a local Room database, read from Room,
   write back to CalendarContract.

Option 1 is the established pattern for native Android calendar apps.
The Calendar Provider already handles recurring-event expansion
(`CalendarContract.Instances`), account visibility, and per-account
sync.

## Decision

Read and write directly through `CalendarContract`. Asala owns no events
table of its own. ContentObserver listens for provider changes and triggers
re-queries.

## Consequences

- Zero sync code to maintain. Account changes in system Settings are
  reflected immediately.
- No data divergence between Asala and the rest of the device's calendar
  ecosystem.
- Recurring-event expansion is handled by the provider via
  `CalendarContract.Instances`, not by us.
- Reads are IPC-gated through the provider. Acceptable for month / week / day
  scope; if a future view crosses many years it may warrant memoization.
- We are locked into Android's data model. A future cross-platform port would
  need to abstract the data layer.
- DST and timezone correctness lives in our query-window math
  ([DayRangeMath.kt](../../app/src/main/kotlin/com/arishawke/asala/calendar/data/DayRangeMath.kt)),
  not in a sync layer. The JVM unit test in `app/src/test/.../DayRangeMathTest.kt`
  is the regression guard.
- We cannot offer "offline edit, sync later." Without a network the provider
  still serves cached entries, but new writes go through the system sync
  adapter's normal queue.
