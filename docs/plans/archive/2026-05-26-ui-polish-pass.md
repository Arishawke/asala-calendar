# Plan: UI polish pass

Date: 2026-05-26.
Spec:
[2026-05-26-ui-polish-pass-design.md](../specs/2026-05-26-ui-polish-pass-design.md).

Six small PRs. Each shippable on its own. Each verified per
`feedback-test-perf-in-release` (release APK on Pixel 10 Pro XL).
Update `CHANGELOG.md` under `[Unreleased]` in the same commit when
user-visible.

## PR-A: Design tokens

- [ ] New `ui/theme/Spacing.kt` with `xs / sm / md / lg / xl / xxl`
  on the 8dp grid.
- [ ] New `ui/theme/Typography.kt` with `AsalaTypography`: M3
  `Typography` overlay, system font, title lineHeights at 1.2x,
  body lineHeights at 1.45-1.5x, `labelLarge` weight Medium (500)
  vs `bodyMedium` Normal (400).
- [ ] `ui/theme/Theme.kt`: pass `typography = AsalaTypography` to
  `MaterialTheme(...)`.
- [ ] CHANGELOG entry under `[Unreleased]` (Changed: typography
  overlay; Added: spacing tokens for internal use).
- [ ] `./gradlew :app:lintDebug :app:testDebugUnitTest` clean.
- [ ] Release APK installs on Pixel 10 Pro XL. Theme flip
  (System / Light / Dark / AMOLED) all render fine.

## PR-B: Settings restructure

- [ ] `ui/settings/SettingsScreen.kt`: rewrite to `LazyColumn(state =
  rememberLazyListState())` with six grouped sections (General,
  Appearance, Notifications, Calendars & accounts, About).
- [ ] Section headers: `Text` `titleSmall`, color = primary,
  `Spacing.lg` top + `Spacing.md` bottom.
- [ ] Each row uses M3 `ListItem` with leading icon, headline,
  supporting description, trailing slot for toggle / value /
  chevron.
- [ ] `ui/settings/SettingsRows.kt`: collapse to thin `ListItem`
  wrappers exposing the existing toggle / dropdown row APIs.
- [ ] `ui/settings/SettingsRowPrimitives.kt`: likely delete once
  `ListItem` handles the slots; verify no remaining external
  callers before removal.
- [ ] `res/values/strings.xml`: section headers, secondary
  descriptions, missing icon contentDescription labels.
- [ ] Conditional sections (e.g., Hidden Accounts) become
  conditional `item { }` blocks in the LazyColumn.
- [ ] CHANGELOG entry under `[Unreleased]` (Changed: settings
  page reorganized into grouped sections).
- [ ] `./gradlew :app:lintDebug :app:testDebugUnitTest` clean.
- [ ] Fresh install verification per `feedback-test-fresh-install`:
  `adb uninstall com.arishawke.asala.calendar` then
  `./gradlew :app:installRelease`. Open Settings, scroll, deny
  notification permission then re-request: scroll position
  survives the recompose.
- [ ] All toggles persist across kill + relaunch.

## PR-C: EventVisual + Month & Schedule migration

- [ ] New `ui/components/EventVisual.kt` with `EventChipCompact`,
  `EventChipRow`, `EventChipBlock` plus module-private
  `resolveEventColor`, `truncatedTitle`, `pastDateAlpha` helpers.
- [ ] `ui/month/MonthScreen.kt` + `ui/month/DayCell.kt`: route Month
  chips through `EventChipCompact`.
- [ ] `ui/schedule/ScheduleScreen.kt`: route Schedule rows through
  `EventChipRow`.
- [ ] `ui/components/EventChip.kt`: delete after callers migrated.
- [ ] `ui/components/EventListRow.kt`: delete after callers
  migrated.
- [ ] New `app/src/test/.../EventVisualVariantTest.kt`: color
  resolution per variant (default, calendar override, event
  override, past-date opacity).
- [ ] CHANGELOG entry under `[Unreleased]` (Changed: unified event
  chip rendering across Month + Schedule).
- [ ] `./gradlew :app:lintDebug :app:testDebugUnitTest` clean. All
  existing color-override tests pass.
- [ ] Manual on release APK: Month + Schedule chips render
  correctly; calendar override, event override, past-date opacity
  visible; theme flip works; palette switch (Okabe-Ito <-> Radix)
  works.

## PR-D: EventVisual Week/Day + AllDayRow refresh

- [ ] `ui/week/EventBlock.kt`: replace visual content with
  `EventChipBlock` call; drag gesture logic stays.
- [ ] `ui/day/DayScreen.kt`: route Day's chip layer through
  `EventChipBlock`.
- [ ] `ui/week/AllDayRow.kt`: refresh visual to match
  `MultiDayBarRow.kt` bar style (rounded rectangle, calendar color
  background, contrasting foreground text).
