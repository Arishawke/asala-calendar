# Asala Calendar Roadmap

A forward-looking list of what I plan to add, in Now / Next / Later
order. Not a commitment, and not a history. Shipped work lives in
[CHANGELOG.md](../CHANGELOG.md); this file is only what is still ahead.
Detail decreases with distance: Now is specified, Later is a line.

## Now

Nothing in active development right now. See Next.

## Next

Planned and near-term. Rough priority order within each group.

**Week view**

- **Edge auto-scroll while dragging.** Cross-day drag lands on visible
  columns only; dragging past the edge should scroll the week.
- **Pinch-to-zoom** (deferred since M2).

**Editor**

- **Natural-language event creation** (`"lunch tomorrow at noon"`).
  Local rule-based parser first; an on-device model only if the local
  parser misses too much.

**Settings**

- **Settings page reorganization + a visual taste pass.** Grouping,
  dividers, and a taste pass on the grown-out sections. Reach for the
  design-taste-frontend / ui-ux-pro-max skills when scoping.

**Color**

- **Per-event color category names** ("Focus", "Travel", "Family").
  Builds on the existing color-override system: a name field plus a
  filter UI keyed off `eventColor_key`.

**Sync and tasks**

- **Native CalDAV sync inside Asala.** Probably via `ical4j-android`
  plus a `SyncAdapter`. Server providers: Nextcloud, Fastmail,
  mailbox.org, posteo. Lets Asala stand alone without the DAVx5
  companion app.
- **Tasks integration:** a new "Tasks" view plus a unified Schedule
  that interleaves events and incomplete tasks.
  - Read tasks from the Android Tasks Provider (where supported) or
    from Tasks.org / DAVx5-synced CalDAV TODO lists.
  - Show or hide completed tasks as a toggle.
  - Create / edit / delete tasks with title, due date, optional
    reminder, completion checkbox.
  - Open design questions: depend on Tasks.org instead of
    reimplementing? CalDAV `VTODO` natively? Or a local-first store
    first and sync later?

**Widgets** (category not started)

- Small widget (day-of-month + next event).
- Resizable widget at 2x1 / 2x2 / 4x1 / 4x2.
- Year-view widget (12 mini-month grids).
- Midnight auto-update via `AlarmManager` / `WorkManager`.

**Engineering hygiene (owed)**

- File splits over the ~200-line convention (about 8 candidates).
- Plurals resources to replace hardcoded English "1 day" / "2 days".
- RTL / bidi text wrapping for languages like Arabic and Hebrew.
- Compose `stability_config.conf` population.
- ContentObserver self-change dedupe in the application observer, which
  currently reruns the reminder reschedule on its own self-writes.
- Replace the `session = System.nanoTime()` viewModel-keying workaround
  with `rememberViewModelStoreOwner()` or `androidx.navigation`
  back-stack entries.
- Push parent-event fields into `AppViewModel` instead of round-tripping
  through the `loadedDetail` callback in `MainActivity`.

## Later

Ideas and opportunities. May not happen. Kept for reference.

- **Multi-line event titles in Month view.** The chip-capacity math in
  `EventChips.kt` assumes a fixed per-chip height to decide the +N
  overflow row; variable heights break it. Needs a capacity-heuristics
  design pass.
- **Drag-to-resize event duration** via a bottom-edge handle. Builds on
  the existing `RescheduleDragState`.
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
- **Full year view.** A scrollable 12-month overview of mini-month
  grids; tap a month to jump in.
- **Quarter view (3 stacked months).** May merge with the continuous
  month surface rather than being a separate view.
- **ICS export / import of all events.** Offline backup without a sync
  server. Large effort (full ical4j parse / serialize).
- **Month / Schedule split view.** A combined surface, e.g. a mini-month
  atop the Schedule list, or other hybrid month-plus-list layouts.
- **Dynamic launcher icon showing today's day-of-month number.** Day-1
  through day-31 brand SVGs already exist in `logos/` (gitignored).
  Full feature: 31 activity-aliases, generated foregrounds, a
  `WorkManager` midnight job flipping the enabled alias, boot and
  timezone receivers, and a settings toggle. ADR-0003 has the notes.
- Honor system font scale up to 200% (audit hardcoded sp / dp values).
- **In-app font-size control.** A text-size preference inside Asala,
  independent of the OS font-scale support above.
- **Multiple timezones per event** (second timezone in editor and
  detail sheet; optional pinned secondary timezone in the Day / Week
  rail), and travel-aware display when the device timezone differs from
  the event's stored timezone.
- **Locale audit:** confirm every `DateTimeFormatter.ofPattern(...)`
  call site honors `Locale.getDefault()` and
  `DateFormat.is24HourFormat(...)` rather than hardcoded formats.
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
- **Obtainium pointer in the README.** Obtainium watches GitHub
  Releases tags and notifies users of new versions; add an
  `obtainium://app/...` deep-link button. GitHub Releases is the only
  distribution channel (see README "Install"); no app store is pursued.
- Optional opt-in **in-app update check** (GitHub releases API, browser
  hand-off first), only if users ask.
