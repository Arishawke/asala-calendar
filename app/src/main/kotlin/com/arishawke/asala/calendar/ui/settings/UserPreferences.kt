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
import androidx.compose.runtime.Immutable
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.arishawke.asala.calendar.CalendarView
import com.arishawke.asala.calendar.data.StorageMode
import com.arishawke.asala.calendar.data.TimeUnits
import com.arishawke.asala.calendar.ui.theme.PaletteId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.time.DayOfWeek

val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

// Mon-Fri office baseline; the reader coerces empty/null/invalid stored
// values to this so a corrupt write can't dim every day.
internal val WorkingDaysDefault: Set<DayOfWeek> = setOf(
    DayOfWeek.MONDAY,
    DayOfWeek.TUESDAY,
    DayOfWeek.WEDNESDAY,
    DayOfWeek.THURSDAY,
    DayOfWeek.FRIDAY,
)

// Set<DayOfWeek> isn't Compose-stable (collection generics aren't
// inferable), so screens take a Long bitmask. bit position = ordinal 0-6.
internal fun Set<DayOfWeek>.toWorkingDaysMask(): Long {
    var mask = 0L
    for (day in this) mask = mask or (1L shl day.ordinal)
    return mask
}

internal fun Long.containsWorkingDay(day: DayOfWeek): Boolean = (this and (1L shl day.ordinal)) != 0L

// @Immutable is safe: each emission is a fresh UserPrefs whose map/set
// fields are never mutated in place, so recompose can skip on equality.
@Immutable
data class UserPrefs(
    val themeMode: ThemeMode,
    val defaultView: CalendarView,
    val weekStartsOn: DayOfWeek?,
    // tri-state: null follows the system 24-hour setting, true/false force
    // it. starts null so 24-hour-region users see their default unoverridden.
    val is24HourOverride: Boolean?,
    val hiddenCalendarIds: Set<Long>,
    // accounts hidden from the drawer entirely. key "<type>:<name>" (matches
    // accountOverrideKey). hiding drops the group and its events; restoring
    // preserves each calendar's prior checkbox state.
    val drawerHiddenAccountKeys: Set<String>,
    val collapsedAccounts: Set<String>,
    val dimPastDates: Boolean,
    val defaultSnoozeMinutes: Int,
    val oemAdvisoryShown: Boolean,
    val storageMode: StorageMode,
    // per-account avatar color. key "<type>:<name>", value ARGB int.
    val accountAvatarColors: Map<String, Int>,
    // per-calendar color keyed by Calendars._ID. only persistence for synced
    // calendars; local ones also write CALENDAR_COLOR (see CalendarRepository).
    val calendarColorOverrides: Map<Long, Int>,
    val defaultDurationMinutes: Int,
    // per-event color keyed by Events._ID (row, not instance) so it covers
    // every recurring instance. DataStore only, never CalendarContract, so
    // sync can't clobber it and it works on read-only synced calendars.
    val eventColorOverrides: Map<Long, Int>,
    // palette the recolor pickers offer. defaults Okabe-Ito so existing
    // installs see no visual change.
    val paletteId: PaletteId,
    // working-hours dim: hours outside [start, end) render at half opacity.
    // stored 0-23; end exclusive (end=17 dims the 17:00 slot too).
    val workingHoursEnabled: Boolean,
    val workingHoursStartHour: Int,
    val workingHoursEndHour: Int,
    // default reminder for new timed events; null = none. existing events
    // never altered.
    val defaultTimedReminderMinutes: Int?,
    // separate all-day default: "15 min before" on an all-day event fires
    // at 23:45 the night before, rarely wanted. null = none.
    val defaultAllDayReminderMinutes: Int?,
    // working-days dim: days not in workingDays render at half opacity.
    // empty falls back to Mon-Fri on read so a corrupt write can't dim all.
    val workingDaysEnabled: Boolean,
    val workingDays: Set<DayOfWeek>,
    // ISO 8601 week-of-year on the Month/Week left rail. off by default.
    val showWeekNumber: Boolean,
    // see [[MonthScrollStyle]].
    val monthScrollStyle: MonthScrollStyle,
    // where the app bar sits across every screen. see [[ToolbarPosition]].
    val toolbarPosition: ToolbarPosition,
    // home-screen widget appearance, independent of the app theme. FollowApp
    // reproduces the prior behavior (widgets track themeMode).
    val widgetThemeMode: WidgetThemeMode,
    val widgetTranslucent: Boolean,
) {
    companion object {
        // the values an empty DataStore yields. UserPreferences.prefs and the
        // Settings stateIn both derive from this so they can't drift apart.
        val Defaults = UserPrefs(
            themeMode = ThemeMode.System,
            defaultView = CalendarView.Month,
            weekStartsOn = null,
            is24HourOverride = null,
            hiddenCalendarIds = emptySet(),
            drawerHiddenAccountKeys = emptySet(),
            collapsedAccounts = emptySet(),
            dimPastDates = false,
            defaultSnoozeMinutes = 10,
            oemAdvisoryShown = false,
            storageMode = StorageMode.Unset,
            accountAvatarColors = emptyMap(),
            calendarColorOverrides = emptyMap(),
            defaultDurationMinutes = 60,
            eventColorOverrides = emptyMap(),
            paletteId = PaletteId.OkabeIto,
            workingHoursEnabled = false,
            workingHoursStartHour = 9,
            workingHoursEndHour = 17,
            defaultTimedReminderMinutes = null,
            defaultAllDayReminderMinutes = null,
            workingDaysEnabled = false,
            workingDays = WorkingDaysDefault,
            showWeekNumber = false,
            monthScrollStyle = MonthScrollStyle.Continuous,
            toolbarPosition = ToolbarPosition.Top,
            widgetThemeMode = WidgetThemeMode.FollowApp,
            widgetTranslucent = false,
        )
    }
}

