# Changelog

All notable changes to Asala Calendar are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project follows [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Changed
- TalkBack now reads the search field, the Settings switches (dim past dates,
  working hours, working days, week numbers, and the translucent-widget toggle),
  and the event editor's "All day" toggle with their proper name and on/off
  state, and the whole row is tappable rather than just the switch.

### Fixed
- Snoozing a reminder no longer occasionally loses the snooze. The snoozed alarm
  shared a scheduling slot with the original reminder, so reopening the app (or a
  calendar change) could cancel the pending snooze before it fired. Snoozes now
  keep their own slot.
- Natural-language quick add no longer crashes if you type an out-of-range
  relative date such as "in 99999999999 weeks". The unrecognized amount is left
  in the title instead.

## [0.21.0] - 2026-06-11

### Removed
- The experimental Tasks toggle and its "coming soon" placeholder view. Asala is
  focusing on being a clean calendar; task management is no longer planned. A
  saved "Tasks" default-view preference falls back to Month.

### Added
- Natural-language quick add in the new-event editor. Type a phrase like "lunch
  at Cafe Rio tomorrow at noon" and the title, location, date, and time fill in
  for you to review before saving. It recognizes relative dates (today, tomorrow,
  weekdays, "next Friday", "the 15th"), times and ranges, durations, and
  "at <place>" locations; a date with no time becomes an all-day event. Anything
  it does not recognize is left for you to fill in by hand.
- Saving a new or edited event now points you to it when it lands off-screen. In
  the day, week, and 3-day views, a tappable indicator appears at the top or
  bottom edge ("New event at 9:00 PM") without moving your place; tapping it
  glides to the event, which briefly glows. An event saved onto a different day
  opens that day parked on it. Events already in view just glow, and all-day
  events show no indicator. The schedule, month, and year views continue to
  jump straight to the date.

### Changed
- Calendar day cells in the month grid, mini-month, and year views now announce
  their full date (for example "Wednesday, June 10, 2026") and a button role to
  TalkBack, instead of reading just the bare day number.
- The mini-month date-picker cells are now at least 48dp tall, meeting the
  recommended minimum touch-target size.
- Text on colored fills (multi-day bars, the account avatar, and day-view all-day
  rows) now picks black or white by actual WCAG contrast rather than a rough
  brightness midpoint, so labels stay legible across more colors. The custom
  calendar-color picker now previews sample text on the chosen color and warns
  when a color is too close to the background to tell apart.
- TalkBack support improvements: the screen title now announces view and date
  changes, Schedule day headers are exposed as headings for quick navigation, and
  timeline events offer "Move 15 minutes earlier/later" actions so the drag-to-
  reschedule gesture is reachable without dragging.

### Fixed
- Applying a change to "All events" of a repeating event from a later occurrence
  no longer drops the earlier ones. Dragging an occurrence to a new time (or
  editing one) and then choosing "All events" now shifts the whole series by the
  amount you moved it, keeping every occurrence, instead of moving the series
  start onto the occurrence you touched and silently removing the ones before it.
  Choosing "This event" or "This and following" was unaffected.
- The Schedule view's event window now follows the date. Previously it was fixed
  at app start, so leaving the app open across one or more midnights slowly
  shrank how far ahead the agenda showed events. It now re-anchors as the day
  changes.
- Natural-language quick add no longer misreads the everyday words "sun", "sat",
  and "wed" as weekdays, so a phrase like "enjoy the sun" stays a plain title.
  Spelled-out names ("sunday") and qualified forms ("next sun") still set the
  date. A parsed zero-length time ("for 0 minutes") now falls back to the
  default event length instead of creating a 24-hour event.
- Opening an existing event to edit (or duplicating one) now shows a brief
  loading spinner until its details finish loading, instead of a blank form.
  This prevents an edit made in that first moment, such as changing the time,
  from being silently discarded when the event's data lands a moment later.
  This was only reachable when the load was slow (large or syncing calendars,
  slower devices). Creating a new event is unaffected.
- Events on read-only calendars (such as holidays, birthdays, and subscribed
  calendars) no longer show Edit and Delete buttons that could never work. When
  a delete is rejected for any reason, for example a read-only calendar or
  calendar permission revoked while the app is open, the app now shows a brief
  message instead of failing silently.
- A repeating event whose "ends on" date was set earlier than its start date no
  longer saves as an event with no occurrences that silently disappears. The
  date picker now blocks those dates, and the save is rejected if one slips
  through.
- Deleting "this and following events" from the first occurrence of a repeating
  event now removes the whole series cleanly, instead of leaving an invisible
  leftover event behind that could resurface when syncing.
- The highlighted "today" now updates after a device time-zone change, for
  example when travelling across the date line, instead of staying on the
  previous day until the app is restarted.

## [0.20.0] - 2026-06-06

### Added
- Widget appearance settings (Settings > Widgets): the home-screen agenda and
  month widgets can now use their own theme (Follow app, System, Light, Dark, or
  AMOLED) independent of the app theme, and an optional translucent background
  that lets the wallpaper show through. Existing widgets keep following the app
  theme until changed.

### Changed
- The Settings screen is now organized into collapsible sections. It opens with
  General expanded and the rest collapsed, so it is shorter and easier to scan;
  tap a section header to expand or collapse it.
- Settings sections reordered: Widgets now sits below Notifications.
- Month scroll style moved into the General section, just below Default view.
- The month view now defaults to continuous (endless vertical scroll) instead of
  paged. Switch back any time in Settings > General > Month scroll style.
- Week can now start on any day, not just Sunday, Monday, or Saturday.
- The mini-month panel (the split view in Week, 3-Day, Day, and Schedule) now
  changes months by swiping left or right instead of arrow buttons, and
  switching months moves the view to the first of that month.

### Fixed
- Deleting a single occurrence of a repeating event ("this event only") no
  longer removes the whole series. The other occurrences now stay.
- Editing a single occurrence of a repeating event ("this event only") no
  longer removes the whole series. Only that occurrence changes; the rest
  stay. The edited occurrence becomes its own one-off event.
- A repeating event with an end date now stops on the correct day in time zones
  other than UTC. The final occurrence on the end date is no longer dropped.
- Editing a repeating event no longer changes its stored time zone to the
  device's. An event created in (or imported from) another time zone keeps its
  original zone, so its later occurrences stay put instead of shifting.
- Saving an event whose end is before its start is now rejected instead of
  silently storing an inverted or zero-length time range.
- Editing "this and all following" on a repeating event that recurs on specific
  weekdays (for example an imported every-Monday-and-Wednesday series) now keeps
  those weekdays on the later occurrences instead of repeating every day.
- A delete or drag-to-reschedule that the calendar storage rejects no longer
  looks like it worked: the event stays visible (delete) or snaps back to its
  original time (drag) instead of silently appearing to change.
- A cancelled occurrence synced from another calendar (for example an event
  cancelled in Google Calendar) is now hidden from the calendar views, search,
  and widgets instead of lingering struck-through.
- Opening a malformed timed event whose stored end equals its start (for
  example a zero-length row from an import) no longer leaves the editor stuck.
  Its end is shown as the default event duration so it can be edited and saved.

## [0.19.0] - 2026-06-05

### Added
- Home-screen agenda widget: a resizable, scrollable list of your upcoming
  events grouped by day (Today, Tomorrow, then by weekday), in your chosen
  light or dark theme, showing only the calendars you have visible. Tap an
  event to open it, or the header to open the app. It refreshes when your
  calendar changes and rolls over at midnight.
- Home-screen month widget: the current month as a grid, showing each day's
  events as calendar-colored chips, with multi-day events spanning every day they
  cover as a continuous band (and a +N count when a day has more), and today
  circled. Tap a day to open it in the Schedule view, or the month header to open
  Month view. It follows your theme, week-start, and visible calendars, and
  refreshes with your calendar and at midnight.

### Fixed
- Creating or saving a repeating all-day event no longer crashes. The repeat
  length was written in a time-based form the calendar storage rejects for
  all-day events; it is now written in whole days.
- The app no longer crashes when the calendar storage rejects a change or
  access is revoked mid-session (for example a removed account or a changed
  permission). Such failures now surface the save-failed message or show an
  empty view instead of closing the app.
- Editing a repeating event without changing its repeat settings no longer
  shifts when the series ends or drops repeat details the editor does not
  show. Previously an unrelated edit could move the end to the end of the day
  and bring back an occurrence that a "this and following" change had split off.
- Choosing "this and following" on the first occurrence of a repeating event
  now edits the whole series instead of leaving an empty leftover event behind.
- The agenda widget now caps how many events it lists and adds a "+N more" row
  that opens the app, so a very busy two weeks can no longer make the widget
  fail to load. Event colors are also forced opaque so a bar cannot render
  invisible.
- An all-day event with a malformed zero length (from another app's data) no
  longer shows a backwards date range or opens the editor with the end before
  the start; it now reads as a single day.
- Tapping a day in the month view now opens that exact day. Previously a day more
  than about two months from today opened a nearer day instead, because the day
  view only covered a short range around today.
- Editing "this and following" on an event that repeats a set number of times
  no longer adds extra occurrences. The remaining repeats are now split between
  the original run and the edited one so the total count stays the same, instead
  of the edited part starting a fresh full count.
- Screen readers can now open an event from the Week, Day, and 3-Day views.
  Each event block is exposed as a button labelled with the event, so a
  TalkBack double-tap opens its details.
- Editing and saving an event whose repeat rule carried both an end date and a
  repeat count no longer crashes. Such rules can arrive from imported or synced
  calendars; the end date is kept and the count dropped, matching how the editor
  already treats the two as mutually exclusive.
- Screen readers now announce an event's tentative or cancelled status, which was
  previously conveyed only by italic or strikethrough styling. The status is read
  as part of the event's label in the Month, Week, Day, Schedule, and Search views.
- Multi-day and all-day event bars are now exposed as buttons to screen readers,
  so a TalkBack double-tap opens the event, matching the timed event blocks.
- Year view day cells now announce their full date to screen readers, plus
  whether the day is today and how many events it has, instead of reading only the
  bare day number.
- A widget date deep-link carrying an out-of-range date is now ignored instead of
  preventing the app from opening.
- Editing "this and following" and typing a new repeat count now keeps the count
  you set. Previously, on a series that repeats a set number of times, your new
  count was further reduced by the number of earlier occurrences.
- A reminder for one occurrence of a repeating event no longer replaces an
  earlier, still-showing reminder for another occurrence of the same event. Each
  occurrence now gets its own notification.

## [0.18.0] - 2026-05-31

### Added
- Year view: a scrollable overview of the whole year as a 3-column grid of
  mini-months, with today highlighted and dots on days that have events. Tap a
  day to open it in Day view, or a month name to open Month view. Pick it from
  the view switcher or set it as your default view.

### Changed
- The continuous (scrolling) Month view now shows only each month's own days.
  Where a month does not start or end on a week boundary the surrounding cells
  are left blank instead of filled with the previous and next month's dates, so
  consecutive months no longer run together. The paged Month view is unchanged
  and still shows the adjacent days.
- Views now switch from a button in the top bar, which opens a dropdown of all
  views with the current one checked, instead of from the navigation drawer.
  The drawer now holds only your calendars and Settings.

## [0.17.0] - 2026-05-30

### Added
- Custom event and calendar colors: tap "+" in any color picker to choose a
  free color with an HSV wheel or a hex code. Custom colors are stored on this
  device only and are not synced to other apps or devices.
- Duplicate an event: the event detail sheet now has a Duplicate action that
  opens the editor prefilled with a copy to tweak and save separately.
  Duplicating a repeating event creates a single one-off.
- A "Support Asala" link in Settings, under About, that opens the project's
  Ko-fi page.

### Changed
- The Settings "About" section is now a single compact block: app name,
  version, and license on two small lines, with a Source / Licenses /
  Support action row. Support leads with a heart and opens Ko-fi on tap;
  the raw URLs are no longer shown.
- In "Local only" and "Sync only" modes the excluded accounts are fully
  hidden: gone from the drawer and from the Settings hidden-accounts list,
  as if they do not exist while that mode is active.

### Fixed
- "Sync only" calendar source now also hides on-device (local) calendar
  events from the timeline, not just from the drawer and event editor.
  Previously local events still rendered, so "Sync only" looked the same
  as "Both" on the calendar grid.

## [0.16.0] - 2026-05-30

### Changed
- Events that cross midnight now read as one event in two parts. Each piece shows a "1/2" / "2/2" continuation badge, the first piece its start time and the second piece its end time, instead of the next-day slice looking like a separate event starting at midnight. Applies to Week, Day, 3-Day, and Schedule views.
- The calendar picker in the event editor now shows each calendar as a color-dotted chip in a side-scrolling row, with an account selector above it when more than one account has calendars. Replaces the plain dropdown so the chosen calendar and its color are visible at a glance.

## [0.15.1] - 2026-05-30

### Added
- An "Open source licenses" entry in Settings, and a NOTICE file crediting the bundled open-source libraries.

### Changed
- Smoother Week and Day timelines: per-day event clipping and overlap layout are now cached instead of recomputed on every frame.
- Accessibility: larger overflow-chip touch target, and mini-month day cells now announce their event count to screen readers.

### Fixed
- Editing "this and following" on a recurring event no longer risks dropping the
  later occurrences if the underlying provider write is interrupted.
- Editing or deleting "this and following" on a recurring event now correctly truncates the original series instead of leaving the later occurrences duplicated.

## [0.15.0] - 2026-05-29

### Added
- 3-Day view. A timeline between Week and Day that pages three days at a
  time, rolling from the day you open it on. It shows three readable
  columns with the same event blocks, now-line, and working-hours and
  working-days shading as Week, and sits between Week and Day in the view
  switcher and settings. Set it as the default view to open into it.

## [0.14.0] - 2026-05-29

### Added
- Week view collapses crowded overlaps. When three or more events
  overlap at the same time, the first stays readable at full width and
  the rest move into a "+N" chip that opens a sheet listing every event
  at that time, instead of slicing the day column into unreadable
  strips. One and two overlapping events are unchanged, and Day view
  keeps its side-by-side columns.

## [0.13.0] - 2026-05-29

### Added
- Continuous-scroll Month view as an alternative to the existing
  paged Month. New Settings entry `Appearance > Month scroll style`
  with two options: `Paged` (default) and `Continuous`. Continuous
  mode stacks ~10 years of months vertically inside a LazyColumn
  with sticky month headers; reuses the same chip and multi-day bar
  rendering as the paged surface. The shared MonthViewModel widens
  its event-fetch window in continuous mode so pre-composed items
  render with events rather than blank cells.

### Fixed
- Schedule view current-time line now sits below events already in
  progress instead of jumping to the start of a long ongoing event.
  The line marks where upcoming events begin: events that have
  already started (including a long event still running) stay above
  it, matching the agenda's start-time ordering.

## [0.12.0] - 2026-05-27

### Changed
- New-event editor now opens on the date the user was looking at,
  not always today. From Month view: today if today is in the
  visible month, otherwise the first of that month. From Week:
  today if today is in the visible week, otherwise the week's first
  day. From Day: the visible day. From Schedule: today. Editing an
  existing event still uses that event's own date. Implementation
  threads a new `viewedDate` StateFlow on `AppViewModel`: each view
  updates it when its pager moves, `openCreateEditor` snapshots it
  into `editInitialStartDate`, and the editor seeds
  `EventEditFormState` with that date.
- Birthday-detection keywords picked up ASCII forms ("Cumpleanos",
  "Aniversarios") alongside the diacritic forms ("Cumpleaños",
  "Aniversários") so calendars typed without tildes or written by
  sync adapters that strip diacritics still get the cake icon.
- Time format setting in Settings is now a three-option picker
  ("Follow system default" / "12-hour" / "24-hour"). New installs and
  existing installs that never flipped the toggle now follow the
  Android system 24-hour preference; users who explicitly chose
  12-hour or 24-hour keep that override.

### Fixed
- The today highlight, mini-month panel, and new-event default date
  now refresh across local midnight, manual clock changes, and
  timezone shifts. Previously the value was captured once per process
  start, so an app left open past midnight showed yesterday's date as
  "today" until the user backgrounded and reopened the app.
- Edits to a synced event no longer overwrite STATUS (Tentative /
  Cancelled) or AVAILABILITY (Free / Busy) set on the server. The
  edit path now carries the loaded values through the save instead
  of unconditionally writing CONFIRMED / BUSY. New events still
  default to CONFIRMED / BUSY.
- 24-hour time format previously ignored the Android system preference
  on a fresh install: a French / German / 24-hour-region user saw
  12-hour AM / PM until they manually flipped the Settings toggle.
  Asala now reads the system setting whenever the user has not picked
  an explicit override.
- Time chips, hour-axis labels, and the multi-day strip now pass the
  device locale to their formatters, so French / German / Japanese
  users see the correct AM / PM marker (e.g., "午前 / 午後" instead of
  "AM / PM"). Event-block labels also gained the AM / PM marker they
  were previously dropping in 12-hour mode.
- Week view's toolbar title now renders via ICU `DateIntervalFormat`
  with the user's locale instead of a hardcoded US-style concatenation
  ("Mar 2 - 8, 2026"). German users now see "2.-8. März 2026", Japanese
  "2026年3月2日～8日", and so on; the previous fixed `MMM d - d, yyyy`
  shape was wrong in every non-English locale.
- Day view's all-day list and the drawer's account avatar now pick
  white or black foreground text per the swatch's luminance, matching
  the multi-day bar treatment. White-on-yellow (Okabe-Ito #F0E442)
  previously rendered at roughly 1.6:1, failing WCAG 1.4.3.
- Date and time picker dialogs in the event editor now use the
  localized OK / Cancel labels instead of hardcoded English.
- Working-hours hour labels in Settings now show locale-correct
  AM / PM markers (or the locale equivalent) on 12-hour clocks
  rather than hardcoded English suffixes.
- Time-range supporting lines under Month-view's "+N more" sheet
  now render via a translatable separator string.
- Per-instance edits and deletes on all-day recurring events
  (birthdays, anniversaries, recurring holidays) now bind to the
  parent series correctly. `CalendarContract` matches exception rows
  to their parent by `ORIGINAL_ID + ORIGINAL_INSTANCE_TIME +
  ORIGINAL_ALL_DAY`; the all-day flag was missing on every exception
  write, so deleting one occurrence of an all-day series silently
  no-oped (the original instance still rendered) and per-instance
  rename/move/recolor edits floated free of their parent. The fix
  threads `parentAllDay` through `deleteEvent` / `updateEvent` and
  pairs it with `ALL_DAY=1`, `EVENT_TIMEZONE="UTC"`, and
  `ORIGINAL_ALL_DAY=1` on the cancellation and exception cv.
- RFC 5545 UNTIL on all-day recurrence rules is now emitted as
  `YYYYMMDD` (date form) instead of `YYYYMMDDT235959Z` (datetime
  form). The provider tolerated the mismatch locally but
  standards-compliant CalDAV adapters (DAVx5, iCloud, Fastmail)
  reject the wrong shape; "this and following"
  on an all-day series now round-trips through sync without dropping
  the truncation.
- Drag-rescheduling a recurring event no longer collapses every
  future occurrence when "this and following" is picked. The new
  tail series keeps the parent's RRULE so it continues to recur
  from the rescheduled time; `ThisInstance` correctly stays
  one-shot.
- All-day reminder alarms now fire on the correct date in negative-
  offset timezones (US Eastern, Central, Mountain, Pacific, Alaska,
  Hawaii, much of Asia). The v0.11.0 timezone sweep covered Month /
  Week / Day / Schedule reads but missed three downstream call sites
  that interpreted `startMillis` in the device zone. CalendarContract
  stores all-day events at 00:00 UTC by convention; reading them in
  a negative-offset zone rolled the date back a day, so a "1 day
  before" alarm for a June 1 birthday could fire on May 30 9am in
  Pacific instead of May 31. Three paths now extract the date in UTC
  (matching `EventItem.startDate`'s existing `effectiveZone` logic)
  while preserving local-zone behavior for the alarm anchor and
  timed events:
  - `notifications/ReminderTimeMath.computeAlarmTime` for all-day
    reminder firing.
  - `ui/search/SearchScreen` grouped-by-date headers (all-day search
    results no longer fall under the prior day).
  - `ui/eventedit/EventEditViewModel` load path (opening an existing
    all-day event for editing now shows the correct start / end
    dates; saving via the existing UTC-store path preserves the
    round trip).
- Event editor's calendar picker no longer shows calendars hidden
  from the drawer. Previously the picker filtered only by
  `Calendars.VISIBLE` (the provider-level flag) plus storage-mode
  rules, but ignored Asala's in-app hide set (manual drawer toggles
  and `drawerHiddenAccountKeys` for whole-account hides). A user
  who hid a calendar from the drawer could still create events on
  it. The picker now consumes the same hide set the drawer uses, so
  hidden calendars are absent as create targets.
- Single-day all-day events now appear in Week view's all-day row.
  v0.11.0 changed `WeekBucketer.bucketize` to skip single-day all-day
  events so Month view could render them inline inside their day
  cell (closing a layout regression where one single-day all-day
  event reserved a week-wide lane). Week view's `AllDayRow` calls
  the same bucketer, which silently dropped those events from the
  Week all-day surface. `bucketize` now accepts `includeSingleDay`
  (default false, matching the prior Month behavior); `AllDayRow`
  passes true so single-day all-day events render as a one-column
  bar at the correct day.

### Added
- Show week number setting in Settings > Appearance. When enabled,
  Month view renders the ISO 8601 week-of-year in a thin (28dp) left
  column, with the weekday header above it offset to match; Week
  view renders the number in the existing hour-axis slot above the
  timeline. Defaults to off; designed for users who plan in week
  numbers (common in European calendars). Numbering follows ISO 8601
  (Monday-start, year-based on the week containing each year's first
  Thursday), so week 1 of any year may start in late December of the
  prior year. Locale-aware week numbering schemes (US-style) remain
  out of scope for v0.12.0.
- Cake leading-icon on birthday-type events. Events whose source
  calendar's display name matches a birthday keyword in any of 12
  supported locales (English, German, French, Spanish, Italian,
  Dutch, Portuguese, Polish, Japanese, Chinese, Korean, Russian)
  render with a small cake glyph everywhere they appear: Week and
  Day timed chips, Schedule and Search rows, the detail-sheet title
  row, Week view's all-day bar, and Day view's all-day list. Month-
  grid timed chips skip the icon since they are too short (around
  12dp) to read it cleanly; multi-day all-day birthday bars in
  Month do pick it up via the shared bar renderer. Detection is
  case-insensitive substring match; no Settings switch. If the
  calendar's name says "birthday" (or its locale equivalent), the
  events get the cake.

## [0.11.0] - 2026-05-27

### Changed
- Documented Asala's distribution stance in the README. APKs continue
  to ship through GitHub Releases; Obtainium is the recommended
  auto-update path. Asala will not be submitted to IzzyOnDroid: their
  App Inclusion Policy opposes apps "fully or in part created by
  generative AI tools," and out of respect for that policy this app
  is not a candidate for their repository. If F-Droid main is ever
  pursued, the existing README AI disclaimer will be repeated in the
  submission. Google Play's AI policy covers user-facing generated
  content (chatbots, image generation), which Asala does not
  produce, so no disclosure would be required there. ROADMAP
  §"Distribution and updates" reordered to put Obtainium first and
  add Accrescent as a stretch goal.

### Fixed
- Tapping a Month-view day cell now reliably opens the right date in
  Day view. Every screen observes the shared `pendingDateJump`
  StateFlow, and during the AnimatedContent transition between views
  the source screen was sometimes consuming the jump before the
  destination's `LaunchedEffect` had a chance to read it — leaving
  Day view stuck on today, or one day off depending on timing. The
  jump payload now carries the target view, and each screen's
  pendingDate handler ignores jumps that aren't for it.

### Changed
- Header dropdown expand / collapse animation shortened from the
  Compose default (~250ms) to 180ms. The Month-view chip strip is a
  much smaller height delta than the Week / Day / Schedule mini-
  month panel; at the default duration the chip strip's animation
  felt disproportionately slow because the per-pixel speed was so
  much lower. Both panels now use the same shorter tween, which
  feels snappier on the chip strip without making the mini-month
  feel rushed.

### Fixed
- All-day events near month boundaries no longer land on the wrong
  date in Month view (and the wrong week/day in mini-month / Week /
  Day / Schedule). `EventItem.startDate` / `endDate` were
  interpreting all-day `startMillis` / `endMillis` in the device
  timezone, but `CalendarContract` stores all-day at 00:00 UTC by
  convention. In a non-UTC zone (e.g., US Eastern, UTC-5) a Feb 1
  all-day event would be keyed under Jan 31 in the per-day grouping,
  which made the event detail sheet show "February" while the user
  had tapped a January cell. The all-day path now uses UTC; timed
  events continue to use the device zone.

### Changed
- Day view event chips now show the start *and* end time when the
  chip is tall enough (previously only the start time), rendered as
  a single-line range like "9:00 – 10:30" using the user's 24-hour
  preference. Week view chips intentionally stay start-time-only
  since columns are narrower and a range would risk truncation. For
  a midnight-crossing event the second-day chip shows the clipped
  end (e.g., "22:00 – 02:00" on day 2) so the visible slice matches
  what the user sees.
- Month view tap behavior. Tapping a single-day chip or a multi-day
  bar in Month view now switches to Day view focused on that date,
  instead of opening the event detail sheet. The "+N more" overflow
  affordance still opens a per-day list and then routes through the
  detail sheet, preserving the path for a quick deep-dive without
  changing views.

### Fixed
- Single-day all-day events in Month view no longer reserve a
  week-wide bar lane that pushed every other day's timed chips down
  the row. `WeekBucketer` now skips single-day all-day events so they
  render inline as the first chip of their own cell; multi-day all-
  day events continue to render as continuous bars across the days
  they cover. Cells now sort all-day events above timed events.

### Added
- Working days dim in Day and Week views, mirroring the working-hours
  treatment on the day axis. New "Show working days" toggle in
  Settings > Appearance plus a working-days picker (FilterChips, one
  per day of week, defaults to Mon-Fri). When enabled, non-working
  days in Week view get a full-column dim (and their header label
  too); the Day view dims the full timeline if the visible date is a
  non-working day. The whole-column dim suppresses the working-hours
  band so a column doesn't double-dim. Today highlight stays bright
  even when today is a non-working day. Off by default. Empty / invalid
  working-days values coerce to Mon-Fri on read so a corrupt write
  can't dim every day.

### Fixed
- Linkified URLs in event notes and location now capture the full URL
  instead of truncating after the first match-anchor character. The
  regex used a non-greedy quantifier followed by a single-character end
  class, which caused `https://github.com/Arishawke/asala-calendar` to
  match as little as `https://gi`. Greedy match with a post-trim of
  trailing punctuation (`. , ; : ! ? )`) now produces the expected
  full link.
- Working hours dim band is now visible in both light and dark themes.
  The previous overlay used `surfaceVariant @ 50% alpha` which only
  differed from `surface` by ~5-7% in light theme and was effectively
  invisible in dark / AMOLED (where `surfaceVariant` is pure black on a
  near-black surface). Switched to `Color.Black @ 12% alpha` so the
  band uniformly darkens both themes without overwhelming the work
  block.
- Smart re-seed on the editor's all-day toggle no longer mutates an
  existing event's saved reminder. The re-seed was meant for the
  new-event flow (so flipping to all-day swaps the timed default for
  the all-day default); a saved reminder that coincidentally equalled
  the current timed default was getting silently swapped on toggle.
  Gated behind a new `isNewEvent` flag in the form state.

### Added
- Default reminder settings in Settings > Notifications. Separate
  pickers for timed events and all-day events, both defaulting to
  None. New events seed `reminderMinutesBefore` from the timed
  default at editor open. Flipping the all-day toggle inside the
  editor smart-rebases the reminder: if the current value still
  matches the previous default, it switches to the new default;
  if the user picked something custom, it's left alone. Avoids the
  classic 11:45pm-the-night-before reminder that a timed default
  produces on an all-day event.
- Working hours dim in Day and Week views. New "Show working hours"
  toggle in Settings > Appearance with a start / end hour picker
  (defaults to 9:00 - 17:00, 24-hour aware). When enabled, hours
  outside the work day render with a half-opacity overlay over the
  hour grid; events drawn during non-working hours stay full-opacity
  on top, so the work block anchors the viewport without hiding
  off-hours meetings. Disabled by default. Past-date dim and the
  now-line remain unchanged.
- Event STATUS rendering. Asala already wrote `STATUS_CONFIRMED` on
  create and `STATUS_CANCELED` on cancellation but never read the
  column back, so tentative or cancelled events imported from CalDAV
  rendered identically to confirmed ones. The instance + detail
  projections now include `STATUS`; tentative events render with an
  italic title, cancelled events render with a strikethrough title at
  50% container opacity, and the detail sheet shows an explicit
  "Tentative" or "Cancelled" badge below the title. Past-date dimming
  multiplies on top of the cancelled opacity unchanged. Older imports
  with no STATUS value fall through to confirmed.
- Custom reminder offset. The reminder dropdown in the event editor
  now has a "Custom…" entry that opens a small dialog with a numeric
  input plus a Minutes / Hours / Days unit selector. Pick any whole
  value up to 999 of the chosen unit; the result is stored as
  minutes-before and the label renders back as "3 h before",
  "2 days before", etc. Preset values (60 → "1 hour", 1440 → "1 day")
  still match their existing labels.

### Fixed
- Plain-text URLs, email addresses, and `tel:` links in event notes
  and the location field are now tappable. The detail sheet's
  description already linkified `<a href>` tags inside HTML-bearing
  synced invites; plain-text descriptions (CalDAV,
  hand-edited) and the location field were rendered as inert text.
  Both now route through a shared linkifier and pick up the same
  theme primary color + underline styling as the HTML branch. Bare
  emails are wrapped as `mailto:` at click time. Phone numbers
  without an explicit `tel:` prefix are intentionally not detected
  (false positives on dates, addresses, event IDs).

## [0.10.0] - 2026-05-26

### Added
- Header dropdown for in-place date navigation. Tap the title in the
  top app bar (or the chevron next to it) to expand an inline panel
  below the bar that pushes the rest of the UI down instead of
  overlaying it. In Month view the panel is a horizontal scroller of
  "MMM yy" chips spanning twelve months either side of today; tap a
  chip to jump the pager to that month. In Week / Day / Schedule the
  panel is a mini month calendar with a `< Month Year >` nav row and a
  six by seven date grid; each day cell shows up to three colored
  dots representing the distinct calendars with events on that day,
  and tapping a day jumps the current view to that date. Today is
  highlighted in the mini-month with the existing today circle.
  System Back collapses the panel before any other handler.
- Current-time indicator across Day, Week, and Schedule views. A small
  filled dot on the left and a thin line extending to the right mark
  the current moment and tick forward every minute while a "today"
  surface is visible. Day view: line spans the full timeline width.
  Week view: scoped to today's column only (previously spanned the
  whole 7-day Row). Schedule view: inserted between past and future
  timed events inside today's section; all-day rows stay above the
  line since they span the whole day. Skipped entirely when today has
  no events. Color is monochrome `onSurface` (near-black on light
  themes, near-white on dark themes), replacing the prior red
  Week-view line.
- Hide synced accounts from the drawer. Each account header now has a
  3-dot menu with "Change account color" (previously avatar long-press)
  and "Hide from drawer". Hiding an account removes it from the drawer
  entirely and suppresses every one of its calendars' events. A
  "Hidden accounts" section appears in Settings whenever the set is
  non-empty; tap "Show in drawer" on any row to restore. Each
  calendar's checkbox state is preserved across the round-trip. The
  per-calendar 3-dot menu still offers Change color, Rename, and
  Delete; hide lives on the account header instead so a full account
  takes one tap rather than one per calendar.

### Added
- Settings: new "About" section listing the app version + build code,
  license (`GPL-3.0-or-later`), and a tappable source-code link that
  opens the GitHub repository.
- OEM battery-advisory list expanded to recognize Realme (ColorOS-based)
  and Honor (post-Huawei-split). Both also get explicit deep-link targets
  to their startup / background-restriction settings screens; if the
  manufacturer's screen is not resolvable, the advisory still falls
  back to the system per-app notification settings.

### Changed (UI polish pass, PR-F)
- Event detail sheet fields are now visually grouped into When,
  Where, Notes, and Reminders blocks, separated by horizontal
  dividers. Empty blocks (no location, no description, no reminder)
  collapse so the sheet stays tight on minimal events; recurring
  events surface their summary inside the When block. Spacing across
  the sheet now uses the design tokens introduced in PR-A.
- Drawer typography contrast: calendar names drop from `bodyLarge`
  to `bodyMedium` so they read one size below the account name
  they live under. Account names keep their existing `bodyLarge`
  size with `FontWeight.Medium`. Net effect: clear visual hierarchy
  between account header and the calendars it groups.
- Schedule date-section header bumps the weekday label from
  `titleSmall` to `titleMedium`, narrowing the size gap with the
  large day-number that sits beside it.

(PR-E of the UI polish pass turned out to be a no-op on verification.
Now-line dedup: there was only ever one implementation, in
`ui/timeline/NowLineMarker.kt`; all four timeline-aware screens
already imported it. Color resolver centralization: the data layer's
`filteredAndRecolored` is already the single resolver for chip
rendering. Documented in `docs/plans/2026-05-26-ui-polish-pass.md`.)

### Changed (UI polish pass, PR-D)
- Week and Day timeline event chips now route through the new
  `EventChipBlock` variant of the shared `EventVisual` primitive,
  matching the chip-cohesion model PR-C established for Month /
  Schedule / Search. The drag-to-reschedule gesture stays where it
  was (in `EventBlock.kt`); only the visual layer (clipped tinted
  surface + 3dp left color bar + title + optional time) moved into
  the shared primitive.
- Timeline chip background tint lowered from 30% to 18% calendar
  color over surface. Drag
  feedback still bumps to 55% so the in-flight chip reads as picked
  up. Net effect: title type wins over the color fill, color stays
  as the calendar identifier rather than the primary visual weight.
  Per-event color override still flows through unchanged.

### Changed (UI polish pass, PR-C)
- Unified event-chip rendering across Month, Schedule, and Search.
  `EventChip` and `EventListRow` (two separate composables that
  rendered the same logical thing two different ways) have been
  replaced by `EventChipCompact` and `EventChipRow` variants of a new
  `EventVisual` primitive. Color resolution and the chip layout
  vocabulary are now shared across views. Visual deltas: Schedule and
  Search list rows trade the 4dp x 36dp left color bar for an 8dp
  circular calendar-color dot leading the row; time text scales down
  from `labelMedium` to `labelSmall`, title from `bodyLarge` to
  `bodyMedium`. Month grid chips bump their left color bar from 3dp
  to 4dp and title type from `labelSmall` to `bodySmall`. The 48dp
  touch target from PR #57 is preserved on Schedule and Search rows;
  Month chip stays compact-by-design (the surrounding day cell is
  the broader tap target).

### Fixed
- Detail sheet notes now render HTML markup instead of showing literal
  tags. Some synced invites store the description as HTML
  (`<br>`, `<a href>`, `<b>`); the sheet previously displayed those
  tags verbatim. Descriptions are sniffed for a structural HTML tag
  and, when detected, parsed via `AnnotatedString.fromHtml` with
  `<a href>` rendered as a tappable link styled in the theme primary
  color. Plain-text descriptions (CalDAV, hand-edited) are routed
  around the parser so their newlines survive intact.
- Event detail sheet's calendar / event color override now refreshes
  while the sheet is open. Previously the sheet baked in an eager
  snapshot of the override maps at open time, so a calendar color
  changed by another app (e.g., a CalDAV sync push) left the chip and
  accent colors stale until the user closed and reopened the sheet.
  The displayed `EventDetail` is now derived via `combine(...)` of the
  raw detail plus the two override flows.
- Locale-aware month and day names across the four views. Week / Month /
  Day / Schedule titles and the month-overflow sheet now construct
  `DateTimeFormatter` instances with `LocalConfiguration.current.locales[0]`
  passed explicitly, matching the pattern the header dropdowns already
  used. The "+N more" overflow indicator is now a `plurals` resource
  instead of an English-only literal, so a future translation pass can
  pluralize correctly.
- Reduce-motion is honored on the header dropdown panel. When the user
  has disabled animations (system setting or in-app toggle), the panel
  reveals and the chevron rotation now snap to their final state instead
  of running the expand / fade / spin animations.
- Touch target for Schedule and Search event rows raised to the 48dp
  Android minimum (was ~30dp from the row's small vertical padding).
  Color-picker swatches keep their 28dp visual size but expand their
  tap region to 48dp via `Modifier.minimumInteractiveComponentSize()`,
  so the picker still fits compactly while meeting the touch-target
  floor.

### Changed
- Settings reorganized into five function-first sections: General,
  Appearance, Notifications, Calendars & accounts, About. Default
  view / week start / default duration / 24-hour time / tasks toggle
  collapsed under General. Dim-past-dates moved to Appearance.
  Hidden-accounts list collapsed under Calendars & accounts together
  with the DAVx5 sync pointer and storage mode. Section order matches
  Android settings conventions (function-first); the prior
  Notifications / Appearance / Week / General / Sync / Storage /
  Hidden ordering interleaved related controls.
- Settings now uses `LazyColumn` with `rememberLazyListState()`, so
  the scroll position survives the recompose triggered when granting
  or denying the notification permission. Previously the screen
  bounced to top on the permission callback.
- Title typography tightened slightly: `titleLarge` line-height reduced
  from 28sp to 27sp, `titleMedium` from 24sp to 22sp. Brings the top app
  bar title and section headers closer to display-style line-spacing
  rather than body-text spacing, while staying on the M3 type scale and
  the device default font. First step in a multi-PR UI polish pass.
- Week view's day-name header strip now leaves space for the hour-axis
  column at the left, so each day header aligns with its column below.
  Previously the seven headers split the full screen width but the
  columns underneath split only the post-hour-axis width, which shifted
  every day right under its header and clipped Sunday on the trailing
  edge.
- Account avatar long-press no longer changes the account color. The
  same action now lives in the account's 3-dot menu as "Change account
  color", consolidating account actions onto one affordance.

### Fixed
- Month view's "+N more" overflow indicator now actually appears when a
  day has more events than fit in its cell. The previous fixed
  three-chip cap rendered three chips plus a +N row regardless of the
  available cell height; on devices and densities where the cell could
  hold fewer than four rows, the +N row was pushed below the cell
  boundary and clipped, so dense days silently dropped their tail.
  Chip count is now derived from `BoxWithConstraints.maxHeight` divided
  by the chip row footprint, and one slot is reserved for the +N row
  whenever the day has overflow.
- Per-event color override. Pick a swatch in the new "Color" row of the
  event editor to override the calendar's color for just that event.
  Recurring events apply the override to every instance. Overrides
  persist via DataStore and never touch `CalendarContract`, so sync
  adapters cannot clobber them and the override works even on read-only
  synced calendars. The override flows through to chips, the detail
  sheet, search results, and reminder notifications.
- Radix Colors step 9 palette as an optional second swatch set. Eleven
  hues (Tomato, Orange, Amber, Grass, Teal, Cyan, Indigo, Violet, Pink,
  Crimson, Slate) tuned so every swatch clears WCAG 1.4.11 non-text
  contrast (>=3:1) against the base surface in at least one theme mode;
  some shine in light, others in dark. Pick which palette the recolor
  pickers show under Settings > Appearance > Color palette. Default
  stays Okabe-Ito (color-blind safe) so existing installs see no
  visual change. A "Custom" pip appears in the picker if a saved
  override color isn't in the active palette, so switching palettes
  never silently mutates saved data.
- "Reset to calendar color" action in the event editor's recolor
  dialog removes a per-event override and falls back to the calendar's
  color.
- Custom account avatar and per-calendar colors. Long-press an account
  avatar in the drawer to recolor it; pick "Change color" from the 3-dot
  menu on any calendar row to recolor the calendar. Both pickers use the
  existing 8-swatch Okabe-Ito palette. Overrides persist locally via
  DataStore and survive sync; for local calendars the new color is also
  written to `CalendarContract.Calendars.CALENDAR_COLOR` so other apps
  and exports see it. For synced calendars only the local override is
  written, so the sync adapter cannot clobber the user's choice. The
  override flows through to event chips, the event detail sheet, search
  results, and reminder notifications.
- Hash basis for the deterministic avatar color shifted from the account
  name alone to `"<accountType>:<accountName>"`. This is a one-time
  visual shift for existing installs: a given account may pick a
  different palette entry on next launch. Pick "Change color" on the
  avatar to restore the previous color manually.
- Cross-day drag-to-reschedule in Week view: long-press a timed event
  and drag horizontally (along with the existing vertical time shift)
  to move it to another day in the visible week. Day delta is snapped
  to the column on release and clamped to the week, so the chip never
  silently jumps into the adjacent week. Day shifts go through
  `ZonedDateTime.plusDays`, so spring-forward and fall-back keep the
  event at the same local wall-clock time on the new day. Day view's
  single-column layout naturally disables cross-day drag.
- Drag-to-reschedule in Day and Week views. Long-press a timed event to
  pick it up, drag vertically to shift it (snaps to 15-minute slots on
  release), and let go to save. Duration is preserved. Press-and-hold
  gates the drag so casual swipes still page between days or weeks.
  Recurring events show the existing this / this and following / all
  scope picker before saving; cancelling the picker discards the move.
  All-day events and cross-day horizontal drag are not yet supported.
- Timed events that cross midnight now render on every day they cover.
  Day and Week views show one chip per covered day, clipped to the
  visible day, with square corners on the cut edge to signal the
  continuation. Schedule view lists the event under each covered day
  with the per-day time slice. Month view continues to show the chip
  only on the event's start day so the dense grid stays readable.
- All-day events now render as continuous horizontal bars across the
  days they cover in Month view and in Week view's all-day strip,
  instead of only appearing as a single chip on the start day. Bars
  break at week boundaries with square cut edges and rounded natural
  ends; the event title repeats at the start of each week's segment
  so a continuation row remains identifiable. Overlapping multi-day
  events stack on separate lanes per week via a longest-first greedy
  lane assignment.
- Schedule view shows multi-day all-day events on each day they cover,
  with a "Day N/M" badge appended to the title so the user can see
  which day of the span they are looking at. Single-day events are
  unchanged.

### Changed
- The `applyCalendarColorOverrides` extension on `List<EventItem>` was
  replaced by `applyColorOverrides(calendarOverrides, eventOverrides)`
  on every view model that loads events (Month, Week, Day, Schedule,
  Search). A `filteredAndRecolored(hidden, calendar, event)` helper
  now composes the hidden-calendar filter with the override remap so
  the five view models share one pipeline rather than five copies.
- `UserPrefs` is `@Immutable` so the Compose smart-recomposer can
  skip on equality. The override-map fields were already effectively
  immutable by construction; the annotation makes that explicit.
- Month view now stacks each week as date numbers on top, the multi-day
  all-day bars below them, then the timed-event chips. Previously the
  bars sat above the date numbers, which pushed dates down and made the
  date row feel out of place. Bars stay continuous across the seven
  columns and click targets are unchanged.
- Storage mode onboarding subtitle now mentions that calendar
  permission is required next, regardless of which mode is chosen.
  All three modes (Local Only, Sync Only, Hybrid) use Android's
  system calendar storage, so the runtime permission is unavoidable.

### Fixed
- Per-event color overrides are now cleared from DataStore when the
  underlying event is deleted (scope: All events). Per-calendar
  overrides are cleared when the local calendar is deleted. Previously
  the override map grew unboundedly on delete churn, and any future
  event or calendar that reused the recycled `_ID` would silently
  inherit the stale color.
- DataStore write failures on the three color-override setters
  (account avatar, calendar, event) are now logged via Timber instead
  of silently swallowed, so a wrong-color complaint can be diagnosed
  from logs.
- Long-press on a Day or Week timed event without dragging no longer
  opens the detail sheet on release. Replaced the chip's click modifier
  with paired `detectTapGestures` + `detectDragGesturesAfterLongPress`
  pointerInput blocks, and pass a no-op `onLongPress` so the tap
  detector actually times out the press (without it, `detectTapGestures`
  sets the long-press timeout to `MAX_VALUE/2` and fires `onTap` on
  release regardless of how long the press was held).
- Drag-release no longer flashes the chip back to its original position
  before snapping to the new time. The snapped pixel offset is held
  through the Calendar Provider save round-trip; the chip's remember
  key resets atomically when the new start millis arrives. Cancelling
  the recurring scope dialog drops the offset via a new
  `LocalDragRevertSignal`, so the chip snaps back instead of getting
  stranded at the dragged position.

## [0.9.0] - 2026-05-24

### Added
- AMOLED theme option in Settings (System / Light / Dark / AMOLED
  black). Reuses the Material dark scheme for chips, text, and
  accents but forces every surface tone to pure black so OLED
  panels can power-gate pixels. Existing Dark mode is unchanged
  at the M3 near-black `#211F26` family.
- ADR-0004 documents the quality-gates initiative for Asala, the
  per-project application of the universal gates defined in
  an internal quality-gates reference. Three phases (A: additive low-
  risk, B: test infrastructure, C: supply-chain). Phases A and B
  landed in this release; C is in flight.
- ADR-0005 documents the migration from OWASP Dependency-Check to
  GitHub-native dependency scanning (`gradle/actions/dependency-
  submission` + `actions/dependency-review-action` + the existing
  Dependabot). Supersedes ADR-0004 §Phase C.
- Phase A static-analysis gates: Spotless 8.5.1 + ktlint 1.5.0
  (style), detekt 1.23.8 with baseline (smells and complexity),
  slack/compose-lints 1.4.3 (Compose footguns), Compose Compiler
  stability reports (recompose visibility). All run in CI on
  every PR.
- Android lint flips: `HardcodedText`, `ContentDescription`, and
  `RtlHardcoded` promoted from warning to error.
  `MissingTranslation` was already error by default. Existing
  Compose-lint findings (40 across 7 rule ids, mostly
  `ComposeUnstableCollections` and `ComposeViewModelInjection`)
  are baselined as follow-up work; new violations fail CI.
- `.github/pull_request_template.md` per the Addy-Osmani "PR
  contract" pattern (intent, risk tier, AI contribution
  disclosure, test plan, threat model if risk-tier >= medium,
  code-reviewer subagent checkbox, PR size, verification).
- Phase B coverage gate: Kover 0.9.8 (JetBrains official Kotlin
  coverage) wired at root and `:app`. Baseline overall line
  coverage approximately 12 percent (Asala's test suite is pure-
  math / data-layer focused; UI coverage is intentionally low).
  No threshold enforcement yet per ADR-0004 §"NOT adopting"; the
  data is being collected first. Reports live at
  `app/build/reports/kover/html/index.html` after
  `./gradlew koverHtmlReport`; CI runs `koverXmlReport` on every
  PR and uploads the result as an artifact.

### Fixed
- Tapping a reminder notification no longer silently drops the open
  if Android recreates `MainActivity` between intent receipt and
  Compose tree consumption (rotation, dark-mode flip, process death
  + restart). The pending event id and instance millis are now
  persisted across `onSaveInstanceState`.
- Recurring-event reminder notifications no longer show
  "1 January 1970" as the end time. The alarm receiver previously
  projected only `DTEND`, which is null on recurring rows (the
  provider stores `DURATION` instead). The receiver now reads both
  columns and routes them through `EventEndMillis.compute`, matching
  the detail-sheet path.
- `[Unreleased]` and intermediate-version compare links in the
  CHANGELOG footer are no longer stuck at `v0.4.0`. Added links
  for v0.4.1, v0.4.2, v0.5.0, v0.6.0, v0.7.0, v0.8.0; `[Unreleased]`
  now compares against `v0.8.0...HEAD`.
- Dependabot's `androidx` group used to swallow `androidx.compose.*`
  artifacts because Dependabot resolves overlapping group patterns
  by first match (YAML-order-dependent). Added an `exclude-patterns`
  entry under the `androidx` group so Compose dependencies land in
  their own grouped PR as intended.
- Removed the dead `kotlin-android` plugin alias from
  `gradle/libs.versions.toml`. AGP brings Kotlin support
  transitively; nothing in the build references this alias. Cuts
  spurious Dependabot churn on Kotlin bumps that no consumer
  exercises.

- Per-instance recurring-event edits now attach the reminder to the
  new exception or split event, not the original parent series. The
  prior behavior silently overwrote the parent's reminders on a
  `ThisInstance` or `ThisAndFollowing` save, so the new exception
  shipped without a reminder and other instances lost theirs.
  `EventRepository.updateEvent` now returns the row id that reminders
  should target (`Long?`), and `EventSave` routes the reminder write
  to that id.
- All-day events no longer ghost-appear on the day after their last
  visible day in Day and Week views. CalendarContract stores all-day
  `endMillis` as start-of-day-after (exclusive); the per-day and
  per-week filters were treating it as inclusive. A new
  `EventItem.isVisibleIn` helper bundles the all-day exclusivity so
  the three call sites (DayScreen, WeekScreen, AllDayRow) stay
  consistent.
- Calendar permission gate now re-checks on `ON_RESUME`. Revoking
  calendar permission from system settings and returning to the app
  used to leave the gate stuck on "granted", which crashed the next
  ContentObserver subscription with `SecurityException`.

### Changed
- Event editor now auto-corrects end-side edits that would create an
  invalid range. Setting end date earlier than start date clamps end
  to start; setting end time at-or-before start time on a timed event
  rolls end forward to the next day at that time. Matches the
  established pattern. Start-side edits continue to delta-shift end
  to preserve duration, as before.
- Event detail sheet date ranges now use the platform's locale-aware
  interval formatter (`android.icu.text.DateIntervalFormat`), so the
  connector between start and end follows the device language instead
  of the previously hardcoded English `" to "`.
- CI build job now runs `spotlessCheck detekt` ahead of and
  `koverXmlReport` alongside the existing `lintDebug
  testDebugUnitTest assembleDebug` chain. Failures upload lint and
  detekt reports as artifacts; Kover XML uploads on every run (not
  just failures) so coverage trends are visible per PR.
- Hardcoded English strings `"all-day"` (Day view), `"OK"` and
  `"Cancel"` (recurrence end-date picker) replaced with existing
  `stringResource` references so non-English locales display them
  translated.
- Schedule and Search now share a single `EventListRow` composable
  at `ui/components/EventListRow.kt` (previously two near-identical
  per-screen copies). Palette and accessibility changes apply in
  one place.
- ROADMAP.md style swept to match the project's no-em-dashes rule
  and stale "v0.6.0 (in progress)" header updated to reflect the
  v0.6.0 ship date (2026-05-22).
- PR template lightened to fit a solo / hobby / offline-first
  scope: dropped the mandatory STRIDE Threat Model section and the
  mandatory code-reviewer subagent checkbox. The code-reviewer
  subagent stays available as an opt-in tool; it just is not a
  merge gate. Risk-tier remains as a one-glance label; High-risk
  changes now request a brief inline security note instead of a
  full STRIDE table. ADR-0004 §"NOT adopting" tightened by trimming
  Roborazzi and Gradle-verification-metadata failure archaeology
  to their actionable "revisit when X" conditions. Added a
  Calibration section to project CLAUDE.md naming the lighter
  scale-of-process the project is calibrated to.
- CI concurrency groups in `dependency-graph.yml` and
  `dependency-review.yml` now include `github.event_name` alongside
  `github.ref`, so push and workflow_dispatch events on the same ref
  do not collide and cancel each other.

### Security
- Scoped `gradle/actions/dependency-submission` to the
  `(debug|release)RuntimeClasspath` configurations via the action's
  `dependency-graph-include-configurations` input. Without this, the
  submitted graph included Gradle and AGP plugin internals (Netty,
  BouncyCastle, jose4j, commons-lang3, httpclient, jdom2), which do
  not ship in Asala's APK but were generating Dependabot alerts.
  Same scope decision as PR #21's OWASP Dependency-Check setup;
  ADR-0005 captures the rationale. Tracking upstream `gradle/actions`
  issue #815 in case this becomes a future Android default.
- Replaced OWASP Dependency-Check with two GitHub-native
  workflows: `.github/workflows/dependency-graph.yml` runs
  `gradle/actions/dependency-submission@v6.1.0` on push to main
  and weekly Monday 06:00 UTC (submits the resolved transitive
  tree so Dependabot alerts against the real graph, not just the
  version catalog). `.github/workflows/dependency-review.yml`
  runs `actions/dependency-review-action@v5.0.0` on every PR with
  `fail-on-severity: moderate` and an SPDX `allow-licenses`
  whitelist (GPL-3.0-or-later + permissives). OWASP DC consistently
  added 15-30 minutes to each PR for a single-user offline app; the
  signal it returned was already covered by Dependabot. Removed:
  `.github/workflows/security-scan.yml`, `config/owasp/`, the
  `dependencyCheck { }` Gradle block, the OWASP plugin entries in
  `gradle/libs.versions.toml`, and the `NVD_API_KEY` GitHub Actions
  secret. Both new actions pinned to full 40-char commit SHAs.
  Rationale in ADR-0005.

### Notes
- Roborazzi 1.63.0 (screenshot tests) was attempted as part of
  Phase B and deferred per ADR-0004 §"NOT adopting". Diagnosis:
  Roborazzi 1.63.0 plus ComposablePreviewScanner-android 0.9.0
  cannot discover `@Preview` functions on the unit-test runtime
  classpath under AGP 9.1.1's built-in Kotlin pipeline. Roborazzi
  PR #782 (1.56.0) addressed task plumbing but not preview-scanner
  classpath wiring; nothing in 1.57.0 through 1.63.0 fixes it.
  Revisit when Roborazzi explicitly notes AGP 9 built-in Kotlin
  support in a release. Tracking: Roborazzi issue #830.
- Gradle dependency verification metadata (originally Phase C2)
  was attempted and deferred per ADR-0004 §"NOT adopting". Two
  blockers: AGP's `aapt2` binary resolves via a detached
  configuration that bypasses the `--write-verification-metadata`
  capture mechanism (every build then fails verification on the
  unrecorded artifact), and per-Dependabot-PR friction would be
  ongoing rather than one-time. The asymmetric cost / benefit
  ratio is unfavorable for Asala's offline single-user threat
  model. Generated bootstrap files added to `.gitignore` so they
  do not accidentally land in commits. Revisit when (a) Gradle /
  AGP solve aapt2 capture upstream, OR (b) Asala adds a remote
  backend.

## [0.8.0] - 2026-05-23

### Fixed
- Launch no longer crashes with `SecurityException` on a fresh install. The
  v0.8.0 hidden-ids fix made `hiddenCalendarIdsFlow` combine in
  `calendarRepo.observeCalendars()` but left the flow `SharingStarted.Eagerly`,
  so the underlying `ContentObserver` tried to register against CalendarProvider
  before the permission gate had run. `hiddenCalendarIdsFlow` now uses
  `WhileSubscribed(5_000)` (matching `uiState`); `ContentResolverFlow.observeChanges`
  also wraps `registerContentObserver` in a `SecurityException` guard as
  defense in depth (established upstream-defensive pattern).
- Reminder PendingIntent request codes are now derived from a stable string
  hash that includes the full 64-bit event id and instance time, not from
  `Long.toInt()`. The old code truncated the upper 32 bits, so two distinct
  event instances roughly 49.7 days apart could collide on the same request
  code and `FLAG_UPDATE_CURRENT` would silently replace the older alarm or
  notification action. The scheduler's existing format is preserved bit for
  bit so alarms armed by prior builds keep firing after an in-place upgrade.
- Manual drawer toggles for calendars survive Storage-mode switches. The
  old code wrote mode-driven hides (Local only blanking the sync calendars)
  into the same persisted set as the user's manual choices, so a Local only
  -> Hybrid -> Local only round trip wiped per-calendar preferences. Mode
  hides are now derived on read in `StorageModeFilter` from the live
  calendar list; only user toggles are persisted.
- ReminderAlarmReceiver and BootRescheduler now wrap their `goAsync()`
  coroutine bodies in `runCatching { ... }.onFailure { Timber.e(...) }`.
  An unexpected provider hiccup or malformed alert row could previously
  surface as an uncaught exception inside the receiver's `try/finally`
  block and crash the process; throwables are now logged instead.
- Snooze no longer leaks orphan SCHEDULED rows in CalendarAlerts. The old
  code dismissed only the alert id passed in the intent and inserted a new
  STATE_SCHEDULED row each time, so every re-snooze grew the table by one
  unprunable row for the same event+instance. Before inserting the new
  row, NotificationActionReceiver now marks any pre-existing SCHEDULED
  rows for that event+instance as DISMISSED via a WHERE-scoped update.

### Internal
- One source of truth for the local "Asala" calendar identity: account
  name, display name, and default color now live in `data/LocalCalendar`.
  `CalendarRepository`, the permission gate, and `SettingsViewModel` all
  point at it.
- `StorageModeSetup.ensureLocalCalendarIfNeeded` unifies the calendar-
  ensure logic the permission gate and Settings both used to inline.
- `EventEditViewModel.Factory` no longer does a second `runBlocking` to
  read the storage mode. The editor reads it off `appViewModel.prefs`
  (already populated by the single startup blocking read in
  `AppViewModel.Factory`).
- New pure helpers extracted from formerly-inline logic so the partial-
  failure save flow, recurring cancellation row, DTEND-from-DURATION
  fallback, snooze alert-id resolution, and storage-mode calendar
  filtering are unit-testable: `EventSave`, `EventCancellation`,
  `EventEndMillis`, `SnoozeResolution`, `StorageModeFilter`,
  `EventEditCalendarPicker`.
- `MainActivity` (469 -> 71), `CalendarDrawer` (459 -> 199), and
  `SettingsScreen` (430 -> 183) each split into smaller files per the
  200-line convention.

### Tests
- Six new unit-test files covering the helpers above. Total
  test files goes from 8 to 14, total cases from 14 to 36.

### Notes for upgraders
- v0.7.0 users in Local only had sync-calendar IDs persisted in
  `hiddenCalendarIds` by the old write path. Switching to Hybrid on v0.8.0
  will still leave those calendars hidden until they are un-hidden via the
  drawer once. After that, they will follow mode-driven visibility
  correctly.

## [0.7.0] - 2026-05-23

### Added
- Event editor calendar picker now respects the storage mode. Local only
  shows just the local "Asala" calendar; Sync only hides it; Hybrid shows
  both. Prevents creating an event on a calendar that the chosen mode is
  meant to hide.
- Timber logging in debug builds. Plants `Timber.DebugTree()` from
  `AsalaCalendarApplication.onCreate` when `BuildConfig.DEBUG` is true.
  Strategic log points at app startup, permission grants, storage mode
  switches, alarm fires, snooze receiver dispatch, and snooze applied.
  Release builds plant nothing; calls compile to no-ops. Grep recipe
  documented in `docs/CODE_TOUR.md`.
- Local calendars can be renamed from the drawer's kebab menu. Reuses the
  same dialog pattern as the create flow.
- .github/FUNDING.yml adds a GitHub Sponsor button pointing at Ko-fi.
- Snooze picker's Custom option now opens an inline minutes input field
  with number-only keyboard. Previously Custom auto-picked 90 minutes
  with no way to choose.
- Sync only storage mode now filters local calendars out of the drawer,
  symmetric to Local only. Nothing is deleted; switching back to Hybrid
  restores them.

### Fixed
- Snooze actions now actually work. Root cause was a SQLiteException
  ("ambiguous column name: _id") in the CalendarAlerts query because the
  provider joins CalendarAlerts with view_events under the hood. Switched
  readAlert and markAlertState to URI-scoped operations via
  ContentUris.withAppendedId so the WHERE clause is gone.
- "Snooze for..." notification action now launches the picker directly
  rather than going through a broadcast trampoline. Android collapses the
  notification shade automatically when the activity starts; previously
  the picker rendered behind the still-open shade.
- Local-only mode now filters synced calendars out of the drawer entirely
  rather than dropping them into a "Hidden" section. The calendars are
  not deleted, just out of sight; switching to Hybrid restores them.
- Reminder notification small icon (status bar) is now a proper Material
  calendar silhouette instead of the launcher icon. The launcher icon
  rendered as a featureless circle once Android stripped the color.

### Added
- First-run storage mode picker. Choose Local only (private calendar on this
  device), Sync only (use existing synced calendars), or Both. The choice is
  persisted and can be switched any time in Settings under the new Storage
  section. Switching is non-destructive: sync calendars are hidden rather than
  deleted; the local "Asala" calendar is created on demand and kept across
  switches.
- Tasks (experimental) toggle in Settings > General. Off by default. When on,
  a Tasks tab appears in the drawer and opens a Coming Soon placeholder. The
  full Tasks feature lands in a later release; this ships the opt-in wiring.
- Code tour at docs/CODE_TOUR.md. A single audit-focused guide aimed at
  technical readers who want to verify the codebase: reading order, wiring
  diagram, the tricky parts to watch for, what is and is not tested, and
  the verification commands. Written specifically with auditors of
  AI-assisted code in mind.
- Debuggability assessment under docs/specs/. Single-module is the right call
  at this size; the biggest gap is the absence of any logging. Three
  prioritized recommendations follow.

### Fixed
- Launch screen no longer flashes white on phones using system dark mode. The
  app theme now resolves through values/ and values-night/ so the first frame
  matches system theme before Compose mounts.
- BootRescheduler no longer crashes after a fresh install or app upgrade. It
  now checks READ_CALENDAR permission before querying the Calendar Provider,
  matching the pattern used for the calendar observer.
- Snooze picker no longer crashes when a duration is selected. The picker now
  uses a Compose AlertDialog inside a transparent full-screen Activity instead
  of a ModalBottomSheet inside a floating-dialog Activity. The most common case
  is handled by a new "Snooze Nm" notification action that uses the user's
  default snooze without opening the picker at all.
- Day-range math now has explicit regression tests for events that begin in
  the final minute of a day (e.g. 23:59). The existing math was already
  correct; the new tests lock in the half-open boundary so future refactors
  cannot regress it. The user-reported ghost event near midnight has a
  different root cause and remains under investigation.
- Dismissing a reminder notification no longer closes the app when the app is
  open. The dismiss action's PendingIntent now carries EXTRA_EVENT_ID directly,
  so the notification cancel does not depend on a provider round-trip that could
  race or return null before the cancel fires.
- Dismiss now reliably clears the notification from the shade. The cancel call
  is made before the CalendarAlerts state write, so the notification disappears
  even if the provider write is slow or blocked by a ContentObserver callback.
- Snooze picker no longer shows MainActivity behind it. The picker activity
  theme changed from Theme.Translucent.NoTitleBar to
  Theme.Material.Light.Dialog.NoActionBar, making it opaque and modal.
  The singleTask launch mode was removed; the picker is a one-shot activity and
  the default standard mode is correct.
- Selecting a snooze option no longer crashes the app. The bottom sheet now
  animates closed (sheetState.hide()) before the activity finishes, preventing
  Activity teardown from racing the Compose animation scope.

### Added
- Reminders fire from Asala. Set a reminder on any event and
  it alerts at the chosen offset, on schedule, whether or not
  a sync adapter (or any other calendar app) is installed.
  Heads-up notifications include event title, time range,
  calendar color, and location.
- Notification snooze. Tap Snooze on a reminder to pick 5 /
  10 / 15 / 30 minutes, 1 hour, or a custom value. The choice
  becomes a one-tap default for the rest of the session;
  picker reappears on the first snooze of the next session.
- Settings > Notifications: status indicator, default snooze
  duration, and a recovery path if you previously denied the
  notification permission.
- One-time background-reliability advisory for Samsung,
  Xiaomi, OnePlus, Huawei, Oppo, and Vivo devices, with a
  deep link to the manufacturer's battery settings. Skip if
  your phone doesn't aggressively kill background apps.
- Permission rationale dialog before the first POST_NOTIFICATIONS
  system prompt explains why Asala needs to post notifications.
  If declined, reminders still save but the event detail sheet
  shows a banner explaining notifications are off, with a
  one-tap path to re-enable.

### Changed
- Dark theme lifted from Material 3's default near-black
  surface (`#141218`) to a proper dark grey (`#211F26`,
  with the rest of the surface family lifted to match).
  Previously the Dark setting read closer to AMOLED than
  to standard dark mode on most devices. Pure-black is
  reserved for a future AMOLED option (see ROADMAP).
  Dynamic-color accents (Android 12+) are preserved; only
  the surface roles are overridden.
- All-day event reminders now fire at 09:00 local on the
  offset day, instead of midnight. Matches the standard
  calendar-app behavior and avoids the "my reminder rang at
  midnight" surprise.

### Security
- Added `USE_EXACT_ALARM` (install-time grant, calendar app
  carve-out per Google Play policy) and `RECEIVE_BOOT_COMPLETED`.
  `SCHEDULE_EXACT_ALARM` declared with `maxSdkVersion="32"`
  so Android 13+ devices never request it. Deliberately omits
  `FOREGROUND_SERVICE`, `WAKE_LOCK`, and
  `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` to keep the
  permission surface minimal.

## [0.6.0] - 2026-05-22

### Added
- Full-text event search across title, location, and notes.
  A new search icon in the top app bar opens a dedicated
  search screen with results grouped by date; tap a result
  to open the event detail sheet.
- Create local calendar from the drawer. A "+" next to the
  "Calendars" header opens a dialog where you pick a name
  and a color; the calendar lives entirely on this device
  with no account required. Closes the gap that previously
  required a third-party calendar app to seed an offline calendar.
- "Sync" section in Settings with a DAVx5 pointer for
  CalDAV servers (Nextcloud, Fastmail, mailbox.org, posteo,
  Disroot, self-hosted Radicale, etc.). Tap to launch
  DAVx5 if installed, or install it from Play / F-Droid.
- Delete a local calendar from the drawer. Each
  local-account calendar gets a three-dot menu with a
  "Delete..." option that prompts for confirmation.
  Cloud / CalDAV calendars are managed by their sync
  provider so they stay without a kebab.
- Week view: swipe horizontally anywhere on the page to
  move to the next or previous week. Previously only the
  small day-name strip at the top registered the swipe.

### Changed
- Day and Week views: overlapping events now lay out in
  side-by-side columns so every event is visible and
  tappable. Previously dense days like a six-event 14:00
  to 17:30 cluster stacked events on top of each other and
  buried titles behind later events.
- Hour rows are taller (72dp, up from 56dp) so the
  timeline reads with more breathing room. The all-day row
  and now-line are unchanged.
- Day view no longer repeats the date inside the page; the
  top app bar's date label is the single source.
- Schedule view: all-day events now lead each day, with
  timed events following in start-time order.
- Week view swipe follows your finger and snaps with a
  spring fling, matching Day and Month.
- Account avatars and the new local-calendar color picker
  use the Okabe-Ito (Wong) color-blind-safe palette
  recommended by Nature Methods, replacing a brand-color
  palette whose adjacent hues failed deuteranopia.

### Fixed
- DAVx5 settings row now launches DAVx5 directly when
  installed. Previously the lookup returned null on
  Android 11+ because the manifest didn't declare
  package visibility, so the row always fell through to
  the Play Store even when DAVx5 was already present.
- Event chips, all-day rows, and the Day all-day list now
  use the localized "(no title)" string for blank-title
  fallbacks. Previously three rendering sites used a
  hardcoded English literal that bypassed translation.
- Event search escapes SQL LIKE wildcards (`%`, `_`, `\`)
  in user queries; typing `100%` no longer matches every
  event.
- Settings DAVx5 launcher guards its F-Droid web-URL
  fallback against `ActivityNotFoundException` so a device
  with no browser doesn't crash.

### Performance
- Day, Week, and Month pagers pre-render one page on each
  side so swipe gestures pick up immediately instead of
  blanking for a frame.
- Month view composes faster. Marked `EventItem` as
  `@Immutable` so Compose can skip recomposition of the
  42-cell grid when nothing changed, and dropped a
  redundant `BoxWithConstraints` measure pass that ran
  on every recompose.

## [0.5.0] - 2026-05-22

### Added
- Tap anywhere on a Month cell (the date number, the empty
  area, or an event chip) to jump to Day view at that date.
  Month view is now navigation-only; event detail sheets are
  reached by tapping events in Day, Week, or Schedule view.
- System Back from Day view returns to the previous view
  (e.g., Month) when the user arrived there via a Month-cell
  tap. Manually switching views from the drawer clears this
  history, so Back never strands you on a stale screen.
- Drawer now groups calendars by account, with a clickable
  account header per group (avatar, email, account type)
  that collapses or expands its calendars. The collapsed-or-
  expanded state for each account persists across app
  restarts.
- New "Dim past dates" toggle in Settings. When on, past
  days in Month and Week views render at reduced opacity so
  upcoming dates are easier to find at a glance. Default off.
- Tap "+N more" on a dense Month-view day to open a bottom
  sheet listing every event on that day. Each row taps
  through to the event detail sheet. Fixes the case where
  more than three events on one day were hidden behind the
  "+N more" label. Cell-tap navigation to Day view is
  unchanged.

### Fixed
- Calendar visibility toggles in the drawer now persist
  across app restarts. Previously the hidden-calendar set
  lived only in memory, so closing the app re-enabled every
  calendar on next launch.
- Save failures in the event editor now surface an inline
  error banner instead of silently closing the editor and
  discarding the user's work. The editor stays open so the
  user can adjust the form (for example, pick a different
  calendar) and retry.
- The event detail sheet now shows the tapped occurrence's
  date and time, not the parent series's anchor date.
  Previously, opening any later occurrence of a recurring
  event still rendered the series's original start.
- The calendar dropdown in the event editor now lists the
  owning account under each calendar name, disambiguating
  same-named calendars on multi-account devices (e.g., two
  "General" calendars from different accounts).
- DAVx5-synced accounts in the drawer now show "CalDAV" as
  their account-type subtitle instead of the raw
  `bitfire.at.davdroid` package id.
- Setting a reminder in the event editor no longer fails
  silently. If the Calendar Provider rejects the reminder
  insert (rare: permission revoked or account removed
  mid-save), the save now surfaces the inline error banner
  instead of returning success and leaving the user thinking
  the reminder is set.

### Accessibility
- Month and Week date cells now announce "today" and "past"
  as TalkBack state in addition to the existing colour cue.
  The "Dim past dates" toggle and the today-circle highlight
  previously communicated state by colour alone, which a
  screen-reader user could not perceive.

### Changed
- Internal: the event detail sheet's loaded `EventDetail` is
  now owned by `AppViewModel` instead of round-tripping through
  a callback into `MainActivity`. The event editor now runs in
  a scoped `ViewModelStoreOwner` so each open gets a fresh
  store and the prior session's state can no longer leak.
  `EventDetailViewModel` is removed (its single-fetch role
  collapsed into `AppViewModel.openEventDetail`).

## [0.4.2] - 2026-05-22

### Changed
- Replaced the placeholder launcher icon (two simple vector
  paths on a solid `#1F6FEB` background) with the Asala adaptive
  icon: foreground and background PNG at 432 x 432 in
  `drawable-xxxhdpi`. Adaptive-icon XML wrappers already
  reference the drawables by name, so no manifest or mipmap
  changes are needed. The foreground is pre-scaled to 75% of
  the 108dp canvas so the calendar shape fits inside any
  launcher mask (Pixel, Pro Launcher, Nova, etc.) without
  corner cropping.

### Fixed
- App now exposes "Asala Calendar" as the activity-level label,
  not just the application-level one. Surfaces that prefer the
  activity label (some launchers, the recents view on certain
  Android versions, share-sheet activity rows) previously fell
  back to the raw package id `com.arishawke.asala.calendar`.
  Mirrored `@string/app_name` onto the MainActivity element to
  match the AOSP launcher template. Third-party launchers that
  cache their own labels may need a manual rename reset.

## [0.4.1] - 2026-05-21

### Build
- Release builds now run through R8 with `isMinifyEnabled = true`
  and `isShrinkResources = true`. APK drops from 25 MB to 3.1 MB
  (87% reduction). Dex shrinks from 24 MB to 2.5 MB as R8 removes
  unused Compose, Material3, and androidx code. Comfortably
  smaller than typical Android calendar apps (which range from
  ~7 to ~30 MB on the Play Store). Smoke-tested on hardware:
  permission gate, month view, FAB editor, and writable-calendar
  default all work unchanged.

### Fixed
- Editing or viewing a recurring event no longer renders the end
  time as "Dec 31, 1969 7:00 PM" and no longer blocks the save
  button. Recurring rows store DURATION instead of DTEND, so the
  Calendar Provider returns DTEND = NULL. `fetchEventDetail` read
  that as 0, which the editor's end-after-start guard rejected.
  Now the detail fetch reads the DURATION column too and computes
  `endMillis = dtstart + parseIso8601Duration(duration)`. The
  parser accepts our `P{d}DT{h}H{m}M{s}S` shape plus the shorter
  RFC 5545 forms (`P1D`, `PT1H`, `P1W`) and the `P3600S` form
  (no `T` separator) that some sync adapters write back after a
  server round-trip.

### Changed
- `RecurrenceExceptionMath.originalInstanceTime(parent, instance)`
  collapsed: `instance` from `Instances.BEGIN` is already the
  DST-correct UTC ms the provider expects, so the wrapper that
  ignored `parentDtstart` is gone. The same value passes straight
  through `EventRepository.updateEvent` / `deleteEvent`, which no
  longer take `parentDtstart`.

### Fixed
- Editing a recurring event with "All events" scope now preserves
  the existing end-by-date, occurrence count, and INTERVAL. The
  prior code populated only FREQ from the loaded RRULE and rebuilt
  the rule from scratch on save, silently wiping a `;UNTIL=...`,
  `;COUNT=...`, or `;INTERVAL=2` from any series the user touched.
- Editing a recurring event from a specific instance now prefills
  the form with the instance's clock time, not the parent series's
  start. "Only this event" and "This and following" edits now
  attach to (or branch from) the right occurrence; "All events"
  still updates the whole series at the chosen time, matching
  the established recurring-edit-scope conventions.
- Delete "Only this event" on a recurring event now writes both
  the parent's calendar id and a DTEND on the cancellation row.
  The provider requires DTSTART + (DTEND or DURATION) on every
  Events insert and does not infer CALENDAR_ID from ORIGINAL_ID;
  the original code passed only DTSTART and CALENDAR_ID = 0, so
  the insert was silently rejected and the canceled instance
  reappeared on the next refresh. DTEND = DTSTART (zero-duration
  marker) is enough because the cancellation row is hidden.
- Event editor's calendar dropdown now hides read-only calendars
  (USA Holidays, subscriptions, foreign-attendee shares). Picking one
  previously caused the Calendar Provider to reject the insert
  silently, which the app surfaced only as a generic save failure.
  Filter follows the Calendar Provider's standard threshold:
  `CALENDAR_ACCESS_LEVEL >= 500` (CAL_ACCESS_CONTRIBUTOR).

## [0.4.0] - 2026-05-21

M3 milestone: full event CRUD on top of the existing read-only
views. Reminders are written to the Calendar Provider but
notification firing from this app lands in M4.

### Added
- Tap any event in Month, Week, Day, or Schedule to open a detail
  bottom sheet with the event's title, calendar, date and time
  range, location, description, recurrence summary, and reminder
  offset.
- Tap the FAB to create a new event. The editor has title,
  all-day toggle, start and end date and time, calendar selector,
  location, description, recurrence (daily / weekly / monthly /
  yearly with end-by-date or count), and one reminder offset
  (none, at time, 5 / 10 / 15 / 30 minutes, 1 hour, or 1 day
  before). Save writes through the Android Calendar Provider; the
  four views update automatically via the existing ContentObserver.
- Tap Edit in the event detail sheet to edit any event in the
  same form. Tap Delete and confirm to remove. Editing or
  deleting a recurring event opens a scope
  dialog with three options: "Only this event", "This and
  following events", or "All events". No option is preselected;
  the user must explicitly choose. Recurring exceptions are
  stored natively in the Calendar Provider via the original_id
  link, which keeps the data interoperable with other calendar
  apps.
- A single reminder per event is written to
  CalendarContract.Reminders. External calendar apps that share
  the calendar (any sync adapter that handles the Reminders
  table) will fire the reminder. Asala's own notification firing
  lands in M4.
- Full Settings screen reachable from the drawer's gear icon,
  replacing the prior theme-only dialog. Settings: System / Light
  / Dark theme, default view on cold start (Month / Week / Day /
  Schedule), week-starts-on (Sunday / Monday / Saturday / system
  default), and a 24-hour time toggle that applies to the time
  picker AND to all event time displays across the app.
- Permission gate now requests READ_CALENDAR and WRITE_CALENDAR
  together on first launch (the manifest already declared both as
  of 0.2.0; this slice wires the gate).
- Back gesture closes the event editor, settings screen, or
  detail sheet, rather than exiting the app.

### Build
- `logos/` directory and WSL `Zone.Identifier` extended-attribute
  files are now ignored, so design assets and Windows-side
  metadata cannot be staged accidentally.

## [0.3.0] - 2026-05-21

### Added
- A Today action in the TopAppBar (calendar icon, top right) jumps every
  view back to today with a smooth animated scroll. Available in Month,
  Week, Day, and Schedule views.
- `docs/ROADMAP.md` listing what's queued for upcoming milestones and
  longer-term ideas (widgets, CalDAV, holiday calendar, snooze, etc.).
  Not a commitment, just a living direction.

### Changed
- Month view swipe animation is now a 400 ms Material 3 standard-easing
  curve via Compose's `HorizontalPager`, replacing the kizitonwose
  `HorizontalCalendar`'s default snap. The transition is noticeably
  smoother; rapid-frame capture shows the animation
  duration roughly doubled, from ~160 ms abrupt snap to ~320 ms
  decelerated. The 6-row grid and OutDate-styled overflow week are
  rendered manually using kizitonwose's data types.
- Month view loads events for a one-month buffer on each side of the
  visible month so swiping to an adjacent month renders the new page
  fully populated immediately, rather than briefly showing empty
  cells while the new query runs in the background.
- Month view always renders six full-height week rows. Short months
  (April, June, September, November in 2026) now leak the next month's
  first week into the last row instead of leaving the bottom of the
  screen empty, matching the established six-week-row layout.
- Drawer is now the single navigation surface. Views (Month, Week,
  Day, Schedule), Calendars, and Settings live in one panel. The
  TopAppBar overflow menu is gone.
- Drawer swipe-to-open is disabled. The hamburger button and the scrim
  tap still open and close the drawer; back also closes it. Horizontal
  swipes no longer steal week-to-week or month-to-month navigation.
- All user-facing strings extracted to `res/values/strings.xml` so the UI is
  ready for localization. Affects the top bar, view switcher, Settings
  menu, theme dialog, drawer headers, permission gate, schedule empty
  state, and event chips.

### Fixed
- Drawer scrim tap now closes the drawer. Material 3's
  `ModalNavigationDrawer` gates the scrim's click handler behind the
  `gesturesEnabled` flag, so setting it to a constant `false` (to block
  accidental swipe-to-open) silently disabled scrim tap as well.
  `gesturesEnabled` is now `drawerState.isOpen`, which blocks swipe
  gestures when the drawer is closed and allows scrim tap (plus
  swipe-to-close) when it is open.

### Security
- `android:allowBackup` set to `false`. Calendar data lives in the Android
  Calendar Provider (which has its own backup policy); the app's only
  local data is a single theme preference. No need for the per-file
  backup/extraction rule XML, so both have been removed.

### Build
- Lint config now disables `OldTargetApi`, `GradleDependency`, and
  `AndroidGradlePluginVersion`. These checks fire based on the build
  environment's installed Android SDK and on Maven metadata fetched at
  build time, so they drift between a developer machine and CI runners,
  breaking CI on purely informational upstream-version hints. Lint
  baseline regenerated to four stable entries.
- CI uploads the full lint report as an artifact when the build fails,
  so future drift is easier to diagnose.
- Lint runs in strict mode (`warningsAsErrors = true`, `abortOnError = true`)
  with a checked-in `app/lint-baseline.xml` covering 11 pre-existing
  issues (outdated dependency hints, missing monochrome launcher icon,
  obsolete `mipmap-anydpi-v26` qualifier, deprecated `allowBackup`
  attribute on Android 12+). New lint issues now fail the build.

## [0.2.0] - 2026-05-21

### Added
- Reduce-motion preference is now honored: view-switch animations become
  instant when the system "Remove animations" toggle is on.
- Calendar drawer rows pinned to a 48 dp minimum height for Material's
  touch target standard.
- `WRITE_CALENDAR` and `POST_NOTIFICATIONS` declared in the manifest;
  the permission gate requests READ and WRITE together.
- JVM unit test suite covering DST query-window math
  (`DayRangeMathTest`).
- Architecture Decision Records under `docs/adr/`:
  ADR-0000 (record decisions) and ADR-0001 (CalendarContract as the
  single data layer).
- Editor and git baseline: `.editorconfig`, `.gitattributes`,
  `.gitmessage`, `CHANGELOG.md`, `CONTRIBUTING.md`, `SECURITY.md`.
- GitHub Actions CI workflow (`lintDebug`, `testDebugUnitTest`,
  `assembleDebug`) on push to main and PRs against main. Actions
  pinned to commit SHAs.
- Manual theme override (System, Light, Dark) persisted via DataStore
  Preferences. Available under the Settings entry in the TopAppBar
  overflow menu.
- `@Preview` composables on leaf widgets (`DayCell`, `EventChip`,
  `AllDayChip`, `EventBlock`, `CalendarRow`) with light and dark
  variants.

### Changed
- `EventRepository` day-boundary math extracted to a dedicated
  `dayRangeMillis(...)` helper so it is unit-testable.
- Today highlight and Week-view now-line migrated to semantic
  `CalendarTokens` color roles in `ui/theme/Color.kt`.
- `WeekScreen.kt` split into four files (`WeekScreen`, `AllDayRow`,
  `TimelineGrid`, `EventBlock`), all under 200 lines.
- README expanded with CI badge, Configuration, Tests, and
  Contributing sections.

### Fixed
- Cold-start flash of the wrong theme when the saved preference
  differed from the system theme. `AppViewModel.Factory` now reads
  the persisted `ThemeMode` synchronously, so the first composition
  paints the chosen colors.
- System status bar and navigation bar icons no longer stay
  white-on-light (or dark-on-dark) when the manual theme override
  diverges from the system theme. `AsalaCalendarTheme` syncs
  `WindowInsetsController.isAppearanceLightStatusBars` (and the
  navigation-bar equivalent) on every theme change.
- Crash on first launch when the calendar permission had not yet been
  granted. The Phase E refactor moved `AppViewModel` construction
  above the permission gate, which caused `observeCalendars()` to
  register a `ContentObserver` on `CalendarContract` before the user
  could grant access. `themeMode` is now exposed as its own
  `StateFlow` so the theme can be applied without subscribing to
  `uiState`; the calendar-touching `uiState` collection moved into
  `AppShell`, which only renders after permission is granted.

## [0.1.0] - 2026-05-20

### Added
- M0: Compose application scaffold on Gradle 9.3.1, Kotlin 2.3.21,
  Material 3, Compose BOM 2026.05.00. minSdk 28, targetSdk 36.
- M1: Read-only Month view backed by the Android Calendar Provider,
  with per-calendar color chips, an overflow indicator, today
  highlight, dimmed leading and trailing days, and a side drawer to
  toggle calendar visibility.
- M2: Week, Day, and Schedule views with a TopAppBar view switcher.
- Material 3 dynamic color on Android 12+, light / dark / system theme.
- Live updates from the Calendar Provider via ContentObserver.
- Branding as Asala Calendar; GPL v3 license; README.

[Unreleased]: https://github.com/Arishawke/asala-calendar/compare/v0.21.0...HEAD
[0.21.0]: https://github.com/Arishawke/asala-calendar/compare/v0.20.0...v0.21.0
[0.20.0]: https://github.com/Arishawke/asala-calendar/compare/v0.19.0...v0.20.0
[0.19.0]: https://github.com/Arishawke/asala-calendar/compare/v0.18.0...v0.19.0
[0.18.0]: https://github.com/Arishawke/asala-calendar/compare/v0.17.0...v0.18.0
[0.17.0]: https://github.com/Arishawke/asala-calendar/compare/v0.16.0...v0.17.0
[0.16.0]: https://github.com/Arishawke/asala-calendar/releases/tag/v0.16.0
