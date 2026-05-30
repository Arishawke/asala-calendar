# Color customization expansion implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use
> `superpowers:subagent-driven-development` (recommended) or
> `superpowers:executing-plans` to implement this plan
> task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship the three queued color customization follow-ups
(a curated second palette, a Settings palette switcher, and
per-event color override) plus a CI-enforced contrast guard.

**Spec reference:**
[docs/specs/2026-05-25-color-customization-expansion-design.md](../specs/2026-05-25-color-customization-expansion-design.md)

**Branch:** `feat/color-customization-expansion`

**Suggested commit split:** two logical commits.

1. `feat(theme): radix palette, palette switcher, wcag contrast guard`
2. `feat(events): per-event color override`

---

## Task 1: WcagContrast helper + sanity test

**Files:**

- Create: `app/src/main/kotlin/com/arishawke/asala/calendar/ui/theme/WcagContrast.kt`
- Create: `app/src/test/kotlin/com/arishawke/asala/calendar/ui/theme/WcagContrastTest.kt`

- [ ] **1.1** Add `WcagContrast` object with `ratio(argbA: Int,
      argbB: Int): Double`. sRGB -> linear -> relative luminance
      -> `(L1+0.05)/(L2+0.05)`. Pure-int math, no Android deps.
- [ ] **1.2** Sanity test: black on white = 21.0, white on white
      = 1.0, mid-gray on white = ~5.74. Round-trip symmetry
      (`ratio(a, b) == ratio(b, a)`).
- [ ] **1.3** Run `./gradlew :app:testDebugUnitTest --tests
      WcagContrastTest`. Expect green.

## Task 2: PaletteId enum + RadixSolidPalette

**Files:**

- Modify: `app/src/main/kotlin/com/arishawke/asala/calendar/ui/theme/Palette.kt`
- Modify: `app/src/main/res/values/strings.xml`

- [ ] **2.1** Add `RadixSolidPalette: List<Color>` with the 10
      hue-ordered swatches from the spec.
- [ ] **2.2** Add `enum class PaletteId(val swatches: List<Color>,
      @StringRes val labelRes: Int)` with `OkabeIto` and `Radix`
      entries.
- [ ] **2.3** Add top-of-file KDoc on `OkabeItoPalette` noting
      the intentional yellow contrast tradeoff for color-blind
      safety.
- [ ] **2.4** Add string resources: `palette_okabe_ito`,
      `palette_radix`.

## Task 3: RadixPaletteContrastTest (build-time guard)

**Files:**

- Create: `app/src/test/kotlin/com/arishawke/asala/calendar/ui/theme/RadixPaletteContrastTest.kt`

- [ ] **3.1** Assert every Radix swatch hits >=3:1 (WCAG 1.4.11
      non-text UI component contrast) against the base
      surface in *at least one* theme mode. Bases: `#FFFBFE`
      (light), `#211F26` (dark, lifted). Swatches that fail
      both modes are broken; passing one is the guarantee. See
      spec WCAG guard rationale.
- [ ] **3.2** Test failure message names the swatch hex, the
      surface hex, and the computed ratio so a future drift is
      diagnosable.
- [ ] **3.3** Run `./gradlew :app:testDebugUnitTest --tests
      RadixPaletteContrastTest`. Expect green.

## Task 4: UserPreferences extension

**Files:**

- Modify: `app/src/main/kotlin/com/arishawke/asala/calendar/ui/settings/UserPreferences.kt`

- [ ] **4.1** Add `paletteId: PaletteId` and
      `eventColorOverrides: Map<Long, Int>` to `UserPrefs` (both
      with sensible defaults: `OkabeIto`, empty map).
- [ ] **4.2** Add DataStore keys: `KEY_PALETTE_ID =
      stringPreferencesKey("palette_id")`, `KEY_EVENT_COLOR_OVERRIDES
      = stringPreferencesKey("event_color_overrides")`.
- [ ] **4.3** Add `setPaletteId(...)` writing `.name`.
- [ ] **4.4** Add `setEventColorOverride(eventId: Long, color:
      Int?)` mirroring the existing
      `setCalendarColorOverride` shape (read map, mutate, write
      JSON, log `Timber.w` on decode failure).
- [ ] **4.5** Extend the `prefs` flow mapper to hydrate the two
      new fields. `paletteId` decode failure -> `OkabeIto`.

## Task 5: Parameterize ColorSwatchGrid + RecolorDialog

**Files:**

- Modify: `app/src/main/kotlin/com/arishawke/asala/calendar/ui/calendars/ColorSwatchGrid.kt`
- Modify: `app/src/main/kotlin/com/arishawke/asala/calendar/ui/calendars/RecolorDialog.kt`
- Modify: `app/src/main/res/values/strings.xml`

- [ ] **5.1** `ColorSwatchGrid`: add `palette: PaletteId` param.
      Iterate `palette.swatches` instead of hardcoded
      `OkabeItoPalette`.
