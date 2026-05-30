# M3 Event CRUD Implementation Plan

> **Status:** Shipped in v0.4.0 on 2026-05-21. Archived; do not edit.
> Refer to `CHANGELOG.md` for what actually landed, and to
> `notes/v0.4-review-findings.md` for the post-release review.

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make Asala Calendar a daily-driver write app: tap to view, FAB to create, edit, delete, with basic recurrence and a single reminder offset. Notification firing is M4.

**Architecture:** Three new data-layer pieces (`EventDraft`, `RecurrenceRule`, `RemindersRepository`) bolt onto the existing `EventRepository`. Three new UI subtrees (`ui/eventdetail/`, `ui/eventedit/`, real `ui/settings/`) plug into the existing Scaffold. ViewModels remain one-per-screen. Calendar Provider writes flow through `ContentResolver` and the existing `ContentObserver` triggers UI re-emit automatically — no signal plumbing needed.

**Tech Stack:** Kotlin 2.3.21, Compose BOM 2026.05.00, Material 3, `kizitonwose/Calendar`, `CalendarContract` (Android Calendar Provider), DataStore Preferences, JUnit 4 for JVM tests.

**Plan reference:** [docs/specs/2026-05-21-m3-event-crud-design.md](../specs/2026-05-21-m3-event-crud-design.md)

---

## File structure

### New files
```
app/src/main/kotlin/com/arishawke/asala/calendar/
  data/
    EventDraft.kt                   ; write-side data class + toContentValues()
    EventDetail.kt                  ; full event row from Events table
    RecurrenceRule.kt               ; RRULE builder + parser for the 4 frequencies
    RecurringEditScope.kt           ; enum: ThisInstance, ThisAndFollowing, AllEvents
    RemindersRepository.kt          ; wraps CalendarContract.Reminders
  ui/
    eventdetail/
      EventDetailSheet.kt           ; ModalBottomSheet composable
      EventDetailViewModel.kt
    eventedit/
      EventEditScreen.kt            ; full-screen form
      EventEditViewModel.kt
      EventForm.kt                  ; the form sub-composables
      RecurrenceSection.kt          ; recurrence row + frequency picker
      RecurringEditScopeDialog.kt   ; 3-option dialog
      ReminderPicker.kt             ; dropdown
      DateTimePickerRow.kt          ; date + time picker pair
    settings/
      SettingsScreen.kt             ; replaces ThemeSettingsDialog
      SettingsViewModel.kt
      UserPreferences.kt            ; consolidated DataStore wrapper (theme + view + week + ISO)
app/src/test/kotlin/com/arishawke/asala/calendar/
  data/
    RecurrenceRuleTest.kt
    EventDraftTest.kt
    RecurringExceptionMathTest.kt
```

### Modified files
```
app/src/main/AndroidManifest.xml                          ; verify only (no change expected)
app/src/main/kotlin/com/arishawke/asala/calendar/
  MainActivity.kt                                          ; add FAB, sheet/edit routing
  AppViewModel.kt                                          ; extend UI state for sheet visibility + edit target; switch to UserPreferences
  data/EventRepository.kt                                  ; add insertEvent / updateEvent / deleteEvent / fetchEventDetail
  data/EventItem.kt                                        ; add hasRecurrence: Boolean (read from RRULE column)
  ui/month/DayCell.kt                                      ; onClick on event chip
  ui/month/MonthScreen.kt                                  ; thread onClick up
  ui/week/EventBlock.kt                                    ; onClick
  ui/week/AllDayRow.kt                                     ; onClick
  ui/week/WeekScreen.kt                                    ; thread onClick up
  ui/day/DayScreen.kt                                      ; thread onClick up (reuses Week column)
  ui/schedule/ScheduleScreen.kt                            ; onClick on EventRow
  ui/permissions/CalendarPermissionGate.kt                 ; verify only (already requests R+W)
  ui/settings/ThemePreference.kt                           ; delete (folded into UserPreferences)
  ui/settings/ThemeSettingsDialog.kt                       ; delete (replaced by SettingsScreen)
  ui/month/CalendarDrawer.kt                               ; settings entry navigates to screen instead of opening dialog
app/src/main/res/values/strings.xml                       ; new strings per slice
CHANGELOG.md                                              ; one Unreleased entry per slice
```

---

## Slice 1 — Verify WRITE_CALENDAR is requested correctly

WRITE_CALENDAR is declared in the manifest and the permission gate
requests both READ and WRITE (per v0.2.0). This slice is a sanity
check before opening write paths in slice 2.

**Files touched:**
- Verify: [`app/src/main/AndroidManifest.xml`](../../app/src/main/AndroidManifest.xml)
- Verify: [`app/src/main/kotlin/com/arishawke/asala/calendar/ui/permissions/CalendarPermissionGate.kt`](../../app/src/main/kotlin/com/arishawke/asala/calendar/ui/permissions/CalendarPermissionGate.kt)

### Task 1.1: Verify manifest + permission gate

- [ ] **Step 1: Confirm manifest entries**

  ```bash
  grep -n "WRITE_CALENDAR\|READ_CALENDAR" app/src/main/AndroidManifest.xml
  ```

  Expected output:
  ```
  4:    <uses-permission android:name="android.permission.READ_CALENDAR" />
  5:    <uses-permission android:name="android.permission.WRITE_CALENDAR" />
  ```

- [ ] **Step 2: Confirm gate requests both**

  ```bash
  grep -n "Manifest.permission" app/src/main/kotlin/com/arishawke/asala/calendar/ui/permissions/CalendarPermissionGate.kt
  ```

  Expected: both `READ_CALENDAR` and `WRITE_CALENDAR` in the
  `required` array.

- [ ] **Step 3: Fresh-install verification on phone**

  ```bash
  adb uninstall com.arishawke.asala.calendar 2>/dev/null
  ./gradlew installDebug
  adb shell am start -n com.arishawke.asala.calendar/.MainActivity
  ```

  Manually verify: a single system permission dialog appears,
  showing "Calendars" with both READ and WRITE implied (Android
  groups these). Grant. The app opens to Month view.

- [ ] **Step 4: No commit if verification clean**

  This is a verify-only task. If both grep checks pass and the
  manual flow grants permission cleanly, proceed to slice 2 with no
  commit. If anything is broken, fix it as a single `fix(perm): ...`
  commit before proceeding.

---

## Slice 2 — Tap-to-view event detail bottom sheet (read path)

The first user-visible M3 work: tapping any event in any view opens
a Material 3 `ModalBottomSheet` with the event's full details. Adds
the tap-target plumbing all four screens need, without yet writing
the Calendar Provider.

**Files touched:**
- Create: `data/EventDetail.kt`
- Create: `data/RecurrenceRule.kt` (parse-only in this slice; full
  builder lands in slice 5)
- Create: `ui/eventdetail/EventDetailSheet.kt`
- Create: `ui/eventdetail/EventDetailViewModel.kt`
- Create: `app/src/test/kotlin/.../data/RecurrenceRuleTest.kt`
- Modify: `data/EventRepository.kt` (+`fetchEventDetail`)
- Modify: `data/EventItem.kt` (+`hasRecurrence: Boolean`)
- Modify: `MainActivity.kt` (sheet host)
- Modify: `AppViewModel.kt` (sheet visibility state + selected event)
- Modify: 4 view screens + their event composables (onClick)
- Modify: `res/values/strings.xml`
- Modify: `CHANGELOG.md`

### Task 2.1: Data class for full event details

- [ ] **Step 1: Create EventDetail.kt**

  Create `app/src/main/kotlin/com/arishawke/asala/calendar/data/EventDetail.kt`:

  ```kotlin
  /*
   * Copyright (C) 2026 Arishawke
   *
   * This program is free software: you can redistribute it and/or modify
   * it under the terms of the GNU General Public License as published by
   * the Free Software Foundation, either version 3 of the License, or
   * (at your option) any later version.
   */
  package com.arishawke.asala.calendar.data

  data class EventDetail(
      val eventId: Long,
      val calendarId: Long,
      val title: String,
      val description: String?,
      val location: String?,
      val startMillis: Long,
      val endMillis: Long,
      val allDay: Boolean,
      val eventTimezone: String,
      val rrule: String?,
      val displayColor: Int,
      val calendarDisplayName: String,
      val reminderMinutesBefore: Int?,
  )
  ```

- [ ] **Step 2: Compile**

  ```bash
  ./gradlew :app:compileDebugKotlin
  ```

  Expected: BUILD SUCCESSFUL. No file uses `EventDetail` yet.

### Task 2.2: RecurrenceRule parse (for summary text)

This task adds parse only — RRULE building lands in slice 5. We
parse to render "Repeats weekly" / "Repeats daily" etc. in the
detail sheet.

- [ ] **Step 1: Write failing test**

  Create `app/src/test/kotlin/com/arishawke/asala/calendar/data/RecurrenceRuleTest.kt`:

  ```kotlin
  /*
   * Copyright (C) 2026 Arishawke
   * GPL v3.
   */
  package com.arishawke.asala.calendar.data

  import org.junit.Test
  import org.junit.Assert.assertEquals
  import org.junit.Assert.assertNull

  class RecurrenceRuleTest {

      @Test fun parses_daily() {
          assertEquals(RecurrenceFrequency.Daily, RecurrenceRule.frequencyOf("FREQ=DAILY"))
      }

      @Test fun parses_weekly() {
          assertEquals(RecurrenceFrequency.Weekly, RecurrenceRule.frequencyOf("FREQ=WEEKLY;BYDAY=MO,WE,FR"))
      }

      @Test fun parses_monthly() {
          assertEquals(RecurrenceFrequency.Monthly, RecurrenceRule.frequencyOf("FREQ=MONTHLY;BYMONTHDAY=15"))
      }

      @Test fun parses_yearly() {
          assertEquals(RecurrenceFrequency.Yearly, RecurrenceRule.frequencyOf("FREQ=YEARLY"))
      }

      @Test fun null_for_unknown_or_empty() {
          assertNull(RecurrenceRule.frequencyOf(""))
          assertNull(RecurrenceRule.frequencyOf("FREQ=HOURLY"))
          assertNull(RecurrenceRule.frequencyOf(null))
      }
  }
  ```

- [ ] **Step 2: Run, verify fails**

  ```bash
  ./gradlew :app:testDebugUnitTest --tests RecurrenceRuleTest
  ```

  Expected: FAIL (unresolved reference `RecurrenceRule`,
  `RecurrenceFrequency`).

- [ ] **Step 3: Create RecurrenceRule.kt with parse-only**

  Create `app/src/main/kotlin/com/arishawke/asala/calendar/data/RecurrenceRule.kt`:

  ```kotlin
  /*
   * Copyright (C) 2026 Arishawke
   * GPL v3.
   */
  package com.arishawke.asala.calendar.data

  enum class RecurrenceFrequency { Daily, Weekly, Monthly, Yearly }

  object RecurrenceRule {

      fun frequencyOf(rrule: String?): RecurrenceFrequency? {
          if (rrule.isNullOrBlank()) return null
          val freq = rrule.split(";")
              .firstOrNull { it.startsWith("FREQ=") }
              ?.removePrefix("FREQ=")
              ?: return null
          return when (freq) {
              "DAILY" -> RecurrenceFrequency.Daily
              "WEEKLY" -> RecurrenceFrequency.Weekly
              "MONTHLY" -> RecurrenceFrequency.Monthly
              "YEARLY" -> RecurrenceFrequency.Yearly
              else -> null
          }
      }
  }
  ```

- [ ] **Step 4: Run, verify passes**

  ```bash
  ./gradlew :app:testDebugUnitTest --tests RecurrenceRuleTest
  ```

  Expected: 5 tests passed.

### Task 2.3: EventRepository.fetchEventDetail

The repo gains a suspend function that queries `Events.CONTENT_URI`
for a single event by id, joining `Calendars` for the calendar
display name and `Reminders` for the optional reminder.

- [ ] **Step 1: Add fetchEventDetail to EventRepository**

  In `app/src/main/kotlin/com/arishawke/asala/calendar/data/EventRepository.kt`, add (above `private companion object`):

  ```kotlin
  suspend fun fetchEventDetail(eventId: Long): EventDetail? =
      withContext(Dispatchers.IO) {
          val eventUri = ContentUris.withAppendedId(
              CalendarContract.Events.CONTENT_URI,
              eventId,
          )
          val eventProjection = arrayOf(
              CalendarContract.Events._ID,
              CalendarContract.Events.CALENDAR_ID,
              CalendarContract.Events.TITLE,
              CalendarContract.Events.DESCRIPTION,
              CalendarContract.Events.EVENT_LOCATION,
              CalendarContract.Events.DTSTART,
              CalendarContract.Events.DTEND,
              CalendarContract.Events.ALL_DAY,
              CalendarContract.Events.EVENT_TIMEZONE,
              CalendarContract.Events.RRULE,
              CalendarContract.Events.DISPLAY_COLOR,
              CalendarContract.Events.CALENDAR_DISPLAY_NAME,
          )

          val event = contentResolver.query(
              eventUri, eventProjection, null, null, null,
          )?.use { c ->
              if (!c.moveToFirst()) return@use null
              EventDetail(
                  eventId = c.getLong(0),
                  calendarId = c.getLong(1),
                  title = c.getString(2) ?: "",
                  description = c.getString(3).takeUnless { it.isNullOrBlank() },
                  location = c.getString(4).takeUnless { it.isNullOrBlank() },
                  startMillis = c.getLong(5),
                  endMillis = c.getLong(6),
                  allDay = c.getInt(7) == 1,
                  eventTimezone = c.getString(8) ?: TimeZone.getDefault().id,
                  rrule = c.getString(9).takeUnless { it.isNullOrBlank() },
                  displayColor = c.getInt(10),
                  calendarDisplayName = c.getString(11) ?: "",
                  reminderMinutesBefore = null,
              )
          } ?: return@withContext null

          val reminderMinutes = contentResolver.query(
              CalendarContract.Reminders.CONTENT_URI,
              arrayOf(CalendarContract.Reminders.MINUTES),
              "${CalendarContract.Reminders.EVENT_ID} = ?",
              arrayOf(eventId.toString()),
              null,
          )?.use { c -> if (c.moveToFirst()) c.getInt(0) else null }

          event.copy(reminderMinutesBefore = reminderMinutes)
      }
  ```

  Add the imports at the top:

  ```kotlin
  import kotlinx.coroutines.withContext
  import java.util.TimeZone
  ```

