# Design: color customization expansion

Status: Draft.
Target release: next user-visible release after the post-M4
custom-colors + duration ship (`604bf39`).

## Context

The post-M4 colors work shipped per-account avatar and per-calendar
overrides from a single hardcoded 8-swatch Okabe-Ito palette. Three
follow-ups were queued: an additional curated palette, a settings
switcher to pick between palettes, and per-event color override.
This spec covers all three plus a CI-enforced contrast guard. The
hex / HSV picker queued alongside is intentionally deferred
(rationale below).

Web research informs the scope:

- Per-event color is supported by most mainstream calendar apps,
  and open user requests for it exist on several FOSS calendar
  projects. It is table stakes for a calendar that aims at parity
  with mainstream and FOSS peers.
- Radix Colors step 9 is the only widely-used palette system
  explicitly designed for single-token light / dark parity with
  verified contrast. Tailwind 500 explicitly recommends per-mode
  swapping. Material 3 tonal palettes are theming primitives, not
  categorical labels. Carbon's accent rule splits 10-50 (light)
  vs 60-100 (dark).
- The recurring user complaint about fixed palettes is "palette
  too small," not "I want a wheel." A 10-swatch curated palette
  absorbs that demand; the hex / HSV picker is deferred until a
  swatch palette proves too limiting.

## Goal

- Let users choose between two built-in palettes (Okabe-Ito or
  Radix) for the recolor pickers, with Okabe-Ito as the default
  so existing installs see no visual change.
- Let users override the color of any event (not just calendar /
  account), with the override applying to every instance of a
  recurring event.
- Guarantee at build time that every swatch in the new palette
  clears WCAG 2.1 1.4.11 (>=3:1 non-text contrast) against the
  base surface in *at least one* theme mode (light or dark).
  The "options, not policing" framing: users typically pick one
  theme mode and then pick colors that work for that mode; an
  amber that looks great in dark mode is a fine swatch to offer
  even if it washes out in light mode. The guarantee is that
  every swatch has a home mode where it works, not that every
  swatch works everywhere. The 4.5:1 text threshold (1.4.3) does
  not apply: chip text renders in `onSurface`, not the swatch.

## Explicit non-goals

- Free hex / HSV picker. The two curated palettes cover the
  recurring demand. Revisit if users specifically request a
  wheel.
- Recolor entry on the event detail sheet (long-press the accent
  strip). Editor row is the only entry point for v1.
- Writing per-event overrides into `CalendarContract.Events`
  (`eventColor` field). Overrides stay in DataStore so sync
  adapters cannot clobber them and so the override works on
  read-only synced calendars.
- Per-palette WCAG verification of the Okabe-Ito palette. Its
  yellow famously fails 4.5:1 on light backgrounds; it is
  retained as a color-blind-safety option, documented in a
  KDoc on `OkabeItoPalette`.
- APCA contrast. Revisit when AndroidX adds first-class APCA
  support.
- A migration that re-keys existing saved override hexes when
  the palette changes. Saved overrides keep their hex; the
  picker surfaces them via a "Custom" pip (see below).

## Prior art consulted

- Mainstream calendar apps: a fixed set of preset event colors
  plus a "Default" that inherits from the calendar.
- Per-event recolor is common across mainstream calendar apps.
- Open user requests for per-event color exist on several FOSS
  calendar projects.
- Radix Colors documentation: step 9 is designed for
  single-token solid backgrounds with verified APCA contrast.

No off-the-shelf Material 3 pattern for palette-switching exists.
The convention picked here mirrors the existing settings
dropdown pattern (`DefaultDurationDropdown`,
`DefaultSnoozeDropdown`): `ExposedDropdownMenuBox` +
`OutlinedTextField`.

## Interaction rules

### Palette switcher

- Settings -> Appearance -> Color palette dropdown.
- Two options: Okabe-Ito (color-blind safe) and Radix.
- Default: Okabe-Ito. Existing installs see no visual change.
- Switching the palette never mutates saved override hexes.

### Per-event recolor

- The event editor (`EventForm`) gains a new "Color" row,
  slotted between the calendar dropdown and the recurrence
  section.
- The row shows a 28dp swatch (the resolved current color), a
  "Color" label, and a trailing "Default" or "Custom" text.
- Tapping the row opens the shared `RecolorDialog` with the
  active palette's swatches plus a "Reset to calendar color"
  text button in the dismiss slot.
