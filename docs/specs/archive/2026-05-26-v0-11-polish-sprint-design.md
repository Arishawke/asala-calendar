# Spec: v0.11.0 polish + standards sprint

Date: 2026-05-26.

## Context

Three research agents (peer-OSS audit, commercial calendar feature
survey, user community voices) converged on a handful of small
calendar features Asala lacks. This sprint takes the cross-cited
high-confidence subset and ships them as one minor-version bump.
Each item is individually small or small-medium; together they close
several of the most-requested feature gaps at once.

Goal: deliver six visible improvements without a single heavy
architectural change. Each lands as its own commit so a problematic
item can be reverted independently. Defer anything that turns out to
be medium-or-larger than expected to the roadmap.

## Verified state before scope-locking

- **Read-only event detail by default** — a common complaint is that
  tap drops users into the editor. Asala already routes tap
  through `openEventDetail` to the detail sheet ([AppShell.kt:123](../../app/src/main/kotlin/com/arishawke/asala/calendar/ui/AppShell.kt#L123)),
  edit lives behind the pencil affordance. **Dropped from sprint.**
- **Custom reminder offset** — `ReminderPicker` has a fixed dropdown
  `[null, 0, 5, 10, 15, 30, 60, 1440]`. No Custom option. In scope.
- **Default reminder for new events** — `reminderMinutesBefore: Int? = null`
  in `EventEditFormState`. No "default reminder" Settings entry today.
  Sprint introduces both timed-default and all-day-default settings.
- **Event STATUS** — Asala writes `STATUS_CONFIRMED` on create and
  `STATUS_CANCELED` on cancellation, but the `EventInstanceReader`
  query does not project STATUS, so the visual layer can't show it.
  Sprint extends the query and routes through `EventItem` /
  `EventDetail`.

## Items

### 1. Linkified URLs in notes and location

**Source:** common request; also a standard in other calendar apps.

**Behavior.** In the event detail sheet:
- Plain-text descriptions (CalDAV, hand-edited): currently routed
  around the HTML parser to preserve newlines. Add Linkify-equivalent
  detection so plain URLs become tappable spans styled in the theme
  primary color, matching the HTML branch.
- Location field: currently plain `Text`. Wrap with the same
  Linkify-equivalent detection so an address pasted as a URL
  becomes tappable.
- HTML descriptions already get tappable `<a>` rendering via
  `AnnotatedString.fromHtml`; no change there.

**Files.** `ui/eventdetail/EventDetailSheet.kt` (notes + location
spans); possibly a new `ui/util/LinkifyText.kt` helper if the logic
duplicates.

**Edge cases.** Email addresses (`mailto:`) and phone numbers (`tel:`)
should linkify the same way URLs do. Long URLs in narrow sheets wrap.

**Size.** Small.

### 2. Custom reminder offset

**Source:** frequently requested across calendar apps.

**Behavior.** The `ReminderPicker` dropdown gains a "Custom..." entry
at the bottom. Selecting it opens a small dialog with a number input
plus a unit selector (minutes / hours / days). On confirm, the chosen
total in minutes becomes the reminder offset. The picker label
formats it back as "11 days before" / "3 h 15 min before" via the
existing `reminder_minutes_before` plurals.

**Files.** `ui/eventedit/ReminderPicker.kt`; new
`ui/eventedit/CustomReminderDialog.kt`; one new strings entry for the
"Custom..." label.

**Edge cases.** Negative or zero rejected; max capped at 9999 minutes
(enough for ~7 days) or convert hours/days to minutes via the unit
selector (no cap on the result). Picker shows the closest preset
label if the custom value happens to match one (e.g., 60 → "1 hour
before").

**Size.** Small.

### 3. Multi-line event titles in Month view

**Source:** frequently requested.

**Behavior.** `EventChipCompact` currently caps title at `maxLines = 1`.
Bump to `maxLines = 2`. Chip height becomes variable: short titles
stay one line, long titles wrap to two. The Month-cell overflow
math (`BoxWithConstraints.maxHeight / chipRowHeight`) needs to use a
single-line estimate to keep the +N indicator honest, but two-line
chips occupy two of the "slots" the math allocates.

**Files.** `ui/components/EventVisual.kt` (one line change); possibly
`ui/month/DayCell.kt` (overflow math).

**Edge cases.** Very long titles ellipsize at line 2. Verify Month
chip count doesn't silently drop events on dense days at low
densities — adjust the +N math if so.

**Size.** Small (verify the overflow interaction).

### 4. Event STATUS display

**Source:** frequently requested.

**Behavior.** Project `CalendarContract.Events.STATUS` through the
event-instance query into `EventItem`, then render distinctly:
- `STATUS_TENTATIVE` (1): dashed border around the chip, full opacity
  text. Detail sheet shows a "Tentative" badge near the title.
- `STATUS_CANCELED` (2): strikethrough title, ~50% opacity body.
  Detail sheet shows a "Cancelled" badge near the title.
- `STATUS_CONFIRMED` (0): no decoration. Default rendering.
- Other / null: treat as confirmed.

**Files.** `data/EventInstanceReader.kt` (add STATUS to query),
`data/EventItem.kt` (add `status: Int` field), `data/EventDetail.kt`
(add status field), `ui/components/EventVisual.kt` (chip decoration
per variant), `ui/eventdetail/EventDetailSheet.kt` (status badge).
New strings: `status_tentative`, `status_cancelled`.

**Edge cases.** All three EventVisual variants (Compact / Block / Row)
need the decoration. Past-event opacity dim still applies on top of
status decoration.

**Size.** Small-medium.

### 5. Working hours dim in Day / Week

**Source:** convergent across many calendar apps.

**Behavior.** New Settings entries under Appearance:
- "Show working hours" toggle (default off).
- "Working day start" time picker (default 09:00).
- "Working day end" time picker (default 17:00).

When the toggle is on, Day and Week views render hours outside the
working range with reduced opacity (~50%) — a single overlay
`Modifier.alpha` block above the timeline grid, drawn behind
the chips so events themselves stay full-opacity. Hours inside the
working range render unchanged.

**Files.** New `data/WorkingHoursPrefs.kt` (DataStore-backed),
`ui/settings/SettingsScreen.kt` (Appearance section entry +
sub-entries), `ui/day/DayScreen.kt` + `ui/week/WeekScreen.kt`
(overlay layer above the timeline grid).

**Edge cases.** Working day that wraps midnight (e.g., night-shift
"start 22:00, end 06:00") — out of scope for v0.11; require end > start.
Working hours apply only inside today's column in Week view? No —
apply to every visible day for consistency; users can toggle off if
they don't want weekends dimmed.

**Size.** Small-medium.

### 6. Default reminder settings (timed + all-day variants)

**Source:** frequently requested.

**Behavior.** Two new Settings entries under Notifications:
- "Default reminder for timed events" — dropdown matching
  `ReminderPicker` choices, default "None".
- "Default reminder for all-day events" — same picker, separate
  value, default "None". Explicitly distinct because "15 minutes
  before" on an all-day event fires at 23:45 the night before, which
  is rarely what the user wanted.

When a user opens the editor for a NEW event:
- If the form is in "all-day = true" state, seed
  `reminderMinutesBefore` from the all-day default.
- Otherwise, seed from the timed default.
- Editing an existing event leaves its current value alone.
- Toggling the all-day switch in the editor does NOT re-seed
  reminders — only the initial open does. Avoids surprising the user
  who already chose a reminder.

**Files.** New `data/ReminderDefaultsPrefs.kt` (DataStore-backed),
`ui/settings/SettingsScreen.kt` (two entries under Notifications),
`ui/eventedit/EventEditViewModel.kt` (seed values on initial
creation flow). Reuse the existing `ReminderPicker` for the Settings
entries.

**Edge cases.** The all-day default of "15 minutes before" would
mean the reminder fires at 23:45 on the day before the event. The
default is None, but for power users who set it: their choice, just
document the behavior.

**Size.** Small-medium.

## Out of scope

- Multiple reminders per event (existing roadmap item).
- iCal URL property (separate roadmap entry).
- CalDAV attachments (separate roadmap entry).
- Drag-to-resize duration (separate roadmap entry).

## Verification

- `./gradlew :app:spotlessKotlinCheck :app:lintDebug :app:testDebugUnitTest`
  clean per commit.
- Build debug APK and install on the connected device via
  `adb uninstall com.arishawke.asala.calendar` then
  `./gradlew :app:installDebug`. Manual smoke testing handled by the
  maintainer.
- CHANGELOG entry under `[Unreleased]` per item.
- Each item ships as a separate commit so it can be reverted
  independently.

## Reusable utilities (do not reimplement)

- Existing `AnnotatedString.fromHtml` HTML link rendering in
  `ui/eventdetail/EventDetailSheet.kt` — extend with plain-text
  linkification, don't fork.
- `ReminderPicker` — extend with "Custom..." entry, reuse for the new
  Settings default-reminder rows.
- DataStore-backed preference pattern in `data/*Prefs.kt` files.
- M3 `ListItem` rows in `ui/settings/SettingsScreen.kt` — match
  existing section styling.