- [ ] **Step 2: Compile**

  ```bash
  ./gradlew :app:compileDebugKotlin
  ```

  Expected: BUILD SUCCESSFUL.

### Task 2.4: AppViewModel — sheet visibility state

Add state for the open detail sheet: which `eventId` (nullable) is
selected. When non-null, the sheet is visible.

- [ ] **Step 1: Add state to AppViewModel**

  In [`AppViewModel.kt`](../../app/src/main/kotlin/com/arishawke/asala/calendar/AppViewModel.kt), add:

  ```kotlin
  // null = sheet closed
  private val _detailSheetEventId = MutableStateFlow<Long?>(null)
  val detailSheetEventId: StateFlow<Long?> = _detailSheetEventId.asStateFlow()

  fun openEventDetail(eventId: Long) {
      _detailSheetEventId.update { eventId }
  }

  fun closeEventDetail() {
      _detailSheetEventId.update { null }
  }
  ```

- [ ] **Step 2: Compile**

  ```bash
  ./gradlew :app:compileDebugKotlin
  ```

### Task 2.5: EventDetailViewModel

A lightweight VM that takes an `eventId` and loads the matching
`EventDetail` once. Created via factory from `MainActivity` when the
sheet opens.

- [ ] **Step 1: Create EventDetailViewModel.kt**

  Create `app/src/main/kotlin/com/arishawke/asala/calendar/ui/eventdetail/EventDetailViewModel.kt`:

  ```kotlin
  /*
   * Copyright (C) 2026 Arishawke
   * GPL v3.
   */
  package com.arishawke.asala.calendar.ui.eventdetail

  import android.content.Context
  import androidx.lifecycle.ViewModel
  import androidx.lifecycle.ViewModelProvider
  import androidx.lifecycle.viewModelScope
  import com.arishawke.asala.calendar.data.EventDetail
  import com.arishawke.asala.calendar.data.EventRepository
  import kotlinx.coroutines.flow.MutableStateFlow
  import kotlinx.coroutines.flow.StateFlow
  import kotlinx.coroutines.flow.asStateFlow
  import kotlinx.coroutines.launch

  class EventDetailViewModel(
      private val repo: EventRepository,
      private val eventId: Long,
  ) : ViewModel() {

      private val _detail = MutableStateFlow<EventDetail?>(null)
      val detail: StateFlow<EventDetail?> = _detail.asStateFlow()

      init {
          viewModelScope.launch {
              _detail.value = repo.fetchEventDetail(eventId)
          }
      }

      class Factory(
          private val appContext: Context,
          private val eventId: Long,
      ) : ViewModelProvider.Factory {
          @Suppress("UNCHECKED_CAST")
          override fun <T : ViewModel> create(modelClass: Class<T>): T {
              require(modelClass == EventDetailViewModel::class.java)
              return EventDetailViewModel(
                  repo = EventRepository(appContext.contentResolver),
                  eventId = eventId,
              ) as T
          }
      }
  }
  ```

- [ ] **Step 2: Compile**

  ```bash
  ./gradlew :app:compileDebugKotlin
  ```

### Task 2.6: EventDetailSheet composable

The Material 3 ModalBottomSheet with title, calendar swatch + name,
date/time, location, description, recurrence summary, reminder
summary, and three actions (Edit, Delete, Close).

Edit and Delete are wired to no-op callbacks in this slice; slices 3
and 4 connect them.

- [ ] **Step 1: Create EventDetailSheet.kt**

  Create `app/src/main/kotlin/com/arishawke/asala/calendar/ui/eventdetail/EventDetailSheet.kt`:

  ```kotlin
  /*
   * Copyright (C) 2026 Arishawke
   * GPL v3.
   */
  package com.arishawke.asala.calendar.ui.eventdetail

  import androidx.compose.foundation.background
  import androidx.compose.foundation.layout.*
  import androidx.compose.foundation.shape.CircleShape
  import androidx.compose.material3.*
  import androidx.compose.runtime.*
  import androidx.compose.runtime.getValue
  import androidx.compose.ui.Alignment
  import androidx.compose.ui.Modifier
  import androidx.compose.ui.graphics.Color
  import androidx.compose.ui.platform.LocalContext
  import androidx.compose.ui.res.stringResource
  import androidx.compose.ui.unit.dp
  import androidx.lifecycle.viewmodel.compose.viewModel
  import com.arishawke.asala.calendar.R
  import com.arishawke.asala.calendar.data.EventDetail
  import com.arishawke.asala.calendar.data.RecurrenceFrequency
  import com.arishawke.asala.calendar.data.RecurrenceRule
  import java.time.Instant
  import java.time.LocalDate
  import java.time.ZoneId
  import java.time.format.DateTimeFormatter
  import java.time.format.FormatStyle
  import java.util.Locale

  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  fun EventDetailSheet(
      eventId: Long,
      onDismiss: () -> Unit,
      onEdit: (Long) -> Unit,
      onDelete: (Long) -> Unit,
  ) {
      val context = LocalContext.current
      val vm: EventDetailViewModel = viewModel(
          factory = EventDetailViewModel.Factory(context.applicationContext, eventId),
          key = "event-detail-$eventId",
      )
      val detail by vm.detail.collectAsState()
      val sheetState = rememberModalBottomSheetState()

      ModalBottomSheet(
          onDismissRequest = onDismiss,
          sheetState = sheetState,
      ) {
          val d = detail
          if (d == null) {
              Box(
                  modifier = Modifier.fillMaxWidth().padding(48.dp),
                  contentAlignment = Alignment.Center,
              ) { CircularProgressIndicator() }
          } else {
              EventDetailContent(
                  detail = d,
                  onEdit = { onEdit(d.eventId) },
                  onDelete = { onDelete(d.eventId) },
                  onClose = onDismiss,
              )
          }
      }
  }

  @Composable
  private fun EventDetailContent(
      detail: EventDetail,
      onEdit: () -> Unit,
      onDelete: () -> Unit,
      onClose: () -> Unit,
  ) {
      Column(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
          Text(
              text = detail.title.ifBlank { stringResource(R.string.event_no_title) },
              style = MaterialTheme.typography.headlineSmall,
          )

          Row(verticalAlignment = Alignment.CenterVertically) {
              Box(
                  modifier = Modifier.size(12.dp).background(Color(detail.displayColor), CircleShape),
              )
              Spacer(Modifier.width(8.dp))
              Text(detail.calendarDisplayName, style = MaterialTheme.typography.bodyMedium)
          }

          Text(
              text = formatWhen(detail),
              style = MaterialTheme.typography.bodyMedium,
          )

          detail.location?.let {
              Text(text = it, style = MaterialTheme.typography.bodyMedium)
          }
          detail.description?.let {
              Text(text = it, style = MaterialTheme.typography.bodyMedium)
          }

          RecurrenceRule.frequencyOf(detail.rrule)?.let { freq ->
              Text(
                  text = stringResource(recurrenceSummaryRes(freq)),
                  style = MaterialTheme.typography.bodySmall,
              )
          }

          detail.reminderMinutesBefore?.let {
              Text(
                  text = stringResource(R.string.reminder_summary, it),
                  style = MaterialTheme.typography.bodySmall,
              )
          }

          Spacer(Modifier.height(8.dp))

          Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
              TextButton(onClick = onDelete) { Text(stringResource(R.string.action_delete)) }
              Spacer(Modifier.weight(1f))
              TextButton(onClick = onClose) { Text(stringResource(R.string.action_close)) }
              Button(onClick = onEdit) { Text(stringResource(R.string.action_edit)) }
          }
      }
  }

  private fun recurrenceSummaryRes(freq: RecurrenceFrequency): Int = when (freq) {
      RecurrenceFrequency.Daily -> R.string.repeats_daily
      RecurrenceFrequency.Weekly -> R.string.repeats_weekly
      RecurrenceFrequency.Monthly -> R.string.repeats_monthly
      RecurrenceFrequency.Yearly -> R.string.repeats_yearly
  }

  private fun formatWhen(d: EventDetail): String {
      val zone = ZoneId.systemDefault()
      val locale = Locale.getDefault()
      return if (d.allDay) {
          // All-day: dtstart is 00:00 UTC; render as local date range.
          val startDate = Instant.ofEpochMilli(d.startMillis).atZone(ZoneId.of("UTC")).toLocalDate()
          val endDate = Instant.ofEpochMilli(d.endMillis).atZone(ZoneId.of("UTC")).toLocalDate().minusDays(1)
          val df = DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale)
          if (startDate == endDate) df.format(startDate) else "${df.format(startDate)} – ${df.format(endDate)}"
      } else {
          val start = Instant.ofEpochMilli(d.startMillis).atZone(zone)
          val end = Instant.ofEpochMilli(d.endMillis).atZone(zone)
          val dateFmt = DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale)
          val timeFmt = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale)
          val sameDay = start.toLocalDate() == end.toLocalDate()
          if (sameDay) {
              "${dateFmt.format(start)}\n${timeFmt.format(start)} – ${timeFmt.format(end)}"
          } else {
              "${dateFmt.format(start)} ${timeFmt.format(start)} –\n${dateFmt.format(end)} ${timeFmt.format(end)}"
          }
      }
  }
  ```

- [ ] **Step 2: Add strings to res/values/strings.xml**

  Append:

  ```xml
  <string name="event_no_title">(No title)</string>
  <string name="action_delete">Delete</string>
  <string name="action_close">Close</string>
  <string name="action_edit">Edit</string>
  <string name="repeats_daily">Repeats daily</string>
  <string name="repeats_weekly">Repeats weekly</string>
  <string name="repeats_monthly">Repeats monthly</string>
  <string name="repeats_yearly">Repeats yearly</string>
  <string name="reminder_summary">%1$d minutes before</string>
  ```

- [ ] **Step 3: Compile**

  ```bash
  ./gradlew :app:compileDebugKotlin
  ```

### Task 2.7: Wire onClick through the 4 views

Each event composable gains a no-default `onClick: (eventId: Long) -> Unit` parameter that the parent screen threads down from the AppViewModel.

- [ ] **Step 1: Add onClick to EventChip (Month)**

  In [`app/src/main/kotlin/com/arishawke/asala/calendar/ui/components/EventChip.kt`](../../app/src/main/kotlin/com/arishawke/asala/calendar/ui/components/EventChip.kt), add an
  `onClick: () -> Unit` parameter, attach via `Modifier.clickable { onClick() }`.

- [ ] **Step 2: Thread onClick from MonthScreen → DayCell → EventChip**

  `MonthScreen` accepts `onEventClick: (Long) -> Unit`, passes it
  to each `DayCell`. `DayCell` invokes `onEventClick(item.eventId)`
  on tap.

- [ ] **Step 3: Add onClick to Week's EventBlock and AllDayRow**

  In `ui/week/EventBlock.kt` and `ui/week/AllDayRow.kt`, add the
  same parameter and clickable modifier.

- [ ] **Step 4: Thread onClick from WeekScreen + DayScreen + ScheduleScreen**

  Each screen accepts `onEventClick: (Long) -> Unit` at the top
  level and routes to the right composable.

- [ ] **Step 5: Pass onClick from MainActivity → screens**

  In `MainActivity`, wire `vm.openEventDetail` to each screen's
  `onEventClick` parameter.

- [ ] **Step 6: Compile**

  ```bash
  ./gradlew :app:compileDebugKotlin
  ```

### Task 2.8: Host the sheet in MainActivity

- [ ] **Step 1: Add sheet host to MainActivity**

  Above the existing `Scaffold` content (or after it inside the
  Compose tree, but as a sibling so it overlays), add:

  ```kotlin
  val openEventId by vm.detailSheetEventId.collectAsState()
  openEventId?.let { id ->
      EventDetailSheet(
          eventId = id,
          onDismiss = { vm.closeEventDetail() },
          onEdit = { /* slice 3 */ },
          onDelete = { /* slice 4 */ },
      )
  }
  ```

- [ ] **Step 2: Compile + install + manual verify**

  ```bash
  ./gradlew installDebug
  adb shell am start -n com.arishawke.asala.calendar/.MainActivity
  ```

  Test on phone:
  - Month view: tap an event chip → sheet opens with details.
  - Week view: tap a block → sheet opens.
  - Day view: tap a block → sheet opens.
  - Schedule view: tap a row → sheet opens.
  - Tap scrim or swipe down → sheet closes.
  - Edit / Delete buttons exist but no-op (intentional, slices 3/4).

### Task 2.9: CHANGELOG + commit

- [ ] **Step 1: Add Unreleased entry**

  Under `## [Unreleased]` → `### Added`:

  ```markdown
  - Tap any event in Month, Week, Day, or Schedule to open a
    detail bottom sheet showing the event's title, calendar,
    date/time, location, description, recurrence summary, and
    reminder. Edit and Delete buttons are visible but not yet
    functional (lands in upcoming slices).
  ```

- [ ] **Step 2: Commit**

  ```bash
  git add -A
  git commit -m "feat(event): tap-to-view detail bottom sheet"
  ```

---

## Slice 3 — Event create (FAB, non-recurring)

Add a FAB to the Scaffold. Tap opens `EventEditScreen` in "create"
mode. Save writes via the new `EventRepository.insertEvent`.

**Files touched:**
- Create: `data/EventDraft.kt`
- Create: `app/src/test/kotlin/.../data/EventDraftTest.kt`
- Create: `ui/eventedit/EventEditScreen.kt`
- Create: `ui/eventedit/EventEditViewModel.kt`
- Create: `ui/eventedit/EventForm.kt`
- Create: `ui/eventedit/DateTimePickerRow.kt`
- Modify: `data/EventRepository.kt` (+`insertEvent`)
- Modify: `MainActivity.kt` (+FAB, +edit screen routing)
- Modify: `AppViewModel.kt` (+edit-mode state)
- Modify: `res/values/strings.xml`
- Modify: `CHANGELOG.md`

