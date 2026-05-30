# Spec: v0.12.0 quick-wins sprint (sprint A)

Date: 2026-05-27.

## Context

First sprint after v0.11.0 polish. Per the multi-sprint sequencing
agreed 2026-05-27 (four themes across four releases), sprint A is
deliberately small: three items, each individually small, each
landing as its own commit so any can be reverted independently. Aim:
fast, low-risk release that ships across all four user-selected
themes where possible.

Items originally scoped for sprint A but moved to sprint B after a
code-mapping pass surfaced they were larger or design-gated than
expected:

- **Edge auto-scroll in Week drag** — current `RescheduleDragState`
  clamping is intentional
  ([RescheduleDragState.kt:36](../../app/src/main/kotlin/com/arishawke/asala/calendar/ui/week/RescheduleDragState.kt#L36)).
  Reversing requires a design decision. Moved to sprint B alongside
  drag-to-resize.
- **Show declined events** — depends on `CalendarContract.Attendees`
  plumbing that the codebase does not currently load. Bundling with
  sprint B's "attendee / response indicators" is more efficient than
  doing the attendee data layer twice.
- **iCal URL property support** — greenfield
  `CalendarContract.ExtendedProperties` work. Platform-variance
  investigation needed (not every sync adapter writes URL there).
  Moved to sprint B.

## Verified state before scope-locking

- **Multi-local-calendars data layer is unblocked.**
  `CalendarRepository.createLocalCalendar`
  ([CalendarRepository.kt:29-51](../../app/src/main/kotlin/com/arishawke/asala/calendar/data/CalendarRepository.kt#L29-L51))
  has no guard preventing a second create against the same account.
  The user-observed "only one local calendar" symptom must therefore
  live upstream in the UI (`AppOverlays.CreateCalendarDialog`
  [AppOverlays.kt:94-103](../../app/src/main/kotlin/com/arishawke/asala/calendar/ui/AppOverlays.kt#L94-L103))
  or in `AppViewModel.createLocalCalendar`
  ([AppViewModel.kt:418-422](../../app/src/main/kotlin/com/arishawke/asala/calendar/AppViewModel.kt#L418-L422)).
  Investigation is item 1 of the implementation plan.
- **No existing birthday-event detection.**
  `CalendarContract` exposes no birthday metadata column.
  `EventDetail` does not carry an `isBirthday` flag. Detection has
  to come from calendar account-type or calendar-name pattern at
  read time (`EventDetailReader`).
- **Existing `statusStyling` is the right pattern to mirror** for
  the cake icon. `EventVisual.kt:61-77` applies italic / strikethrough
  based on `STATUS` — the cake icon plugs into the same render path.
- **Working-days toggle is the right pattern to mirror** for the
  show-week-number setting. `UserPreferences` boolean key, DataStore
  read / write, Settings checkbox, downstream boolean parameter to
  Month / Week composables ([UserPreferences.kt:152, 308-320](../../app/src/main/kotlin/com/arishawke/asala/calendar/ui/settings/UserPreferences.kt#L152)).

## Items

### 1. Multiple local calendars under the Asala account

**Source:** ROADMAP §"Quality of life" — long-standing observed bug.

**Behavior.** A user can create more than one local calendar from
the drawer "Create local calendar" flow. Each subsequent create
appears in the drawer and is independently selectable / writable.
Existing single-calendar installs are unaffected.

**Investigation first.** Code-mapping showed the data layer accepts
the second insert. The "only one in practice" symptom lives upstream.
First commit verifies which of:

- The dialog's `onConfirm` is suppressed when a local calendar
  already exists.
- The `AppViewModel` swallows the result.
- The drawer renders only the first match.
- The insert silently fails (e.g., duplicate `Calendars.NAME`
  collision in `ContentProvider`).

Reproduction on the paired device, then trace the gap. Fix follows
in the same commit if obvious, or in a second commit if a separate
design point surfaces.

**Files.** `ui/AppOverlays.kt` (the dialog); `AppViewModel.kt` (the
ViewModel call site); `data/CalendarRepository.kt` (verify behavior,
likely no change); possibly the drawer's calendar-list composable.

**Edge cases.** Name collision (two locals both default-named
"Calendar"); colour assignment for a second auto-coloured calendar;
the Storage section in Settings reflecting multiple local calendars
in its accounting.

**Size.** Small. Investigation may surface a single missing guard
to remove, in which case the fix is one or two lines.

### 2. Cake icon for birthday-type events

**Source:** frequently requested.

**Behavior.** Events that originate from a birthday-type calendar
render with a small cake glyph as a leading icon in their chip
title (across Month / Week / Day / Schedule chips) and in the detail
sheet title row. Differentiates auto-imported birthdays from
meetings at a glance.

**Detection.** `CalendarContract` exposes no birthday metadata.
Practical detection paths (pick the cleaner one during
implementation):

- **Account type match.** Birthday calendars from the Android
  Contacts Provider typically carry `Calendars.ACCOUNT_TYPE ==
  "com.android.contacts"` or similar provider-specific value.
  Compile a short allow-list of known birthday account types.
- **Calendar name pattern.** Match localized name fragments
  ("Birthdays", "Geburtstage", "Anniversaires"). Fragile; only
  fall back to this if account-type match is insufficient.

Field on `EventDetail` (and on the chip-relevant `EventItem`):
`isBirthday: Boolean`, defaulting to `false`. Set at read time
in `EventDetailReader` and `EventInstanceReader`.

**Files.** `data/EventDetail.kt` (add field); `data/EventItem.kt`
(add field if chips need to render); `data/EventDetailReader.kt`
and `data/EventInstanceReader.kt` (detection logic); `ui/components/EventVisual.kt`
(render leading icon in Compact / Block chips); `ui/eventdetail/EventDetailSheet.kt`
(detail-sheet title row).

**Edge cases.** Multi-language calendar names; user-renamed birthday
calendars; future locales; rendering at small chip sizes (icon may
not fit in 12dp Compact chip — fall back to text-only for those);
detail sheet badge already shows status (Tentative / Cancelled), so
the cake icon needs to coexist visually.

**Size.** Small. Detection logic is the main bit; rendering is a
single leading-icon slot.

### 3. Show week number setting

**Source:** ROADMAP §"Visual polish" — common in European calendar
apps and useful for planning.

**Behavior.** New "Show week number" toggle in Settings > Appearance,
defaulting to off. When enabled:

- Month view: ISO 8601 week-of-year rendered in a thin fixed-width
  left column, one number per week row.
- Week view: ISO 8601 week-of-year rendered above the hour axis (the
  existing fixed-width left column).

Disabled is the default and matches the current US-style behavior.

**Files.** `ui/settings/UserPreferences.kt` (new boolean key, mirror
the working-days pattern); `ui/settings/SettingsScreen.kt`
(Appearance section, insert after the working-days row);
`ui/month/MonthScreen.kt` (`WeekLayoutRow` at lines 215-287 adds a
prefix column when enabled); `ui/week/TimelineGrid.kt` (HourAxis
header at lines 86-87 adds the number).

**Layout decision.** Month view uses an equal-weight `Row` for the
seven day cells. Adding a fixed-width prefix column requires either:

- Fixed-width left column (e.g., 28dp), day cells share the
  remaining width via equal weights. **Recommended.** Smallest
  visual change; matches Week view's existing left-column
  convention.
- Strip above each week row showing "W21". Rejected: doubles row
  height and pushes content down significantly.
- Inline rendering inside the first cell. Rejected: clutters the
  cell layout and competes with the date number.

**Edge cases.** ISO 8601 week numbering (Java `WeekFields.ISO`)
starts on Monday and uses the year-week of the Thursday in that
week, so week 1 of any given year may begin in late December of
the prior year. Sunday-start locales (US) still get ISO 8601 numbers
when this toggle is on — the toggle is opt-in, so users who don't
want ISO numbering leave it off. The leading / trailing days that
the Month view dims still get their week number even if those days
belong to a different month.

**Size.** Small-medium. The layout shift in Month view is the main
work; Week view is a single `Text` placement.

## Out of scope

- Locale-aware week-numbering scheme (e.g., US week-of-year vs ISO).
  ISO 8601 only for v0.12.0. Locale-aware variant can be a future
  toggle if anyone asks.
- Drag-handle for the multi-local-calendars dialog. The fix in
  item 1 is upstream-of-create; reorder / drag-handle is a
  separate UX track.
- Hiding cake icon if a user dislikes it. The cake icon is a
  derived visual cue with no Settings switch in v0.12.0; if
  feedback surfaces a need to disable it, a future toggle can be
  added under Appearance.

## Verification

- Local gate: `./gradlew :app:spotlessKotlinCheck :app:lintDebug
  :app:testDebugUnitTest`.
- Device smoke (fresh install per `feedback-test-fresh-install`,
  device at `100.122.79.24:33781`):
  - Create two local calendars from the drawer flow, both visible
    and writable.
  - Sync a contacts account (or local birthday calendar);
    verify birthday events render with the cake icon and other
    events do not.
  - Toggle "Show week number" in Settings > Appearance; verify
    Month view shows the week number column and Week view shows
    the number above the hour axis. Disable; verify they vanish.
- Verify ISO 8601 boundary: scroll to a week that crosses a
  year boundary (e.g., late December 2025 / early January 2026) and
  confirm the number is correct (W01 starts the week containing
  Jan 4 by definition).
