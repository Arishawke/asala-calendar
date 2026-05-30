# Spec: now-line indicator + hide synced accounts from drawer

Date: 2026-05-26.
Status: shipped to working tree.

## Problem

Two small UX gaps + one layout bug, bundled because they touch settled
UI surfaces:

1. **No current-time indicator** in Day or Schedule views. Week view
   had a `NowLineOverlay` that ticked every minute, but rendered red
   across the full 7-day Row instead of scoping the line to today's
   column.
2. **Synced accounts cannot be removed from the drawer.** Users can
   uncheck individual calendars to hide their events, but the rows
   still occupy space in the drawer. Users with multiple accounts
   want to drop an entire account from view, not toggle every calendar
   under it one by one.
3. **Week view's Sunday column clipped under its header.** The day-name
   header strip split the full screen width seven ways; the timeline
   columns below split only the post-hour-axis width seven ways. Every
   header sat slightly right of its column and Sunday lost the
   trailing edge.

## Goals

- A time marker (dot + line) in monochrome that
  adapts to theme (light surface -> near-black, dark surface ->
  near-white). Day view: full width. Schedule view: full width, sits
  between past and future timed events in today's section. Week view:
  scoped to today's column only.
- Per-account "Hide from drawer" action in a new 3-dot menu on each
  account header. Hides the entire `AccountGroup` and suppresses every
  one of its calendars' events. Restore section in Settings.
- Week-view day headers align with timeline columns.

## Non-goals

- Per-calendar "Hide from drawer" (initially considered, dropped after
  testing in favor of account-level only).
- Avatar long-press recolor (rolled into the new 3-dot menu).
- Synthetic "today" sections in Schedule view when today has no events.
- Touching the existing `CalendarContract.VISIBLE=false` "Hidden in
  system settings" drawer section (orthogonal, provider-controlled).

## Approach

### Now-line

`ui/timeline/NowLineMarker.kt` exposes:

- `NowLineRow(modifier)` — 8.dp circle + 2.dp `HorizontalDivider`,
  both `CalendarTokens.nowLine`.
- `rememberNowMinutes(zone, enabled)` — `produceState` block with a
  60s tick. Returns `hour * 60 + minute` or `null`.

Then:

- `CalendarTokens.nowLine` -> `colorScheme.onSurface` (was
  `colorScheme.error`).
- Week's `TimelineGrid` deletes its private overlay; passes
  `nowMinutes` into today's `DayColumn` only. Today's column renders
  `NowLineRow` inside its `BoxWithConstraints` at the calculated
  y-offset, so the line auto-scopes to that column's width.
- Day's `Timeline` follows the same pattern via the reused `DayColumn`.
- Schedule's `DaySection` partitions today's rows by
  `displayEndMillis > nowMillis`, **ignoring all-day rows** (which
  span the whole day and would always look "future"). Threads
  `nowMillis: Long?` through as a parameter so DaySection's remember
  scope keys on the tick and the split index advances each minute.

### Drawer hide (per-account)

- `UserPrefs.drawerHiddenAccountKeys: Set<String>`, keyed
  `"<accountType>:<accountName>"` (matches `accountOverrideKey`).
- `AppViewModel` exposes `drawerHiddenAccountKeysFlow: StateFlow<Set<String>>`
  plus a resolved `drawerHiddenAccountsFlow: StateFlow<List<DrawerHiddenAccount>>`
  for Settings to render with display metadata. Actions:
  `hideAccountFromDrawer(accountKey)` and
  `restoreAccountToDrawer(accountKey)`. The hidden set is merged into
  `hiddenCalendarIdsFlow` so all of a hidden account's calendars stay
  filtered from event views.
- `AccountHeader` gains a trailing 3-dot menu with "Change account
  color" (rolling the old avatar long-press in) and "Hide from drawer".
  The avatar's long-press handler is removed.
- `CalendarDrawer` filters its calendar list by
  `accountOverrideKey(...) !in drawerHiddenAccountKeys` before
  grouping into account sections.
- `SettingsScreen` renders a "Hidden accounts" section (suppressed
  when empty) listing each account by name with a "Show in drawer"
  trailing action.

### Week day-header alignment

Adds a `Spacer(width = HourAxisWidth)` at the start of the day-name
header Row in `WeekScreen.WeekPage`. Headers now split the
post-hour-axis width, matching the timeline columns below.

## Decisions

- **Color**: monochrome `onSurface` across all three views.
- **Account-level, not calendar-level**: a single account-hide is one
  tap regardless of how many calendars; per-calendar hide added churn
  without a clear win.
- **Consolidate avatar long-press into the 3-dot menu**: one
  affordance, two actions, no invisible gestures.
- **Schedule edge case**: skip the marker when today has no events
  rather than synthesize a section.
- **One bundled PR**: both features + the week-header fix; small diff.

## Risk

Low. Schedule partition reads one clock (passed in from the tick), so
the split position advances each minute. DataStore migration: new key
`drawer_hidden_account_keys` is read-defaulting to empty.

## Verification

Plan file: [2026-05-26-now-line-and-drawer-hide.md](../plans/2026-05-26-now-line-and-drawer-hide.md).
