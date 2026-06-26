# Asala Calendar Roadmap

A forward-looking list of what I plan to add, in Now / Next / Later
order. 

## Now

Nothing in active development right now. See Next.

## Next

Planned and near-term. Rough priority order within each group.

**Sync**

- **Native CalDAV sync inside Asala.** Probably via `ical4j-android`
  plus a `SyncAdapter`. Server providers: Nextcloud, Fastmail,
  mailbox.org, posteo. Lets Asala stand alone without the DAVx5
  companion app.

**Widgets**

- Multiple agenda-widget sizes with distinct compact / large layouts (the
  first agenda widget ships one tuned size that scrolls).
- Year-view widget (12 mini-month grids).

## Later

Ideas and opportunities. May not happen. Kept for reference.

- **Reorder calendar accounts in the drawer.** Persist a per-account
  display order and apply it where accounts are grouped. The clean build
  is a dedicated flat "Reorder accounts" sheet with drag handles (or
  plain Move up / Move down on each account header); dragging accounts in
  place in the drawer is the trap, since each account is a header plus its
  calendar rows inside one mixed `LazyColumn`.
- **Multi-line event titles in Month view.** The chip-capacity math in
  `EventChips.kt` assumes a fixed per-chip height to decide the +N
  overflow row; variable heights break it. Needs a capacity-heuristics
  design pass.
- **iCal `URL` property support.** Meeting links land in the URL field,
  not notes; `CalendarContract` has no first-class URL column, so
  storage routes through `ExtendedProperties` for CalDAV adapters.
- **CalDAV attachment display (read-only)** as chips that open the
  underlying viewer intent.
- **Attendee / response indicators in the event list** (person icon
  plus count, plus your own accepted / declined / tentative status),
  and a "show declined events" toggle that hides declined events by
  default. Response-state rendering, matching the common convention in
  other calendar apps: in timeline views a declined event shows a
  translucent cross-hatch fill; a not-yet-accepted (invited) event
  shows outline-only with no background fill; an accepted event renders
  normally.
- **Quarter view (3 stacked months).** May merge with the continuous
  month surface rather than being a separate view.
- **ICS export / import of all events.** Offline backup without a sync
  server. Large effort (full ical4j parse / serialize).
- **In-app font-size control.** A text-size preference inside Asala,
  independent of the OS font-scale support above.
- **Bottom-aligned header / navigation toggle.** Optional setting to
  move the top bar (title, nav buttons, controls) to the bottom of the
  screen for one-handed reach on tall phones. Affects the screens with a
  top app bar; needs a layout pass per view plus a settings switch.
- **Multiple timezones per event** (second timezone in editor and
  detail sheet; optional pinned secondary timezone in the Day / Week
  rail), and travel-aware display when the device timezone differs from
  the event's stored timezone.
- **Translation infrastructure:** strings already live in
  `res/values/strings.xml`; add `values-XX/` per language once
  translators or machine-translation seeds are available (candidate
  first locales: German, French, Spanish, Portuguese-BR, Japanese,
  simplified Chinese).
- Subscribe to a holiday calendar (ICS feed); optionally bundle one
  in-app. Import significant dates (birthdays, anniversaries) from
  `ContactsContract` as a virtual calendar.
- Email reminders (needs a server or carefully scoped SMTP; defer);
  multiple reminders per event with per-event lead-time override;
  custom-snooze IME choice.
- Fuzzy / typo-tolerant search on top of the existing exact-substring
  search; saved searches / quick filters.
- Sync conflict resolution UI (only relevant once CalDAV sync ships).
- Optional opt-in **in-app update check** (GitHub releases API, browser
  hand-off first), only if users ask.
