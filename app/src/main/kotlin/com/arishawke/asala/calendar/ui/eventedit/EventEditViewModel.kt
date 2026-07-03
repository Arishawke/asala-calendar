/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.ui.eventedit

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.arishawke.asala.calendar.data.CalendarItem
import com.arishawke.asala.calendar.data.CalendarRepository
import com.arishawke.asala.calendar.data.EventDetail
import com.arishawke.asala.calendar.data.EventEditCalendarPicker
import com.arishawke.asala.calendar.data.EventRepository
import com.arishawke.asala.calendar.data.RecurrenceFrequency
import com.arishawke.asala.calendar.data.RecurrenceRule
import com.arishawke.asala.calendar.data.RecurringEditScope
import com.arishawke.asala.calendar.data.RemindersRepository
import com.arishawke.asala.calendar.data.StorageMode
import com.arishawke.asala.calendar.ui.eventedit.naturallanguage.EventTextParser
import com.arishawke.asala.calendar.ui.eventedit.naturallanguage.ParsedEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.Locale

data class EventEditFormState(
    val calendars: List<CalendarItem> = emptyList(),
    val selectedCalendarId: Long? = null,
    val title: String = "",
    val description: String = "",
    val location: String = "",
    val startDate: LocalDate = LocalDate.now(),
    val startTime: LocalTime = nextRoundHour(),
    val endDate: LocalDate = LocalDate.now(),
    val endTime: LocalTime = nextRoundHour().plusMinutes(60L),
    val allDay: Boolean = false,
    val recurrenceFrequency: RecurrenceFrequency? = null,
    val recurrenceInterval: Int = 1,
    val recurrenceUntilDate: LocalDate? = null,
    val recurrenceCount: Int? = null,
    val reminderMinutes: List<Int> = emptyList(),
    // seeds endTime on new-event construction and on first all-day toggle-off.
    val defaultDurationMinutes: Int = 60,
    // default reminders snapshotted at editor open. new events seed from the
    // timed variant; the all-day toggle re-seed swaps in the all-day one.
    // existing events ignore both (their saved reminder wins).
    val defaultTimedReminderMinutes: Int? = null,
    val defaultAllDayReminderMinutes: Int? = null,
    // gates the withAllDay re-seed: an existing event's saved reminder is
    // sacred even if it matches the current default.
    val isNewEvent: Boolean = true,
    // one-shot: first toggle-off re-seeds end-time; later toggles preserve edits.
    val convertedFromAllDay: Boolean = false,
    // null means "follow calendar". seeded from the override map on open,
    // persisted via AppViewModel post-save (not in EventDraft / CalendarContract).
    val colorOverrideArgb: Int? = null,
) {
    val isEndAfterStart: Boolean
        get() {
            if (allDay) return !endDate.isBefore(startDate)
            val s = startDate.atTime(startTime)
            val e = endDate.atTime(endTime)
            return e.isAfter(s)
        }

    // start-side: shift end by the same delta to preserve duration. split out
    // so the logic is testable without driving the Compose lambdas.
    fun withStartDate(newStartDate: LocalDate): EventEditFormState {
        val dayDelta = newStartDate.toEpochDay() - startDate.toEpochDay()
        return copy(
            startDate = newStartDate,
            endDate = endDate.plusDays(dayDelta),
        )
    }

    fun withStartTime(newStartTime: LocalTime): EventEditFormState {
        val oldMinutes = startTime.toSecondOfDay() / SecondsPerMinute
        val newMinutes = newStartTime.toSecondOfDay() / SecondsPerMinute
        val minuteDelta = newMinutes - oldMinutes
        val endDateTime = endDate.atTime(endTime).plusMinutes(minuteDelta)
        return copy(
            startTime = newStartTime,
            endDate = endDateTime.toLocalDate(),
            endTime = endDateTime.toLocalTime(),
        )
    }

    // end-side guards for a direct edit into an invalid range: clamp on date
    // edits, roll forward on time edits (9 AM start, 8 AM end -> next-day 8 AM).
    fun withEndDate(newEndDate: LocalDate): EventEditFormState {
        val clamped = if (newEndDate.isBefore(startDate)) startDate else newEndDate
        return copy(endDate = clamped)
    }

    fun withEndTime(newEndTime: LocalTime): EventEditFormState {
        if (allDay) return copy(endTime = newEndTime)
        val newEnd = endDate.atTime(newEndTime)
        val start = startDate.atTime(startTime)
        return if (!newEnd.isAfter(start)) {
            val rolled = newEnd.plusDays(1)
            copy(endDate = rolled.toLocalDate(), endTime = rolled.toLocalTime())
        } else {
            copy(endTime = newEndTime)
        }
    }

    // one-shot: first all-day -> timed toggle re-seeds times from
    // defaultDurationMinutes (the collapse would else leave end at next-day
    // midnight). date preserved; later toggles preserve the user's edits.
    fun withAllDay(newValue: Boolean): EventEditFormState {
        // re-seed the reminder only if it still matches the OLD state's default
        // (custom picks left alone); avoids a timed default landing 11:45pm the
        // prior night on an all-day event. gated on isNewEvent so a saved
        // reminder matching the default isn't silently mutated.
        val rebasedReminders = if (isNewEvent) {
            val oldDefault = if (allDay) defaultAllDayReminderMinutes else defaultTimedReminderMinutes
            val newDefault = if (newValue) defaultAllDayReminderMinutes else defaultTimedReminderMinutes
            // re-seed only when the list is exactly the previously seeded default.
            if (reminderMinutes == listOfNotNull(oldDefault)) listOfNotNull(newDefault) else reminderMinutes
        } else {
            reminderMinutes
        }

        val isFirstToggleOff = allDay && !newValue && !convertedFromAllDay
        if (!isFirstToggleOff) return copy(allDay = newValue, reminderMinutes = rebasedReminders)
        val seedStart = nextRoundHour()
        val seedEnd = seedStart.plusMinutes(defaultDurationMinutes.toLong())
        return copy(
            allDay = false,
            startTime = seedStart,
            endDate = startDate,
            endTime = seedEnd,
            convertedFromAllDay = true,
            reminderMinutes = rebasedReminders,
        )
    }

    // overlay parsed fields from the Quick add phrase, leaving anything the
    // parser did not recognize at its seeded default (date, duration, reminder,
    // calendar). a date without a time means all-day.
    fun withParsed(parsed: ParsedEvent): EventEditFormState {
        var s = this
        if (parsed.title.isNotBlank()) s = s.copy(title = parsed.title)
        parsed.location?.let { s = s.copy(location = it) }
        return when {
            parsed.startTime != null -> {
                val date = parsed.date ?: s.startDate
                val start = parsed.startTime
                // a zero-length end (end == start) is treated as no end, so it
                // takes the default duration instead of rolling to a 24h event.
                val end = parsed.endTime?.takeIf { it != start }
                    ?: start.plusMinutes(s.defaultDurationMinutes.toLong())
                val rollsOver = !end.isAfter(start)
                s.copy(
                    allDay = false,
                    startDate = date,
                    startTime = start,
                    endDate = if (rollsOver) date.plusDays(1) else date,
                    endTime = end,
                )
            }
            parsed.date != null -> s.copy(
                allDay = true,
                startDate = parsed.date,
                endDate = parsed.date,
                startTime = LocalTime.MIDNIGHT,
                endTime = LocalTime.MIDNIGHT,
            )
            else -> s
        }
    }

    companion object {
        private const val SecondsPerMinute = 60L

        fun nextRoundHour(): LocalTime = LocalTime
            .now()
            .withMinute(0)
            .withSecond(0)
            .withNano(0)
            .plusHours(1)

        // initial form for a new event honoring default-duration. the
        // data-class endTime default is a literal 60-min span (parameter
        // ordering blocks referencing defaultDurationMinutes), so new events
        // route through here to apply the preference.
        fun forNewEvent(
            defaultDurationMinutes: Int,
            defaultTimedReminderMinutes: Int? = null,
            defaultAllDayReminderMinutes: Int? = null,
            initialStartDate: LocalDate? = null,
            initialStartTime: LocalTime? = null,
        ): EventEditFormState {
            // a timeline empty-slot tap supplies the exact snapped time; the
            // FAB path falls back to the next round hour.
            val start = initialStartTime ?: nextRoundHour()
            // initialStartDate is the date viewed at FAB tap; falls back to
            // today for callers (and tests) that don't pass one.
            val date = initialStartDate ?: LocalDate.now()
            val end = start.plusMinutes(defaultDurationMinutes.toLong())
            return EventEditFormState(
                startDate = date,
                // a late-evening start wraps LocalTime past midnight; roll the
                // end date forward so the form opens valid (same rule as
                // withEndTime), instead of a disabled Save on an empty-slot tap.
                endDate = if (end.isAfter(start)) date else date.plusDays(1),
                startTime = start,
                endTime = end,
                reminderMinutes = listOfNotNull(defaultTimedReminderMinutes),
                defaultDurationMinutes = defaultDurationMinutes,
                defaultTimedReminderMinutes = defaultTimedReminderMinutes,
                defaultAllDayReminderMinutes = defaultAllDayReminderMinutes,
            )
        }

        // new-event form seeded from an existing event for Duplicate. drops
        // identity and recurrence so the copy inserts as a one-off. a recurring
        // source seeds from the opened instance, not parent DTSTART, so
        // duplicating "this Tuesday" lands on the right day.
        @Suppress("LongParameterList")
        fun forDuplicate(
            source: EventDetail,
            instanceStartMillis: Long?,
            defaultDurationMinutes: Int,
            defaultTimedReminderMinutes: Int? = null,
            defaultAllDayReminderMinutes: Int? = null,
            colorOverrideArgb: Int? = null,
            zone: ZoneId = ZoneId.systemDefault(),
        ): EventEditFormState {
            val range = extractLocalRange(
                startMillis = source.startMillis,
                endMillis = source.endMillis,
                allDay = source.allDay,
                rrule = source.rrule,
                instanceStartMillis = instanceStartMillis,
                zone = zone,
                defaultDurationMinutes = defaultDurationMinutes,
            )
            return EventEditFormState(
                selectedCalendarId = source.calendarId,
                title = source.title,
                description = source.description.orEmpty(),
                location = source.location.orEmpty(),
                startDate = range.startDate,
                startTime = range.startTime,
                endDate = range.endDate,
                endTime = range.endTime,
                allDay = source.allDay,
                recurrenceFrequency = null,
                reminderMinutes = source.reminderMinutes,
                defaultDurationMinutes = defaultDurationMinutes,
                defaultTimedReminderMinutes = defaultTimedReminderMinutes,
                defaultAllDayReminderMinutes = defaultAllDayReminderMinutes,
                isNewEvent = true,
                colorOverrideArgb = colorOverrideArgb,
            )
        }
    }
}

