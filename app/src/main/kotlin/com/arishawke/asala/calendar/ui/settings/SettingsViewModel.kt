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
import com.arishawke.asala.calendar.data.CalendarRepository
import com.arishawke.asala.calendar.data.StorageMode
import com.arishawke.asala.calendar.data.StorageModeSetup
import com.arishawke.asala.calendar.ui.theme.PaletteId
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.DayOfWeek

class SettingsViewModel(private val prefs: UserPreferences, private val calendarRepo: CalendarRepository) :
    ViewModel() {
    val state: StateFlow<UserPrefs> =
        prefs.prefs.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = UserPrefs.Defaults,
        )

    fun setTheme(mode: ThemeMode) {
        viewModelScope.launch { prefs.setThemeMode(mode) }
    }

    fun setShowWeekNumber(enabled: Boolean) {
        viewModelScope.launch { prefs.setShowWeekNumber(enabled) }
    }

    fun setMonthScrollStyle(style: MonthScrollStyle) {
        viewModelScope.launch { prefs.setMonthScrollStyle(style) }
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

    fun setTasksEnabled(b: Boolean) {
        viewModelScope.launch { prefs.setTasksEnabled(b) }
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
            ) as T
        }
    }
}