class UserPreferences(private val dataStore: DataStore<Preferences>) {
    val prefs: Flow<UserPrefs> =
        dataStore.data.map { p ->
            val d = UserPrefs.Defaults
            UserPrefs(
                themeMode = parseEnum(p[KEY_THEME], d.themeMode) { ThemeMode.valueOf(it) },
                defaultView = parseEnum(p[KEY_DEFAULT_VIEW], d.defaultView) { CalendarView.valueOf(it) },
                weekStartsOn = p[KEY_WEEK_START]?.let { runCatching { DayOfWeek.valueOf(it) }.getOrNull() },
                is24HourOverride = p[KEY_24H],
                hiddenCalendarIds =
                p[KEY_HIDDEN_CALENDAR_IDS]
                    ?.mapNotNullTo(mutableSetOf()) { it.toLongOrNull() }
                    ?: d.hiddenCalendarIds,
                drawerHiddenAccountKeys = p[KEY_DRAWER_HIDDEN_ACCOUNT_KEYS] ?: d.drawerHiddenAccountKeys,
                collapsedAccounts = p[KEY_COLLAPSED_ACCOUNTS] ?: d.collapsedAccounts,
                dimPastDates = p[KEY_DIM_PAST_DATES] ?: d.dimPastDates,
                defaultSnoozeMinutes = p[KEY_DEFAULT_SNOOZE_MIN] ?: d.defaultSnoozeMinutes,
                oemAdvisoryShown = p[KEY_OEM_ADVISORY_SHOWN] ?: d.oemAdvisoryShown,
                storageMode = parseEnum(p[KEY_STORAGE_MODE], d.storageMode) { StorageMode.valueOf(it) },
                accountAvatarColors = decodeStringIntMap(p[KEY_ACCOUNT_AVATAR_COLORS]),
                calendarColorOverrides = decodeLongIntMap(p[KEY_CALENDAR_COLOR_OVERRIDES]),
                defaultDurationMinutes = p[KEY_DEFAULT_DURATION_MIN] ?: d.defaultDurationMinutes,
                eventColorOverrides = decodeLongIntMap(p[KEY_EVENT_COLOR_OVERRIDES]),
                paletteId = parseEnum(p[KEY_PALETTE_ID], d.paletteId) { PaletteId.valueOf(it) },
                workingHoursEnabled = p[KEY_WORKING_HOURS_ENABLED] ?: d.workingHoursEnabled,
                workingHoursStartHour = (p[KEY_WORKING_HOURS_START] ?: WorkingHoursDefaultStart)
                    .coerceIn(0, MaxWorkingHoursStart),
                workingHoursEndHour = (p[KEY_WORKING_HOURS_END] ?: WorkingHoursDefaultEnd)
                    .coerceIn(0, TimeUnits.HoursPerDay),
                defaultTimedReminderMinutes = p[KEY_DEFAULT_TIMED_REMINDER_MIN],
                defaultAllDayReminderMinutes = p[KEY_DEFAULT_ALL_DAY_REMINDER_MIN],
                workingDaysEnabled = p[KEY_WORKING_DAYS_ENABLED] ?: d.workingDaysEnabled,
                workingDays = decodeWorkingDays(p[KEY_WORKING_DAYS]),
                showWeekNumber = p[KEY_SHOW_WEEK_NUMBER] ?: d.showWeekNumber,
                monthScrollStyle = parseEnum(p[KEY_MONTH_SCROLL_STYLE], d.monthScrollStyle) {
                    MonthScrollStyle.valueOf(it)
                },
                toolbarPosition = parseEnum(p[KEY_TOOLBAR_POSITION], d.toolbarPosition) {
                    ToolbarPosition.valueOf(it)
                },
                widgetThemeMode = parseEnum(p[KEY_WIDGET_THEME_MODE], d.widgetThemeMode) {
                    WidgetThemeMode.valueOf(it)
                },
                widgetTranslucent = p[KEY_WIDGET_TRANSLUCENT] ?: d.widgetTranslucent,
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

    suspend fun setIs24HourOverride(value: Boolean?) {
        dataStore.edit { p ->
            if (value == null) p.remove(KEY_24H) else p[KEY_24H] = value
        }
    }

    suspend fun setHiddenCalendarIds(ids: Set<Long>) {
        dataStore.edit { p ->
            if (ids.isEmpty()) {
                p.remove(KEY_HIDDEN_CALENDAR_IDS)
            } else {
                p[KEY_HIDDEN_CALENDAR_IDS] = ids.mapTo(mutableSetOf()) { it.toString() }
            }
        }
    }

    suspend fun setDrawerHiddenAccountKeys(keys: Set<String>) {
        dataStore.edit { p ->
            if (keys.isEmpty()) {
                p.remove(KEY_DRAWER_HIDDEN_ACCOUNT_KEYS)
            } else {
                p[KEY_DRAWER_HIDDEN_ACCOUNT_KEYS] = keys
            }
        }
    }

    suspend fun setCollapsedAccounts(accounts: Set<String>) {
        dataStore.edit { p ->
            if (accounts.isEmpty()) {
                p.remove(KEY_COLLAPSED_ACCOUNTS)
            } else {
                p[KEY_COLLAPSED_ACCOUNTS] = accounts
            }
        }
    }

    suspend fun setDimPastDates(enabled: Boolean) {
        dataStore.edit { it[KEY_DIM_PAST_DATES] = enabled }
    }

    suspend fun setDefaultSnoozeMinutes(minutes: Int) {
        dataStore.edit { it[KEY_DEFAULT_SNOOZE_MIN] = minutes }
    }

    suspend fun setOemAdvisoryShown(shown: Boolean) {
        dataStore.edit { it[KEY_OEM_ADVISORY_SHOWN] = shown }
    }

    suspend fun setStorageMode(mode: StorageMode) {
        dataStore.edit { it[KEY_STORAGE_MODE] = mode.name }
    }

    suspend fun setAccountAvatarColor(accountKey: String, argb: Int?) {
        dataStore.edit { p ->
            val current = decodeStringIntMap(p[KEY_ACCOUNT_AVATAR_COLORS]).toMutableMap()
            if (argb == null) current.remove(accountKey) else current[accountKey] = argb
            if (current.isEmpty()) {
                p.remove(KEY_ACCOUNT_AVATAR_COLORS)
            } else {
                p[KEY_ACCOUNT_AVATAR_COLORS] = json.encodeToString(stringIntMap, current)
            }
        }
    }

    suspend fun setCalendarColorOverride(calendarId: Long, argb: Int?) {
        dataStore.edit { p ->
            val current = decodeLongIntMap(p[KEY_CALENDAR_COLOR_OVERRIDES]).toMutableMap()
            if (argb == null) current.remove(calendarId) else current[calendarId] = argb
            if (current.isEmpty()) {
                p.remove(KEY_CALENDAR_COLOR_OVERRIDES)
            } else {
                p[KEY_CALENDAR_COLOR_OVERRIDES] = json.encodeToString(longIntMap, current)
            }
        }
    }

    suspend fun setDefaultDurationMinutes(minutes: Int) {
        dataStore.edit { it[KEY_DEFAULT_DURATION_MIN] = minutes }
    }

    suspend fun setEventColorOverride(eventId: Long, argb: Int?) {
        dataStore.edit { p ->
            val current = decodeLongIntMap(p[KEY_EVENT_COLOR_OVERRIDES]).toMutableMap()
            if (argb == null) current.remove(eventId) else current[eventId] = argb
            if (current.isEmpty()) {
                p.remove(KEY_EVENT_COLOR_OVERRIDES)
            } else {
                p[KEY_EVENT_COLOR_OVERRIDES] = json.encodeToString(longIntMap, current)
            }
        }
    }

    suspend fun setPaletteId(palette: PaletteId) {
        dataStore.edit { it[KEY_PALETTE_ID] = palette.name }
    }

    suspend fun setWorkingHoursEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_WORKING_HOURS_ENABLED] = enabled }
    }

    suspend fun setWorkingHoursRange(startHour: Int, endHour: Int) {
        // read path coerces; reject inverted ranges in case a stale write
        // slips past the UI.
        if (endHour <= startHour) return
        dataStore.edit { p ->
            p[KEY_WORKING_HOURS_START] = startHour
            p[KEY_WORKING_HOURS_END] = endHour
        }
    }

    suspend fun setDefaultTimedReminderMinutes(minutes: Int?) {
        dataStore.edit { p ->
            if (minutes ==
                null
            ) {
                p.remove(KEY_DEFAULT_TIMED_REMINDER_MIN)
            } else {
                p[KEY_DEFAULT_TIMED_REMINDER_MIN] = minutes
            }
        }
    }

    suspend fun setDefaultAllDayReminderMinutes(minutes: Int?) {
        dataStore.edit { p ->
            if (minutes ==
                null
            ) {
                p.remove(KEY_DEFAULT_ALL_DAY_REMINDER_MIN)
            } else {
                p[KEY_DEFAULT_ALL_DAY_REMINDER_MIN] = minutes
            }
        }
    }

    suspend fun setWorkingDaysEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_WORKING_DAYS_ENABLED] = enabled }
    }

    suspend fun setWorkingDays(days: Set<DayOfWeek>) {
        dataStore.edit { p ->
            if (days.isEmpty()) {
                p.remove(KEY_WORKING_DAYS)
            } else {
                p[KEY_WORKING_DAYS] = days.mapTo(mutableSetOf()) { it.name }
            }
        }
    }

    suspend fun setShowWeekNumber(enabled: Boolean) {
        dataStore.edit { it[KEY_SHOW_WEEK_NUMBER] = enabled }
    }

    suspend fun setMonthScrollStyle(style: MonthScrollStyle) {
        dataStore.edit { it[KEY_MONTH_SCROLL_STYLE] = style.name }
    }

    suspend fun setToolbarPosition(pos: ToolbarPosition) {
        dataStore.edit { it[KEY_TOOLBAR_POSITION] = pos.name }
    }

    suspend fun setWidgetThemeMode(mode: WidgetThemeMode) {
        dataStore.edit { it[KEY_WIDGET_THEME_MODE] = mode.name }
    }

    suspend fun setWidgetTranslucent(enabled: Boolean) {
        dataStore.edit { it[KEY_WIDGET_TRANSLUCENT] = enabled }
    }

    private inline fun <T> parseEnum(raw: String?, default: T, of: (String) -> T): T = raw?.let {
        runCatching { of(it) }.getOrElse { e ->
            Timber.w(e, "discarding unparseable stored enum value %s", it)
            null
        }
    } ?: default

    private companion object {
        val KEY_THEME = stringPreferencesKey("theme_mode") // same key as ThemePreference
        val KEY_DEFAULT_VIEW = stringPreferencesKey("default_view")
        val KEY_WEEK_START = stringPreferencesKey("week_starts_on")
        val KEY_24H = booleanPreferencesKey("is_24_hour")
        val KEY_HIDDEN_CALENDAR_IDS = stringSetPreferencesKey("hidden_calendar_ids")
        val KEY_DRAWER_HIDDEN_ACCOUNT_KEYS = stringSetPreferencesKey("drawer_hidden_account_keys")
        val KEY_COLLAPSED_ACCOUNTS = stringSetPreferencesKey("collapsed_accounts")
        val KEY_DIM_PAST_DATES = booleanPreferencesKey("dim_past_dates")
        val KEY_DEFAULT_SNOOZE_MIN = intPreferencesKey("default_snooze_minutes")
        val KEY_OEM_ADVISORY_SHOWN = booleanPreferencesKey("oem_advisory_shown")
        val KEY_STORAGE_MODE = stringPreferencesKey("storage_mode")
        val KEY_ACCOUNT_AVATAR_COLORS = stringPreferencesKey("account_avatar_colors")
        val KEY_CALENDAR_COLOR_OVERRIDES = stringPreferencesKey("calendar_color_overrides")
        val KEY_DEFAULT_DURATION_MIN = intPreferencesKey("default_duration_minutes")
        val KEY_EVENT_COLOR_OVERRIDES = stringPreferencesKey("event_color_overrides")
        val KEY_PALETTE_ID = stringPreferencesKey("palette_id")
        val KEY_WORKING_HOURS_ENABLED = booleanPreferencesKey("working_hours_enabled")
        val KEY_WORKING_HOURS_START = intPreferencesKey("working_hours_start")
        val KEY_WORKING_HOURS_END = intPreferencesKey("working_hours_end")
        val KEY_DEFAULT_TIMED_REMINDER_MIN = intPreferencesKey("default_timed_reminder_minutes")
        val KEY_DEFAULT_ALL_DAY_REMINDER_MIN = intPreferencesKey("default_all_day_reminder_minutes")
        val KEY_WORKING_DAYS_ENABLED = booleanPreferencesKey("working_days_enabled")
        val KEY_WORKING_DAYS = stringSetPreferencesKey("working_days")
        val KEY_SHOW_WEEK_NUMBER = booleanPreferencesKey("show_week_number")
        val KEY_MONTH_SCROLL_STYLE = stringPreferencesKey("month_scroll_style")
        val KEY_TOOLBAR_POSITION = stringPreferencesKey("toolbar_position")
        val KEY_WIDGET_THEME_MODE = stringPreferencesKey("widget_theme_mode")
        val KEY_WIDGET_TRANSLUCENT = booleanPreferencesKey("widget_translucent")

        fun decodeWorkingDays(raw: Set<String>?): Set<DayOfWeek> {
            if (raw.isNullOrEmpty()) return WorkingDaysDefault
            val decoded = raw.mapNotNullTo(mutableSetOf()) { name ->
                runCatching { DayOfWeek.valueOf(name) }.getOrNull()
            }
            return if (decoded.isEmpty()) WorkingDaysDefault else decoded
        }

        // 9-to-5 office baseline.
        const val MaxWorkingHoursStart = TimeUnits.HoursPerDay - 1
        const val WorkingHoursDefaultStart = 9
        const val WorkingHoursDefaultEnd = 17

        // decode wraps runCatching so a malformed/legacy entry can't crash
        // the read; failures are logged so a lossy schema bump is observable.
        val json = Json { ignoreUnknownKeys = true }
        val stringIntMap = MapSerializer(String.serializer(), Int.serializer())
        val longIntMap = MapSerializer(Long.serializer(), Int.serializer())

        fun decodeStringIntMap(raw: String?): Map<String, Int> {
            if (raw == null) return emptyMap()
            return runCatching { json.decodeFromString(stringIntMap, raw) }
                .onFailure { Timber.w(it, "decodeStringIntMap failed; resetting to empty") }
                .getOrDefault(emptyMap())
        }

        fun decodeLongIntMap(raw: String?): Map<Long, Int> {
            if (raw == null) return emptyMap()
            return runCatching { json.decodeFromString(longIntMap, raw) }
                .onFailure { Timber.w(it, "decodeLongIntMap failed; resetting to empty") }
                .getOrDefault(emptyMap())
        }
    }
}