### Task 3.1: EventDraft data class + toContentValues

EventDraft is the write-side representation: what the editor
collects, what the repo accepts.

- [ ] **Step 1: Write failing test**

  Create `app/src/test/kotlin/.../data/EventDraftTest.kt`:

  ```kotlin
  /*
   * Copyright (C) 2026 Arishawke
   * GPL v3.
   */
  package com.arishawke.asala.calendar.data

  import android.provider.CalendarContract
  import org.junit.Test
  import org.junit.Assert.assertEquals
  import org.junit.Assert.assertNull

  class EventDraftTest {

      @Test fun timed_event_writes_dtstart_dtend_dtend_when_no_rrule() {
          val draft = EventDraft(
              calendarId = 1L,
              title = "Lunch",
              description = "with M",
              location = "Cafe",
              startMillis = 1_700_000_000_000L,
              endMillis = 1_700_003_600_000L,
              allDay = false,
              eventTimezone = "America/New_York",
              rrule = null,
          )

          val cv = draft.toContentValues()

          assertEquals(1L, cv.getAsLong(CalendarContract.Events.CALENDAR_ID))
          assertEquals("Lunch", cv.getAsString(CalendarContract.Events.TITLE))
          assertEquals("with M", cv.getAsString(CalendarContract.Events.DESCRIPTION))
          assertEquals("Cafe", cv.getAsString(CalendarContract.Events.EVENT_LOCATION))
          assertEquals(1_700_000_000_000L, cv.getAsLong(CalendarContract.Events.DTSTART))
          assertEquals(1_700_003_600_000L, cv.getAsLong(CalendarContract.Events.DTEND))
          assertEquals(0, cv.getAsInteger(CalendarContract.Events.ALL_DAY))
          assertEquals("America/New_York", cv.getAsString(CalendarContract.Events.EVENT_TIMEZONE))
          assertNull(cv.getAsString(CalendarContract.Events.RRULE))
          assertNull(cv.getAsString(CalendarContract.Events.DURATION))
      }

      @Test fun recurring_event_writes_duration_not_dtend() {
          val draft = EventDraft(
              calendarId = 1L,
              title = "Standup",
              description = null,
              location = null,
              startMillis = 1_700_000_000_000L,
              endMillis = 1_700_001_800_000L,  // 30 minutes
              allDay = false,
              eventTimezone = "America/New_York",
              rrule = "FREQ=DAILY",
          )

          val cv = draft.toContentValues()

          assertEquals("FREQ=DAILY", cv.getAsString(CalendarContract.Events.RRULE))
          assertEquals("PT1800S", cv.getAsString(CalendarContract.Events.DURATION))
          assertNull(cv.getAsLong(CalendarContract.Events.DTEND))
      }

      @Test fun all_day_event_uses_utc_and_excludes_time() {
          val draft = EventDraft(
              calendarId = 1L,
              title = "Vacation",
              description = null,
              location = null,
              startMillis = 1_700_000_000_000L,  // any millis
              endMillis = 1_700_086_400_000L,
              allDay = true,
              eventTimezone = "UTC",
              rrule = null,
          )

          val cv = draft.toContentValues()

          assertEquals(1, cv.getAsInteger(CalendarContract.Events.ALL_DAY))
          assertEquals("UTC", cv.getAsString(CalendarContract.Events.EVENT_TIMEZONE))
      }
  }
  ```

- [ ] **Step 2: Run, verify fails**

  ```bash
  ./gradlew :app:testDebugUnitTest --tests EventDraftTest
  ```

  Expected: FAIL (unresolved reference `EventDraft`).

- [ ] **Step 3: Create EventDraft.kt**

  Create `app/src/main/kotlin/com/arishawke/asala/calendar/data/EventDraft.kt`:

  ```kotlin
  /*
   * Copyright (C) 2026 Arishawke
   * GPL v3.
   */
  package com.arishawke.asala.calendar.data

  import android.content.ContentValues
  import android.provider.CalendarContract

  data class EventDraft(
      val calendarId: Long,
      val title: String,
      val description: String?,
      val location: String?,
      val startMillis: Long,
      val endMillis: Long,
      val allDay: Boolean,
      val eventTimezone: String,
      val rrule: String?,
  ) {

      fun toContentValues(): ContentValues = ContentValues().apply {
          put(CalendarContract.Events.CALENDAR_ID, calendarId)
          put(CalendarContract.Events.TITLE, title.ifBlank { "(No title)" })
          put(CalendarContract.Events.DESCRIPTION, description)
          put(CalendarContract.Events.EVENT_LOCATION, location)
          put(CalendarContract.Events.DTSTART, startMillis)
          put(CalendarContract.Events.ALL_DAY, if (allDay) 1 else 0)
          put(CalendarContract.Events.EVENT_TIMEZONE, eventTimezone)

          if (rrule != null) {
              // Recurring events MUST use DURATION, not DTEND.
              put(CalendarContract.Events.RRULE, rrule)
              put(CalendarContract.Events.DURATION, iso8601Duration(endMillis - startMillis))
          } else {
              put(CalendarContract.Events.DTEND, endMillis)
          }
      }

      private fun iso8601Duration(durationMillis: Long): String {
          val seconds = durationMillis / 1000
          return "PT${seconds}S"
      }
  }
  ```

- [ ] **Step 4: Run, verify passes**

  ```bash
  ./gradlew :app:testDebugUnitTest --tests EventDraftTest
  ```

  Expected: 3 tests passed.

### Task 3.2: EventRepository.insertEvent

- [ ] **Step 1: Add insertEvent**

  In `EventRepository.kt`, add:

  ```kotlin
  suspend fun insertEvent(draft: EventDraft): Long? =
      withContext(Dispatchers.IO) {
          val uri = contentResolver.insert(
              CalendarContract.Events.CONTENT_URI,
              draft.toContentValues(),
          ) ?: return@withContext null
          ContentUris.parseId(uri)
      }
  ```

- [ ] **Step 2: Compile**

  ```bash
  ./gradlew :app:compileDebugKotlin
  ```

### Task 3.3: EventEditViewModel

- [ ] **Step 1: Create EventEditViewModel.kt**

  Create `app/src/main/kotlin/com/arishawke/asala/calendar/ui/eventedit/EventEditViewModel.kt`:

  ```kotlin
  /*
   * Copyright (C) 2026 Arishawke
   * GPL v3.
   */
  package com.arishawke.asala.calendar.ui.eventedit

  import android.content.Context
  import androidx.lifecycle.ViewModel
  import androidx.lifecycle.ViewModelProvider
  import androidx.lifecycle.viewModelScope
  import com.arishawke.asala.calendar.data.CalendarItem
  import com.arishawke.asala.calendar.data.CalendarRepository
  import com.arishawke.asala.calendar.data.EventDraft
  import com.arishawke.asala.calendar.data.EventRepository
  import kotlinx.coroutines.flow.MutableStateFlow
  import kotlinx.coroutines.flow.StateFlow
  import kotlinx.coroutines.flow.asStateFlow
  import kotlinx.coroutines.flow.first
  import kotlinx.coroutines.launch
  import java.time.LocalDate
  import java.time.LocalTime
  import java.time.ZoneId
  import java.util.TimeZone

  data class EventEditFormState(
      val calendars: List<CalendarItem> = emptyList(),
      val selectedCalendarId: Long? = null,
      val title: String = "",
      val description: String = "",
      val location: String = "",
      val startDate: LocalDate = LocalDate.now(),
      val startTime: LocalTime = nextRoundHour(),
      val endDate: LocalDate = LocalDate.now(),
      val endTime: LocalTime = nextRoundHour().plusHours(1),
      val allDay: Boolean = false,
  ) {
      val isEndAfterStart: Boolean
          get() {
              if (allDay) return !endDate.isBefore(startDate)
              val s = startDate.atTime(startTime)
              val e = endDate.atTime(endTime)
              return e.isAfter(s)
          }

      companion object {
          fun nextRoundHour(): LocalTime = LocalTime.now().withMinute(0).withSecond(0).withNano(0).plusHours(1)
      }
  }

  sealed interface SaveResult {
      data class Success(val eventId: Long) : SaveResult
      object Failure : SaveResult
  }

  class EventEditViewModel(
      private val eventRepo: EventRepository,
      calendarRepo: CalendarRepository,
  ) : ViewModel() {

      private val _form = MutableStateFlow(EventEditFormState())
      val form: StateFlow<EventEditFormState> = _form.asStateFlow()

      init {
          viewModelScope.launch {
              val cals = calendarRepo.observeCalendars().first().filter { it.isVisible }
              _form.value = _form.value.copy(
                  calendars = cals,
                  selectedCalendarId = cals.firstOrNull()?.id,
              )
          }
      }

      fun updateForm(transform: EventEditFormState.() -> EventEditFormState) {
          _form.update(transform)
      }

      suspend fun save(): SaveResult {
          val s = _form.value
          val calId = s.selectedCalendarId ?: return SaveResult.Failure
          val zone = ZoneId.systemDefault()
          val tz = if (s.allDay) "UTC" else TimeZone.getDefault().id

          val startMillis: Long
          val endMillis: Long
          if (s.allDay) {
              startMillis = s.startDate.atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli()
              // RFC 5545: all-day end is exclusive (day after).
              endMillis = s.endDate.plusDays(1).atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli()
          } else {
              startMillis = s.startDate.atTime(s.startTime).atZone(zone).toInstant().toEpochMilli()
              endMillis = s.endDate.atTime(s.endTime).atZone(zone).toInstant().toEpochMilli()
          }

          val draft = EventDraft(
              calendarId = calId,
              title = s.title,
              description = s.description.ifBlank { null },
              location = s.location.ifBlank { null },
              startMillis = startMillis,
              endMillis = endMillis,
              allDay = s.allDay,
              eventTimezone = tz,
              rrule = null,
          )

          val id = eventRepo.insertEvent(draft)
          return if (id != null) SaveResult.Success(id) else SaveResult.Failure
      }

      class Factory(private val appContext: Context) : ViewModelProvider.Factory {
          @Suppress("UNCHECKED_CAST")
          override fun <T : ViewModel> create(modelClass: Class<T>): T {
              require(modelClass == EventEditViewModel::class.java)
              return EventEditViewModel(
                  eventRepo = EventRepository(appContext.contentResolver),
                  calendarRepo = CalendarRepository(appContext.contentResolver),
              ) as T
          }
      }
  }

  // Top-level helper because Kotlin Compose doesn't import the inner one.
  private fun MutableStateFlow<EventEditFormState>.update(t: EventEditFormState.() -> EventEditFormState) {
      value = t(value)
  }
  ```

- [ ] **Step 2: Compile**

  ```bash
  ./gradlew :app:compileDebugKotlin
  ```

### Task 3.4: EventEditScreen + form composables

The screen is full-screen, not a sheet. Contains the form: title,
date/time pickers (Material 3 `DatePicker` + `TimePicker`), all-day
switch, calendar dropdown, location, description, and a Save button
in the TopAppBar.

- [ ] **Step 1: Create DateTimePickerRow.kt**

  Small composable that shows a label + clickable date pill + (when
  not all-day) clickable time pill. Tapping each opens a Material 3
  picker dialog.

  ```kotlin
  /*
   * Copyright (C) 2026 Arishawke
   * GPL v3.
   */
  package com.arishawke.asala.calendar.ui.eventedit

  import androidx.compose.foundation.layout.*
  import androidx.compose.material3.*
  import androidx.compose.runtime.*
  import androidx.compose.ui.Modifier
  import androidx.compose.ui.unit.dp
  import java.time.LocalDate
  import java.time.LocalTime
  import java.time.ZoneOffset
  import java.time.format.DateTimeFormatter
  import java.time.format.FormatStyle
  import java.util.Locale

  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  fun DateTimePickerRow(
      label: String,
      date: LocalDate,
      time: LocalTime,
      showTime: Boolean,
      onDateChange: (LocalDate) -> Unit,
      onTimeChange: (LocalTime) -> Unit,
  ) {
      var showDatePicker by remember { mutableStateOf(false) }
      var showTimePicker by remember { mutableStateOf(false) }
      val locale = Locale.getDefault()
      val dateFmt = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
      val timeFmt = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale)

      Row(
          modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(12.dp),
      ) {
          Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
          AssistChip(
              onClick = { showDatePicker = true },
              label = { Text(dateFmt.format(date)) },
          )
          if (showTime) {
              AssistChip(
                  onClick = { showTimePicker = true },
                  label = { Text(timeFmt.format(time)) },
              )
          }
      }

      if (showDatePicker) {
          val state = rememberDatePickerState(
              initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
          )
          DatePickerDialog(
              onDismissRequest = { showDatePicker = false },
              confirmButton = {
                  TextButton(onClick = {
                      state.selectedDateMillis?.let { millis ->
                          val picked = java.time.Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                          onDateChange(picked)
                      }
                      showDatePicker = false
                  }) { Text("OK") }
              },
              dismissButton = {
                  TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
              },
          ) { DatePicker(state = state) }
      }

      if (showTimePicker) {
          val state = rememberTimePickerState(initialHour = time.hour, initialMinute = time.minute, is24Hour = false)
          AlertDialog(
              onDismissRequest = { showTimePicker = false },
              confirmButton = {
                  TextButton(onClick = {
                      onTimeChange(LocalTime.of(state.hour, state.minute))
                      showTimePicker = false
                  }) { Text("OK") }
              },
              dismissButton = {
                  TextButton(onClick = { showTimePicker = false }) { Text("Cancel") }
              },
              text = { TimePicker(state = state) },
          )
      }
  }
  ```