- [ ] **5.2** `ColorSwatchGrid`: render a "Custom" pip (small
      swatch) when `selectedArgb` is not in `palette.swatches`.
      Content description = "Custom color".
- [ ] **5.3** `RecolorDialog`: add `palette: PaletteId` param;
      forward to `ColorSwatchGrid`.
- [ ] **5.4** `RecolorDialog`: add a "Reset to calendar color"
      `TextButton` in the dismiss slot. Visible only when the
      dialog is given an `onReset: (() -> Unit)?` callback; null
      hides the button (the existing drawer call sites pass
      null; the new editor row passes a real callback).
- [ ] **5.5** Add strings: `field_color`, `field_color_default`,
      `field_color_custom`, `action_reset_to_calendar_color`,
      `swatch_custom`, `settings_palette_label`.

## Task 6: Wire palette into CalendarDrawer call sites

**Files:**

- Modify: `app/src/main/kotlin/com/arishawke/asala/calendar/ui/month/CalendarDrawer.kt`

- [ ] **6.1** Read `paletteId` from `appViewModel.prefs.value`
      (or via a collected state in the parent screen if that is
      the established pattern) and pass into both `RecolorDialog`
      call sites (account avatar recolor + calendar row recolor).

## Task 7: Settings palette switcher

**Files:**

- Modify: `app/src/main/kotlin/com/arishawke/asala/calendar/ui/settings/SettingsScreen.kt`
- Modify: `app/src/main/kotlin/com/arishawke/asala/calendar/ui/settings/SettingsViewModel.kt`

- [ ] **7.1** Add `PaletteRow` composable using the
      `ExposedDropdownMenuBox` + `OutlinedTextField` pattern
      established by `DefaultDurationDropdown`. Insert in the
      Appearance section, after `ThemeRow`.
- [ ] **7.2** `SettingsViewModel`: expose `paletteId` in state
      and `setPaletteId(...)` action; seed in `initialValue`.

## Task 8: applyColorOverrides + 5 view models + Factory wiring

**Files:**

- Modify: `app/src/main/kotlin/com/arishawke/asala/calendar/data/EventItem.kt`
- Modify: 5 view models under `ui/month`, `ui/week`, `ui/day`,
  `ui/schedule`, `ui/search`.
- Modify: `app/src/main/kotlin/com/arishawke/asala/calendar/AppShell.kt`
  (or wherever the Factories are wired).
- Modify: `app/src/test/kotlin/com/arishawke/asala/calendar/data/EventItemColorOverrideTest.kt`

- [ ] **8.1** Add `applyColorOverrides(calendarOverrides,
      eventOverrides)`. Precedence: `eventOverrides[eventId] ?:
      calendarOverrides[calendarId] ?: displayColor`. Early-return
      `this` when both maps empty.
- [ ] **8.2** Mark old `applyCalendarColorOverrides` as
      `@Deprecated("Use applyColorOverrides")`, delegate to the
      new one with empty event map.
- [ ] **8.3** Update each of MonthViewModel, WeekViewModel,
      DayViewModel, ScheduleViewModel, SearchViewModel: accept
      `eventColorOverridesFlow`, replace
      `applyCalendarColorOverrides(...)` with
      `applyColorOverrides(...)`, update Factory signatures.
- [ ] **8.4** Update `AppShell.kt` (or equivalent wire-up site)
      to pass the new flow into each Factory.
- [ ] **8.5** Add tests to `EventItemColorOverrideTest`:
      event > calendar; recurring event uses `eventId`; empty
      maps short-circuit.

## Task 9: AppViewModel additions

**Files:**

- Modify: `app/src/main/kotlin/com/arishawke/asala/calendar/AppViewModel.kt`

- [ ] **9.1** Add `eventColorOverridesFlow: StateFlow<Map<Long,
      Int>>` mirroring `calendarColorOverridesFlow` exactly
      (same scope, same `WhileSubscribed(5_000)`, same
      `initialValue` shape).
- [ ] **9.2** Add `setEventColorOverride(eventId: Long, color:
      Int?)` delegating to `userPreferences.setEventColorOverride(...)`.

## Task 10: Extract resolveEventDetailColor

**Files:**

- Modify: `app/src/main/kotlin/com/arishawke/asala/calendar/data/EventDetail.kt`
- Modify: `app/src/main/kotlin/com/arishawke/asala/calendar/AppViewModelSheetState.kt`
- Create: `app/src/test/kotlin/com/arishawke/asala/calendar/data/EventDetailColorTest.kt`

- [ ] **10.1** Add `fun resolveEventDetailColor(detail:
      EventDetail, eventOverrides: Map<Long, Int>,
      calendarOverrides: Map<Long, Int>): Int`. Same precedence
      as the list extension.