- The row is always visible, including when editing an event in
  a read-only synced calendar. Overrides are app-local; write
  access to the provider is irrelevant.
- Saving an event with a picked color writes the override to
  DataStore after the save succeeds and the event ID is known.
  Picking "Reset to calendar color" clears the entry from the
  override map.

### Recolor picker on palette mismatch

- When the currently saved hex is not present in the active
  palette's swatch list, the `ColorSwatchGrid` renders the
  active palette's swatches and appends a small extra "pip"
  filled with the saved hex, labelled "Custom".
- Picking any palette swatch replaces the override; the pip
  disappears on next open.
- Non-destructive: switching palettes never quietly mutates
  saved data.

## Storage model

Two new `UserPrefs` fields:

```kotlin
val paletteId: PaletteId
val eventColorOverrides: Map<Long, Int>
```

Two new DataStore keys, following the existing pattern in
`UserPreferences.kt`:

```kotlin
val KEY_PALETTE_ID = stringPreferencesKey("palette_id")
val KEY_EVENT_COLOR_OVERRIDES =
    stringPreferencesKey("event_color_overrides")
```

- `PaletteId` persisted as its `.name` string, matching the
  `ThemeMode` / `StorageMode` / `CalendarView` precedent
  already in this file. Decode failures fall back to
  `PaletteId.OkabeIto`.
- `eventColorOverrides` JSON-encoded via the existing
  `MapSerializer(Long.serializer(), Int.serializer())` pattern
  used for `calendarColorOverrides`. JSON's Long-as-string key
  quirk is handled by the existing decoder. Decode failures log
  via `Timber.w` like the existing override maps.
- Override is keyed by `eventId` (not `instanceId`) so a
  per-event override applies to every instance of a recurring
  event.

## Render path

### Palette enum

```kotlin
enum class PaletteId(
    val swatches: List<Color>,
    @StringRes val labelRes: Int,
) {
    OkabeIto(OkabeItoPalette, R.string.palette_okabe_ito),
    Radix(RadixSolidPalette, R.string.palette_radix),
}
```

Lives in `ui/theme/Palette.kt`. Adding a third palette is one
enum entry plus one swatch list constant.

### Radix swatches

Hue-ordered, 11 entries. Each clears 3:1 against the base
surface in at least one theme mode.

| Slot | Name | Hex | Home mode |
|---|---|---|---|
| 1 | Tomato 9 | `#E54D2E` | both |
| 2 | Orange 9 | `#F76B15` | dark |
| 3 | Amber 9 | `#FFB224` | dark |
| 4 | Grass 9 | `#46A758` | dark |
| 5 | Teal 9 | `#12A594` | dark |
| 6 | Cyan 9 | `#05A2C2` | dark |
| 7 | Indigo 9 | `#3E63DD` | light |
| 8 | Violet 9 | `#6E56CF` | light |
| 9 | Pink 9 | `#D6409F` | both |
| 10 | Crimson 9 | `#E93D82` | both |
| 11 | Slate 9 | `#687076` | dark |

### Override stack

Replace `applyCalendarColorOverrides(overrides)` on
`List<EventItem>` with:

```kotlin
fun List<EventItem>.applyColorOverrides(
    calendarOverrides: Map<Long, Int>,
    eventOverrides: Map<Long, Int>,
): List<EventItem>
```

Precedence: `eventOverrides[eventId] ?:
calendarOverrides[calendarId] ?: displayColor`. Single pass.
Early-return `this` when both maps are empty. The old
extension stays one release as a `@Deprecated` thin delegate.

Five view models thread the new map alongside the existing one:
Month, Week, Day, Schedule, Search.

### Risk extraction

`AppViewModelSheetState.openEventDetail` currently inlines the
calendar override resolution. The same lookup must now also
honor per-event overrides. Extract to a pure helper in
`data/EventDetail.kt`:

```kotlin
fun resolveEventDetailColor(
    detail: EventDetail,
    eventOverrides: Map<Long, Int>,
    calendarOverrides: Map<Long, Int>,
): Int
```

Unit-test this directly. The detail-sheet ViewModel call site
then becomes a one-line delegation.

### Reminder notifications

`resolveReminderColor(providerColor, calendarId, overrides)` is
extended to
`resolveReminderColor(providerColor, eventId, calendarId,
eventOverrides, calendarOverrides)`. Caller in
`ReminderAlarmReceiver` passes `prefs.eventColorOverrides` and
the event ID.