- [ ] **Step 2: Create EventForm.kt**

  All form fields except recurrence (slice 5) and reminder
  (slice 6).

  ```kotlin
  /*
   * Copyright (C) 2026 Arishawke
   * GPL v3.
   */
  package com.arishawke.asala.calendar.ui.eventedit

  import androidx.compose.foundation.layout.*
  import androidx.compose.foundation.rememberScrollState
  import androidx.compose.foundation.verticalScroll
  import androidx.compose.material3.*
  import androidx.compose.runtime.*
  import androidx.compose.ui.Modifier
  import androidx.compose.ui.res.stringResource
  import androidx.compose.ui.unit.dp
  import com.arishawke.asala.calendar.R

  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  fun EventForm(
      state: EventEditFormState,
      onChange: (EventEditFormState) -> Unit,
      modifier: Modifier = Modifier,
  ) {
      Column(
          modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
          OutlinedTextField(
              value = state.title,
              onValueChange = { onChange(state.copy(title = it)) },
              label = { Text(stringResource(R.string.field_title)) },
              singleLine = true,
              modifier = Modifier.fillMaxWidth(),
          )

          Row(verticalAlignment = Alignment.CenterVertically) {
              Text(stringResource(R.string.field_all_day), modifier = Modifier.weight(1f))
              Switch(
                  checked = state.allDay,
                  onCheckedChange = { onChange(state.copy(allDay = it)) },
              )
          }

          DateTimePickerRow(
              label = stringResource(R.string.field_start),
              date = state.startDate,
              time = state.startTime,
              showTime = !state.allDay,
              onDateChange = { onChange(state.copy(startDate = it)) },
              onTimeChange = { onChange(state.copy(startTime = it)) },
          )

          DateTimePickerRow(
              label = stringResource(R.string.field_end),
              date = state.endDate,
              time = state.endTime,
              showTime = !state.allDay,
              onDateChange = { onChange(state.copy(endDate = it)) },
              onTimeChange = { onChange(state.copy(endTime = it)) },
          )

          if (!state.isEndAfterStart) {
              Text(
                  stringResource(R.string.error_end_before_start),
                  color = MaterialTheme.colorScheme.error,
                  style = MaterialTheme.typography.bodySmall,
              )
          }

          CalendarDropdown(
              calendars = state.calendars,
              selectedId = state.selectedCalendarId,
              onSelect = { onChange(state.copy(selectedCalendarId = it)) },
          )

          OutlinedTextField(
              value = state.location,
              onValueChange = { onChange(state.copy(location = it)) },
              label = { Text(stringResource(R.string.field_location)) },
              singleLine = true,
              modifier = Modifier.fillMaxWidth(),
          )

          OutlinedTextField(
              value = state.description,
              onValueChange = { onChange(state.copy(description = it)) },
              label = { Text(stringResource(R.string.field_description)) },
              minLines = 3,
              modifier = Modifier.fillMaxWidth(),
          )
      }
  }

  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  private fun CalendarDropdown(
      calendars: List<com.arishawke.asala.calendar.data.CalendarItem>,
      selectedId: Long?,
      onSelect: (Long) -> Unit,
  ) {
      var expanded by remember { mutableStateOf(false) }
      val selected = calendars.firstOrNull { it.id == selectedId }
      ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
          OutlinedTextField(
              value = selected?.displayName.orEmpty(),
              onValueChange = {},
              readOnly = true,
              label = { Text(stringResource(R.string.field_calendar)) },
              trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
              modifier = Modifier.menuAnchor().fillMaxWidth(),
          )
          ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
              calendars.forEach { cal ->
                  DropdownMenuItem(
                      text = { Text(cal.displayName) },
                      onClick = {
                          onSelect(cal.id)
                          expanded = false
                      },
                  )
              }
          }
      }
  }
  ```

- [ ] **Step 3: Create EventEditScreen.kt**

  ```kotlin
  /*
   * Copyright (C) 2026 Arishawke
   * GPL v3.
   */
  package com.arishawke.asala.calendar.ui.eventedit

  import androidx.compose.foundation.layout.padding
  import androidx.compose.material.icons.Icons
  import androidx.compose.material.icons.filled.ArrowBack
  import androidx.compose.material.icons.filled.Check
  import androidx.compose.material3.*
  import androidx.compose.runtime.*
  import androidx.compose.runtime.getValue
  import androidx.compose.ui.Modifier
  import androidx.compose.ui.platform.LocalContext
  import androidx.compose.ui.res.stringResource
  import androidx.lifecycle.viewmodel.compose.viewModel
  import com.arishawke.asala.calendar.R
  import kotlinx.coroutines.launch

  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  fun EventEditScreen(
      onClose: () -> Unit,
      onSaved: (Long) -> Unit,
      onSaveFailed: () -> Unit,
  ) {
      val ctx = LocalContext.current
      val vm: EventEditViewModel = viewModel(factory = EventEditViewModel.Factory(ctx.applicationContext))
      val state by vm.form.collectAsState()
      val scope = rememberCoroutineScope()

      Scaffold(
          topBar = {
              TopAppBar(
                  title = { Text(stringResource(R.string.event_new)) },
                  navigationIcon = {
                      IconButton(onClick = onClose) {
                          Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.action_close))
                      }
                  },
                  actions = {
                      IconButton(
                          enabled = state.isEndAfterStart && state.selectedCalendarId != null,
                          onClick = {
                              scope.launch {
                                  when (val r = vm.save()) {
                                      is SaveResult.Success -> onSaved(r.eventId)
                                      SaveResult.Failure -> onSaveFailed()
                                  }
                              }
                          },
                      ) {
                          Icon(Icons.Filled.Check, contentDescription = stringResource(R.string.action_save))
                      }
                  },
              )
          },
      ) { padding ->
          EventForm(
              state = state,
              onChange = { newState -> vm.updateForm { newState } },
              modifier = Modifier.padding(padding),
          )
      }
  }
  ```

- [ ] **Step 4: Add strings**

  Append to `strings.xml`:

  ```xml
  <string name="event_new">New event</string>
  <string name="action_save">Save</string>
  <string name="field_title">Title</string>
  <string name="field_all_day">All day</string>
  <string name="field_start">Starts</string>
  <string name="field_end">Ends</string>
  <string name="field_calendar">Calendar</string>
  <string name="field_location">Location</string>
  <string name="field_description">Description</string>
  <string name="error_end_before_start">End must be after start</string>
  <string name="fab_new_event">New event</string>
  ```

- [ ] **Step 5: Compile**

  ```bash
  ./gradlew :app:compileDebugKotlin
  ```

### Task 3.5: FAB and routing in MainActivity

- [ ] **Step 1: Add edit-mode state to AppViewModel**

  ```kotlin
  // null = no editor open; -1L = create new; >0 = edit existing
  private val _editEventId = MutableStateFlow<Long?>(null)
  val editEventId: StateFlow<Long?> = _editEventId.asStateFlow()

  fun openCreateEditor() { _editEventId.update { -1L } }
  fun openEditEditor(eventId: Long) { _editEventId.update { eventId } }
  fun closeEditor() { _editEventId.update { null } }
  ```

- [ ] **Step 2: Add FAB and routing in MainActivity**

  Inside the Scaffold's `floatingActionButton`:

  ```kotlin
  FloatingActionButton(onClick = { vm.openCreateEditor() }) {
      Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.fab_new_event))
  }
  ```

  Outside the Scaffold (or as a sibling overlay), conditionally
  render the editor full-screen when `editEventId != null`. For
  `-1L`, render `EventEditScreen` in create mode.

  ```kotlin
  val editId by vm.editEventId.collectAsState()
  if (editId != null) {
      EventEditScreen(
          onClose = { vm.closeEditor() },
          onSaved = { vm.closeEditor() }, // sheet stays closed; Calendar Provider observer re-emits and views refresh
          onSaveFailed = { /* slice handles snackbar */ vm.closeEditor() },
      )
  }
  ```

- [ ] **Step 3: Compile + install + manual verify**

  ```bash
  ./gradlew installDebug
  adb shell am start -n com.arishawke.asala.calendar/.MainActivity
  ```

  Test on phone:
  - Tap FAB → editor opens with sensible defaults (today, next hour).
  - Enter title, set start/end, pick calendar, tap Save (check icon) → editor closes, new event appears in views.
  - Try Save with end < start → check is disabled, error text visible.
  - Try all-day toggle → time pickers disappear.
  - Tap back arrow → editor closes without saving.

### Task 3.6: CHANGELOG + commit

- [ ] **Step 1: Add Unreleased entry**

  ```markdown
  - Tap the FAB to create a new event. Form includes title,
    start/end date and time, all-day toggle, calendar selector,
    location, and description. Save writes the event through the
    Calendar Provider; the views update via the existing
    ContentObserver. Editing existing events lands in the next
    slice.
  ```

- [ ] **Step 2: Commit**

  ```bash
  git add -A
  git commit -m "feat(event): create non-recurring events from FAB"
  ```

---

## Slice 4 — Edit + delete (non-recurring)

The Edit and Delete buttons in the detail sheet now do something.
Edit pre-fills the editor with the event's current values; Save
calls `updateEvent`. Delete shows a confirm dialog; on confirm,
calls `deleteEvent`.

**Files touched:**
- Modify: `data/EventRepository.kt` (+ `updateEvent`, `deleteEvent`)
- Modify: `ui/eventedit/EventEditViewModel.kt` (load existing event)
- Modify: `ui/eventedit/EventEditScreen.kt` (title says "Edit event" when editing)
- Modify: `ui/eventdetail/EventDetailSheet.kt` (Delete shows AlertDialog confirm)
- Modify: `MainActivity.kt` (route detail-sheet Edit/Delete to AppViewModel)
- Modify: `res/values/strings.xml`
- Modify: `CHANGELOG.md`

### Task 4.1: Repo update + delete

- [ ] **Step 1: Add updateEvent and deleteEvent**

  In `EventRepository.kt`:

  ```kotlin
  suspend fun updateEvent(eventId: Long, draft: EventDraft): Boolean =
      withContext(Dispatchers.IO) {
          val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
          contentResolver.update(uri, draft.toContentValues(), null, null) > 0
      }

  suspend fun deleteEvent(eventId: Long): Boolean =
      withContext(Dispatchers.IO) {
          val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
          contentResolver.delete(uri, null, null) > 0
      }
  ```

- [ ] **Step 2: Compile**

### Task 4.2: EventEditViewModel — load existing event

- [ ] **Step 1: Accept optional eventId in factory**

  Replace the factory's signature:

  ```kotlin
  class Factory(
      private val appContext: Context,
      private val eventId: Long? = null,
  ) : ViewModelProvider.Factory {
      @Suppress("UNCHECKED_CAST")
      override fun <T : ViewModel> create(modelClass: Class<T>): T {
          require(modelClass == EventEditViewModel::class.java)
          return EventEditViewModel(
              eventRepo = EventRepository(appContext.contentResolver),
              calendarRepo = CalendarRepository(appContext.contentResolver),
              editingEventId = eventId,
          ) as T
      }
  }
  ```

  Replace the VM constructor:

  ```kotlin
  class EventEditViewModel(
      private val eventRepo: EventRepository,
      calendarRepo: CalendarRepository,
      private val editingEventId: Long? = null,
  ) : ViewModel() {
      // ... existing _form ...

      init {
          viewModelScope.launch {
              val cals = calendarRepo.observeCalendars().first().filter { it.isVisible }
              val existing = editingEventId?.let { eventRepo.fetchEventDetail(it) }

              _form.value = if (existing != null) {
                  val zone = ZoneId.systemDefault()
                  val startInstant = java.time.Instant.ofEpochMilli(existing.startMillis)
                  val endInstant = java.time.Instant.ofEpochMilli(existing.endMillis)
                  val sLocal = startInstant.atZone(zone).toLocalDateTime()
                  val eLocal = endInstant.atZone(zone).toLocalDateTime()
                  EventEditFormState(
                      calendars = cals,
                      selectedCalendarId = existing.calendarId,
                      title = existing.title,
                      description = existing.description.orEmpty(),
                      location = existing.location.orEmpty(),
                      startDate = sLocal.toLocalDate(),
                      startTime = sLocal.toLocalTime(),
                      endDate = eLocal.toLocalDate(),
                      endTime = eLocal.toLocalTime(),
                      allDay = existing.allDay,
                  )
              } else {
                  _form.value.copy(
                      calendars = cals,
                      selectedCalendarId = cals.firstOrNull()?.id,
                  )
              }
          }
      }
  ```

- [ ] **Step 2: Update save() to route to update vs insert**

  Replace `save()`:

  ```kotlin
  suspend fun save(): SaveResult {
      val s = _form.value
      val calId = s.selectedCalendarId ?: return SaveResult.Failure
      val zone = ZoneId.systemDefault()
      val tz = if (s.allDay) "UTC" else TimeZone.getDefault().id

      val startMillis: Long
      val endMillis: Long
      if (s.allDay) {
          startMillis = s.startDate.atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli()
          endMillis = s.endDate.plusDays(1).atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli()
      } else {
          startMillis = s.startDate.atTime(s.startTime).atZone(zone).toInstant().toEpochMilli()
          endMillis = s.endDate.atTime(s.endTime).atZone(zone).toInstant().toEpochMilli()
      }

      val draft = EventDraft(
          calendarId = calId,
          title = s.title,
          description = s.description.ifBlank { null },
          location = s.location.ifBlank { null },
          startMillis = startMillis,
          endMillis = endMillis,
          allDay = s.allDay,
          eventTimezone = tz,
          rrule = null,
      )

      return if (editingEventId == null) {
          val id = eventRepo.insertEvent(draft)
          if (id != null) SaveResult.Success(id) else SaveResult.Failure
      } else {
          val ok = eventRepo.updateEvent(editingEventId, draft)
          if (ok) SaveResult.Success(editingEventId) else SaveResult.Failure
      }
  }

  suspend fun delete(): Boolean {
      val id = editingEventId ?: return false
      return eventRepo.deleteEvent(id)
  }
  ```

- [ ] **Step 3: Compile**

### Task 4.3: EventEditScreen — title and eventId

