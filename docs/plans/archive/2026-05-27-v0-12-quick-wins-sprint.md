# Plan: v0.12.0 quick-wins sprint (sprint A)

Date: 2026-05-27.
Spec: [2026-05-27-v0-12-quick-wins-sprint-design.md](../specs/2026-05-27-v0-12-quick-wins-sprint-design.md).

One feature branch (`feat/v0.12.0-quick-wins-sprint`, already
created off main at `59ceff1`). Three commits, each landing one
sprint item, so a problematic one can be reverted independently.
CHANGELOG entry under `[Unreleased]` in the same commit as the
change. `feedback-spotless-local-check`: run
`spotlessKotlinCheck + lintDebug + testDebugUnitTest` per commit
before pushing.

Order is roughly small to larger so early commits don't block on
mid-sprint lint surprises.

## Commit 0 (already done) — spec + plan

- [x] `docs/specs/2026-05-27-v0-12-quick-wins-sprint-design.md`
- [x] `docs/plans/2026-05-27-v0-12-quick-wins-sprint.md`
- [ ] Commit as `docs: spec + plan for v0.12.0 quick-wins sprint`.

## Commit 1 — multi-local-calendars (SKIPPED, not reproducible)

Investigation on 2026-05-27 found no bug. All code paths between
the drawer + button, `CreateCalendarDialog`, `AppViewModel.createLocalCalendar`,
`CalendarRepository.createLocalCalendar`, and the drawer's grouping
logic are clean (no guards, drawer groups by `accountName` and
renders multiple rows per group). Device verification confirmed on
a fresh-install debug APK: two consecutive "Create local calendar"
attempts via the drawer flow both produced visible, independent
calendars. ROADMAP entry removed in the same commit as this plan
update. No code change shipped for this item.

## Commit 2 — cake icon for birthday-type events

- [ ] Pick detection mechanism: try account-type match first
  (compile a short allow-list of known birthday account types via
  `adb shell content query --uri content://com.android.calendar/calendars`
  on the paired device). Fall back to name-pattern only if
  account-type doesn't suffice.
- [ ] Add `isBirthday: Boolean = false` to `data/EventItem.kt` and
  `data/EventDetail.kt`. Update `equals` / `hashCode` /
  `applyColorOverrides` if needed.
- [ ] In `data/EventDetailReader.kt` and `data/EventInstanceReader.kt`,
  detect at query time. Add a helper `isBirthdayCalendar(accountType:
  String, calendarName: String): Boolean` in a shared place (probably
  `data/BirthdayDetection.kt`) so both readers share one source of
  truth.
- [ ] `ui/components/EventVisual.kt`: helper `birthdayLeadingIcon(isBirthday:
  Boolean): @Composable () -> Unit?` returning the cake glyph (Material
  `Icons.Filled.Cake` or `Icons.Outlined.Cake`, whichever ships in
  the M3 icon set). Apply to Compact + Block + Row chip variants.
  Hide on the smallest Compact size if the icon doesn't fit.
- [ ] `ui/eventdetail/EventDetailSheet.kt`: leading cake icon in the
  title row when `isBirthday`. Coexists with the existing Tentative /
  Cancelled status badge.
- [ ] No new strings for v0.12.0 (visual-only).
- [ ] CHANGELOG entry under `[Unreleased]` > Added: "Cake icon on
  birthday-type events. Events from the Android Contacts birthday
  calendar (and other birthday-account-typed calendars) render with
  a small cake glyph in their chip title and detail-sheet header to
  differentiate them from regular events at a glance."
- [ ] Gate.

## Commit 3 — show week number setting

- [ ] `ui/settings/UserPreferences.kt`: add `showWeekNumber: Boolean
  = false` to `UserPrefs` data class; add the DataStore key + setter
  mirroring the working-days pattern (lines 152, 308-320).
- [ ] `ui/settings/SettingsScreen.kt`: add a Switch row "Show week
  number" in the Appearance section, after the working-days row.
- [ ] `ui/month/MonthScreen.kt`: when enabled, prepend a fixed-width
  (28dp) `Column` to each `WeekLayoutRow` containing the ISO 8601
  week number for that row's date range. Use Java
  `WeekFields.ISO.weekOfWeekBasedYear()` for the calculation.
- [ ] `ui/week/TimelineGrid.kt`: when enabled, add the ISO 8601 week
  number as a small label above the existing `HourAxis` column.
- [ ] New strings: `settings_show_week_number` (title), maybe
  `settings_show_week_number_supporting` (description) if the row
  uses a supporting line.
- [ ] CHANGELOG entry under `[Unreleased]` > Added: "Show week number
  setting in Settings > Appearance. When enabled, Month view renders
  ISO 8601 week-of-year in a thin left column and Week view renders
  it above the hour axis. Defaults to off; designed for users who
  plan in week numbers (common in European calendars)."
- [ ] Gate.

## Final steps

- [ ] Run the full gate one more time: `./gradlew :app:spotlessKotlinCheck
  :app:lintDebug :app:testDebugUnitTest`.
- [ ] `adb uninstall com.arishawke.asala.calendar` then `./gradlew
  :app:installDebug` per `feedback-test-fresh-install` and
  `feedback-device-install-autonomous`. Walk the verification list
  in the spec.
- [ ] Push branch, open PR, wait for CI.
- [ ] User does the manual smoke pass on each of the three items.
- [ ] After PR merges to main: cut v0.12.0 per
  `feedback-releases-prerelease`; archive spec + plan; then start
  sprint B.

## Reusable utilities (do not reimplement)

- `StorageModeSetup.ensureLocalCalendarIfNeeded` pattern
  ([data/StorageModeSetup.kt:17-28](../../app/src/main/kotlin/com/arishawke/asala/calendar/data/StorageModeSetup.kt#L17-L28))
  — existence check for local-calendar creation; reuse the
  `.any()` pattern in the multi-local-calendars fix if the gap
  turns out to be a guard.
- `EventVisual.statusStyling`
  ([ui/components/EventVisual.kt:61-77](../../app/src/main/kotlin/com/arishawke/asala/calendar/ui/components/EventVisual.kt#L61-L77))
  — pattern for chip-render-time decoration. Cake icon plugs into
  the same render path.
- Working-days toggle plumbing
  ([data/UserPreferences.kt:152, 308-320](../../app/src/main/kotlin/com/arishawke/asala/calendar/ui/settings/UserPreferences.kt#L152))
  — DataStore + Settings + downstream parameter pattern; mirror
  exactly for show-week-number.
- `Linkify` and `AnnotatedString.fromHtml` — out of scope for sprint
  A; called out for sprint B's iCal URL work.