### WCAG guard

A pure-JVM unit test
(`RadixPaletteContrastTest`) asserts every Radix swatch hits
>=3:1 (WCAG 2.1 criterion 1.4.11, non-text UI component
contrast) against the base surface in *at least one* theme
mode. Bases are `#FFFBFE` (light) and `#211F26` (dark, lifted
per `Theme.kt::withLiftedDarkSurfaces`). A swatch that fails
in BOTH modes is broken and must be dropped.

The reframing is intentional: a single-hex palette tuned for
both light and dark mode chip surfaces is structurally
impossible without per-mode color tokens (the override storage
keys on hex, not slot, so changing this would break existing
overrides). Each swatch having a "home mode" is the honest
guarantee; users in light mode will naturally pick light-mode-
friendly colors and vice versa.

The 4.5:1 text threshold (1.4.3) is deliberately not applied:
chip text always uses `onSurface` (Material 3 managed), so the
swatch is a UI accent (3dp stripe, alpha-tinted block, drawer
dot), not a text background.

A new `WcagContrast` helper (sRGB -> linear -> relative
luminance -> `(L1+0.05)/(L2+0.05)`) sits in
`ui/theme/WcagContrast.kt` for the test (and any future use).
Pure-int math, no Android deps.

Okabe-Ito is intentionally not asserted. Its yellow's ~1.6:1
light-mode ratio is documented in a top-of-file KDoc on
`OkabeItoPalette` as the color-blind-safety tradeoff.

## Implementation outline

New files:

```
ui/theme/WcagContrast.kt                          ; relative-luminance helper
test/ui/theme/WcagContrastTest.kt                 ; helper sanity test
test/ui/theme/RadixPaletteContrastTest.kt         ; build-time palette guard
test/data/EventDetailColorTest.kt                 ; precedence test for the
                                                  ; extracted resolver
```

Modified files (grouped by sub-feature):

Palette + swatch grid
- `ui/theme/Palette.kt` (add `RadixSolidPalette`, `PaletteId`)
- `ui/calendars/ColorSwatchGrid.kt` (accept `palette` +
  `currentArgb` for the Custom pip)
- `ui/calendars/RecolorDialog.kt` (forward `palette` and
  `currentArgb`; add "Reset" text button in dismiss slot)
- `ui/month/CalendarDrawer.kt` (pass active `PaletteId` into
  the two recolor call sites)

Settings switcher + persistence
- `ui/settings/UserPreferences.kt` (`paletteId`,
  `eventColorOverrides`, keys, setters)
- `ui/settings/SettingsScreen.kt` (new `PaletteRow` after
  `ThemeRow`)
- `ui/settings/SettingsViewModel.kt` (`setPaletteId(...)`)
- `res/values/strings.xml` (palette labels, color field label,
  reset action, Custom pip label)

Override stack
- `data/EventItem.kt` (`applyColorOverrides`, deprecate old)
- `data/EventDetail.kt` (`resolveEventDetailColor` helper)
- `AppViewModel.kt` (`eventColorOverridesFlow`,
  `setEventColorOverride`)
- `AppViewModelSheetState.kt` (call extracted resolver)
- `ui/month/MonthViewModel.kt`
- `ui/week/WeekViewModel.kt`
- `ui/day/DayViewModel.kt`
- `ui/schedule/ScheduleViewModel.kt`
- `ui/search/SearchViewModel.kt`
- `AppShell.kt` (Factory wiring of the new flow)
- `notifications/ReminderNotification.kt`
  (`resolveReminderColor` signature)
- `notifications/ReminderAlarmReceiver.kt` (pass through)

Editor row
- `ui/eventedit/EventEditViewModel.kt` (`colorOverrideArgb` in
  form state, init load, `setColorOverride`)
- `ui/eventedit/EventForm.kt` (new `ColorRow`)
- `ui/eventedit/EventEditScreen.kt` (post-save override write)

Test additions
- `test/data/EventItemColorOverrideTest.kt` (precedence)
- `test/notifications/ReminderColorOverrideTest.kt`
  (precedence)

## Strings

```xml
<string name="palette_okabe_ito">Okabe-Ito (color-blind safe)</string>
<string name="palette_radix">Radix</string>
<string name="settings_palette_label">Color palette</string>
<string name="field_color">Color</string>
<string name="field_color_default">Default</string>
<string name="field_color_custom">Custom</string>
<string name="action_reset_to_calendar_color">Reset to calendar color</string>
<string name="swatch_custom">Custom color</string>
```