- [ ] **Step 1: Pass eventId through**

  Update `EventEditScreen` to take `eventId: Long?` and pass to the
  factory. Change the TopAppBar title based on null vs non-null:
  `stringResource(if (eventId == null) R.string.event_new else R.string.event_edit)`.

  Add string:
  ```xml
  <string name="event_edit">Edit event</string>
  ```

### Task 4.4: Detail sheet — confirm delete

- [ ] **Step 1: Add confirm dialog in EventDetailSheet**

  Inside `EventDetailContent`, add:

  ```kotlin
  var showConfirmDelete by remember { mutableStateOf(false) }

  // ... in the Row of buttons, change Delete:
  TextButton(onClick = { showConfirmDelete = true }) { Text(stringResource(R.string.action_delete)) }

  if (showConfirmDelete) {
      AlertDialog(
          onDismissRequest = { showConfirmDelete = false },
          title = { Text(stringResource(R.string.confirm_delete_title)) },
          text = { Text(stringResource(R.string.confirm_delete_body)) },
          confirmButton = {
              TextButton(onClick = {
                  showConfirmDelete = false
                  onDelete()
              }) { Text(stringResource(R.string.action_delete)) }
          },
          dismissButton = {
              TextButton(onClick = { showConfirmDelete = false }) { Text(stringResource(R.string.action_cancel)) }
          },
      )
  }
  ```

  Add strings:
  ```xml
  <string name="confirm_delete_title">Delete event?</string>
  <string name="confirm_delete_body">This event will be removed from your calendar.</string>
  <string name="action_cancel">Cancel</string>
  ```

### Task 4.5: Wire detail-sheet actions through MainActivity

- [ ] **Step 1: Add eventRepo to AppViewModel via Factory injection**

  Update AppViewModel's constructor and Factory in
  [`AppViewModel.kt`](../../app/src/main/kotlin/com/arishawke/asala/calendar/AppViewModel.kt)
  to inject an `EventRepository` alongside `calendarRepo`:

  ```kotlin
  class AppViewModel(
      private val calendarRepo: CalendarRepository,
      private val eventRepo: EventRepository,
      private val themePreference: ThemePreference,
      initialThemeMode: ThemeMode,
  ) : ViewModel() {
      // ... existing fields ...

      fun deleteEvent(eventId: Long) {
          viewModelScope.launch {
              eventRepo.deleteEvent(eventId)
              closeEventDetail()
          }
      }

      class Factory(private val appContext: Context) : ViewModelProvider.Factory {
          @Suppress("UNCHECKED_CAST")
          override fun <T : ViewModel> create(modelClass: Class<T>): T {
              require(modelClass == AppViewModel::class.java)
              val themePref = ThemePreference(appContext.settingsDataStore)
              val initialTheme = runBlocking { themePref.themeMode.first() }
              return AppViewModel(
                  calendarRepo = CalendarRepository(appContext.contentResolver),
                  eventRepo = EventRepository(appContext.contentResolver),
                  themePreference = themePref,
                  initialThemeMode = initialTheme,
              ) as T
          }
      }
  }
  ```

  Choosing constructor injection over promoting to AndroidViewModel
  because the existing Factory already follows this pattern for
  CalendarRepository — keeps a consistent shape.

- [ ] **Step 2: Wire MainActivity callbacks**

  In MainActivity, replace the no-op `onEdit`/`onDelete`:

  ```kotlin
  EventDetailSheet(
      eventId = id,
      onDismiss = { vm.closeEventDetail() },
      onEdit = { eid ->
          vm.closeEventDetail()
          vm.openEditEditor(eid)
      },
      onDelete = { eid -> vm.deleteEvent(eid) },
  )
  ```

- [ ] **Step 3: Connect editEventId routing**

  Wire `vm.editEventId` to `EventEditScreen` with the right id:

  ```kotlin
  val editId by vm.editEventId.collectAsState()
  editId?.let { id ->
      val effectiveId = if (id == -1L) null else id
      EventEditScreen(
          eventId = effectiveId,
          onClose = { vm.closeEditor() },
          onSaved = { vm.closeEditor() },
          onSaveFailed = { vm.closeEditor() },
      )
  }
  ```

### Task 4.6: Manual verify + commit

- [ ] **Step 1: Install and verify**

  ```bash
  ./gradlew installDebug && adb shell am start -n com.arishawke.asala.calendar/.MainActivity
  ```

  Test on phone:
  - Tap an event you created in slice 3 → detail sheet.
  - Tap Edit → editor opens pre-filled.
  - Change title, Save → detail sheet stays closed, change visible in Month/Week.
  - Tap an event again → tap Delete → confirm dialog → Delete → event disappears from views.
  - Cancel on confirm dialog → event remains.

- [ ] **Step 2: CHANGELOG**

  ```markdown
  - Tap Edit in the event detail sheet to edit the event in the
    same form used for new events. Save updates the event in
    place. Tap Delete to remove the event after a confirm dialog.
    Recurring events are handled in the next slice; for now,
    edit and delete on a recurring event modify the whole series.
  ```

- [ ] **Step 3: Commit**

  ```bash
  git add -A
  git commit -m "feat(event): edit and delete non-recurring events"
  ```

---

## Slice 5 — Recurrence end-to-end

The biggest slice. Adds: recurrence picker in the editor, RRULE
builder, repository writes that handle recurring-event exception
math, and the 3-option scope dialog for editing or deleting
recurring events.

**Why combined:** Shipping recurrence-create without the scope
dialog would leave the app in a state where users can create
recurring events that they cannot subsequently edit safely.

**Files touched:**
- Create: `data/RecurringEditScope.kt`
- Create: `ui/eventedit/RecurrenceSection.kt`
- Create: `ui/eventedit/RecurringEditScopeDialog.kt`
- Create: `app/src/test/kotlin/.../data/RecurringExceptionMathTest.kt`
- Modify: `data/RecurrenceRule.kt` (+ builder)
- Modify: `data/EventRepository.kt` (+ exception logic in update/delete)
- Modify: `data/EventDetail.kt` (no change but used)
- Modify: `data/EventItem.kt` (+ `hasRecurrence`, populated from RRULE)
- Modify: `ui/eventedit/EventEditViewModel.kt` (route through scope)
- Modify: `ui/eventedit/EventEditScreen.kt` (add RecurrenceSection)
- Modify: `ui/eventdetail/EventDetailSheet.kt` (Delete on recurring opens scope dialog)
- Modify: `MainActivity.kt` (scope-aware routing)
- Modify: `AppViewModel.kt` (scope-aware delete; route edit with scope)
- Modify: `res/values/strings.xml`
- Modify: `CHANGELOG.md`

### Task 5.1: RecurringEditScope enum + RecurrenceRule builder

- [ ] **Step 1: Create RecurringEditScope.kt**

  ```kotlin
  /*
   * Copyright (C) 2026 Arishawke
   * GPL v3.
   */
  package com.arishawke.asala.calendar.data

  enum class RecurringEditScope { ThisInstance, ThisAndFollowing, AllEvents }
  ```

- [ ] **Step 2: Write builder tests**

  Append to `RecurrenceRuleTest.kt`:

  ```kotlin
  import java.time.LocalDate

  @Test fun builds_daily_no_end() {
      val r = RecurrenceRule.build(RecurrenceFrequency.Daily, interval = 1, untilUtc = null, count = null)
      assertEquals("FREQ=DAILY", r)
  }

  @Test fun builds_weekly_until() {
      // 2026-12-31 in UTC at end of day-ish
      val r = RecurrenceRule.build(
          frequency = RecurrenceFrequency.Weekly,
          interval = 1,
          untilUtc = LocalDate.of(2026, 12, 31),
          count = null,
      )
      assertEquals("FREQ=WEEKLY;UNTIL=20261231T235959Z", r)
  }

  @Test fun builds_monthly_count() {
      val r = RecurrenceRule.build(RecurrenceFrequency.Monthly, interval = 1, untilUtc = null, count = 12)
      assertEquals("FREQ=MONTHLY;COUNT=12", r)
  }

  @Test fun builds_yearly_interval_2() {
      val r = RecurrenceRule.build(RecurrenceFrequency.Yearly, interval = 2, untilUtc = null, count = null)
      assertEquals("FREQ=YEARLY;INTERVAL=2", r)
  }
  ```

- [ ] **Step 3: Run, verify fails**

  ```bash
  ./gradlew :app:testDebugUnitTest --tests RecurrenceRuleTest
  ```

- [ ] **Step 4: Add build() to RecurrenceRule.kt**

  ```kotlin
  import java.time.LocalDate

  // Extend the existing object:
  object RecurrenceRule {

      // ... existing frequencyOf() ...

      fun build(
          frequency: RecurrenceFrequency,
          interval: Int = 1,
          untilUtc: LocalDate? = null,
          count: Int? = null,
      ): String {
          require(!(untilUtc != null && count != null)) {
              "Specify either untilUtc or count, not both"
          }
          val parts = mutableListOf("FREQ=${frequency.name.uppercase()}")
          if (interval != 1) parts += "INTERVAL=$interval"
          if (count != null) parts += "COUNT=$count"
          if (untilUtc != null) {
              // RFC 5545 UTC form: YYYYMMDDTHHMMSSZ
              val y = "%04d".format(untilUtc.year)
              val m = "%02d".format(untilUtc.monthValue)
              val d = "%02d".format(untilUtc.dayOfMonth)
              parts += "UNTIL=${y}${m}${d}T235959Z"
          }
          return parts.joinToString(";")
      }
  }
  ```

- [ ] **Step 5: Run, verify passes**

### Task 5.2: Recurring exception math tests

The trickiest logic in M3 is computing the parent's RRULE truncation
and the exception event's `originalInstanceTime`.

- [ ] **Step 1: Write failing tests**

  Create `RecurringExceptionMathTest.kt`:

  ```kotlin
  /*
   * Copyright (C) 2026 Arishawke
   * GPL v3.
   */
  package com.arishawke.asala.calendar.data

  import org.junit.Test
  import org.junit.Assert.assertEquals
  import java.time.LocalDate
  import java.time.LocalDateTime
  import java.time.ZoneId

  class RecurringExceptionMathTest {

      @Test fun originalInstanceTime_is_parent_dtstart_for_first_occurrence() {
          val parentDtstart = ZoneId.of("America/New_York")
              .let { LocalDateTime.of(2026, 3, 1, 9, 0).atZone(it) }
              .toInstant().toEpochMilli()
          val instanceTime = parentDtstart  // first occurrence
          val computed = RecurrenceExceptionMath.originalInstanceTime(
              parentDtstart = parentDtstart,
              instanceUtcMillis = instanceTime,
          )
          assertEquals(parentDtstart, computed)
      }

      @Test fun originalInstanceTime_for_third_weekly_occurrence_in_NY() {
          val zone = ZoneId.of("America/New_York")
          val first = LocalDateTime.of(2026, 3, 1, 9, 0).atZone(zone).toInstant().toEpochMilli()
          val third = LocalDateTime.of(2026, 3, 15, 9, 0).atZone(zone).toInstant().toEpochMilli()
          val computed = RecurrenceExceptionMath.originalInstanceTime(
              parentDtstart = first,
              instanceUtcMillis = third,
          )
          // Provider expects the instance's UTC millis as it would have appeared without modification.
          assertEquals(third, computed)
      }

      @Test fun untilForTruncation_is_one_millisecond_before_instance() {
          val instanceMillis = 1_700_000_000_000L
          val until = RecurrenceExceptionMath.untilUtcForTruncation(instanceMillis)
          // UNTIL must be strictly before the truncated instance.
          // We choose 1ms before in epoch and emit the RFC 5545 UTC string.
          assertEquals("UNTIL=20231114T220639Z", until)
      }

      @Test fun appendUntil_replaces_existing_until_or_count() {
          assertEquals(
              "FREQ=WEEKLY;UNTIL=20260301T085959Z",
              RecurrenceExceptionMath.appendUntil("FREQ=WEEKLY", "UNTIL=20260301T085959Z"),
          )
          assertEquals(
              "FREQ=WEEKLY;UNTIL=20260301T085959Z",
              RecurrenceExceptionMath.appendUntil("FREQ=WEEKLY;UNTIL=20271231T235959Z", "UNTIL=20260301T085959Z"),
          )
          assertEquals(
              "FREQ=WEEKLY;UNTIL=20260301T085959Z",
              RecurrenceExceptionMath.appendUntil("FREQ=WEEKLY;COUNT=10", "UNTIL=20260301T085959Z"),
          )
      }
  }
  ```

  Notes about the second test: `originalInstanceTime` for a
  recurring event's exception is the instance's start time as the
  provider expanded it, in UTC millis. For a DST-stable zone like
  America/New_York and a 9am weekly event, this is straightforward;
  for events that cross DST, the provider does the heavy lifting
  and our helper just passes the value through.

  Note about the third test: the expected hardcoded string corresponds to
  1_699_999_999_999L (one ms before 1_700_000_000_000L) rendered
  in UTC. Verify by computing it inline in the helper.

- [ ] **Step 2: Run, verify fails**

- [ ] **Step 3: Create RecurrenceExceptionMath.kt**

  Create `app/src/main/kotlin/com/arishawke/asala/calendar/data/RecurrenceExceptionMath.kt`:

  ```kotlin
  /*
   * Copyright (C) 2026 Arishawke
   * GPL v3.
   */
  package com.arishawke.asala.calendar.data

  import java.time.Instant
  import java.time.ZoneOffset
  import java.time.format.DateTimeFormatter

  object RecurrenceExceptionMath {

      /** The provider's `originalInstanceTime` for a single-instance edit is the
       *  instance's start time in UTC millis as the recurrence would have placed it.
       */
      fun originalInstanceTime(parentDtstart: Long, instanceUtcMillis: Long): Long =
          instanceUtcMillis

      /** Compute the `UNTIL=...` segment that truncates a parent recurrence
       *  immediately before the given instance.
       */
      fun untilUtcForTruncation(instanceUtcMillis: Long): String {
          val cutoff = Instant.ofEpochMilli(instanceUtcMillis - 1).atOffset(ZoneOffset.UTC)
          val s = cutoff.format(UTC_ICAL_FORMAT)
          return "UNTIL=$s"
      }

      /** Splice an `UNTIL=...` segment into an existing RRULE, replacing any
       *  existing UNTIL or COUNT.
       */
      fun appendUntil(rrule: String, untilSegment: String): String {
          val parts = rrule.split(";").filter {
              !it.startsWith("UNTIL=") && !it.startsWith("COUNT=")
          }
          return (parts + untilSegment).joinToString(";")
      }

      private val UTC_ICAL_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
  }
  ```

