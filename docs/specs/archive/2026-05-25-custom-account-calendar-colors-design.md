# Design: custom account avatar and calendar colors

Status: Draft (not yet implemented).
Target release: next milestone after the M4 closeout.

## Context

Account avatar colors and local-calendar colors are picked today
from a fixed 8-color Okabe-Ito (Wong) palette (introduced in
v0.6.0, commit `bf7639e`). Assignment is automatic and not
user-overridable:

- **Account avatars** use a deterministic hash of the account
  name (`AccountSection.avatarColor`, lines 109-113), so two
  accounts with similar names can land on the same avatar color
  and the user cannot swap.
- **Local calendars** can pick a color at creation time
  (`CreateCalendarDialog.kt`, lines 66-99) but cannot be recolored
  afterwards.
- **Synced calendars** always render the color the sync adapter
  wrote to `CalendarContract.Calendars.CALENDAR_COLOR`. If the
  user dislikes the color (or two synced calendars collide), the
  only workaround is to change it in the source app, which is
  annoying and platform-dependent.

This spec adds the missing override path for both account avatars
and individual calendars, drawing from the same Okabe-Ito
palette. No free hex / HSV input. No per-event color (the data
layer's `Events.eventColor` is left alone for a future spec).

## Goal

Let the user reassign the Okabe-Ito color for any account avatar
and any calendar, with overrides persisting across launches and
surviving sync. Sync calendars keep their provider-side color in
the database; Asala just renders the override on top.

## Explicit non-goals

- Free hex or HSV picker. The Okabe-Ito palette stays the only
  source of colors.
- Per-event color override. The provider field exists
  (`Events.eventColor`) but is out of scope here.
- Replacing or extending the Okabe-Ito palette (separate ADR work
  if we later want a different CB-safe set).
- Writing color overrides to the Calendar Provider for synced
  calendars. The override stays local so the sync adapter cannot
  clobber it on its next pass.
- Account avatar shape, initial, or fallback rendering changes.
  Only the color tint changes.
- A "reset to default" UI for the hash-based avatar color.
  Picking the same palette entry the hash would have chosen is
  the manual reset path; revisit if users ask.

## Prior art consulted

Per-calendar recolor is universal across mainstream calendar
apps and well-maintained open-source Android calendar editors:
fixed palette grid with an optional "Custom" hex escape hatch,
attached to each calendar row. Per-account avatar recolor is
rare on calendar apps; the closest pattern is account color
customization in mail clients. There is no off-the-shelf Material
3 pattern to copy for either.


W3C WCAG 1.4.1 (don't use color as the only signal) is already
satisfied today: avatar chips pair the color tint with the
account initial, and calendar list rows pair the color dot with
the calendar name. No new accessibility work is needed for that
constraint.

Pattern picked: fixed Okabe-Ito grid only (no "Custom" escape
hatch for v1). Attached behind the existing 3-dot overflow menu
on each `CalendarRow`, and behind a long-press on the account
avatar (no 3-dot menu exists on the account header today).

## Interaction rules

### Per-calendar recolor

- Tap the existing 3-dot overflow menu on a `CalendarRow` (lines
  100-133 of `CalendarRow.kt`). Today the menu has two entries:
  "Rename calendar" and "Delete calendar".
- Menu gains a third entry: "Change color". Placed above the
  existing two entries for visibility.
- Selecting "Change color" opens a small dialog: title is the
  calendar's display name, body is an 8-swatch grid of the
  Okabe-Ito palette (reusing a `ColorSwatchGrid` composable
  pulled out of `CreateCalendarDialog`). The currently-applied
  color is shown with a ring around its swatch.
- Tapping a swatch dismisses the dialog and applies the new
  color immediately. The dismiss action is "Close"; there is no
  separate save action.

### Per-account avatar recolor

- Long-press the avatar circle inside `AccountHeader` (the
  `AccountAvatar` composable, currently called at
  `AccountSection.kt:62` and defined at lines 92-106).
- Opens the same `ColorSwatchGrid` dialog. Title is the account
  display name.
- Tapping a swatch persists the override and dismisses.
- The existing tap-on-row behavior (collapse / expand the
  account section) is unchanged; only long-press on the avatar
  triggers recolor.

## Storage model

Two new `UserPreferences` keys, both backed by the existing
`Context.settingsDataStore` instance, stored as JSON-encoded
strings via `kotlinx.serialization`:

```kotlin
val KEY_ACCOUNT_AVATAR_COLORS = stringPreferencesKey("account_avatar_colors")
val KEY_CALENDAR_COLOR_OVERRIDES = stringPreferencesKey("calendar_color_overrides")
```

Wire format: `Json.encodeToString` of the override maps,
serialised with `kotlinx.serialization`.

- Account avatar overrides keyed by `"<accountType>:<accountName>"`
  (string), values ARGB `Int`.
- Calendar color overrides keyed by `Calendars._ID` (Long),
  values ARGB `Int`. JSON serialises Long keys as strings (a
  standard JSON limitation); the decoder converts back.

**Why kotlinx.serialization.** Android account names legitimately
contain `=`, `:`, `@`, and spaces, so a hand-rolled `Set<String>`
"key=value" encoding has real delimiter-collision risk. The
Android Developers team's published guidance recommends
`kotlinx.serialization` as the typed path for non-scalar
preferences inside Preferences DataStore (URLs at the bottom of
this spec). Adoption cost is one Gradle plugin line + one
dependency line, around 100 KB of AAR.

`UserPrefs` data class gains two new fields:

```kotlin
val accountAvatarColors: Map<String, Int>,
val calendarColorOverrides: Map<Long, Int>,
```

Both default to an empty map. The reader in the `prefs` flow
maps the stored JSON back into the map, falling back to empty
on decode failure (defensive only; the encoder controls the
wire format).

## Render path

### Avatars

Today's signature is `private fun avatarColor(account: String)`
in `AccountSection.kt:109-113`. The new signature accepts the
account type so the hash basis can disambiguate two accounts
with the same display name on different account types:

```kotlin
private fun avatarColor(
    accountType: String,
    accountName: String,
    overrides: Map<String, Int>,
): Color {
    val key = "$accountType:$accountName"
    overrides[key]?.let { return Color(it) }
    val idx = (key.hashCode().toUInt() % OkabeItoPalette.size.toUInt()).toInt()
    return OkabeItoPalette[idx]
}
```

`AccountAvatar` (defined at `AccountSection.kt:92-106`) gains an
`accountType: String` parameter alongside the existing
`account: String` name parameter. The caller already has
`group.accountType` available (`AccountSection.kt:45`).

The hash basis changes from `account.name.hashCode()` to
`"$type:$name".hashCode()`. This is a one-time visual shift for
existing installs: an account may pick a different palette entry
on next launch. The override path lets users restore the
previous color manually. Document the shift in the CHANGELOG.

### Calendar rows

`CalendarItem` data class gains an `overrideColor: Int?` field
and a computed `displayColor: Int`:

```kotlin
val displayColor: Int
    get() = overrideColor ?: color
```

(`color` is the existing field name in `CalendarItem.kt`; we do
not rename it.)

`CalendarRepository.queryCalendars` (the cursor-read at
`CalendarRepository.kt:103`) hydrates `overrideColor` from the
prefs flow after the cursor pass. Every call site that reads
`CalendarItem.color` today gets retargeted to `displayColor`.
Search before editing:

```
rg "calendar\.color\b" app/src/main/kotlin
rg "CalendarItem\.color\b" app/src/main/kotlin
```

All event chip rendering paths (Month / Week / Day / Schedule)
already key off `CalendarItem` or its derived event-side color,
so the override flows through automatically.

### Synced-calendar safety

The override sits purely in DataStore. The sync adapter has no
visibility into Asala's preferences and cannot overwrite the
override.

### ContentObserver interaction

The app registers a `ContentObserver` on
`CalendarContract.Calendars.CONTENT_URI` (see
`AsalaCalendarApplication.kt:63-68` and
`ContentResolverFlow.kt:38`). Writing `CALENDAR_COLOR` to a
local calendar through the new `updateLocalCalendarColor` will
fire that observer and trigger a re-query of calendars. The
override hydration in `queryCalendars` re-applies overrides
from prefs on every pass, so the live-update is idempotent and
safe. Add an inline comment in `queryCalendars` explaining the
re-trigger expectation so future readers do not break it.

## Implementation outline

New files:

```
ui/calendars/ColorSwatchGrid.kt     ; the 8-swatch picker composable,
                                    ; extracted from CreateCalendarDialog
                                    ; for reuse
ui/calendars/RecolorDialog.kt       ; small wrapper composable around
                                    ; ColorSwatchGrid for both calendar
                                    ; and account flows
```

Modified files:

```
build.gradle.kts (root + :app)       ; +kotlin("plugin.serialization")
                                    ; +implementation(
                                    ;   "org.jetbrains.kotlinx:
                                    ;    kotlinx-serialization-json:<ver>")
data/CalendarRepository.kt           ; +updateLocalCalendarColor() that
                                    ; writes CALENDAR_COLOR via
                                    ; contentResolver.update(), mirroring
                                    ; the existing renameLocalCalendar()
                                    ; pattern at lines 70-84;
                                    ; queryCalendars() hydrates
                                    ; overrideColor from prefs
data/CalendarItem.kt                ; +overrideColor, +displayColor
ui/settings/UserPreferences.kt      ; +KEY_ACCOUNT_AVATAR_COLORS,
                                    ; +KEY_CALENDAR_COLOR_OVERRIDES,
                                    ; +setAccountAvatarColor(...),
                                    ; +setCalendarColorOverride(...);
                                    ; uses kotlinx.serialization Json
ui/calendars/CreateCalendarDialog.kt ; refactor color grid out into
                                    ; ColorSwatchGrid for shared use
                                    ; (current grid at lines 66-99)
ui/month/drawer/AccountSection.kt   ; avatarColor() takes accountType
                                    ; and overrides; AccountAvatar
                                    ; signature gains accountType;
                                    ; AccountHeader plumbs long-press
                                    ; to open RecolorDialog
ui/month/drawer/CalendarRow.kt      ; 3-dot menu (lines 113-130) gains
                                    ; "Change color" above the existing
                                    ; "Rename calendar" and "Delete
                                    ; calendar" entries
ui/month/CalendarDrawer.kt          ; wires the new override handlers
                                    ; from drawer state to the prefs
                                    ; writer
res/values/strings.xml              ; +menu_change_color,
                                    ; +dialog_recolor_calendar,
                                    ; +dialog_recolor_account,
                                    ; +cd_recolor_avatar_longpress
```

**Local vs synced calendar write path.** For local calendars,
the new color is written both to `CALENDAR_COLOR` (so the
provider reflects it, useful if the user later exports) and to
the override map. For synced calendars, only the override map
is written. The dialog branches on
`calendar.accountType == CalendarContract.ACCOUNT_TYPE_LOCAL`
to pick the path (there is no `isLocal` helper today; the check
is inlined).

**Update precedent.** `CalendarRepository.renameLocalCalendar`
(lines 70-84) is the template for the new write: build a
`ContentValues`, call `contentResolver.update()` on the
per-calendar URI. Mirror its error handling and threading.

## Strings

```xml
<string name="menu_change_color">Change color</string>
<string name="dialog_recolor_calendar">Calendar color</string>
<string name="dialog_recolor_account">Account color</string>
<string name="cd_recolor_avatar_longpress">Long-press to change account color</string>
```

## Accessibility

- The avatar long-press gesture is exposed via
  `Modifier.semantics { customActions = ... }` so TalkBack users
  can trigger "Change color" without a long-press.
- The color swatch grid uses the 8 Okabe-Ito values, all of which
  already meet the 3:1 UI-component contrast minimum against the
  surface color (verified during v0.6.0 palette work). No new
  contrast audit needed.
- Each swatch carries a content description naming its color
  (e.g. "Vermilion", "Sky blue"), per the Okabe-Ito names so the
  user gets a meaningful announcement rather than "color 1 of 8".
- WCAG 1.4.1 compliance: account avatars continue to display the
  account initial; calendar rows continue to display the calendar
  name. Color is never the sole signal.

## Testing

Unit tests:

- `UserPreferencesTest`: round-trip writes and reads for both new
  maps. Empty default. Account names containing `=`, `:`, `@`,
  and spaces serialise and deserialise correctly via the JSON
  encoder.
- `CalendarItemTest`: `displayColor` returns override when set,
  falls back to `color` otherwise.
- `AvatarColorTest`: deterministic hash output unchanged for a
  fixed `type:name` input; override is respected when present.

Manual verification (see Verification below) covers the UI flows.

## Verification

For the implementer to confirm the spec landed:

1. `./gradlew :app:lintDebug :app:testDebugUnitTest` green.
2. Fresh install per the CLAUDE.md test-fresh-install rule.
3. Open the drawer. Long-press the avatar for any account. The
   recolor dialog opens. Pick a different swatch. Dialog closes,
   avatar color updates immediately, all calendars under that
   account keep their own colors unchanged.
4. Tap the 3-dot menu on any calendar row. "Change color" is the
   topmost entry. Pick a new color. Dialog closes, the calendar's
   color dot updates, and the same color appears on every event
   chip rendered from that calendar across Month, Week, Day, and
   Schedule.
5. Repeat (4) on a synced calendar. Force a sync from the source
   app. The override persists; the sync adapter does not
   overwrite it.
6. Kill the app, relaunch. Both overrides survive.
7. Enable TalkBack. Walk through the account avatar; confirm the
   "Change color" custom action is announced and triggerable.

## Open questions

- Hash basis change from `name` to `"type:name"` is a one-time
  visual shift for existing installs. Acceptable? (No alternative
  preserves the old colors while letting two same-name accounts
  diverge; the override path lets users restore the previous
  color manually.)
- Should the "Change color" menu entry on a synced calendar be
  worded differently to set expectations (e.g., "Override
  color")? Single-word "Change" is shorter and matches the local
  case; alternative is "Set local color". Implementer's call;
  default to "Change color" unless user-test surfaces confusion.

## Out of scope, captured for future specs

- Free hex / HSV picker as a "Custom" escape hatch behind the
  Okabe-Ito grid. Mainstream pattern; deliberately deferred here
  to ship the smaller change first.
- Per-event color override (`Events.eventColor`).
- Replacing the Okabe-Ito set with a wider CB-safe palette
  (Tol-muted has 9 colors; IBM Carbon data-vis has 14). Worth a
  short ADR comparison before either swap.
- A reset-to-default action on the recolor dialog. Deferred until
  users ask.

## Key references

- DataStore overview, type-safety statement:
  https://developer.android.com/topic/libraries/architecture/datastore
- DataStore + kotlinx.serialization pattern (Android Developers):
  https://medium.com/androiddevelopers/datastore-and-kotlin-serialization-8b25bf0be66c
- kotlinx.serialization setup:
  https://kotlinlang.org/docs/serialization.html
- WCAG 1.4.1, use of color:
  https://www.w3.org/WAI/WCAG21/Understanding/use-of-color.html
