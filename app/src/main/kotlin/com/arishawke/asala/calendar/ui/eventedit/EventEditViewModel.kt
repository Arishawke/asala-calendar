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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

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
    val reminderMinutesBefore: Int? = null,
    // Setting-driven default; the form seeds endTime from this on construction
    // (in the new-event path) and on first all-day toggle-off.
    val defaultDurationMinutes: Int = 60,
    // Setting-driven default reminders, snapshotted at editor open. The
    // new-event path seeds `reminderMinutesBefore` from `defaultTimedReminderMinutes`
    // since new events start timed; the smart re-seed on all-day toggle uses
    // the all-day variant when the user flips. Existing events ignore both
    // defaults (their saved reminder wins).
    val defaultTimedReminderMinutes: Int? = null,
    val defaultAllDayReminderMinutes: Int? = null,
    // True iff this form represents a NEW event (FAB-driven). The smart
    // re-seed in `withAllDay` only applies in the new-event path: an
    // existing event's saved reminder is sacred even if it coincidentally
    // matches the current default for the previous all-day state.
    val isNewEvent: Boolean = true,
    // One-shot flag: first toggle-off in an editor session re-seeds end-time
    // from defaultDurationMinutes; subsequent toggles preserve user edits.
    val convertedFromAllDay: Boolean = false,
    // Per-event color override ARGB; null means "follow calendar" (the
    // default). Editor seeds this from the existing override map when
    // opening an existing event. Persisted via AppViewModel after a
    // successful save (not part of EventDraft; never written to
    // CalendarContract).
    val colorOverrideArgb: Int? = null,
) {
    val isEndAfterStart: Boolean
        get() {
            if (allDay) return !endDate.isBefore(startDate)
            val s = startDate.atTime(startTime)
            val e = endDate.atTime(endTime)
            return e.isAfter(s)
        }

    // Start-side: shift end by the same delta so the event keeps its
    // original duration when the user moves the start date or time.
    // EventForm's onDateChange / onTimeChange call these so the logic is
    // testable without driving the Compose lambdas.
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

    // End-side guards. The start-side already preserves duration via
    // delta-shift in EventForm; these handle the case where the user
    // edits the end field directly into an invalid range. Roll end
    // forward on time edits (so a 9 AM start with end set to 8 AM rolls
    // to next-day 8 AM), clamp on date edits.
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

    // One-shot conversion: on the first toggle from all-day back to timed in
    // this editor session, re-seed time fields from defaultDurationMinutes
    // (the all-day collapse would otherwise leave end at next-day midnight).
    // The loaded date is preserved; only times change. Subsequent toggles
    // preserve the user's intervening edits.
    fun withAllDay(newValue: Boolean): EventEditFormState {
        // Smart-default re-seed: if the current reminder matches the default
        // for the OLD all-day state, swap it for the new default. If the user
        // picked something custom, leave it alone. Avoids the 11:45pm-prior-
        // night annoyance from a timed default applied to an all-day event.
        //
        // Gated on `isNewEvent` so an existing event whose saved reminder
        // happens to equal the current timed default isn't silently mutated
        // when the user toggles all-day.
        val rebasedReminder = if (isNewEvent) {
            val oldDefault = if (allDay) defaultAllDayReminderMinutes else defaultTimedReminderMinutes
            val newDefault = if (newValue) defaultAllDayReminderMinutes else defaultTimedReminderMinutes
            if (reminderMinutesBefore == oldDefault) newDefault else reminderMinutesBefore
        } else {
            reminderMinutesBefore
        }

        val isFirstToggleOff = allDay && !newValue && !convertedFromAllDay
        if (!isFirstToggleOff) return copy(allDay = newValue, reminderMinutesBefore = rebasedReminder)
        val seedStart = nextRoundHour()
        val seedEnd = seedStart.plusMinutes(defaultDurationMinutes.toLong())
        return copy(
            allDay = false,
            startTime = seedStart,
            endDate = startDate,
            endTime = seedEnd,
            convertedFromAllDay = true,
            reminderMinutesBefore = rebasedReminder,
        )
    }

    companion object {
        private const val SecondsPerMinute = 60L

        fun nextRoundHour(): LocalTime = LocalTime
            .now()
            .withMinute(0)
            .withSecond(0)
            .withNano(0)
            .plusHours(1)

        // Build the initial form state for a new event, honoring the user's
        // default-duration preference. The data-class default for endTime
        // captures a literal 60-min span (parameter ordering keeps it from
        // referencing defaultDurationMinutes), so the new-event path runs
        // through here to apply the preference. New events start timed, so
        // the reminder seed comes from defaultTimedReminderMinutes; the
        // all-day variant gets stored on the form for the smart re-seed when
        // the user toggles to all-day.
        fun forNewEvent(
            defaultDurationMinutes: Int,
            defaultTimedReminderMinutes: Int? = null,
            defaultAllDayReminderMinutes: Int? = null,
            initialStartDate: LocalDate? = null,
        ): EventEditFormState {
            val start = nextRoundHour()
            // initialStartDate carries the date the user was looking at
            // when they tapped the FAB (visible day in Day, week start in
            // Week, today / first-of-visible-month in Month, today in
            // Schedule). Falls back to LocalDate.now() so unit tests and
            // any caller that doesn't pass a date keep the prior behavior.
            val date = initialStartDate ?: LocalDate.now()
            return EventEditFormState(
                startDate = date,
                endDate = date,
                startTime = start,
                endTime = start.plusMinutes(defaultDurationMinutes.toLong()),
                reminderMinutesBefore = defaultTimedReminderMinutes,
                defaultDurationMinutes = defaultDurationMinutes,
                defaultTimedReminderMinutes = defaultTimedReminderMinutes,
                defaultAllDayReminderMinutes = defaultAllDayReminderMinutes,
            )
        }
    }
}

