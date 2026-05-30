# Spec: Working days dim (Day + Week)

Date: 2026-05-26.

## Context

`v0.11.0` shipped a "working hours" dim that fades out non-work hour
bands inside Day and Week timelines. Symmetric counterpart: which
*days* count as working. Some calendar apps ship both together as the
cleanest model; others conflate "hide weekends" with the same idea.
Asala already has the hour half; this spec completes the work-block
concept on the day axis.

Scoped to Day + Week views only. Month view dimming has a bigger
visual blast radius (5 of 7 cells dimmed per row dominates the grid)
and a separate design pass; left out of this iteration.

## Behavior

### Setting

New entry in Settings > Appearance, paired with the existing
"Show working hours" entry. Same toggle-plus-picker pattern:

- **Show working days** (Switch). Defaults to *off*. Asala is solo
  and offline-first; many users don't have a fixed work week, so the
  default doesn't assume one.
- When enabled, reveals a **Working days** row that opens a dialog
  with seven `FilterChip`s, one per day of week. Defaults to
  Mon-Fri. First day of the chip row follows the existing
  `weekStartsOn` preference (Sunday-first locales see Sun on the
  left).
- Selecting *zero* days collapses back to "no dim applied" — the
  user effectively turned working-days off without flipping the
  switch. Confirm button stays disabled in that state so the dialog
  can't save a meaningless empty set.

### Rendering

When the setting is enabled and a day is not in the working-days
set, that day is *non-working*:

- **Week view, day header strip**: the `WeekDayHeader` for a
  non-working day renders with the same `Color.Black @ 12% alpha`
  overlay used for working-hours bands. Existing past-date dim
  (PastDateAlpha) and the today highlight both compose on top
  unchanged.
- **Week view, timeline grid**: the `DayColumn` for a non-working
  day renders with the same overlay covering the full 24-hour
  span. Working-hours dim is suppressed inside a non-working day —
  the whole column is already dim, stacking would be visual noise.
- **Day view**: the same overlay covers the full timeline if the
  visible date is a non-working day. The all-day strip stays
  full-opacity (it's narrow; dimming it adds little).
- **Today**: the today highlight in the day-number circle is *not*
  dimmed even when today is a non-working day, so the
  "where am I" signal stays bright.

### Interaction with working-hours dim

- Non-working day + working-hours enabled: the whole-column dim
  wins; no working-hours band stacked on top.
- Working day + working-hours enabled: existing working-hours band
  dim applies unchanged.
- Working day + working-hours disabled: column is full-opacity.

## Files touched

- `app/src/main/kotlin/com/arishawke/asala/calendar/ui/settings/UserPreferences.kt`:
  add `workingDaysEnabled: Boolean` and `workingDays: Set<DayOfWeek>`
  to `UserPrefs`; serialize the set via the existing
  `stringSetPreferencesKey` pattern (store `DayOfWeek.name` strings).
  Defaults: enabled=false, days=[MON, TUE, WED, THU, FRI].
- `app/src/main/kotlin/com/arishawke/asala/calendar/ui/settings/SettingsViewModel.kt`:
  initial values + setters.
- `app/src/main/kotlin/com/arishawke/asala/calendar/ui/settings/SettingsScreen.kt`:
  new SwitchRow + working-days picker row under Appearance, mirroring
  the working-hours pattern.
- New `app/src/main/kotlin/com/arishawke/asala/calendar/ui/settings/WorkingDaysRow.kt`:
  the row composable plus the dialog with seven FilterChips. First-
  day-of-week ordering pulled from the existing `weekStartsOn` pref.
- `app/src/main/kotlin/com/arishawke/asala/calendar/ui/CalendarViewSwitcher.kt`:
  thread `workingDaysEnabled` and `workingDays` from `prefs` into
  WeekScreen and DayScreen.
- `app/src/main/kotlin/com/arishawke/asala/calendar/ui/week/WeekScreen.kt`,
  `WeekDayHeader.kt`,
  `TimelineGrid.kt`:
  accept the two new params; `WeekDayHeader` paints the overlay over
  itself when non-working; `DayColumn` adds a full-height overlay
  inside `BoxWithConstraints` before the chip loop (and suppresses
  the working-hours band when it's non-working).
- `app/src/main/kotlin/com/arishawke/asala/calendar/ui/day/DayScreen.kt`:
  same overlay treatment in `Timeline`.
- `app/src/main/res/values/strings.xml`: new strings for the toggle
  label, supporting text, dialog title.

## Edge cases

- **Empty working-days set**: the dialog's confirm is disabled when
  zero days are checked, so persistence can't reach an empty set.
  Defensive: if a future migration writes empty, the renderer
  treats every day as non-working — which dims everything. Reader
  should coerce empty to the Mon-Fri default to avoid that.
- **`weekStartsOn = null`** (system locale): use
  `firstDayOfWeekFromLocale()` from existing code; the FilterChip
  row orders accordingly.
- **Day-of-week serialization**: store `DayOfWeek.name` strings
  (`MONDAY`, etc.) so a future locale doesn't break decoding.

## Verification

- `./gradlew :app:spotlessKotlinCheck :app:lintDebug :app:testDebugUnitTest`
  clean.
- Fresh install via `adb uninstall com.arishawke.asala.calendar`
  then `./gradlew :app:installDebug`.
- Manual:
  1. Defaults: no dim visible anywhere.
  2. Toggle Show working days on with default Mon-Fri: weekend
     columns dim in Week, weekend days in Day view dim, weekend
     headers dim.
  3. Combine with Show working hours: weekday columns show the
     hour band dim; weekend columns are uniformly dim.
  4. Pick a custom set (e.g., Tue-Sat): the correct columns dim;
     uncheck a day to see the immediate change after dialog
     confirm.
  5. Today highlight stays bright on a non-working today.
  6. Theme flip (System / Light / Dark / AMOLED): dim band visible
     and the same intensity in every theme.

## Reusable utilities (do not reimplement)

- `Color.Black.copy(alpha = 0.12f)` and the `Column` + `Box` overlay
  pattern in `TimelineGrid.WorkingHoursDim` — extend, don't fork.
- `firstDayOfWeekFromLocale()` in `WeekScreen.kt` for the chip
  ordering.
- `Set<DayOfWeek>` serialization: same `stringSetPreferencesKey`
  pattern already used for `hiddenCalendarIds`.
- M3 `FilterChip` for the multi-select.