- [ ] **Step 4: Run, verify passes**

  ```bash
  ./gradlew :app:testDebugUnitTest --tests RecurringExceptionMathTest
  ```

### Task 5.3: Repository writes with scope

- [ ] **Step 1: Modify EventRepository.updateEvent and deleteEvent to accept scope**

  Replace the signatures:

  ```kotlin
  suspend fun updateEvent(
      eventId: Long,
      draft: EventDraft,
      scope: RecurringEditScope = RecurringEditScope.AllEvents,
      instanceMillis: Long? = null,
      parentDtstart: Long? = null,
      parentRrule: String? = null,
  ): Boolean = withContext(Dispatchers.IO) {
      when (scope) {
          RecurringEditScope.AllEvents -> {
              val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
              contentResolver.update(uri, draft.toContentValues(), null, null) > 0
          }
          RecurringEditScope.ThisInstance -> {
              require(instanceMillis != null && parentDtstart != null)
              val cv = draft.toContentValues().apply {
                  put(CalendarContract.Events.ORIGINAL_ID, eventId)
                  put(CalendarContract.Events.ORIGINAL_INSTANCE_TIME,
                      RecurrenceExceptionMath.originalInstanceTime(parentDtstart, instanceMillis))
                  // Exception events are not themselves recurring.
                  remove(CalendarContract.Events.RRULE)
                  remove(CalendarContract.Events.DURATION)
                  put(CalendarContract.Events.DTEND, draft.endMillis)
              }
              contentResolver.insert(CalendarContract.Events.CONTENT_URI, cv) != null
          }
          RecurringEditScope.ThisAndFollowing -> {
              require(instanceMillis != null && parentRrule != null)
              // Truncate the parent.
              val newRrule = RecurrenceExceptionMath.appendUntil(
                  parentRrule,
                  RecurrenceExceptionMath.untilUtcForTruncation(instanceMillis),
              )
              val parentUri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
              val parentCv = android.content.ContentValues().apply {
                  put(CalendarContract.Events.RRULE, newRrule)
              }
              val parentOk = contentResolver.update(parentUri, parentCv, null, null) > 0
              if (!parentOk) return@withContext false
              // Insert a new recurring event starting at instanceMillis with the
              // edited fields.
              val newDraftCv = draft.toContentValues()
              val newUri = contentResolver.insert(CalendarContract.Events.CONTENT_URI, newDraftCv)
              newUri != null
          }
      }
  }

  suspend fun deleteEvent(
      eventId: Long,
      scope: RecurringEditScope = RecurringEditScope.AllEvents,
      instanceMillis: Long? = null,
      parentDtstart: Long? = null,
      parentRrule: String? = null,
  ): Boolean = withContext(Dispatchers.IO) {
      when (scope) {
          RecurringEditScope.AllEvents -> {
              val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
              contentResolver.delete(uri, null, null) > 0
          }
          RecurringEditScope.ThisInstance -> {
              require(instanceMillis != null && parentDtstart != null)
              val cv = android.content.ContentValues().apply {
                  put(CalendarContract.Events.ORIGINAL_ID, eventId)
                  put(CalendarContract.Events.ORIGINAL_INSTANCE_TIME,
                      RecurrenceExceptionMath.originalInstanceTime(parentDtstart, instanceMillis))
                  put(CalendarContract.Events.STATUS, CalendarContract.Events.STATUS_CANCELED)
                  put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
                  put(CalendarContract.Events.DTSTART, instanceMillis)
              }
              contentResolver.insert(CalendarContract.Events.CONTENT_URI, cv) != null
          }
          RecurringEditScope.ThisAndFollowing -> {
              require(instanceMillis != null && parentRrule != null)
              val newRrule = RecurrenceExceptionMath.appendUntil(
                  parentRrule,
                  RecurrenceExceptionMath.untilUtcForTruncation(instanceMillis),
              )
              val parentUri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
              val parentCv = android.content.ContentValues().apply {
                  put(CalendarContract.Events.RRULE, newRrule)
              }
              contentResolver.update(parentUri, parentCv, null, null) > 0
          }
      }
  }
  ```

- [ ] **Step 2: Compile**

### Task 5.4: EventItem.hasRecurrence

Required so the detail sheet and editor know whether to show the
scope dialog. `Instances` exposes the parent's RRULE column.

- [ ] **Step 1: Update EventRepository's Instances projection**

  Add `CalendarContract.Instances.RRULE` to the projection array
  and a `hasRecurrence` field to `EventItem`.

  ```kotlin
  // In EventItem.kt:
  val hasRecurrence: Boolean,

  // In EventRepository.kt's Projection:
  CalendarContract.Instances.RRULE,

  // In queryInstances cursor reading:
  val rruleIdx = it.getColumnIndexOrThrow(CalendarContract.Instances.RRULE)
  // ...
  hasRecurrence = !c.getString(rruleIdx).isNullOrBlank(),
  ```

  (Adjust column indices throughout — easier to switch from
  positional to named once the projection has 9 columns.)

- [ ] **Step 2: Compile**

### Task 5.5: RecurrenceSection in editor

- [ ] **Step 1: Create RecurrenceSection.kt**

  A row inside `EventForm` showing "Does not repeat" by default,
  with a tap to open a small menu of frequencies. When a frequency
  is selected, show a second row for end condition: "Forever" /
  "Ends on…" / "Ends after… occurrences".

  Add fields to `EventEditFormState`:

  ```kotlin
  val recurrenceFrequency: RecurrenceFrequency? = null,
  val recurrenceUntilDate: LocalDate? = null,
  val recurrenceCount: Int? = null,
  ```

  The composable wires onChange to update those fields.

  Show the section after the calendar dropdown in `EventForm`.

  Add strings:
  ```xml
  <string name="repeats">Repeats</string>
  <string name="repeats_does_not">Does not repeat</string>
  <string name="repeats_ends">Ends</string>
  <string name="ends_never">Forever</string>
  <string name="ends_on_date">On date</string>
  <string name="ends_after_count">After N occurrences</string>
  ```

- [ ] **Step 2: Update VM.save() to build RRULE**

  In `EventEditViewModel.save()`:

  ```kotlin
  val rrule = s.recurrenceFrequency?.let { freq ->
      RecurrenceRule.build(
          frequency = freq,
          interval = 1,
          untilUtc = s.recurrenceUntilDate,
          count = s.recurrenceCount,
      )
  }

  val draft = EventDraft(
      // ... existing fields ...
      rrule = rrule,
  )
  ```

  If editing an existing recurring event, populate
  `recurrenceFrequency` etc. from the loaded `EventDetail.rrule`
  via `RecurrenceRule.frequencyOf(...)`.

- [ ] **Step 3: Compile**

### Task 5.6: RecurringEditScopeDialog

- [ ] **Step 1: Create RecurringEditScopeDialog.kt**

  ```kotlin
  /*
   * Copyright (C) 2026 Arishawke
   * GPL v3.
   */
  package com.arishawke.asala.calendar.ui.eventedit

  import androidx.compose.foundation.layout.*
  import androidx.compose.foundation.selection.selectable
  import androidx.compose.material3.*
  import androidx.compose.runtime.*
  import androidx.compose.ui.Modifier
  import androidx.compose.ui.res.stringResource
  import androidx.compose.ui.semantics.Role
  import androidx.compose.ui.unit.dp
  import com.arishawke.asala.calendar.R
  import com.arishawke.asala.calendar.data.RecurringEditScope

  @Composable
  fun RecurringEditScopeDialog(
      titleRes: Int,                         // R.string.scope_title_edit or scope_title_delete
      onPick: (RecurringEditScope) -> Unit,
      onCancel: () -> Unit,
  ) {
      var selected by remember { mutableStateOf<RecurringEditScope?>(null) }
      AlertDialog(
          onDismissRequest = onCancel,
          title = { Text(stringResource(titleRes)) },
          text = {
              Column(Modifier.fillMaxWidth()) {
                  Option(RecurringEditScope.ThisInstance, R.string.scope_this_event, selected) { selected = it }
                  Option(RecurringEditScope.ThisAndFollowing, R.string.scope_this_and_following, selected) { selected = it }
                  Option(RecurringEditScope.AllEvents, R.string.scope_all_events, selected) { selected = it }
              }
          },
          confirmButton = {
              TextButton(
                  enabled = selected != null,
                  onClick = { selected?.let(onPick) },
              ) { Text(stringResource(R.string.action_ok)) }
          },
          dismissButton = {
              TextButton(onClick = onCancel) { Text(stringResource(R.string.action_cancel)) }
          },
      )
  }

  @Composable
  private fun Option(
      scope: RecurringEditScope,
      labelRes: Int,
      selected: RecurringEditScope?,
      onSelect: (RecurringEditScope) -> Unit,
  ) {
      Row(
          modifier = Modifier
              .fillMaxWidth()
              .selectable(
                  selected = scope == selected,
                  role = Role.RadioButton,
                  onClick = { onSelect(scope) },
              )
              .padding(vertical = 8.dp),
          verticalAlignment = Alignment.CenterVertically,
      ) {
          RadioButton(selected = scope == selected, onClick = { onSelect(scope) })
          Spacer(Modifier.width(8.dp))
          Text(stringResource(labelRes))
      }
  }
  ```

  Add strings:
  ```xml
  <string name="scope_title_edit">Change recurring event</string>
  <string name="scope_title_delete">Delete recurring event</string>
  <string name="scope_this_event">Only this event</string>
  <string name="scope_this_and_following">This and following events</string>
  <string name="scope_all_events">All events</string>
  <string name="action_ok">OK</string>
  ```

### Task 5.7: Wire scope dialog into edit and delete flows

The dialog appears when:
- User taps Save in editor AND the event is recurring (hasRecurrence true)
- User confirms Delete in detail sheet AND the event is recurring

- [ ] **Step 1: Update EventDetailSheet's Delete flow**

  Pass `isRecurring: Boolean` derived from the loaded
  `EventDetail.rrule != null`. When user confirms Delete on a
  recurring event, instead of immediately calling `onDelete(id)`,
  open `RecurringEditScopeDialog` and propagate the chosen scope.

  Update `onDelete` callback signature in `EventDetailSheet` to
  `(Long, RecurringEditScope) -> Unit`. Non-recurring events pass
  `RecurringEditScope.AllEvents`.