sealed interface SaveResult {
    data class Success(val eventId: Long) : SaveResult

    object Failure : SaveResult
}

@Suppress("LongParameterList")
class EventEditViewModel(
    private val eventRepo: EventRepository,
    calendarRepo: CalendarRepository,
    private val remindersRepo: RemindersRepository,
    private val editingEventId: Long? = null,
    private val editingInstanceMillis: Long? = null,
    private val storageMode: StorageMode = StorageMode.Unset,
    private val defaultDurationMinutes: Int = 60,
    private val defaultTimedReminderMinutes: Int? = null,
    private val defaultAllDayReminderMinutes: Int? = null,
    // Snapshotted from AppViewModel.eventColorOverridesFlow at construction
    // for the currently-edited event. Null = no existing override.
    private val initialColorOverrideArgb: Int? = null,
    // Snapshotted hide set so the picker hides drawer-hidden calendars
    // (and calendars belonging to drawer-hidden accounts). Snapshot at
    // construction matches the storageMode / defaults pattern: drawer
    // toggles mid-edit do not retroactively rebuild the picker.
    private val hiddenCalendarIds: Set<Long> = emptySet(),
    // Pre-fill date for the new-event form. Null falls back to today,
    // preserving prior behavior for callers that don't pass context.
    private val initialStartDate: LocalDate? = null,
) : ViewModel() {
    private val _form = MutableStateFlow(
        EventEditFormState.forNewEvent(
            defaultDurationMinutes = defaultDurationMinutes,
            defaultTimedReminderMinutes = defaultTimedReminderMinutes,
            defaultAllDayReminderMinutes = defaultAllDayReminderMinutes,
            initialStartDate = initialStartDate,
        ),
    )
    val form: StateFlow<EventEditFormState> = _form.asStateFlow()

    // Surfaces a failed save attempt to the editor screen. Cleared at the
    // start of each save() call so a successful retry hides the banner.
    private val _saveError = MutableStateFlow(false)
    val saveError: StateFlow<Boolean> = _saveError.asStateFlow()

    // preserved so scope-aware save can supply parentDtstart and parentRrule
    private var loadedDetail: EventDetail? = null

    init {
        viewModelScope.launch {
            // Picker contract lives in EventEditCalendarPicker so the
            // writable + storage-mode rules can be unit-tested independently.
            val cals =
                EventEditCalendarPicker.filter(
                    calendars = calendarRepo.observeCalendars().first(),
                    mode = storageMode,
                    hiddenCalendarIds = hiddenCalendarIds,
                )
            val existing = editingEventId?.let { eventRepo.fetchEventDetail(it) }
            loadedDetail = existing

            _form.value =
                if (existing != null) {
                    val zone = ZoneId.systemDefault()
                    // for a recurring event opened from a specific instance, prefill
                    // with the instance's clock time (preserving duration) rather than
                    // the parent series's DTSTART. This makes "this instance only" and
                    // "this and following" edits land on the right occurrence; "all
                    // events" still updates the whole series at the chosen clock time.
                    val effectiveStart =
                        if (existing.rrule != null && editingInstanceMillis != null) {
                            editingInstanceMillis
                        } else {
                            existing.startMillis
                        }
                    val effectiveEnd = effectiveStart + (existing.endMillis - existing.startMillis)
                    // CalendarContract stores all-day events at 00:00 UTC; the
                    // device zone must not be used to extract the date or the
                    // form lands on the prior day in negative-offset zones.
                    // EventSave's all-day path stores `form.endDate + 1 day at
                    // UTC midnight` as the exclusive end, so the inverse here
                    // subtracts a day to restore the inclusive last day for
                    // display.
                    val extractionZone =
                        if (existing.allDay) java.time.ZoneOffset.UTC else zone
                    val sLocal =
                        java.time.Instant
                            .ofEpochMilli(effectiveStart)
                            .atZone(extractionZone)
                            .toLocalDateTime()
                    val eLocal =
                        java.time.Instant
                            .ofEpochMilli(effectiveEnd)
                            .atZone(extractionZone)
                            .toLocalDateTime()
                    val displayEndDate =
                        if (existing.allDay) eLocal.toLocalDate().minusDays(1) else eLocal.toLocalDate()
                    EventEditFormState(
                        calendars = cals,
                        selectedCalendarId = existing.calendarId,
                        title = existing.title,
                        description = existing.description.orEmpty(),
                        location = existing.location.orEmpty(),
                        startDate = sLocal.toLocalDate(),
                        startTime = sLocal.toLocalTime(),
                        endDate = displayEndDate,
                        endTime = eLocal.toLocalTime(),
                        allDay = existing.allDay,
                        recurrenceFrequency = RecurrenceRule.frequencyOf(existing.rrule),
                        recurrenceInterval = RecurrenceRule.intervalOf(existing.rrule),
                        recurrenceUntilDate = RecurrenceRule.untilDateOf(existing.rrule),
                        recurrenceCount = RecurrenceRule.countOf(existing.rrule),
                        reminderMinutesBefore = existing.reminderMinutesBefore,
                        defaultDurationMinutes = defaultDurationMinutes,
                        defaultTimedReminderMinutes = defaultTimedReminderMinutes,
                        defaultAllDayReminderMinutes = defaultAllDayReminderMinutes,
                        isNewEvent = false,
                        colorOverrideArgb = initialColorOverrideArgb,
                    )
                } else {
                    _form.value.copy(
                        calendars = cals,
                        selectedCalendarId = cals.firstOrNull()?.id,
                    )
                }
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
                loadedStatus = loadedDetail?.status,
                loadedAvailability = loadedDetail?.availability,
                insertEvent = eventRepo::insertEvent,
                updateEvent = eventRepo::updateEvent,
                setReminder = remindersRepo::setReminder,
            )
        if (result is SaveResult.Failure) _saveError.update { true }
        return result
    }

    @Suppress("LongParameterList")
    class Factory(
        private val appContext: Context,
        private val eventId: Long? = null,
        private val instanceMillis: Long? = null,
        // Read at construction time on the main thread from AppViewModel.prefs
        // (already populated by the single startup runBlocking in
        // AppViewModel.Factory) so the picker filter applies on first frame
        // without a second blocking DataStore read here.
        private val storageMode: StorageMode = StorageMode.Unset,
        // Same pattern: snapshot the duration preference from AppViewModel.prefs
        // at construction so the new-event end-time seeds correctly without a
        // blocking DataStore read in the factory.
        private val defaultDurationMinutes: Int = 60,
        // Snapshotted reminder defaults; the new-event flow seeds from
        // timed, and the smart re-seed in withAllDay swaps based on the
        // toggle direction.
        private val defaultTimedReminderMinutes: Int? = null,
        private val defaultAllDayReminderMinutes: Int? = null,
        // Snapshot of the existing per-event override for this event, if any.
        // Same pattern as the others; the caller resolves from
        // AppViewModel.eventColorOverridesFlow.value before passing.
        private val initialColorOverrideArgb: Int? = null,
        // Snapshot of drawer-hidden calendar IDs (combined manual +
        // account hides) so the picker excludes them.
        private val hiddenCalendarIds: Set<Long> = emptySet(),
        // Pre-fill date for the new-event form. Null falls back to today.
        private val initialStartDate: LocalDate? = null,
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
                storageMode = storageMode,
                defaultDurationMinutes = defaultDurationMinutes,
                defaultTimedReminderMinutes = defaultTimedReminderMinutes,
                defaultAllDayReminderMinutes = defaultAllDayReminderMinutes,
                initialColorOverrideArgb = initialColorOverrideArgb,
                hiddenCalendarIds = hiddenCalendarIds,
                initialStartDate = initialStartDate,
            ) as T
        }
    }
}
