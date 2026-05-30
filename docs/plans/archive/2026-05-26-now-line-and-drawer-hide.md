# Plan: now-line indicator + hide synced accounts from drawer

Date: 2026-05-26.
Spec:
[2026-05-26-now-line-and-drawer-hide-design.md](../specs/2026-05-26-now-line-and-drawer-hide-design.md).

## Steps

- [x] `ui/theme/Color.kt`: `CalendarTokens.nowLine` returns
  `MaterialTheme.colorScheme.onSurface`.
- [x] New `ui/timeline/NowLineMarker.kt`: `NowLineRow(modifier)` and
  `rememberNowMinutes(zone, enabled)` via `produceState`.
- [x] `ui/week/TimelineGrid.kt`: drop private `NowLineOverlay` + inline
  tick; pass `nowMinutes` into `DayColumn`; render `NowLineRow` inside
  today's `DayColumn` at the calculated y-offset.
- [x] `ui/week/WeekScreen.kt`: add `Spacer(width = HourAxisWidth)` at
  the start of the day-name header Row so headers align with columns.
- [x] `ui/day/DayScreen.kt`: pass `rememberNowMinutes(zone, isToday)`
  through to `DayColumn`.
- [x] `ui/schedule/ScheduleScreen.kt`: derive `nowMillis` from the
  tick; partition today's TIMED rows only (all-day rows stay above the
  line); thread `nowMillis: Long?` into `DaySection`.
- [x] `ui/settings/UserPreferences.kt`: add
  `drawerHiddenAccountKeys: Set<String>` field, `KEY_DRAWER_HIDDEN_ACCOUNT_KEYS`
  preference key, getter wired into `prefs` flow, and
  `setDrawerHiddenAccountKeys` setter. Update default in
  `SettingsViewModel` init state.
- [x] `AppViewModel.kt`: expose `drawerHiddenAccountKeysFlow` and
  `drawerHiddenAccountsFlow` (resolved). Add
  `hideAccountFromDrawer(accountKey)` and
  `restoreAccountToDrawer(accountKey)`. Merge implied calendar IDs
  into `hiddenCalendarIdsFlow`. Inline `drawerAccountKey` helper so
  the VM does not depend on a UI helper.
- [x] `ui/month/drawer/CalendarRow.kt`: remove the previously added
  per-calendar `onHideFromDrawer` callback and menu entry.
- [x] `ui/month/drawer/AccountSection.kt`: add 3-dot menu with
  "Change account color" + "Hide from drawer"; remove avatar
  long-press handler.
- [x] `ui/month/CalendarDrawer.kt`: accept `drawerHiddenAccountKeys`
  and `onHideAccountFromDrawer`; filter `modeFiltered` by
  `accountOverrideKey(...) !in drawerHiddenAccountKeys`; pass change-
  color + hide callbacks into `AccountHeader`.
- [x] `ui/settings/SettingsScreen.kt`: render "Hidden accounts"
  section over a `List<DrawerHiddenAccount>` with restore action.
- [x] `ui/AppShell.kt`: collect `drawerHiddenAccountKeys`, pass into
  drawer, wire `onHideAccountFromDrawer`.
- [x] `ui/AppOverlays.kt`: collect `drawerHiddenAccountsFlow`, pass
  into `SettingsScreen` with `onRestoreAccountToDrawer`.
- [x] `res/values/strings.xml`: add `cd_account_menu`,
  `action_change_account_color`, `settings_section_hidden_accounts`;
  drop `cd_recolor_avatar_longpress` (avatar long-press gone).
- [x] `ui/month/EventChips.kt`: switch from a fixed three-chip cap to
  a `BoxWithConstraints`-driven count so the "+N more" row always
  fits inside the cell and dense days no longer silently drop their
  tail.
- [x] `CHANGELOG.md` `[Unreleased]`: now-line + drawer hide (Added),
  week-header + avatar-long-press consolidation (Changed), +N more
  fit (Fixed).
- [x] `./gradlew :app:lintDebug :app:testDebugUnitTest spotlessCheck detekt :app:assembleDebug`
  green; lint + detekt baselines regenerated.
- [x] Device verify (fresh install on Pixel 10 Pro XL):
  - Day view: full-width line at current time; flips with theme.
  - Week view: line only inside today's column; Sunday no longer
    clipped under its header.
  - Schedule view: line sits between past and future timed events in
    today's section; absent if today has no events; all-day rows stay
    above the line.
  - Drawer: 3-dot on account header offers Change account color + Hide
    from drawer; avatar long-press no longer recolors.
  - Settings: "Hidden accounts" section appears, "Show in drawer"
    restores.
  - Month: a day with 4+ timed events shows three chips and a "+N
    more" row that opens the day-overflow sheet.

## Files touched

- `app/src/main/kotlin/com/arishawke/asala/calendar/ui/theme/Color.kt`
- **NEW** `app/src/main/kotlin/com/arishawke/asala/calendar/ui/timeline/NowLineMarker.kt`
- `app/src/main/kotlin/com/arishawke/asala/calendar/ui/week/TimelineGrid.kt`
- `app/src/main/kotlin/com/arishawke/asala/calendar/ui/week/WeekScreen.kt`
- `app/src/main/kotlin/com/arishawke/asala/calendar/ui/day/DayScreen.kt`
- `app/src/main/kotlin/com/arishawke/asala/calendar/ui/schedule/ScheduleScreen.kt`
- `app/src/main/kotlin/com/arishawke/asala/calendar/ui/settings/UserPreferences.kt`
- `app/src/main/kotlin/com/arishawke/asala/calendar/ui/settings/SettingsViewModel.kt`
- `app/src/main/kotlin/com/arishawke/asala/calendar/AppViewModel.kt`
- `app/src/main/kotlin/com/arishawke/asala/calendar/ui/month/drawer/CalendarRow.kt`
- `app/src/main/kotlin/com/arishawke/asala/calendar/ui/month/drawer/AccountSection.kt`
- `app/src/main/kotlin/com/arishawke/asala/calendar/ui/month/CalendarDrawer.kt`
- `app/src/main/kotlin/com/arishawke/asala/calendar/ui/settings/SettingsScreen.kt`
- `app/src/main/kotlin/com/arishawke/asala/calendar/ui/AppShell.kt`
- `app/src/main/kotlin/com/arishawke/asala/calendar/ui/AppOverlays.kt`
- `app/src/main/res/values/strings.xml`
- `CHANGELOG.md`
- `docs/ROADMAP.md` (drawer-hide moved from "Later > Visual polish" to
  "Recently shipped"; week-header fix called out)
- `docs/adr/README.md` (index brought current: ADR-0003/0004/0005)
- `README.md` (Features list refreshed; ADR list extended; M4
  reference de-staled)