## Accessibility

- The new Color row in the editor has a clear text label and
  trailing state ("Default" / "Custom"), so the color picker is
  reachable from TalkBack without relying on the swatch tint.
- Radix step 9 swatches clear WCAG 1.4.11 (>=3:1, non-text UI
  contrast) against the base surface in at least one theme mode;
  the unit test guards this. The single-hex constraint (per-mode
  override storage would break existing overrides) makes "every
  swatch in every mode" structurally impossible; "at least one
  home mode per swatch" is the honest guarantee.
- The "Custom" pip carries a content description naming the
  saved hex.
- WCAG 1.4.1: the editor row pairs the swatch with a text label;
  the chips and detail sheet pair color with the event title.
  Color is never the sole signal.

## Testing

Unit tests:

- `WcagContrastTest`: round-trips known fixtures (black on white
  = 21:1, etc.).
- `RadixPaletteContrastTest`: every Radix swatch hits >=3:1 (WCAG
  1.4.11 non-text UI contrast) against the base surface in at
  least one theme mode.
- `EventItemColorOverrideTest` (extend): event > calendar
  precedence; recurring event uses `eventId`; empty maps
  short-circuit.
- `EventDetailColorTest`: same precedence rules against the
  extracted resolver.
- `ReminderColorOverrideTest` (extend): event > calendar >
  provider precedence.

Manual verification is covered under Verification.

## Verification

1. `./gradlew :app:lintDebug :app:testDebugUnitTest` green,
   including the new contrast test.
2. `./gradlew :app:assembleRelease` clean (R8 + resource
   shrink), confirming the new enum + strings shrink cleanly.
3. Fresh install per project convention:
   `adb uninstall com.arishawke.asala.calendar` then
   `./gradlew :app:installDebug`.
4. Onboarding -> grant calendar permission.
5. Settings -> Appearance -> Color palette: switch to Radix,
   open a recolor dialog from the drawer or the new editor row,
   confirm the swatches change. Switch back, confirm Okabe-Ito.
6. Save a per-calendar override using Radix Indigo. Switch
   palette to Okabe-Ito. Reopen the recolor dialog: confirm a
   "Custom" pip filled Indigo appears alongside the Okabe-Ito
   swatches.
7. Open the event editor on a non-recurring event in a writable
   calendar. Confirm the new Color row defaults to "Default".
   Pick a swatch. Save. Confirm the chip renders in the new
   color on Month, Week, Day, Schedule, in the detail sheet, in
   Search results, and on the next reminder notification.
8. Create a recurring event with a per-event color. Confirm all
   instances render in the override color.
9. Edit a per-event override; pick "Reset to calendar color".
   Confirm the chip falls back to the calendar color.
10. Test on a synced (read-only) calendar's event: confirm the
    Color row is visible, the override applies in-app, and
    `adb shell content query --uri content://com.android.calendar/events`
    shows the provider's `eventColor` column untouched.

## Open questions

None at design close. The plan-mode review surfaced four
candidate questions; reasonable defaults were picked:

- Color row visible on read-only synced calendars (overrides
  are app-local; write access irrelevant).
- Color is a non-blocking save field (matches the reminder
  picker's behavior).
- AMOLED `#000000` is included as a fifth dark surface in the
  WCAG test (futureproofs the AMOLED variant; one extra line).
- The "Custom" pip on palette mismatch is the chosen behavior
  (non-destructive, honest about state).

## Out of scope, captured for future specs

- Hex / HSV custom color picker. Use
  `github.com/skydoves/colorpicker-compose` (v1.1.4 on
  2026-05-03, Apache 2.0) when revisited.
- Recolor entry on the event detail sheet (long-press accent
  strip).
- Consolidating the four-way `apply...Overrides` wiring across
  Month / Week / Day / Schedule view models. Acceptable as
  duplication today; revisit if a fifth view model appears.
- APCA contrast verification (AndroidX support pending).

## Key references

- Radix Colors documentation:
  https://www.radix-ui.com/colors
- skydoves/colorpicker-compose (deferred picker):
  https://github.com/skydoves/colorpicker-compose
- WCAG 2.1 contrast minimum (1.4.3):
  https://www.w3.org/WAI/WCAG21/Understanding/contrast-minimum.html