- [ ] **Step 2: Update EventEditScreen + VM's save flow**

  When the user taps Save and the event is recurring:
  - If creating: no scope needed (it's a new event).
  - If editing: show `RecurringEditScopeDialog`, pass picked scope
    to `vm.save(scope)`.

  Modify `vm.save()` to accept `scope: RecurringEditScope` and
  thread to `eventRepo.updateEvent(...)`. Also pass `instanceMillis`,
  `parentDtstart`, `parentRrule` when scope is not `AllEvents`. The
  VM needs the loaded `EventDetail` for these — preserve it in
  the VM (don't discard after init).

- [ ] **Step 3: Update AppViewModel.deleteEvent signature**

  ```kotlin
  fun deleteEvent(
      eventId: Long,
      scope: RecurringEditScope = RecurringEditScope.AllEvents,
      instanceMillis: Long? = null,
      parentDtstart: Long? = null,
      parentRrule: String? = null,
  ) {
      viewModelScope.launch {
          eventRepo.deleteEvent(eventId, scope, instanceMillis, parentDtstart, parentRrule)
          closeEventDetail()
      }
  }
  ```

- [ ] **Step 4: Track the tapped instance's BEGIN time end-to-end**

  The detail flow needs the instance's `BEGIN` time (from the tapped
  `EventItem.startMillis`), not just the eventId, so the scope
  dialog's "Only this event" and "This and following" branches know
  which instance was picked.

  Extend `AppViewModel.openEventDetail` to take both:

  ```kotlin
  // null = sheet closed
  data class OpenEvent(val eventId: Long, val instanceMillis: Long)
  private val _detailSheetEvent = MutableStateFlow<OpenEvent?>(null)
  val detailSheetEvent: StateFlow<OpenEvent?> = _detailSheetEvent.asStateFlow()

  fun openEventDetail(eventId: Long, instanceMillis: Long) {
      _detailSheetEvent.update { OpenEvent(eventId, instanceMillis) }
  }

  fun closeEventDetail() { _detailSheetEvent.update { null } }
  ```

  Update the 4 view onClick callbacks (task 2.7) to pass
  `(item.eventId, item.startMillis)` instead of just `item.eventId`.

  `EventDetailSheet` already loads the full `EventDetail` (and thus
  has `rrule` and parent `startMillis`). Hoist `EventDetail?` up to
  `MainActivity` via a callback so the delete call site has it:

  ```kotlin
  var loadedDetail by remember { mutableStateOf<com.arishawke.asala.calendar.data.EventDetail?>(null) }
  val open by vm.detailSheetEvent.collectAsState()
  open?.let { o ->
      EventDetailSheet(
          eventId = o.eventId,
          onLoaded = { loadedDetail = it },
          onDismiss = { loadedDetail = null; vm.closeEventDetail() },
          onEdit = { eid -> loadedDetail = null; vm.closeEventDetail(); vm.openEditEditor(eid) },
          onDelete = { eid, scope ->
              vm.deleteEvent(
                  eventId = eid,
                  scope = scope,
                  instanceMillis = o.instanceMillis,
                  parentDtstart = loadedDetail?.startMillis,
                  parentRrule = loadedDetail?.rrule,
              )
              loadedDetail = null
          },
      )
  }
  ```

  Add the `onLoaded: (EventDetail) -> Unit` parameter to
  `EventDetailSheet`. Invoke it inside the `LaunchedEffect(detail)`
  that watches the VM's flow.

- [ ] **Step 5: Compile**

### Task 5.8: Manual verify + commit

- [ ] **Step 1: Install and verify on phone**

  Test these flows:

  Create:
  - FAB → recurrence = Weekly, ends on a date 2 months out, Save → event appears each week.
  - FAB → recurrence = Monthly, no end → next 6 months show event.
  - FAB → all-day + Yearly → next year shows event on same date.

  Edit:
  - Tap a recurring instance → Edit → change title → Save →
    scope dialog → "Only this event" → only that instance shows
    the new title; siblings unchanged.
  - Tap the recurring series' parent (or any other instance) →
    Edit → change start time → Save → "This and following" →
    instances on and after the picked one move to the new time;
    prior instances keep the old time.
  - Tap an instance → Edit → "All events" → all instances reflect.

  Delete:
  - Tap an instance → Delete → confirm → scope dialog → "Only
    this event" → only that day is removed from the series.
  - Same flow with "This and following" → from picked date forward
    is empty.
  - Same with "All events" → entire series removed.

  Cross-DST test:
  - Create a weekly event on a Tuesday 9am in late February.
  - Confirm March's DST-crossing Tuesday still shows 9am, not 8am
    or 10am.

- [ ] **Step 2: CHANGELOG entry**

  ```markdown
  - Create recurring events with daily, weekly, monthly, or yearly
    frequencies and an optional end date or occurrence count.
    Editing or deleting a recurring event opens a standard
    dialog with three choices: "Only this event", "This and
    following events", or "All events". The choice is required;
    no option is preselected. Recurring exceptions are stored
    natively in the Calendar Provider via the original_id link.
  ```

- [ ] **Step 3: Commit**

  ```bash
  git add -A
  git commit -m "feat(event): recurrence end-to-end with scope-aware edits and deletes"
  ```

---

## Slice 6 — Reminder field in editor

A single reminder offset per event. Writes to
`CalendarContract.Reminders`. Notifications do not fire in M3;
this is purely the write path so M4's notification subsystem has
data to render.

**Files touched:**
- Create: `data/RemindersRepository.kt`
- Create: `ui/eventedit/ReminderPicker.kt`
- Modify: `ui/eventedit/EventEditViewModel.kt` (reminder field in form)
- Modify: `ui/eventedit/EventForm.kt` (add picker below recurrence)
- Modify: `res/values/strings.xml`
- Modify: `CHANGELOG.md`

### Task 6.1: RemindersRepository

- [ ] **Step 1: Create RemindersRepository.kt**

  ```kotlin
  /*
   * Copyright (C) 2026 Arishawke
   * GPL v3.
   */
  package com.arishawke.asala.calendar.data

  import android.content.ContentResolver
  import android.content.ContentValues
  import android.provider.CalendarContract
  import kotlinx.coroutines.Dispatchers
  import kotlinx.coroutines.withContext

  class RemindersRepository(private val contentResolver: ContentResolver) {

      /** Replaces all existing reminders for the event with at most one. */
      suspend fun setReminder(eventId: Long, minutesBefore: Int?) =
          withContext(Dispatchers.IO) {
              contentResolver.delete(
                  CalendarContract.Reminders.CONTENT_URI,
                  "${CalendarContract.Reminders.EVENT_ID} = ?",
                  arrayOf(eventId.toString()),
              )
              if (minutesBefore == null) return@withContext

              val cv = ContentValues().apply {
                  put(CalendarContract.Reminders.EVENT_ID, eventId)
                  put(CalendarContract.Reminders.MINUTES, minutesBefore)
                  put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
              }
              contentResolver.insert(CalendarContract.Reminders.CONTENT_URI, cv)
          }
  }
  ```

- [ ] **Step 2: Compile**

### Task 6.2: ReminderPicker composable

- [ ] **Step 1: Create ReminderPicker.kt**

  ```kotlin
  /*
   * Copyright (C) 2026 Arishawke
   * GPL v3.
   */
  package com.arishawke.asala.calendar.ui.eventedit

  import androidx.compose.foundation.layout.fillMaxWidth
  import androidx.compose.material3.*
  import androidx.compose.runtime.*
  import androidx.compose.ui.Modifier
  import androidx.compose.ui.res.stringResource
  import com.arishawke.asala.calendar.R

  private val ReminderChoices = listOf(
      null,        // None
      0,           // At time of event
      5, 10, 15, 30,
      60,          // 1 hour
      24 * 60,     // 1 day
  )

  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  fun ReminderPicker(
      minutesBefore: Int?,
      onChange: (Int?) -> Unit,
  ) {
      var expanded by remember { mutableStateOf(false) }
      val label = labelFor(minutesBefore)
      ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
          OutlinedTextField(
              value = label,
              onValueChange = {},
              readOnly = true,
              label = { Text(stringResource(R.string.field_reminder)) },
              trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
              modifier = Modifier.menuAnchor().fillMaxWidth(),
          )
          ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
              ReminderChoices.forEach { choice ->
                  DropdownMenuItem(
                      text = { Text(labelFor(choice)) },
                      onClick = {
                          onChange(choice)
                          expanded = false
                      },
                  )
              }
          }
      }
  }

  @Composable
  private fun labelFor(m: Int?): String = when (m) {
      null -> stringResource(R.string.reminder_none)
      0 -> stringResource(R.string.reminder_at_time)
      60 -> stringResource(R.string.reminder_one_hour)
      24 * 60 -> stringResource(R.string.reminder_one_day)
      else -> stringResource(R.string.reminder_minutes_before, m)
  }
  ```

  Add strings:
  ```xml
  <string name="field_reminder">Reminder</string>
  <string name="reminder_none">None</string>
  <string name="reminder_at_time">At time of event</string>
  <string name="reminder_one_hour">1 hour before</string>
  <string name="reminder_one_day">1 day before</string>
  <string name="reminder_minutes_before">%1$d minutes before</string>
  ```

### Task 6.3: Wire reminder through VM and save

- [ ] **Step 1: Add reminderMinutesBefore to form state**

  ```kotlin
  data class EventEditFormState(
      // ... existing fields ...
      val reminderMinutesBefore: Int? = null,
  ) { ... }
  ```

- [ ] **Step 2: Populate when loading existing event**

  In the init block where existing event is loaded:

  ```kotlin
  reminderMinutesBefore = existing.reminderMinutesBefore,
  ```

- [ ] **Step 3: Save reminder after event save**

  Inject `RemindersRepository` via the Factory and constructor
  (same pattern as `eventRepo`). Then change the end of `save()` so
  the reminder is written immediately after the event insert or
  update succeeds:

  ```kotlin
  return if (editingEventId == null) {
      val id = eventRepo.insertEvent(draft)
      if (id != null) {
          remindersRepo.setReminder(id, s.reminderMinutesBefore)
          SaveResult.Success(id)
      } else SaveResult.Failure
  } else {
      val ok = eventRepo.updateEvent(editingEventId, draft)
      if (ok) {
          remindersRepo.setReminder(editingEventId, s.reminderMinutesBefore)
          SaveResult.Success(editingEventId)
      } else SaveResult.Failure
  }
  ```

  Factory update:

  ```kotlin
  return EventEditViewModel(
      eventRepo = EventRepository(appContext.contentResolver),
      calendarRepo = CalendarRepository(appContext.contentResolver),
      remindersRepo = RemindersRepository(appContext.contentResolver),
      editingEventId = eventId,
  ) as T
  ```

- [ ] **Step 4: Add ReminderPicker to EventForm**

  After `RecurrenceSection`:

  ```kotlin
  ReminderPicker(
      minutesBefore = state.reminderMinutesBefore,
      onChange = { onChange(state.copy(reminderMinutesBefore = it)) },
  )
  ```

- [ ] **Step 5: Compile**

### Task 6.4: Manual verify + commit

- [ ] **Step 1: Test on phone**

  - Create a new event, pick "15 minutes before". Save. Tap to
    re-open. Detail sheet shows "15 minutes before".
  - Edit and change to "1 hour before". Save. Re-open. Shows
    "60 minutes before" (string template formatting).
  - Edit and change to "None". Save. Re-open. No reminder line in
    detail sheet.

  Verify (manual external check): open another calendar app that
  shares the same calendar and confirm the event shows the right
  reminder offset. This proves the Reminders row was written
  correctly. Notifications still do not fire from Asala itself.

- [ ] **Step 2: CHANGELOG**

  ```markdown
  - Set a single reminder per event from the editor (none, at time
    of event, 5/10/15/30 minutes, 1 hour, or 1 day before). The
    reminder is written to the Calendar Provider; notifications
    from this app are not yet wired (M4). Other calendar apps will
    fire the reminder if they share the same calendar.
  ```

- [ ] **Step 3: Commit**

  ```bash
  git add -A
  git commit -m "feat(event): single reminder offset in editor"
  ```

---

## Slice 7 — Settings screen

Replaces the existing `ThemeSettingsDialog` with a full-screen
settings UI reachable from the drawer's footer gear icon. Adds
default-view, week-starts-on, and ISO 8601 week numbers
preferences. The week and ISO settings start as no-ops for the
views themselves (the existing views render in the system default);
wiring view behavior to these preferences can ride in M3 or roll
to M4 depending on time. For this slice, the persistence and UI
land; **view behavior is wired up here too** because the change is
small (each view's VM just reads the new flow).

**Files touched:**
- Create: `ui/settings/UserPreferences.kt`
- Create: `ui/settings/SettingsScreen.kt`
- Create: `ui/settings/SettingsViewModel.kt`
- Delete: `ui/settings/ThemePreference.kt` (folded into UserPreferences)
- Delete: `ui/settings/ThemeSettingsDialog.kt`
- Modify: `AppViewModel.kt` (use UserPreferences; expose defaultView etc.)
- Modify: `MainActivity.kt` (settings entry routes to SettingsScreen)
- Modify: `ui/month/CalendarDrawer.kt` (settings gear opens screen)
- Modify: 4 view-screen files / VMs to honor weekStartsOn + isoWeekNumbers where applicable
- Modify: `res/values/strings.xml`
- Modify: `CHANGELOG.md`

### Task 7.1: UserPreferences (consolidate DataStore)

- [ ] **Step 1: Create UserPreferences.kt**

  ```kotlin
  /*
   * Copyright (C) 2026 Arishawke
   * GPL v3.
   */
  package com.arishawke.asala.calendar.ui.settings

  import android.content.Context
  import androidx.datastore.core.DataStore
  import androidx.datastore.preferences.core.Preferences
  import androidx.datastore.preferences.core.booleanPreferencesKey
  import androidx.datastore.preferences.core.edit
  import androidx.datastore.preferences.core.stringPreferencesKey
  import androidx.datastore.preferences.preferencesDataStore
  import com.arishawke.asala.calendar.CalendarView
  import kotlinx.coroutines.flow.Flow
  import kotlinx.coroutines.flow.map
  import java.time.DayOfWeek

  val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

  data class UserPrefs(
      val themeMode: ThemeMode,
      val defaultView: CalendarView,
      val weekStartsOn: DayOfWeek?,            // null = follow locale
      val isoWeekNumbers: Boolean,
  )

  class UserPreferences(private val dataStore: DataStore<Preferences>) {

      val prefs: Flow<UserPrefs> = dataStore.data.map { p ->
          UserPrefs(
              themeMode = parseEnum(p[KEY_THEME], ThemeMode.System) { ThemeMode.valueOf(it) },
              defaultView = parseEnum(p[KEY_DEFAULT_VIEW], CalendarView.Month) { CalendarView.valueOf(it) },
              weekStartsOn = p[KEY_WEEK_START]?.let { runCatching { DayOfWeek.valueOf(it) }.getOrNull() },
              isoWeekNumbers = p[KEY_ISO] ?: false,
          )
      }

      suspend fun setThemeMode(mode: ThemeMode) {
          dataStore.edit { it[KEY_THEME] = mode.name }
      }

      suspend fun setDefaultView(view: CalendarView) {
          dataStore.edit { it[KEY_DEFAULT_VIEW] = view.name }
      }

      suspend fun setWeekStartsOn(day: DayOfWeek?) {
          dataStore.edit { p ->
              if (day == null) p.remove(KEY_WEEK_START) else p[KEY_WEEK_START] = day.name
          }
      }

      suspend fun setIsoWeekNumbers(enabled: Boolean) {
          dataStore.edit { it[KEY_ISO] = enabled }
      }

      private inline fun <T> parseEnum(raw: String?, default: T, of: (String) -> T): T =
          raw?.let { runCatching { of(it) }.getOrNull() } ?: default

      private companion object {
          val KEY_THEME = stringPreferencesKey("theme_mode")          // same key as ThemePreference
          val KEY_DEFAULT_VIEW = stringPreferencesKey("default_view")
          val KEY_WEEK_START = stringPreferencesKey("week_starts_on")
          val KEY_ISO = booleanPreferencesKey("iso_week_numbers")
      }
  }
  ```

  Migrate `ThemePreference` use sites:
  - `AppViewModel.Factory.runBlocking { themePref.themeMode.first() }`
    becomes `runBlocking { userPrefs.prefs.first() }` then `it.themeMode`.
  - `AppViewModel.setThemeMode` becomes `userPrefs.setThemeMode`.

  Delete `ThemePreference.kt`.

### Task 7.2: SettingsScreen + SettingsViewModel

- [ ] **Step 1: Create SettingsViewModel.kt**

  ```kotlin
  /*
   * Copyright (C) 2026 Arishawke
   * GPL v3.
   */
  package com.arishawke.asala.calendar.ui.settings

  import android.content.Context
  import androidx.lifecycle.ViewModel
  import androidx.lifecycle.ViewModelProvider
  import androidx.lifecycle.viewModelScope
  import com.arishawke.asala.calendar.CalendarView
  import kotlinx.coroutines.flow.SharingStarted
  import kotlinx.coroutines.flow.StateFlow
  import kotlinx.coroutines.flow.stateIn
  import kotlinx.coroutines.launch
  import java.time.DayOfWeek

  class SettingsViewModel(
      private val prefs: UserPreferences,
  ) : ViewModel() {

      val state: StateFlow<UserPrefs> = prefs.prefs.stateIn(
          scope = viewModelScope,
          started = SharingStarted.WhileSubscribed(5_000),
          initialValue = UserPrefs(
              themeMode = ThemeMode.System,
              defaultView = CalendarView.Month,
              weekStartsOn = null,
              isoWeekNumbers = false,
          ),
      )

      fun setTheme(mode: ThemeMode) { viewModelScope.launch { prefs.setThemeMode(mode) } }
      fun setDefaultView(v: CalendarView) { viewModelScope.launch { prefs.setDefaultView(v) } }
      fun setWeekStartsOn(d: DayOfWeek?) { viewModelScope.launch { prefs.setWeekStartsOn(d) } }
      fun setIsoWeekNumbers(b: Boolean) { viewModelScope.launch { prefs.setIsoWeekNumbers(b) } }

      class Factory(private val appContext: Context) : ViewModelProvider.Factory {
          @Suppress("UNCHECKED_CAST")
          override fun <T : ViewModel> create(modelClass: Class<T>): T {
              require(modelClass == SettingsViewModel::class.java)
              return SettingsViewModel(UserPreferences(appContext.settingsDataStore)) as T
          }
      }
  }
  ```

- [ ] **Step 2: Create SettingsScreen.kt**

  ```kotlin
  /*
   * Copyright (C) 2026 Arishawke
   * GPL v3.
   */
  package com.arishawke.asala.calendar.ui.settings

  import androidx.compose.foundation.layout.*
  import androidx.compose.foundation.rememberScrollState
  import androidx.compose.foundation.verticalScroll
  import androidx.compose.material.icons.Icons
  import androidx.compose.material.icons.filled.ArrowBack
  import androidx.compose.material3.*
  import androidx.compose.runtime.*
  import androidx.compose.runtime.getValue
  import androidx.compose.ui.Modifier
  import androidx.compose.ui.platform.LocalContext
  import androidx.compose.ui.res.stringResource
  import androidx.compose.ui.unit.dp
  import androidx.lifecycle.viewmodel.compose.viewModel
  import com.arishawke.asala.calendar.CalendarView
  import com.arishawke.asala.calendar.R
  import java.time.DayOfWeek

  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  fun SettingsScreen(onBack: () -> Unit) {
      val ctx = LocalContext.current
      val vm: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory(ctx.applicationContext))
      val s by vm.state.collectAsState()

      Scaffold(
          topBar = {
              TopAppBar(
                  title = { Text(stringResource(R.string.settings_title)) },
                  navigationIcon = {
                      IconButton(onClick = onBack) {
                          Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.action_close))
                      }
                  },
              )
          },
      ) { padding ->
          Column(
              modifier = Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()),
          ) {
              SectionHeader(stringResource(R.string.settings_section_appearance))
              ThemeRow(s.themeMode, vm::setTheme)

              SectionHeader(stringResource(R.string.settings_section_week))
              WeekStartsOnRow(s.weekStartsOn, vm::setWeekStartsOn)
              SwitchRow(
                  label = stringResource(R.string.settings_iso_week),
                  checked = s.isoWeekNumbers,
                  onChange = vm::setIsoWeekNumbers,
              )

              SectionHeader(stringResource(R.string.settings_section_general))
              DefaultViewRow(s.defaultView, vm::setDefaultView)
          }
      }
  }

  @Composable
  private fun SectionHeader(text: String) {
      Text(
          text = text,
          style = MaterialTheme.typography.titleSmall,
          color = MaterialTheme.colorScheme.primary,
          modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
      )
  }

  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  private fun ThemeRow(current: ThemeMode, onChange: (ThemeMode) -> Unit) {
      var expanded by remember { mutableStateOf(false) }
      ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
          OutlinedTextField(
              value = stringResource(themeModeLabel(current)),
              onValueChange = {},
              readOnly = true,
              label = { Text(stringResource(R.string.settings_theme)) },
              trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
              modifier = Modifier.menuAnchor().fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
          )
          ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
              ThemeMode.values().forEach { mode ->
                  DropdownMenuItem(
                      text = { Text(stringResource(themeModeLabel(mode))) },
                      onClick = { onChange(mode); expanded = false },
                  )
              }
          }
      }
  }

  private fun themeModeLabel(mode: ThemeMode): Int = when (mode) {
      ThemeMode.System -> R.string.theme_system
      ThemeMode.Light -> R.string.theme_light
      ThemeMode.Dark -> R.string.theme_dark
  }

  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  private fun WeekStartsOnRow(current: DayOfWeek?, onChange: (DayOfWeek?) -> Unit) {
      var expanded by remember { mutableStateOf(false) }
      val label = when (current) {
          null -> stringResource(R.string.week_starts_system)
          DayOfWeek.SUNDAY -> stringResource(R.string.week_starts_sunday)
          DayOfWeek.MONDAY -> stringResource(R.string.week_starts_monday)
          DayOfWeek.SATURDAY -> stringResource(R.string.week_starts_saturday)
          else -> current.name
      }
      ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
          OutlinedTextField(
              value = label,
              onValueChange = {},
              readOnly = true,
              label = { Text(stringResource(R.string.settings_week_starts_on)) },
              trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
              modifier = Modifier.menuAnchor().fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
          )
          ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
              DropdownMenuItem(
                  text = { Text(stringResource(R.string.week_starts_system)) },
                  onClick = { onChange(null); expanded = false },
              )
              listOf(DayOfWeek.SUNDAY, DayOfWeek.MONDAY, DayOfWeek.SATURDAY).forEach { d ->
                  DropdownMenuItem(
                      text = {
                          Text(
                              stringResource(
                                  when (d) {
                                      DayOfWeek.SUNDAY -> R.string.week_starts_sunday
                                      DayOfWeek.MONDAY -> R.string.week_starts_monday
                                      DayOfWeek.SATURDAY -> R.string.week_starts_saturday
                                      else -> R.string.week_starts_system
                                  },
                              ),
                          )
                      },
                      onClick = { onChange(d); expanded = false },
                  )
              }
          }
      }
  }

  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  private fun DefaultViewRow(current: CalendarView, onChange: (CalendarView) -> Unit) {
      var expanded by remember { mutableStateOf(false) }
      ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
          OutlinedTextField(
              value = current.label(),                  // existing helper from CalendarViewLabel.kt
              onValueChange = {},
              readOnly = true,
              label = { Text(stringResource(R.string.settings_default_view)) },
              trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
              modifier = Modifier.menuAnchor().fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
          )
          ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
              CalendarView.values().forEach { v ->
                  DropdownMenuItem(
                      text = { Text(v.label()) },
                      onClick = { onChange(v); expanded = false },
                  )
              }
          }
      }
  }

  @Composable
  private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
      Row(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
          verticalAlignment = Alignment.CenterVertically,
      ) {
          Text(label, modifier = Modifier.weight(1f))
          Switch(checked = checked, onCheckedChange = onChange)
      }
  }
  ```

  Add strings:
  ```xml
  <string name="settings_title">Settings</string>
  <string name="settings_section_appearance">Appearance</string>
  <string name="settings_section_week">Week</string>
  <string name="settings_section_general">General</string>
  <string name="settings_theme">Theme</string>
  <string name="settings_default_view">Default view</string>
  <string name="settings_week_starts_on">Week starts on</string>
  <string name="settings_iso_week">Show ISO 8601 week numbers</string>
  <string name="week_starts_system">System default</string>
  <string name="week_starts_sunday">Sunday</string>
  <string name="week_starts_monday">Monday</string>
  <string name="week_starts_saturday">Saturday</string>
  ```

- [ ] **Step 3: Compile**

### Task 7.3: Drawer footer routes to SettingsScreen

- [ ] **Step 1: Add navigation state for settings**

  In `AppViewModel`:

  ```kotlin
  private val _settingsOpen = MutableStateFlow(false)
  val settingsOpen: StateFlow<Boolean> = _settingsOpen.asStateFlow()
  fun openSettings() { _settingsOpen.update { true } }
  fun closeSettings() { _settingsOpen.update { false } }
  ```

- [ ] **Step 2: Update drawer callback wiring**

  `CalendarDrawerContent`'s `onSettingsClick` currently shows the
  dialog. Replace with `vm.openSettings()`.

- [ ] **Step 3: Render SettingsScreen on top of Scaffold when open**

  ```kotlin
  val settingsOpen by vm.settingsOpen.collectAsState()
  if (settingsOpen) {
      SettingsScreen(onBack = { vm.closeSettings() })
  }
  ```

  (Use a Box stack to overlay; or wrap the existing content in a
  Box and conditionally swap. SettingsScreen renders a full
  Scaffold so it covers everything underneath.)

- [ ] **Step 4: Delete ThemeSettingsDialog.kt**

  ```bash
  rm app/src/main/kotlin/com/arishawke/asala/calendar/ui/settings/ThemeSettingsDialog.kt
  ```

  Remove the import and call site from wherever it lived.

- [ ] **Step 5: Compile**

### Task 7.4: Honor preferences in views

- [ ] **Step 1: AppViewModel exposes prefs flow**

  ```kotlin
  val prefs: StateFlow<UserPrefs> = userPrefs.prefs.stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5_000),
      initialValue = UserPrefs(themeMode = ..., defaultView = CalendarView.Month, weekStartsOn = null, isoWeekNumbers = false),
  )
  ```

- [ ] **Step 2: Use defaultView on cold start**

  `AppViewModel.Factory` reads the prefs synchronously the same way
  it currently does theme. On VM construction, set
  `currentView.value = persistedDefaultView`.

- [ ] **Step 3: Pass weekStartsOn and isoWeekNumbers into MonthScreen + WeekScreen**

  kizitonwose's `CalendarState` and `WeekCalendar` take a
  `firstDayOfWeek` parameter. Each screen's VM reads `weekStartsOn`
  (falling back to `WeekFields.of(locale).firstDayOfWeek` when
  null) and passes it down.

  For ISO 8601 week numbers, render a small "Wn" label at the left
  edge of each week row in MonthScreen (only when toggle is on).
  Use `WeekFields.ISO.weekOfWeekBasedYear()`.

- [ ] **Step 4: Compile + install + manual verify**

  Tests on phone:
  - Open Settings → set theme to Dark → screen + bars update.
  - Set default view to Week → kill app → reopen → Week view shows.
  - Set Week starts on = Sunday → Month grid header reorders.
  - Toggle ISO week numbers → Wn column appears/disappears in Month view.

### Task 7.5: CHANGELOG + commit

- [ ] **Step 1: CHANGELOG**

  ```markdown
  - Added a full Settings screen reachable from the drawer's gear
    icon. Replaces the prior theme-only dialog. New settings:
    default view (Month/Week/Day/Schedule, applied on app launch),
    week starts on (Sunday/Monday/Saturday/system default), and an
    ISO 8601 week numbers toggle that adds a Wn column to the
    month grid. The existing System/Light/Dark theme override now
    lives on this screen.
  ```

- [ ] **Step 2: Commit**

  ```bash
  git add -A
  git commit -m "feat(settings): full settings screen with default view, week start, ISO weeks"
  ```

---

## Cut v0.4.0

After all 7 slices are landed and CI is green on `main`:

### Task R.1: Release v0.4.0 prerelease

Follow the documented flow in [CONTRIBUTING.md](../../CONTRIBUTING.md)
"Releasing" (which was corrected in the v0.3.0 → post-ship doc fix).
Same steps used for v0.3.0.

- [ ] **Step 1: Verify local builds clean**

  ```bash
  ./gradlew lintDebug testDebugUnitTest assembleRelease
  ```

  Expected: BUILD SUCCESSFUL. Tests pass. Lint clean (only existing
  baseline entries).

- [ ] **Step 2: Verify APK fingerprint**

  ```bash
  ~/android-sdk/build-tools/36.1.0/apksigner verify --print-certs \
    app/build/outputs/apk/release/app-release.apk \
    | grep "SHA-256 digest"
  ```

  Expected: `a701b85f0f356ac30833303c1a13976cd112806a4b1c15afda01ba005302c68e`

  Mismatch = keystore changed; stop.

- [ ] **Step 3: Update CHANGELOG**

  Move `[Unreleased]` content under `## [0.4.0] - YYYY-MM-DD`. Add
  fresh empty `## [Unreleased]`. Update link refs at bottom.

- [ ] **Step 4: Bump versionCode 3 → 4 and versionName 0.3.0 → 0.4.0**

  In `app/build.gradle.kts:32-33`.

- [ ] **Step 5: Commit, tag, push**

  ```bash
  git add CHANGELOG.md app/build.gradle.kts
  git commit -m "chore(release): 0.4.0"
  git tag v0.4.0
  git push origin main v0.4.0
  ```

- [ ] **Step 6: Build the signed APK and create GH release**

  ```bash
  ./gradlew assembleRelease
  gh release create v0.4.0 \
    app/build/outputs/apk/release/app-release.apk \
    --title "v0.4.0" \
    --prerelease \
    --notes-file <(awk '/^## \[0\.4\.0\]/{p=1; next} /^## \[/{p=0} p' CHANGELOG.md)
  ```

- [ ] **Step 7: Confirm CI green on the release commit**

  ```bash
  gh run list --limit 1 --branch main
  ```

---

## What's next after v0.4.0

- **M4**: notifications + AlarmManager + snooze + `POST_NOTIFICATIONS`
  permission (request on first reminder-set, not on launch). The
  reminders rows from slice 6 already exist; M4 wires the firing.
- **M5 or merged into M4**: trash bin, drag-to-reschedule.
- **Later**: search, per-event color, multiple reminders, widgets.

See [docs/ROADMAP.md](../ROADMAP.md) and
[docs/specs/2026-05-21-m3-event-crud-design.md](../specs/2026-05-21-m3-event-crud-design.md).
