# Spec: UI polish pass

Date: 2026-05-26.
Status: design.

## Problem

Asala's feature accretion phase is largely closed out (M4 reminders +
drag-to-reschedule, custom colors expansion, now-line + drawer-hide,
header dropdowns). What's left is visible drift across surfaces:

- Three event chip implementations (`EventChip`, `EventListRow`,
  `EventBlock`) render the same logical thing three different ways.
- Two now-line implementations: `NowLineMarker` in the timeline grid,
  a separate `NowLineRow` in Schedule.
- Four ViewModels each roll their own merge of calendar + event
  color overrides.
- Settings is a 999-line flat scroll across `SettingsScreen.kt` plus
  the `SettingsRows` / `SettingsRowPrimitives` layer; sections are
  not visually grouped.
- Theme layer ships M3 defaults only. No design tokens, no typography
  overlay. Hard-coded `dp` values live across composables.

The maintainer's named pain point is Settings; the cohesion items are
secondary but worth folding in while the surface is open.

## Goals

- Close the polish gap between Asala and top commercial calendar apps,
  prioritizing "makes sense on first glance" over visual novelty.
- Make Settings scannable: grouped sections, leading icons, secondary
  descriptions, scroll position survives recompose.
- One event-chip primitive with three layout variants for Month /
  Schedule / Week+Day. Color resolution, truncation, and past-date
  opacity policies share a single implementation.
- One now-line implementation across the timeline views.
- Design tokens (`Spacing`, `Typography`) replacing ad-hoc dp + the
  M3 default `Typography`.
- Maintain the accessibility floor: WCAG 2.2 AA contrast, 48dp Android
  touch targets, system font scale honored, reduce-motion honored.

## Non-goals

- Schedule view recomposition (sparser agenda direction). Deferred to
  its own spec.
- Editor field reorg (account-then-calendar picker). Already a
  roadmap item; out of scope here.
- All-day cohesion across Day and Schedule. Deferred; requires
  bucketer parameterization that is larger than this pass.
- Custom typeface. Would push toward distinct identity; ruled out in
  favor of platform-native polish.
- Motion overhaul. M3 defaults stay.
- Material 3 Expressive adoption.

## Design principles

- Platform-native polish. Stay on M3, system font, M3 motion. Polish
  comes from hierarchy and spacing, not from departing from M3.
- Informational color. Calendar color identifies which calendar; it
  never decorates. Okabe-Ito remains default for color-blind safety.
- First-glance clarity. Every Settings section answers "what is this
  for" via its header and "what does this row control" via secondary
  text.
- Cohesion via shared primitives. Three event-chip variants are three
  faces of one composable.

## §1 - Design tokens

Two new files under
`app/src/main/kotlin/com/arishawke/asala/calendar/ui/theme/`:

**`Spacing.kt`** - named scale on the M3 8dp grid:

    object Spacing {
        val xs = 4.dp
        val sm = 8.dp
        val md = 12.dp
        val lg = 16.dp
        val xl = 24.dp
        val xxl = 32.dp
    }

Pure Kotlin object. CompositionLocal pattern is canonical but overkill
for a solo phone-only app with one window size class.

**`Typography.kt`** - M3 `Typography` overlay:

- Keep system font (Roboto / device default). No bundled `.ttf`. No
  Downloadable Fonts (the GMS provider phones home on first request
  and requires Play Services; disqualified for offline-first GPLv3).
- titleLarge / titleMedium: lineHeight 1.2x fontSize.
- bodyLarge / bodyMedium: lineHeight 1.45-1.5x fontSize.
- labelLarge: `FontWeight.Medium` (500). bodyMedium: `FontWeight.Normal`
  (400). Weight contrast distinguishes primary actions from supporting
  text.
- Wired through `Theme.kt` by passing
  `typography = AsalaTypography` to the existing `MaterialTheme(...)` call.

## §2 - Settings restructure

`ui/settings/SettingsScreen.kt`:

- Switch `Column(verticalScroll = rememberScrollState())` to
  `LazyColumn(state = rememberLazyListState())`. Fixes the latent
  scroll-reset bug where requesting notification permission triggers
  recomposition and bounces the scroll position to top.
- Six grouped sections in this order:
  1. General: default view, week starts on, default event duration,
     time format.
  2. Appearance: theme (System / Light / Dark), AMOLED black
     (conditional: visible when effective theme is Dark), color
     palette.
  3. Notifications: system permission status row (re-request if
     denied; supporting text reflects state), default snooze.
  4. Calendars & accounts: hidden accounts list with restore action
     (conditional `item { }` block; only rendered if any are hidden),
     DAVx5 sync pointer, storage mode, create local calendar.
  5. About: version + build, license (GPLv3), source link,
     acknowledgements.
- Section header: `Text` styled `titleSmall`,
  color = `colorScheme.primary`, padded `Spacing.lg` top +
  `Spacing.md` bottom.
- Each row: M3 `ListItem` with `leadingContent` (icon),
  `headlineContent` (label, `bodyLarge`), `supportingContent`
  (description, `bodyMedium`), `trailingContent` (toggle / dropdown
  / value / chevron).
- No per-section `Card` wrappers. Per-row `Card` wrappers are an
  aged Material 2 pattern. Card-per-section is Material 3 Expressive,
  which is out of scope here.
- `ui/settings/SettingsRows.kt` becomes a thin layer mapping existing
  toggle / dropdown row APIs onto `ListItem`. Existing callers keep
  their shape.

## §3 - Event visual primitive

New file `ui/components/EventVisual.kt` with three variants:

- `EventChipCompact`: Month grid. 4dp left color bar + truncated
  title in `bodySmall`. Replaces visual of `ui/components/EventChip.kt`.
  Day cell remains the 48dp+ touch target.
- `EventChipRow`: Schedule list. 8dp calendar color dot + start time
  (`labelSmall`) + title (`bodyMedium`). Row min-height 48dp. Replaces
  `ui/components/EventListRow.kt`.
- `EventChipBlock`: Week / Day timeline. Tinted background fill (~18%
  calendar color over `colorScheme.surface`) + 3dp left color bar +
  title + drag handle area. Block min-height 48dp. Replaces the
  visual content of `ui/week/EventBlock.kt` (drag gesture logic stays
  in `EventBlock.kt`; only the visual layer moves).

Module-private helpers in the same file:

- `resolveEventColor(event, calendarColor, calendarOverride,
  eventOverride) -> Color`: pure function.
- `truncatedTitle(title, maxLines) -> String`: single truncation
  policy.
- `pastDateAlpha(eventEnd, now) -> Float`: single past-date opacity
  policy.

Tests:

- New: `EventVisualVariantTest`: color resolution per variant
  (calendar default, calendar override, event override, past-date
  opacity).
- Existing: `EventItemColorOverrideTest`, `VisibilityAndOverridesTest`,
  `EventDetailColorTest`, `ReminderColorOverrideTest`,
  `WcagContrastTest`, `RadixPaletteContrastTest` keep passing.

## §4 - Week all-day visual refresh

Narrowly cohesify Week. Day-view all-day and Schedule all-day stay
unchanged.

- `ui/week/AllDayRow.kt` (59 lines) refreshed to render single-day
  all-day events using the same bar visual treatment as
  `MultiDayBarRow.kt`: rounded rectangle, calendar color background,
  contrasting foreground text. `WeekBucketer` + `MultiDayBarRow` are
  not refactored; a single-day all-day event already buckets as a
  1-cell segment.
- Day view: no all-day section is added in this spec.
- Schedule view: all-day rendering unchanged.

Deferred to a future spec: cross-view all-day cohesion. Requires
either parameterizing `WeekBucketer` to accept `columnCount: Int` so
Day view can have a 1-column all-day lane, or designing a
Schedule-specific bar composable that respects Schedule's list-of-rows
architecture rather than the bucketed-segment model.

## §5 - Now-line dedup + color resolver

Now-line dedup: `ui/timeline/NowLineMarker.kt` is the canonical
implementation. Schedule's `NowLineRow` becomes a thin wrapper that
calls `rememberNowMinutes()` and renders the same visual. Single
tick source, single visual treatment.

Color resolution: new file `data/CalendarColorResolver.kt`:

    object CalendarColorResolver {
        fun resolve(
            event: EventItem,
            calendarColor: Color,
            calendarOverride: Color?,
            eventOverride: Color?,
        ): Color = eventOverride ?: calendarOverride ?: calendarColor
    }

Pure function. ViewModels keep collecting override flows; they pass
snapshots to `resolve` per event. Internally
`EventItem.applyColorOverrides()` already does this; the resolver
formalizes the API so future callers (notification builder, widget)
don't reinvent it. Drop entirely if it doesn't pay for itself once
`EventVisual` (§3) lands and encapsulates resolution internally.

## §6 - Polish application

Apply `Spacing` tokens + `AsalaTypography` across surfaces:

- Drawer (`CalendarDrawer.kt`, `month/drawer/AccountSection.kt`,
  `month/drawer/CalendarRow.kt`): Spacing tokens; account name vs
  calendar name weight contrast (`labelLarge` 500 vs `bodyMedium` 400).
- Detail sheet (`eventdetail/EventDetailSheet.kt`): Spacing tokens.
  Fields grouped into When / Where / Notes / Reminders blocks
  separated by `HorizontalDivider`.
- Editor (`eventedit/EventForm.kt`, `RecurrenceSection.kt`,
  `ReminderPicker.kt`, `DateTimePickerRow.kt`): Spacing + Typography
  tokens only. No field restructure.
- Top bar (`AppShell.kt`): chevron + title hierarchy tightened,
  Spacing applied. Header-dropdown panel (`HeaderDropdownPanel`,
  `MiniMonthPanel`, `MonthChipsPanel`) gets Spacing applied.
- Schedule (`ScheduleScreen.kt`): date-section header refined
  (`titleMedium`, color = primary), Spacing between list items.
- Month / Week / Day (`MonthScreen.kt`, `WeekScreen.kt`,
  `DayScreen.kt`): Spacing applied to chrome (day-name strip, hour
  rail margins, FAB position offset). No layout changes beyond what
  §3 and §4 already deliver.

## Accessibility floor

- WCAG 2.2 AA contrast verified by existing `WcagContrastTest` and
  `RadixPaletteContrastTest`.
- Touch targets >=48dp: `ListItem` rows (M3 default), `EventChipBlock`,
  `EventChipRow`. `EventChipCompact` is exempt because the day cell
  is the touch target.
- System font scale honored: typography overlay only overrides
  `fontWeight` and `lineHeight`, never `fontSize`.
- Reduce-motion honored: Compose M3 honors
  `Settings.Global.ANIMATOR_DURATION_SCALE` natively; no transition
  specs overridden.

## Open questions

- Whether to keep `CalendarColorResolver` after §3 ships. Decision
  point at PR-E.

## Plan reference

[2026-05-26-ui-polish-pass.md](../plans/2026-05-26-ui-polish-pass.md).