- [ ] CHANGELOG entry under `[Unreleased]` (Changed: unified event
  chip rendering across Week + Day; Week all-day bar style aligned
  with multi-day bars).
- [ ] `./gradlew :app:lintDebug :app:testDebugUnitTest` clean.
- [ ] Manual on release APK: Week / Day timeline events render
  with tinted backgrounds + 3dp left bar. Drag-to-reschedule still
  works end-to-end including cross-day drag in Week. All-day
  events in Week render as bars matching multi-day bars.

## PR-E: Now-line dedup + color resolver — N/A (no-op on verification)

Verified against the actual codebase during PR-F prep, both halves
of this PR turn out to already be done:

- **Now-line**: there is exactly one `NowLineRow` composable, in
  `ui/timeline/NowLineMarker.kt`. Schedule, Week (via TimelineGrid),
  and Day all import that single implementation. The original
  review's "two implementations" was naming confusion (the file is
  `NowLineMarker.kt`, the composable inside is `NowLineRow`); no
  duplicate to dedup.
- **Color resolver**: all six view ViewModels (Month, Week, Day,
  Schedule, Search, MiniMonth) already call
  `data/EventItem.filteredAndRecolored()` for the chip-rendering
  merge. AppViewModel uses `data/EventDetail.resolveEventDetailColor`
  for the detail sheet. Notifications use
  `notifications/ReminderColor.resolveReminderColor`. Three
  resolvers for three concerns. Adding a fourth `CalendarColorResolver`
  would be a redundant alias. The spec itself flagged this with
  "drop entirely if it doesn't pay for itself once EventVisual (§3)
  lands and encapsulates resolution internally" — PR-C didn't add
  resolution because `EventItem.displayColor` is pre-resolved at the
  data layer.

## PR-F: Polish application

- [ ] `ui/CalendarDrawer.kt`, `ui/month/drawer/AccountSection.kt`,
  `ui/month/drawer/CalendarRow.kt`: Spacing tokens; account name
  weight `labelLarge` 500, calendar name `bodyMedium` 400.
- [ ] `ui/eventdetail/EventDetailSheet.kt`: Spacing tokens;
  When / Where / Notes / Reminders grouped with `HorizontalDivider`.
- [ ] `ui/eventedit/EventForm.kt`, `RecurrenceSection.kt`,
  `ReminderPicker.kt`, `DateTimePickerRow.kt`: Spacing + Typography
  tokens only; no field restructure.
- [ ] `ui/AppShell.kt`: chevron + title hierarchy tightened,
  Spacing applied.
- [ ] `ui/header/HeaderDropdownPanel.kt`, `MiniMonthPanel.kt`,
  `MonthChipsPanel.kt`: Spacing applied.
- [ ] `ui/schedule/ScheduleScreen.kt`: date-section header
  refined; Spacing between list items.
- [ ] `ui/month/MonthScreen.kt`, `ui/week/WeekScreen.kt`,
  `ui/day/DayScreen.kt`: Spacing applied to chrome.
- [ ] CHANGELOG entry under `[Unreleased]` (Changed: typography +
  spacing polish across all surfaces).
- [ ] `./gradlew :app:lintDebug :app:testDebugUnitTest` clean.
- [ ] Manual end-to-end on release APK: theme flip
  (System / Light / Dark / AMOLED) consistent. Font scale 100% +
  130% no clipping. Drawer + four views + detail sheet + editor +
  settings read "one app."

## End-to-end verification (after all six PRs)

- [ ] Side-by-side polish parity check on Pixel 10 Pro XL Android
  16 against top commercial calendar apps. Walk through each
  surface (Month, Week, Day, Schedule, Settings, drawer, detail
  sheet, editor). Triage any remaining gap into a follow-up.
- [ ] Theme flip (Light / Dark / AMOLED) across all surfaces.
- [ ] Font scale 130%: no clip, no truncation regressions.
- [ ] Color-blind palette (Okabe-Ito default) passes
  `RadixPaletteContrastTest` + manual check on chip variants.

## Reusable utilities (do not reimplement)

- `EventItem.applyColorOverrides()`: color override merge logic.
- `rememberNowMinutes(zone, enabled)` in
  `ui/timeline/NowLineMarker.kt`: 60-second tick source.
- `MultiDayBarRow` + `WeekBucketer` + `LaneAssigner` in
  `ui/multidaybars/`: multi-day bar rendering.
- `OverlapLayout` in `ui/timeline/OverlapLayout.kt`: overlap-aware
  column layout.
- `rememberCalendarPagerFling` + `PagerTuning.kt` in `ui/theme/`:
  pager fling tuning + prerender + spring config.
- `HourHeight`, `PastDateAlpha` in
  `ui/timeline/TimelineMetrics.kt`: shared timeline constants.
- `ColorSwatchGrid` in `ui/calendars/`: palette picker UI.
