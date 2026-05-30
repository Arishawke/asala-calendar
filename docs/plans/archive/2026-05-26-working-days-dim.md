# Plan: Working days dim (Day + Week)

Date: 2026-05-26.
Spec: [2026-05-26-working-days-dim-design.md](../specs/2026-05-26-working-days-dim-design.md).

One commit on the existing `feat/v0.11.0-polish-sprint` branch.
This is a follow-on to the v0.11.0 polish sprint (symmetric
counterpart to working-hours dim). CHANGELOG entry under
`[Unreleased]`. Per `feedback-spotless-local-check`: run
`spotlessKotlinCheck + lintDebug + testDebugUnitTest` before
pushing.

## Tasks

- [ ] `UserPrefs`: add `workingDaysEnabled: Boolean` and
  `workingDays: Set<DayOfWeek>` fields. Default enabled=false,
  default days=Mon-Fri.
- [ ] `UserPreferences`: `KEY_WORKING_DAYS_ENABLED` (boolean) and
  `KEY_WORKING_DAYS` (stringSet of `DayOfWeek.name`). Reader
  coerces empty/invalid to the Mon-Fri default to avoid the
  "everything dimmed" failure mode. Setters mirror
  `setHiddenCalendarIds` pattern.
- [ ] `SettingsViewModel`: initial values + `setWorkingDaysEnabled`
  / `setWorkingDays` launches.
- [ ] `SettingsScreen`: under Appearance, after
  `appearance-working-hours-*`, add `SwitchRow` "Show working
  days" plus a conditional `WorkingDaysRow` shown when enabled.
- [ ] New `ui/settings/WorkingDaysRow.kt`: row composable that
  renders the current selection as text (e.g., "Mon, Tue, Wed,
  Thu, Fri" or "Custom") and opens a dialog with seven
  `FilterChip`s on tap. First day of the chip row follows
  `weekStartsOn` (fall back to `firstDayOfWeekFromLocale()`).
  Confirm disabled when zero days are checked.
- [ ] `CalendarViewSwitcher`: thread `workingDaysEnabled` and
  `workingDays` from `prefs` into WeekScreen and DayScreen.
- [ ] `WeekScreen`: accept the two new params, pass to
  `WeekPage` → `WeekDayHeader` and `TimelineGrid`.
- [ ] `WeekDayHeader`: accept `isNonWorkingDay: Boolean`; apply
  `Color.Black @ 12% alpha` overlay when true (compose with
  existing past-date dim).
- [ ] `TimelineGrid` / `DayColumn`: accept `workingDaysEnabled`
  + `workingDays`; compute `isNonWorkingDay` per day. When true,
  paint the same full-column overlay used by `WorkingHoursDim`
  and *suppress* the WorkingHoursDim (whole column wins).
- [ ] `DayScreen`: same in the single-day `Timeline`.
- [ ] `strings.xml`: `settings_working_days_label`,
  `settings_working_days_supporting`, `settings_working_days_picker`,
  plus a day-name list (reuse `DayOfWeek.getDisplayName(TextStyle.SHORT,
  locale)` rather than baking English).
- [ ] CHANGELOG entry under `[Unreleased]`: "Working days dim. New
  Settings > Appearance toggle plus a working-days picker dims
  non-working day columns in Week view and the timeline in Day
  view, matching the working-hours treatment. Default Mon-Fri,
  off by default. Suppresses the working-hours band on a non-
  working day so the column doesn't double-dim."
- [ ] Gate: `./gradlew :app:spotlessKotlinCheck :app:lintDebug
  :app:testDebugUnitTest`.
- [ ] Fresh install: `adb uninstall com.arishawke.asala.calendar`
  then `./gradlew :app:installDebug`.

## Reusable utilities (do not reimplement)

- `WorkingHoursDim` overlay pattern in
  `ui/week/TimelineGrid.kt` — the new `NonWorkingDayOverlay` can
  be a sibling helper in the same file at the same `Color.Black @
  0.12f alpha` so the two dims look identical.
- `firstDayOfWeekFromLocale()` in `WeekScreen.kt`.
- `stringSetPreferencesKey` serialization pattern from
  `hiddenCalendarIds` / `drawerHiddenAccountKeys`.
- M3 `FilterChip` for the day picker; M3 `AlertDialog` to host it.
