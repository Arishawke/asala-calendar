# Plan: v0.11.0 polish + standards sprint

Date: 2026-05-26.
Spec: [2026-05-26-v0-11-polish-sprint-design.md](../specs/2026-05-26-v0-11-polish-sprint-design.md).

One feature branch (`feat/v0.11.0-polish-sprint`), six commits, one
PR. Each item lands as its own commit so a problematic one can be
reverted independently. CHANGELOG entry under `[Unreleased]` in the
same commit as the change. `feedback-spotless-local-check`: run
`spotlessKotlinCheck + lintDebug + testDebugUnitTest` per commit
before pushing.

Order is roughly small → larger so early commits don't block on
mid-commit lint surprises.

## Commit 1 — multi-line event titles in Month view (DEFERRED)

Looked at this first; the interaction with `EventChips.ChipRowHeightApprox`
(the per-row footprint used to compute cell capacity for the +N row)
turns out non-trivial. Variable-height chips with a fixed-height math
either under-fill cells when titles are short or push the +N row below
the cell border when titles are long. Both regress the v0.10 overflow
fix. Needs its own design pass; punted to the broader ROADMAP backlog.

## Commit 2 — linkified URLs in notes and location

- [ ] `ui/eventdetail/EventDetailSheet.kt`: the plain-text branch of
  the notes renderer currently builds a vanilla `Text`. Switch to
  `AnnotatedString` with `UrlAnnotation` spans built from a
  `LinkifyCompat`-style scan (URL / email / phone regexes).
- [ ] Same treatment for the location field.
- [ ] If the logic is large enough to live in two places, extract
  `ui/util/LinkifyAnnotated.kt`.
- [ ] CHANGELOG: "Plain-text URLs, email addresses, and phone numbers
  in event notes and location are now tappable; the theme primary
  color matches the existing HTML-link rendering."
- [ ] Gate.

## Commit 3 — custom reminder offset

- [ ] `ui/eventedit/ReminderPicker.kt`: append a "Custom..." entry
  to the dropdown.
- [ ] New `ui/eventedit/CustomReminderDialog.kt`: M3 `AlertDialog`
  with a numeric `OutlinedTextField` and a unit `SingleChoiceSegmentedButtonRow`
  (Minutes / Hours / Days). Convert to total minutes on confirm.
- [ ] `reminderLabel` in `ReminderPicker.kt`: snap exact matches
  (60 → "1 hour", 1440 → "1 day"); otherwise fall back to the
  `reminder_minutes_before` plurals.
- [ ] New strings: `reminder_custom`, `custom_reminder_dialog_title`,
  `custom_reminder_unit_minutes`, `_hours`, `_days`.
- [ ] CHANGELOG: "Custom reminder offset. Pick "Custom..." in the
  reminder dropdown to set an arbitrary minutes / hours / days
  before-event time."
- [ ] Gate.

## Commit 4 — event STATUS display

- [ ] `data/EventInstanceReader.kt`: add `CalendarContract.Instances.STATUS`
  to the projection. Map to `EventItem.status: Int` (default 0).
- [ ] `data/EventItem.kt`: add `status: Int` field. Update equals /
  hashCode / `applyColorOverrides` if needed.
- [ ] `data/EventDetail.kt` + `data/EventDetailReader.kt`: same
  treatment for the detail-sheet path.
- [ ] `ui/components/EventVisual.kt`: helper `statusDecoration(status):
  Modifier + TextDecoration?`. Apply to Compact / Block / Row.
  Tentative → dashed border via `Modifier.drawBehind`. Cancelled →
  `TextDecoration.LineThrough` on title + 0.5f alpha.
- [ ] `ui/eventdetail/EventDetailSheet.kt`: small badge in the title
  row for non-confirmed statuses.
- [ ] New strings: `status_tentative`, `status_cancelled`.
- [ ] CHANGELOG: "Tentative and cancelled events now render
  distinctly. Tentative gets a dashed border around the event chip
  and a badge in the detail sheet; cancelled gets a strikethrough
  title at reduced opacity."
- [ ] Gate.

## Commit 5 — working hours dim

- [ ] New `data/WorkingHoursPrefs.kt`: DataStore preference for
  `enabled: Boolean`, `startHour: Int`, `endHour: Int`. Match the
  pattern of existing `*Prefs.kt` files.
- [ ] `ui/settings/SettingsScreen.kt` Appearance section: add a
  Switch row "Show working hours" plus two `TimePicker`-backed
  entries that appear when enabled. Reuse the existing M3 `ListItem`
  rendering.
- [ ] `ui/day/DayScreen.kt` + `ui/week/WeekScreen.kt`: overlay
  composable above the timeline grid that draws two `Modifier.alpha(0.5f)`
  rectangles over the [0, startHour] and [endHour, 24] bands.
  Z-stack below chips so events stay full-opacity.
- [ ] New strings under Appearance.
- [ ] CHANGELOG: "Working hours dim. Toggle "Show working hours" in
  Settings > Appearance and pick a start / end; hours outside the
  range render at half opacity in Day and Week views."
- [ ] Gate.

## Commit 6 — default reminder settings (timed + all-day)

- [ ] New `data/ReminderDefaultsPrefs.kt`: DataStore preference for
  `timedDefault: Int?` and `allDayDefault: Int?`. Both nullable, both
  None by default.
- [ ] `ui/settings/SettingsScreen.kt` Notifications section: add two
  `ReminderPicker` rows ("Default reminder for timed events", "Default
  reminder for all-day events"). Verify the existing picker supports
  use outside the editor — it should, since it takes a value + an
  onChange.
- [ ] `ui/eventedit/EventEditViewModel.kt`: in the new-event creation
  flow (existing `eventId == null` branch), seed
  `reminderMinutesBefore` from the appropriate default based on
  initial `allDay` state.
- [ ] Existing events: leave their reminder value untouched.
- [ ] Editor all-day toggle: no re-seeding. (Stays simple; explained
  in CHANGELOG.)
- [ ] CHANGELOG: "Default reminder settings in Settings > Notifications.
  Pick a default reminder for timed events and a separate one for
  all-day events; new events seed from the appropriate value when the
  editor opens. Existing events are not changed."
- [ ] Gate.

## Final steps

- [ ] Run the full gate one more time: `./gradlew :app:spotlessKotlinCheck
  :app:lintDebug :app:testDebugUnitTest`.
- [ ] `adb uninstall com.arishawke.asala.calendar` then `./gradlew :app:installDebug`
  per `feedback-test-fresh-install` and `feedback-device-install-autonomous`.
- [ ] Push branch, open PR, wait for CI.
- [ ] User does the manual smoke pass on each of the six items.
- [ ] After PR merges to main: archive this plan and spec; cut v0.11.0
  per `feedback-releases-prerelease`.

## Reusable utilities (do not reimplement)

- `AnnotatedString.fromHtml` already in EventDetailSheet for HTML
  links — extend the plain-text branch with similar Linkify scanning,
  do not fork.
- `ReminderPicker` — extend in place, reuse for Settings rows.
- DataStore preference files in `data/*Prefs.kt` — follow the
  existing serialization pattern.
- M3 `ListItem` rows in `ui/settings/SettingsScreen.kt` for new
  settings entries.