sealed interface SaveResult {
    data class Success(val eventId: Long) : SaveResult

    object Failure : SaveResult
}

// existing events and duplicates fetch their data asynchronously and then
// replace the form wholesale; gate input until that lands so a fast edit
// during a slow load isn't clobbered. new events have nothing to wait on.
internal fun shouldGateEditorUntilLoaded(editingEventId: Long?, duplicateFromEventId: Long?): Boolean =
    editingEventId != null || duplicateFromEventId != null

// applies a normalized shared-text parse to a freshly seeded new-event form.
// null/blank text is a no-op, matching the parser's own contract; edit and
// duplicate opens never pass share text (see EventEditScreen), so the
// wholesale-replace paths in the init block below never observe this seed.
internal fun seedFormWithShareText(
    base: EventEditFormState,
    shareText: String?,
    now: LocalDateTime,
    locale: Locale,
): EventEditFormState {
    if (shareText.isNullOrBlank()) return base
    return base.withParsed(EventTextParser.parse(shareText, now, locale))
}

@Suppress("LongParameterList")
class EventEditViewModel(
    private val eventRepo: EventRepository,
    calendarRepo: CalendarRepository,
    private val remindersRepo: RemindersRepository,
    private val editingEventId: Long? = null,
    private val editingInstanceMillis: Long? = null,
    // Duplicate source: editingEventId stays null so save inserts a copy.
    private val duplicateFromEventId: Long? = null,
    private val storageMode: StorageMode = StorageMode.Unset,
    private val defaultDurationMinutes: Int = 60,
    private val defaultTimedReminderMinutes: Int? = null,
    private val defaultAllDayReminderMinutes: Int? = null,
    // null = no existing override.
    private val initialColorOverrideArgb: Int? = null,
    // snapshotted hide set: drawer toggles mid-edit don't rebuild the picker.
    private val hiddenCalendarIds: Set<Long> = emptySet(),
    // pre-fill date for the new-event form. null falls back to today.
    private val initialStartDate: LocalDate? = null,
    // pre-fill time from a timeline empty-slot tap. null = next round hour.
    private val initialStartTime: LocalTime? = null,
    // raw share-sheet text (already normalized/capped by ShareTextNormalizer).
    // create mode only; EventEditScreen never threads this into an edit or
    // duplicate open (see Step 6).
    private val shareText: String? = null,
) : ViewModel() {
    private val _form = MutableStateFlow(
        seedFormWithShareText(
            base = EventEditFormState.forNewEvent(
                defaultDurationMinutes = defaultDurationMinutes,
                defaultTimedReminderMinutes = defaultTimedReminderMinutes,
                defaultAllDayReminderMinutes = defaultAllDayReminderMinutes,
                initialStartDate = initialStartDate,
                initialStartTime = initialStartTime,
            ),
            shareText = shareText,
            now = LocalDateTime.now(),
            locale = Locale.getDefault(),
        ),
    )
    val form: StateFlow<EventEditFormState> = _form.asStateFlow()

    // raw shared text for QuickAddField's initial value; the parse above
    // already applied to the form, so this is display-only, not re-parsed.
    val initialQuickAddText: String = shareText.orEmpty()

    // cleared at the start of each save() so a successful retry hides the banner.
    private val _saveError = MutableStateFlow(false)
    val saveError: StateFlow<Boolean> = _saveError.asStateFlow()

    // true until the async load below lands, but only for opens that replace the
    // form (edit / duplicate). the screen shows a spinner meanwhile so the user
    // can't edit a half-blank form whose edits would be overwritten on load.
    private val _loading = MutableStateFlow(shouldGateEditorUntilLoaded(editingEventId, duplicateFromEventId))
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    // preserved so scope-aware save can supply parentDtstart and parentRrule
    private var loadedDetail: EventDetail? = null

    init {
        viewModelScope.launch {
            // picker contract lives in EventEditCalendarPicker so the rules
            // are unit-testable independently.
            val cals =
                EventEditCalendarPicker.filter(
                    calendars = calendarRepo.observeCalendars().first(),
                    mode = storageMode,
                    hiddenCalendarIds = hiddenCalendarIds,
                )
            val existing = editingEventId?.let { eventRepo.fetchEventDetail(it) }
            loadedDetail = existing
            val duplicateSource =
                if (editingEventId == null && duplicateFromEventId != null) {
                    eventRepo.fetchEventDetail(duplicateFromEventId)
                } else {
                    null
                }

            _form.value =
                if (existing != null) {
                    val zone = ZoneId.systemDefault()
                    // recurring opened from an instance prefills that instance's
                    // slot (preserving duration), not the parent DTSTART, so
                    // instance/following edits land on the right occurrence.
                    val range = extractLocalRange(
                        startMillis = existing.startMillis,
                        endMillis = existing.endMillis,
                        allDay = existing.allDay,
                        rrule = existing.rrule,
                        instanceStartMillis = editingInstanceMillis,
                        zone = zone,
                        defaultDurationMinutes = defaultDurationMinutes,
                    )
                    // an imported rrule may carry both UNTIL and COUNT (RFC 5545
                    // forbids it, but CalDAV rows can); prefer UNTIL so the form
                    // invariant holds and the end-mode radios don't both select.
                    // read the timed UNTIL back in the event zone so a non-UTC
                    // series shows the local end-date the user actually picked.
                    val eventZone = runCatching { ZoneId.of(existing.eventTimezone) }.getOrElse { zone }
                    val recurrenceUntil = RecurrenceRule.untilDateOf(existing.rrule, eventZone)
                    EventEditFormState(
                        calendars = cals,
                        selectedCalendarId = existing.calendarId,
                        title = existing.title,
                        description = existing.description.orEmpty(),
                        location = existing.location.orEmpty(),
                        startDate = range.startDate,
                        startTime = range.startTime,
                        endDate = range.endDate,
                        endTime = range.endTime,
                        allDay = existing.allDay,
                        recurrenceFrequency = RecurrenceRule.frequencyOf(existing.rrule),
                        recurrenceInterval = RecurrenceRule.intervalOf(existing.rrule),
                        recurrenceUntilDate = recurrenceUntil,
                        recurrenceCount = if (recurrenceUntil != null) null else RecurrenceRule.countOf(existing.rrule),
                        reminderMinutes = existing.reminderMinutes,
                        defaultDurationMinutes = defaultDurationMinutes,
                        defaultTimedReminderMinutes = defaultTimedReminderMinutes,
                        defaultAllDayReminderMinutes = defaultAllDayReminderMinutes,
                        isNewEvent = false,
                        colorOverrideArgb = initialColorOverrideArgb,
                    )
                } else if (duplicateSource != null) {
                    val seeded = EventEditFormState.forDuplicate(
                        source = duplicateSource,
                        instanceStartMillis = editingInstanceMillis,
                        defaultDurationMinutes = defaultDurationMinutes,
                        defaultTimedReminderMinutes = defaultTimedReminderMinutes,
                        defaultAllDayReminderMinutes = defaultAllDayReminderMinutes,
                        colorOverrideArgb = initialColorOverrideArgb,
                    ).copy(calendars = cals)
                    // fall back to first writable if the source calendar is
                    // filtered out (hidden / read-only).
                    val seedCal =
                        if (cals.any { it.id == seeded.selectedCalendarId }) {
                            seeded.selectedCalendarId
                        } else {
                            cals.firstOrNull()?.id
                        }
                    seeded.copy(selectedCalendarId = seedCal)
                } else {
                    _form.value.copy(
                        calendars = cals,
                        selectedCalendarId = cals.firstOrNull()?.id,
                    )
                }
            _loading.value = false
        }
    }

    fun updateForm(transform: EventEditFormState.() -> EventEditFormState) {
        _form.update { it.transform() }
    }

    val isEditingRecurring: Boolean
        get() = loadedDetail?.rrule != null

    suspend fun save(
        scope: RecurringEditScope = RecurringEditScope.AllEvents,
        instanceMillis: Long? = null,
    ): SaveResult {
        _saveError.update { false }
        val result =
            EventSave.attempt(
                form = _form.value,
                editingEventId = editingEventId,
                scope = scope,
                instanceMillis = instanceMillis,
                parentRrule = loadedDetail?.rrule,
                parentAllDay = loadedDetail?.allDay == true,
                parentStartMillis = loadedDetail?.startMillis,
                loadedStatus = loadedDetail?.status,
                loadedAvailability = loadedDetail?.availability,
                loadedTimezone = loadedDetail?.eventTimezone,
                loadedReminderMinutes = loadedDetail?.reminderMinutes.orEmpty(),
                preservedReminderMinutes = loadedDetail?.preservedReminderMinutes.orEmpty(),
                insertEvent = eventRepo::insertEvent,
                updateEvent = eventRepo::updateEvent,
                setReminders = remindersRepo::setReminders,
            )
        if (result is SaveResult.Failure) _saveError.update { true }
        return result
    }

    @Suppress("LongParameterList")
    class Factory(
        private val appContext: Context,
        private val eventId: Long? = null,
        private val instanceMillis: Long? = null,
        // Duplicate source: opens a new event seeded from it.
        private val duplicateFromEventId: Long? = null,
        // read from AppViewModel.prefs at construction (already populated by
        // the startup runBlocking) so the picker filter applies on first frame
        // without another blocking DataStore read here.
        private val storageMode: StorageMode = StorageMode.Unset,
        private val defaultDurationMinutes: Int = 60,
        private val defaultTimedReminderMinutes: Int? = null,
        private val defaultAllDayReminderMinutes: Int? = null,
        private val initialColorOverrideArgb: Int? = null,
        private val hiddenCalendarIds: Set<Long> = emptySet(),
        private val initialStartDate: LocalDate? = null,
        private val initialStartTime: LocalTime? = null,
        private val shareText: String? = null,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass == EventEditViewModel::class.java)
            return EventEditViewModel(
                eventRepo = EventRepository(appContext.contentResolver),
                calendarRepo = CalendarRepository(appContext.contentResolver),
                remindersRepo = RemindersRepository(appContext.contentResolver),
                editingEventId = eventId,
                editingInstanceMillis = instanceMillis,
                duplicateFromEventId = duplicateFromEventId,
                storageMode = storageMode,
                defaultDurationMinutes = defaultDurationMinutes,
                defaultTimedReminderMinutes = defaultTimedReminderMinutes,
                defaultAllDayReminderMinutes = defaultAllDayReminderMinutes,
                initialColorOverrideArgb = initialColorOverrideArgb,
                hiddenCalendarIds = hiddenCalendarIds,
                initialStartDate = initialStartDate,
                initialStartTime = initialStartTime,
                shareText = shareText,
            ) as T
        }
    }
}
