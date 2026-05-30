# Design: default event duration setting

Status: Draft (not yet implemented).
Target release: next milestone after the M4 closeout.

## Context

When a user creates a new event in Asala without a pre-selected
duration (FAB from any view, Month-cell tap), the editor seeds a
one-hour duration today:

```kotlin
// EventEditViewModel.kt:42-44
val startTime: LocalTime = nextRoundHour(),
val endTime: LocalTime = nextRoundHour().plusHours(1),
```

The 60-minute hardcode is not user-configurable: a user who runs
back-to-back 30-minute calls cannot avoid trimming every new
event by hand.

This spec adds a Settings entry that lets the user pick the
default. The shipped default stays 60 minutes (the existing
behavior, so no regression on first launch).

## Goal

Let the user set a preferred default event duration once, then
have every "no-duration-hint" create path respect it.

## Explicit non-goals

- Auto-shifting the end time on the *editor's* time pickers. The
  existing v0.9.0 behavior (start-side edits delta-shift end to
  preserve duration; end-side edits clamp / roll forward) already
  does the right thing for in-editor adjustments and is untouched
  here.
- "Speedy meetings" / "shorten appointments" patterns (auto-trim
  30 to 25, 60 to 50). Low priority for a consumer calendar;
  revisit if users ask.
- A per-calendar default. Asala's calendar picker is in-editor,
  so per-calendar defaults would require a reload-on-change flow
  inside the editor. Single global default is sufficient for v1.
- "Match last event duration" or AI-inferred defaults.
- Empty-slot tap-to-create on Day / Week timelines. That gesture
  does not exist in Asala today: `DayColumn` / `WeekColumn` have
  no `clickable` or `pointerInput` modifiers on the grid
  background; only `EventBlock` carries gestures (tap to view,
  long-press-drag to reschedule). The FAB is currently the only
  create path from those views. Adding tap-to-create is a
  separate spec; that spec should make the new gesture respect
  this default-duration setting.

## Prior art consulted

Mainstream calendar apps converge on 30 or 60 minutes as the
default duration, configurable in a General settings section.
Common option sets include 0 / 15 / 30 / 45 / 60 / 90 / 120
minutes (with 45 included by some apps and not others).
Well-maintained open-source Android calendar editors expose the
same setting in the same place.

When the user toggles "All day" off in the editor, the universal
pattern is to seed the form with the default-duration setting
starting at the next round hour (or preserve the original
tap-hint if creation was timeline-tap-seeded). No surveyed app
restores the user's pre-toggle times verbatim; none leave the
form at the midnight-to-next-midnight collapse that the all-day
representation produces. A small refinement in one well-
maintained open-source editor: synthesize defaults only on the
first toggle-off in an editor session; on subsequent toggles,
preserve whatever times the user has since picked. We adopt
that refinement.

## Goal value and option set

Shipped default: **60 minutes** (unchanged from today's
hardcode, so first-launch experience is identical).

Setting options:

- 0 min (creates a point-in-time event; uncommon but offered for
  symmetry with the snooze dropdown's value range)
- 15 min
- 30 min
- 45 min
- 60 min (default)
- 90 min
- 120 min

No "Custom" entry in v1. Add later if users ask for arbitrary
minutes.

## Interaction rules

Today's event-create entry points and how each should treat the
new setting:

| Entry point | Path | Respect default? |
|---|---|---|
| FAB from any view | EventEditViewModel default-seed path | yes |
| Tap empty Month cell | same default-seed path with date hint | yes |
| Long-press + drag on Day or Week timeline | reschedule of an existing event | n/a (no creation; reschedule preserves event's existing duration) |
| Edit existing event | EventEditViewModel hydration path | no — preserves event's existing duration |

The classification rule: if the gesture conveys an intended
duration, use that. If the gesture only conveys a start time,
seed the duration from the setting. If the gesture conveys
nothing about duration or start, seed both from the setting plus
`nextRoundHour()`.

## Storage model

One new `UserPreferences` key, backed by the existing
`Context.settingsDataStore` instance:

```kotlin
val KEY_DEFAULT_DURATION_MIN = intPreferencesKey("default_duration_minutes")
```

`UserPrefs` data class gains one field:

```kotlin
val defaultDurationMinutes: Int,  // default 60
```

`UserPreferences` gains one setter:

```kotlin
suspend fun setDefaultDurationMinutes(minutes: Int) {
    dataStore.edit { it[KEY_DEFAULT_DURATION_MIN] = minutes }
}
```

Reader: in the `prefs` flow `map { p -> UserPrefs(...) }` block,
add `defaultDurationMinutes = p[KEY_DEFAULT_DURATION_MIN] ?: 60`.

## Render path

### Editor seeding

`EventEditFormState` (currently a data class with hardcoded
defaults at `EventEditViewModel.kt:35-50`) gains two new
constructor parameters: `defaultDurationMinutes: Int` for the
seeded duration, and `convertedFromAllDay: Boolean = false` for
the one-shot all-day toggle behavior described below.

```kotlin
data class EventEditFormState(
    val defaultDurationMinutes: Int = 60,
    val startTime: LocalTime = nextRoundHour(),
    val endTime: LocalTime =
        nextRoundHour().plusMinutes(defaultDurationMinutes.toLong()),
    val convertedFromAllDay: Boolean = false,
    ...
)
```

`nextRoundHour()` stays where it is today (companion method at
`EventEditViewModel.kt:83-89`).

### Factory wiring

`EventEditViewModel.Factory` (lines 207-229) currently takes
`appContext`, `eventId`, `instanceMillis`, and `storageMode`.
The `storageMode` is read at the call site from
`appViewModel.prefs.value` in `EventEditScreen.kt:62`. Mirror
that pattern for the new pref:

```kotlin
// EventEditScreen.kt
EventEditViewModel.Factory(
    appContext = ...,
    eventId = ...,
    instanceMillis = ...,
    storageMode = appViewModel.prefs.value.storageMode,
    defaultDurationMinutes =
        appViewModel.prefs.value.defaultDurationMinutes,
)
```

The factory snapshots the value at construction and forwards it
into the `EventEditFormState` defaults. The pref is read once at
editor open; changing the setting while the editor is open does
not retroactively shift the current form. Add an inline comment
in the factory:

```kotlin
// Snapshot the pref at construction. AppViewModel's startup
// runBlocking already populated prefs.value, so this is a
// non-blocking read.
```

### All-day toggle behavior

`EventForm.kt:64` currently calls `state.copy(allDay = it)` with
no duration-preserving logic. New behavior:

- **Toggle on:** as today (allDay flips to true; time fields
  hidden or no-op).
- **First toggle off in this editor session:** if
  `!state.convertedFromAllDay`, set
  `startTime = nextRoundHour()` (or the original tap-hint if
  the editor was opened with a start-time hint),
  `endTime = startTime + defaultDurationMinutes`, and
  `convertedFromAllDay = true`. The flag prevents subsequent
  toggles from clobbering the user's intervening edits.
- **Subsequent toggle off in the same session:** preserve the
  current `startTime` / `endTime`; only flip `allDay`.

This matches the convention across surveyed apps and open-source
editors: no verbatim restore of pre-toggle times,
default-duration seeded fresh on first conversion, user edits
preserved thereafter.

## Settings UI

Settings screen lives in `ui/settings/SettingsScreen.kt`. The
"General" section header is at line 128. A new dropdown row goes
after `DefaultViewRow` (line 129) and before the Tasks switch
(line 134).

Mirror the pattern of `DefaultSnoozeDropdown`, currently inline
in `SettingsScreen.kt:159-186`. That dropdown is deliberately
inline rather than extracted: a comment at lines 154-156 makes
the call explicit ("Lives in the screen file because it
captures only the snooze option set. Pulling it out would just
trade a few lines for an extra file"). The new default-duration
dropdown follows the same convention and stays inline. There is
no separate `SettingsDropdowns.kt`.

- Row title (left):
  `stringResource(R.string.settings_default_duration)`.
- Trailing dropdown showing the current value as `"$current min"`
  (matches `DefaultSnoozeDropdown:164` exactly).
- Menu entries: 0 / 15 / 30 / 45 / 60 / 90 / 120 minutes, each
  rendered as `"$min min"`.
- Selecting an entry calls a new
  `SettingsViewModel.setDefaultDurationMinutes(...)`, which
  forwards to `UserPreferences.setDefaultDurationMinutes(...)`.

## Implementation outline

New files: none.

Modified files:

```
ui/settings/UserPreferences.kt      ; +KEY_DEFAULT_DURATION_MIN,
                                    ; +defaultDurationMinutes field,
                                    ; +setDefaultDurationMinutes()
ui/settings/SettingsScreen.kt       ; +DefaultDurationRow in General
                                    ; section (inline, mirroring
                                    ; DefaultSnoozeDropdown)
ui/settings/SettingsViewModel.kt    ; +setDefaultDurationMinutes(...)
                                    ; passthrough to UserPreferences
ui/eventedit/EventEditViewModel.kt  ; EventEditFormState gains
                                    ; defaultDurationMinutes and
                                    ; convertedFromAllDay; Factory
                                    ; accepts defaultDurationMinutes
                                    ; and forwards into the state
ui/eventedit/EventEditScreen.kt     ; Factory call passes
                                    ; appViewModel.prefs.value
                                    ;   .defaultDurationMinutes
ui/eventedit/EventForm.kt           ; allDay toggle handler at
                                    ; line 64 gains the one-shot
                                    ; conversion logic described
                                    ; above
res/values/strings.xml              ; +settings_default_duration
```

## Strings

```xml
<string name="settings_default_duration">Default event duration</string>
```

Menu entries use the same raw-minutes labels as the snooze
dropdown (`"$min min"`); no per-option string resources.

## Accessibility

- The dropdown row inherits full TalkBack support from the
  Material 3 `Row` + `ExposedDropdownMenuBox` it reuses (same as
  the snooze dropdown). No additional semantic work needed.
- The setting label is descriptive enough on its own; no
  `contentDescription` override.

## Testing

Unit tests:

- `UserPreferencesTest`: round-trip default (60), each option
  value (0 / 15 / 30 / 45 / 90 / 120), and a missing key reads
  back as 60.
- `EventEditFormStateTest`: given
  `defaultDurationMinutes = 30`,
  `endTime - startTime == 30 minutes`. Toggling all-day on and
  back off seeds `endTime - startTime == defaultDurationMinutes`
  exactly once; a second toggle-on-then-off preserves any
  user-edited duration (regression test for the one-shot flag).
- No unit test for FAB or Month-cell paths (composition + state
  glue); covered by manual verification.

## Verification

For the implementer to confirm the spec landed:

1. `./gradlew :app:lintDebug :app:testDebugUnitTest` green.
2. Fresh install per the CLAUDE.md test-fresh-install rule.
3. Open Settings > General. The new "Default event duration" row
   appears below "Default view" and above the Tasks toggle. The
   trailing label reads "60 min".
4. FAB-create from Month view. Editor opens with start =
   `nextRoundHour()`, end = start + 60 min. Save. Event lasts
   one hour.
5. Change the setting to 30 min. FAB-create again. Editor opens
   with end = start + 30 min.
6. Change to 0. FAB-create. Editor opens with end == start. Save
   path accepts the point-in-time event.
7. Toggle "All day" inside the editor, then back off. The form
   re-seeds end = start + setting value via the one-shot
   conversion. Manually edit end to four hours later. Toggle
   all-day on and off again. The manually-set four-hour duration
   is preserved (one-shot flag did its job).
8. Open an existing 2-hour event for edit. The form shows the
   real 2-hour span; the setting does not retroactively
   shrink it.
9. Kill the app, relaunch. Setting persists.

## Open questions

- Label style in the dropdown: raw minutes (matches snooze) or
  human-friendly "1 hour" / "1.5 hours" / "2 hours" for the
  larger values. Default: raw minutes for consistency. Revisit
  if user-test feedback prefers human-friendly.
- Whether to keep the 0-minute option in the menu or hide it
  behind a future "Custom" affordance. Keep for v1; cheap and
  symmetric.

## Out of scope, captured for future specs

- Empty-slot tap-to-create on Day / Week timelines. Gesture does
  not exist today; warrants its own spec. That spec should make
  the new gesture respect this default-duration setting.
- Speedy-meetings / shorten-appointments auto-trim. Low priority
  for consumer calendar.
- Per-calendar default duration.
- "Custom" arbitrary-minutes entry in the dropdown.
- "Match last event duration" / heuristic defaults.
