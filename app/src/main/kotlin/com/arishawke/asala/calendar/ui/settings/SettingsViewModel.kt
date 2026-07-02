/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.arishawke.asala.calendar.CalendarView
import com.arishawke.asala.calendar.R
import com.arishawke.asala.calendar.data.CalendarRepository
import com.arishawke.asala.calendar.data.ContactsRepository
import com.arishawke.asala.calendar.data.EventRepository
import com.arishawke.asala.calendar.data.Occasion
import com.arishawke.asala.calendar.data.OccasionProvisioner
import com.arishawke.asala.calendar.data.OccasionSync
import com.arishawke.asala.calendar.data.RemindersRepository
import com.arishawke.asala.calendar.data.StorageMode
import com.arishawke.asala.calendar.data.StorageModeSetup
import com.arishawke.asala.calendar.data.occasionBaseTitle
import com.arishawke.asala.calendar.ui.UiStateStopTimeoutMillis
import com.arishawke.asala.calendar.ui.theme.PaletteId
import com.arishawke.asala.calendar.ui.widget.updateAllWidgets
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.DayOfWeek

class SettingsViewModel(
    private val prefs: UserPreferences,
    private val calendarRepo: CalendarRepository,
    // application context, retained to push a widget redraw when widget
    // appearance settings change. process-scoped, so no leak.
    private val appContext: Context,
) : ViewModel() {
    val state: StateFlow<UserPrefs> =
        prefs.prefs.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(UiStateStopTimeoutMillis),
            initialValue = UserPrefs.Defaults,
        )

    // contact-occasions collaborators, assembled from the same content
    // resolver / package the rest of the app uses to talk to the providers.
    private val occasionSync = OccasionSync(
        contentResolver = appContext.contentResolver,
        contacts = ContactsRepository(appContext.contentResolver),
        events = EventRepository(appContext.contentResolver),
        reminders = RemindersRepository(appContext.contentResolver),
        appPackage = appContext.packageName,
    )
    private val occasionProvisioner = OccasionProvisioner(calendarRepo, prefs, occasionSync)

    // the stored base title; the render layer treats this as the base to
    // append any decoration (e.g. an age) to, so it must stay in sync with it.
    private val titleFor: (Occasion) -> String = { o -> occasionBaseTitle(appContext, o) }

    fun setTheme(mode: ThemeMode) {
        viewModelScope.launch { prefs.setThemeMode(mode) }
    }

    fun setShowWeekNumber(enabled: Boolean) {
        viewModelScope.launch { prefs.setShowWeekNumber(enabled) }
    }

    fun setMonthScrollStyle(style: MonthScrollStyle) {
        viewModelScope.launch { prefs.setMonthScrollStyle(style) }
    }

    fun setToolbarPosition(pos: ToolbarPosition) {
        viewModelScope.launch { prefs.setToolbarPosition(pos) }
    }

    fun setFontScaleOption(option: FontScaleOption) {
        viewModelScope.launch { prefs.setFontScaleOption(option) }
    }

    fun setWidgetThemeMode(mode: WidgetThemeMode) {
        viewModelScope.launch {
            prefs.setWidgetThemeMode(mode)
            updateAllWidgets(appContext)
        }
    }

    fun setWidgetTranslucent(enabled: Boolean) {
        viewModelScope.launch {
            prefs.setWidgetTranslucent(enabled)
            updateAllWidgets(appContext)
        }
    }

    fun setDefaultView(v: CalendarView) {
        viewModelScope.launch { prefs.setDefaultView(v) }
    }

    fun setWeekStartsOn(d: DayOfWeek?) {
        viewModelScope.launch { prefs.setWeekStartsOn(d) }
    }

    fun setIs24HourOverride(value: Boolean?) {
        viewModelScope.launch { prefs.setIs24HourOverride(value) }
    }

    fun setDimPastDates(b: Boolean) {
        viewModelScope.launch { prefs.setDimPastDates(b) }
    }

    fun setDefaultSnoozeMinutes(minutes: Int) {
        viewModelScope.launch { prefs.setDefaultSnoozeMinutes(minutes) }
    }

    fun setDefaultDurationMinutes(minutes: Int) {
        viewModelScope.launch { prefs.setDefaultDurationMinutes(minutes) }
    }

    fun setPaletteId(palette: PaletteId) {
        viewModelScope.launch { prefs.setPaletteId(palette) }
    }

    fun setWorkingHoursEnabled(enabled: Boolean) {
        viewModelScope.launch { prefs.setWorkingHoursEnabled(enabled) }
    }

    fun setWorkingHoursRange(startHour: Int, endHour: Int) {
        viewModelScope.launch { prefs.setWorkingHoursRange(startHour, endHour) }
    }

    fun setDefaultTimedReminderMinutes(minutes: Int?) {
        viewModelScope.launch { prefs.setDefaultTimedReminderMinutes(minutes) }
    }

    fun setDefaultAllDayReminderMinutes(minutes: Int?) {
        viewModelScope.launch { prefs.setDefaultAllDayReminderMinutes(minutes) }
    }

    fun setWorkingDaysEnabled(enabled: Boolean) {
        viewModelScope.launch { prefs.setWorkingDaysEnabled(enabled) }
    }

    fun setWorkingDays(days: Set<DayOfWeek>) {
        viewModelScope.launch { prefs.setWorkingDays(days) }
    }

    fun setContactOccasionsEnabled(enabled: Boolean) {
        viewModelScope.launch { prefs.setContactOccasionsEnabled(enabled) }
    }

    // provisions the two calendars and runs the first sync; called only
    // after READ_CONTACTS is granted (see ContactsPermissionRequest).
    fun enableContactOccasions() {
        viewModelScope.launch {
            occasionProvisioner.enable(
                appContext.getString(R.string.occasion_birthday_calendar),
                appContext.getString(R.string.occasion_anniversary_calendar),
                state.value.contactReminderMinutesBefore,
                titleFor,
            )
        }
    }

    fun disableContactOccasions() {
        viewModelScope.launch {
            occasionProvisioner.disable()
        }
    }

    fun setContactReminderMinutesBefore(minutes: Int?) {
        viewModelScope.launch {
            prefs.setContactReminderMinutesBefore(minutes)
            val current = state.value
            val birthdaysId = current.birthdaysCalendarId
            val anniversariesId = current.anniversariesCalendarId
            if (current.contactOccasionsEnabled && birthdaysId != null && anniversariesId != null) {
                occasionSync.reapplyReminders(birthdaysId, anniversariesId, minutes)
            }
        }
    }

    fun setBirthdaysCalendarId(id: Long?) {
        viewModelScope.launch { prefs.setBirthdaysCalendarId(id) }
    }

    fun setAnniversariesCalendarId(id: Long?) {
        viewModelScope.launch { prefs.setAnniversariesCalendarId(id) }
    }

    // non-destructive: mode-driven hides are derived on read
    // (StorageModeFilter), so manual hiddenCalendarIds toggles survive.
    fun setStorageMode(mode: StorageMode) {
        viewModelScope.launch {
            prefs.setStorageMode(mode)
            applyStorageMode(mode)
        }
    }

    private suspend fun applyStorageMode(mode: StorageMode) {
        Timber.d("applyStorageMode mode=%s", mode)
        StorageModeSetup.ensureLocalCalendarIfNeeded(calendarRepo, mode)
    }

    class Factory(private val appContext: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass == SettingsViewModel::class.java)
            return SettingsViewModel(
                prefs = UserPreferences(appContext.settingsDataStore),
                calendarRepo = CalendarRepository(appContext.contentResolver),
                appContext = appContext,
            ) as T
        }
    }
}