- [ ] **10.2** `AppViewModelSheetState.openEventDetail`: call the
      extracted resolver, passing both override maps.
- [ ] **10.3** New `EventDetailColorTest`: event > calendar;
      calendar > default; no override returns
      `detail.displayColor` unchanged.

## Task 11: Editor Color row + EventEditViewModel + save

**Files:**

- Modify: `app/src/main/kotlin/com/arishawke/asala/calendar/ui/eventedit/EventEditViewModel.kt`
- Modify: `app/src/main/kotlin/com/arishawke/asala/calendar/ui/eventedit/EventForm.kt`
- Modify: `app/src/main/kotlin/com/arishawke/asala/calendar/ui/eventedit/EventEditScreen.kt`

- [ ] **11.1** `EventEditFormState`: add `colorOverrideArgb:
      Int?` (null = follow calendar). Add
      `setColorOverride(Int?)`.
- [ ] **11.2** `EventEditViewModel`: snapshot the existing
      override from `eventColorOverridesFlow` at init when
      `editingEventId != null`. The Factory takes a snapshot
      `eventColorOverrideArgb: Int?` (mirroring how
      `defaultDurationMinutes` is already snapshotted).
- [ ] **11.3** `EventForm`: new `ColorRow` composable between
      `CalendarDropdown` and `RecurrenceSection`. Row shows a
      28dp swatch + "Color" label + trailing "Default" or
      "Custom" text. Tap opens `RecolorDialog` with the active
      palette + saved color + a real `onReset` callback that
      clears the override.
- [ ] **11.4** `EventEditScreen`: on successful save, once the
      new event ID is known, call
      `appViewModel.setEventColorOverride(eventId,
      formState.colorOverrideArgb)`. Existing-event edit path
      same flow, just with the known event ID.
- [ ] **11.5** Confirm color stays out of `EventDraft` and is
      not written to `CalendarContract`.

## Task 12: Reminder notification path

**Files:**

- Modify: `app/src/main/kotlin/com/arishawke/asala/calendar/notifications/ReminderNotification.kt`
- Modify: `app/src/main/kotlin/com/arishawke/asala/calendar/notifications/ReminderAlarmReceiver.kt`
- Modify: `app/src/test/kotlin/com/arishawke/asala/calendar/notifications/ReminderColorOverrideTest.kt`

- [ ] **12.1** Extend `resolveReminderColor(providerColor,
      eventId, calendarId, eventOverrides, calendarOverrides):
      Int`. Precedence event > calendar > provider.
- [ ] **12.2** `ReminderAlarmReceiver`: pass
      `prefs.eventColorOverrides` and the event ID into
      `resolveReminderColor`.
- [ ] **12.3** Add precedence tests to
      `ReminderColorOverrideTest` (event > calendar > provider;
      empty maps; missing event override falls back to calendar).

## Task 13: Static analysis hygiene

- [ ] **13.1** `./gradlew :app:spotlessApply` clean.
- [ ] **13.2** Detekt baseline: if new long-parameter-list /
      cyclomatic-complexity entries land, regenerate via
      `./gradlew :app:detektBaselineDebug` (or whichever task
      the project uses). Inspect baseline diff before commit.

## Task 14: CHANGELOG + ROADMAP

**Files:**

- Modify: `CHANGELOG.md`
- Modify: `docs/ROADMAP.md`

- [ ] **14.1** Add entries under `[Unreleased]`:
      - Added: per-event color override, Radix palette, palette
        switcher, "Custom" pip indicator.
      - Changed: `applyCalendarColorOverrides` ->
        `applyColorOverrides`.
- [ ] **14.2** `ROADMAP.md`: move "Color palette expansion"
      follow-ups to "Recently shipped"; keep hex/HSV picker as
      the only remaining color customization item under "Later".

## Task 15: Verification

- [ ] **15.1** `./gradlew :app:lintDebug :app:testDebugUnitTest`
      fully green.
- [ ] **15.2** `./gradlew :app:assembleRelease` clean.
- [ ] **15.3** Fresh-install device check (per project
      convention):
      - `adb uninstall com.arishawke.asala.calendar`
      - `./gradlew :app:installDebug`
- [ ] **15.4** Manual checks per the spec's Verification
      section (palette switch, "Custom" pip on mismatch,
      per-event override across all surfaces, reset action,
      synced-calendar safety).

## Task 16: Commit + PR + merge + archive

- [ ] **16.1** Stage and commit by logical split (theme +
      switcher + guard; per-event override).
- [ ] **16.2** Push branch, open PR, wait for CI green.
- [ ] **16.3** FF push `main` from local per the project's
      branch hygiene rule.
- [ ] **16.4** Archive the spec to
      `docs/specs/archive/2026-05-25-color-customization-expansion-design.md`.
- [ ] **16.5** Archive this plan to
      `docs/plans/archive/2026-05-25-color-customization-expansion.md`.
