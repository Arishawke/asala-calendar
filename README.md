# Asala Calendar

<p align="center">
  <img alt="Asala Calendar" src="docs/brand/asala-icon.png" width="120" />
</p>

<p align="center">
A native Android calendar that talks to the system Calendar Provider, dressed in Material 3.
</p>

<p align="center">
  <sub><em>Asala</em>: Qunari for &ldquo;soul.&rdquo;</sub>
</p>

<p align="center">
  <a href="https://github.com/Arishawke/asala-calendar/actions/workflows/ci.yml"><img alt="CI" src="https://github.com/Arishawke/asala-calendar/actions/workflows/ci.yml/badge.svg?branch=main"></a>
  <a href="https://github.com/Arishawke/asala-calendar/releases"><img alt="Latest release" src="https://img.shields.io/github/v/release/Arishawke/asala-calendar?include_prereleases&label=release"></a>
  <a href="LICENSE"><img alt="License" src="https://img.shields.io/badge/license-GPLv3-blue.svg"></a>
  <a href="docs/ROADMAP.md"><img alt="Roadmap" src="https://img.shields.io/badge/roadmap-docs%2FROADMAP.md-informational"></a>
</p>

> [!WARNING]
> **Personal project, heavy AI assistance.** Asala is a hobby Android app built with substantial AI assistance. It is not production software. Use at your own discretion.

Asala keeps no database of its own. Every event lives in the Android Calendar Provider, so the events you see here are the same events every other calendar app on the device sees. Add, remove, or change accounts in system Settings and the change shows up immediately.

## Screenshots

<div align="center">
<table>
  <tr>
    <td align="center"><img src="screenshots/Month.png" alt="Month view" width="200"><br><sub>Month</sub></td>
    <td align="center"><img src="screenshots/Week.png" alt="Week view" width="200"><br><sub>Week</sub></td>
    <td align="center"><img src="screenshots/Day.png" alt="Day view" width="200"><br><sub>Day</sub></td>
  </tr>
  <tr>
    <td align="center"><img src="screenshots/3-day.png" alt="3-Day view" width="200"><br><sub>3-Day</sub></td>
    <td align="center"><img src="screenshots/Schedule.png" alt="Schedule view" width="200"><br><sub>Schedule</sub></td>
    <td align="center"><img src="screenshots/Event.png" alt="Event editor with calendar selector" width="200"><br><sub>Event editor</sub></td>
  </tr>
</table>
</div>

## Features

- **Five views, with two ways to scroll Month.** Month, Week, 3-Day, Day, and Schedule. Month either swipes sideways one at a time (paged) or flows as one long vertical timeline (continuous). Today is always highlighted, a now-line shows where you are in the current day, and the highlight rolls over at midnight on its own. Week numbers can show along the side if you want them.
- **All your calendars in one place.** Work, personal, CalDAV (via the DAVx5 companion app), local. Every event on your device shows up here, colored by the calendar it came from. Days with more events than fit get a tidy "+N more" sheet, and birthdays get a little cake.
- **Create, edit, and delete events.** Including all-day and repeating ones. When you change a repeating event, you choose whether to edit just this one, this and everything after, or the whole series. Changes stay in sync with any other calendar app on the device. Give a single event its own color without recoloring the rest of its calendar.
- **Find anything fast.** Search every calendar by event title, location, or notes, across past and upcoming dates.
- **Drag events to reschedule.** Pick up an event in Day or Week and drop it on a new time. New events open on the date you're looking at, not somewhere random.
- **Reminders before things start.** Set a default reminder for timed events and a different one for all-day events. Snooze or jump to the event right from the notification.
- **Focus on your work week.** Dim non-working hours and non-working days so meetings stand out and after-hours stuff fades back.
- **Material 3 design.** Dynamic color picks up your wallpaper on Android 12+, with Light, Dark, and AMOLED themes. Time format follows your phone's 24-hour setting, and dates and month names follow your device language.
- **Tidy drawer.** Toggle calendars on and off without touching system Settings. Hide whole accounts you don't want cluttering the list. Recolor anything; rename or delete the local calendars.
- **No telemetry.** No analytics, no third-party trackers. Your calendar data leaves the device only if a sync account you set up sends it somewhere.

## Settings

Persisted via Jetpack DataStore. Five groups, matching the in-app layout:

- **General.** Default view on cold start. Week starts on. Default event duration. Time format (Follow system default / 12-hour / 24-hour). Tasks (experimental) toggle.
- **Appearance.** Theme (System / Light / Dark / AMOLED). Color palette (Okabe-Ito / Radix). Dim past dates. Show working hours, with start and end. Show working days, with a per-weekday picker. Show week number (ISO 8601). Month scroll style (Paged / Continuous).
- **Notifications.** Reminder status with a notification-permission prompt. Default snooze. Default reminder for timed events. Default reminder for all-day events. Background reliability shortcut on affected OEMs.
- **Calendars & accounts.** Hidden accounts restore. Sync with a CalDAV server (DAVx5 launcher). Calendar source (Local only / Sync only / Hybrid).
- **About.** Version. License. Source code.

## Install

Asala is distributed only through GitHub Releases. There is no app-store listing, by choice.

- **Direct APK.** Download the latest `app-release.apk` from [Releases](https://github.com/Arishawke/asala-calendar/releases) and install it. Builds are signed and published as pre-releases.
- **Auto-updates via Obtainium.** [Obtainium](https://github.com/ImranR98/Obtainium) watches this repository's releases and notifies you when a new version ships. Point it at the releases page to add Asala.

## Build from source

Requirements:

- JDK 17 (Eclipse Temurin recommended)
- Android SDK packages: `platforms;android-36`, `build-tools;36.1.0`, `platform-tools`
- `ANDROID_HOME` (or `ANDROID_SDK_ROOT`) pointed at your SDK install

```bash
./gradlew assembleDebug
```

The first build downloads Gradle 9.3.1 via the wrapper.
`minSdk` 28, target and compile SDK 36.

## Tests

```bash
./gradlew testDebugUnitTest
```

JVM unit tests live under `app/src/test/`. The day-boundary, recurrence-math, and continuous-Month index suites guard the trickiest pieces: DST transitions, RRULE exception arithmetic, and the LazyColumn entry mapping behind continuous Month view.

## Tech

Kotlin and Jetpack Compose on Material 3. Reads and writes the Android Calendar Provider (`CalendarContract`) directly. Month and Week grids use [kizitonwose/Calendar](https://github.com/kizitonwose/Calendar). Settings persist via Jetpack DataStore. Release builds run through R8 with minify and resource shrink (APK around 3.5 MB).

## Roadmap and architecture

- [docs/ROADMAP.md](docs/ROADMAP.md): where Asala is headed. Releases are published on GitHub as pre-releases.
- [docs/adr/](docs/adr/): architecture decisions.
  - [ADR-0001](docs/adr/0001-data-layer-is-calendarcontract.md): data layer is `CalendarContract`.
  - [ADR-0002](docs/adr/0002-calendar-provider-write-conventions.md): Calendar Provider write conventions.
  - [ADR-0003](docs/adr/0003-launcher-icon-source-and-density.md): launcher icon source and density.
  - [ADR-0004](docs/adr/0004-quality-gates.md): quality gates.
  - [ADR-0005](docs/adr/0005-github-native-dependency-scanning.md): GitHub-native dependency scanning.

## Contributing

Solo hobby project, but build and code conventions are documented for future contributors. See [CONTRIBUTING.md](CONTRIBUTING.md) and [SECURITY.md](SECURITY.md).

## License

[GPL v3](LICENSE). © 2026 Arishawke.
