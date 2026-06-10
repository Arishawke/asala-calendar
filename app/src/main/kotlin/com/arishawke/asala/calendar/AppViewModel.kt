/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.arishawke.asala.calendar.data.CalendarItem
import com.arishawke.asala.calendar.data.CalendarRepository
import com.arishawke.asala.calendar.data.EventDetail
import com.arishawke.asala.calendar.data.EventRepository
import com.arishawke.asala.calendar.data.StorageModeFilter
import com.arishawke.asala.calendar.data.resolveEventDetailColor
import com.arishawke.asala.calendar.ui.settings.ThemeMode
import com.arishawke.asala.calendar.ui.settings.UserPreferences
import com.arishawke.asala.calendar.ui.settings.UserPrefs
import com.arishawke.asala.calendar.ui.settings.settingsDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import timber.log.Timber
import java.time.LocalDate
import java.time.LocalTime

enum class CalendarView { Year, Month, Week, ThreeDay, Day, Schedule }

data class OpenEvent(val eventId: Long, val instanceMillis: Long)

// drag-reschedule held until the recurring scope picker resolves. detail
// avoids a re-fetch in the save path; instanceMillis (original start) is
// needed for ThisInstance / ThisAndFollowing scopes.
data class PendingReschedule(
    val detail: EventDetail,
    val instanceMillis: Long,
    val newStartMillis: Long,
    val newEndMillis: Long,
)

data class AppUiState(
    val currentView: CalendarView,
    val calendars: List<CalendarItem>,
    val hiddenCalendarIds: Set<Long>,
    val collapsedAccounts: Set<String>,
    val themeMode: ThemeMode,
)

// drawer-hidden account with display metadata so Settings can render it
// without joining the calendar list at the UI layer
data class DrawerHiddenAccount(val accountKey: String, val accountName: String, val accountType: String)

// cross-view jump payload. `view` lets each screen's handler ignore jumps
// not meant for it, so the source screen can't consume a jump intended for
// the destination during an AnimatedContent transition.
data class PendingDateJump(val date: LocalDate, val view: CalendarView)

// post-save reveal target for the timeline views. carries the time and event
// id that a plain date jump lacks; `view` is filtered for the same reason as
// PendingDateJump. time is null for all-day (no timeline position to point at).
data class PendingEventReveal(val date: LocalDate, val time: LocalTime?, val eventId: Long, val view: CalendarView)

// mirrors `accountOverrideKey` in ui/month/drawer/AccountSection.kt; inlined
// to avoid importing a UI-layer helper here
internal fun drawerAccountKey(accountType: String, accountName: String): String = "$accountType:$accountName"

class AppViewModel(
    private val appContext: Context,
    private val calendarRepo: CalendarRepository,
    internal val eventRepository: EventRepository,
    private val userPreferences: UserPreferences,
    initialPrefs: UserPrefs,
) : ViewModel() {
    private val currentView = MutableStateFlow(initialPrefs.defaultView)

    // effective hide set = drawer toggles union storage-mode hides. mode
    // hides are computed from the live list, not persisted, so flipping
    // modes never overwrites manual drawer choices. WhileSubscribed (not
    // Eagerly) because this combine pulls in observeCalendars() which
    // registers a CalendarProvider ContentObserver: deferring subscription
    // keeps the observer from registering before READ_CALENDAR is granted.
    val hiddenCalendarIdsFlow: StateFlow<Set<Long>> =
        combine(
            userPreferences.prefs,
            calendarRepo.observeCalendars(),
        ) { p, cals ->
            computeHiddenCalendarIds(
                hiddenCalendarIds = p.hiddenCalendarIds,
                drawerHiddenAccountKeys = p.drawerHiddenAccountKeys,
                storageMode = p.storageMode,
                calendars = cals,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = initialPrefs.hiddenCalendarIds,
        )

    // user's explicit drawer-hide set; drawer filters AccountGroups by it
    val drawerHiddenAccountKeysFlow: StateFlow<Set<String>> =
        userPreferences.prefs
            .map { it.drawerHiddenAccountKeys }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = initialPrefs.drawerHiddenAccountKeys,
            )

    val collapsedAccountsFlow: StateFlow<Set<String>> =
        userPreferences.prefs
            .map { it.collapsedAccounts }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue = initialPrefs.collapsedAccounts,
            )

    // Today-action ticker. each screen remembers its last-seen value, so
    // only a real increment (not a view switch) scrolls it back to today.
    private val _todayJumpCounter = MutableStateFlow(0)
    val todayJumpCounter: StateFlow<Int> = _todayJumpCounter.asStateFlow()

    // cross-view target; consumed once the destination scrolls to the date.
    // view is bundled so each screen ignores jumps not meant for it: during
    // an AnimatedContent transition the source screen also observes this,
    // and an unfiltered handler raced to consume the value first.
    private val _pendingDateJump = MutableStateFlow<PendingDateJump?>(null)
    val pendingDateJump: StateFlow<PendingDateJump?> = _pendingDateJump.asStateFlow()

    // post-save reveal target for the timeline views; consumed once the timeline
    // shows the pill or glows the event.
    private val _pendingEventReveal = MutableStateFlow<PendingEventReveal?>(null)
    val pendingEventReveal: StateFlow<PendingEventReveal?> = _pendingEventReveal.asStateFlow()

    // Month-view's visible YearMonth, pushed up for the header chip strip.
    // stale on other views, but the chip strip only renders in Month view.
    private val _viewedMonth = MutableStateFlow(java.time.YearMonth.from(LocalDate.now()))
    val viewedMonth: StateFlow<java.time.YearMonth> = _viewedMonth.asStateFlow()

    // focused date across views, so openCreateEditor seeds the editor with
    // the date the user was looking at rather than always today
    private val _viewedDate = MutableStateFlow(LocalDate.now())
    val viewedDate: StateFlow<LocalDate> = _viewedDate.asStateFlow()

    // origin view of a requestJumpTo, so Back pops here instead of exiting.
    // cleared on manual selectView so stale history can't strand the user.
    private val _previousView = MutableStateFlow<CalendarView?>(null)
    val previousView: StateFlow<CalendarView?> = _previousView.asStateFlow()

    // separate from uiState so the theme applies before calendar permission:
    // collecting uiState triggers observeCalendars(), whose CalendarProvider
    // ContentObserver throws SecurityException before READ_CALENDAR is granted.
    val themeMode: StateFlow<ThemeMode> =
        userPreferences.prefs
            .map { it.themeMode }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue = initialPrefs.themeMode,
            )

    val prefs: StateFlow<UserPrefs> =
        userPreferences.prefs.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = initialPrefs,
        )

    // per-calendar color overrides; synced calendars apply them at render
    // time, so each event-loading view model takes this flow
    val calendarColorOverridesFlow: StateFlow<Map<Long, Int>> =
        userPreferences.prefs
            .map { it.calendarColorOverrides }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = initialPrefs.calendarColorOverrides,
            )

    // per-event color overrides, keyed on Events._ID (not instanceId) so a
    // recurring event's override covers every instance. app-local only,
    // never written to CalendarContract. precedence: event > calendar > default.
    val eventColorOverridesFlow: StateFlow<Map<Long, Int>> =
        userPreferences.prefs
            .map { it.eventColorOverrides }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = initialPrefs.eventColorOverrides,
            )

    // applies per-calendar overrides at the repo-flow boundary so every
    // consumer sees CalendarItem.displayColor without joining prefs itself
    private val calendarsWithOverrides: Flow<List<CalendarItem>> =
        combine(
            calendarRepo.observeCalendars(),
            userPreferences.prefs.map { it.calendarColorOverrides },
        ) { cals, overrides ->
            if (overrides.isEmpty()) {
                cals
            } else {
                cals.map { c -> overrides[c.id]?.let { c.copy(overrideColor = it) } ?: c }
            }
        }

    // resolved drawer-hidden accounts for Settings' restore section;
    // exposed instead of a raw calendar flow to decouple Settings from it
    val drawerHiddenAccountsFlow: StateFlow<List<DrawerHiddenAccount>> =
        combine(
            calendarRepo.observeCalendars(),
            userPreferences.prefs,
        ) { cals, prefs ->
            val keys = prefs.drawerHiddenAccountKeys
            if (keys.isEmpty()) return@combine emptyList()
            // one entry per hidden key, sorted by name for stable emissions
            keys.mapNotNull { key ->
                val match = cals.firstOrNull { drawerAccountKey(it.accountType, it.accountName) == key }
                    ?: return@mapNotNull null
                // mode-excluded account types read as nonexistent: drop from
                // the restore list too (the drawer already hides them)
                if (StorageModeFilter.accountHiddenByMode(match.accountType, prefs.storageMode)) {
                    return@mapNotNull null
                }
                DrawerHiddenAccount(
                    accountKey = key,
                    accountName = match.accountName,
                    accountType = match.accountType,
                )
            }.sortedBy { it.accountName }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    val uiState: StateFlow<AppUiState> =
        combine(
            currentView,
            calendarsWithOverrides,
            hiddenCalendarIdsFlow,
            collapsedAccountsFlow,
            themeMode,
        ) { view, cals, hidden, collapsed, theme ->
            AppUiState(
                currentView = view,
                calendars = cals,
                hiddenCalendarIds = hidden,
                collapsedAccounts = collapsed,
                themeMode = theme,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue =
            AppUiState(
                currentView = initialPrefs.defaultView,
                calendars = emptyList(),
                hiddenCalendarIds = initialPrefs.hiddenCalendarIds,
                collapsedAccounts = initialPrefs.collapsedAccounts,
                themeMode = initialPrefs.themeMode,
            ),
        )

    fun selectView(view: CalendarView) {
        currentView.update { view }
        _previousView.update { null }
    }

    fun jumpToToday() {
        _todayJumpCounter.update { it + 1 }
    }

    fun requestJumpTo(date: LocalDate, view: CalendarView) {
        val prior = currentView.value
        selectView(view)
        _previousView.update { prior.takeIf { it != view } }
        _pendingDateJump.update { PendingDateJump(date = date, view = view) }
    }

    fun consumePendingDateJump() {
        _pendingDateJump.update { null }
    }

    fun revealSavedEvent(date: LocalDate, time: LocalTime?, eventId: Long) {
        val view = currentView.value
        if (view.isTimelineView()) {
            _pendingEventReveal.update { PendingEventReveal(date, time, eventId, view) }
        } else {
            // schedule/month/year: just land on the date, no pill.
            _pendingDateJump.update { PendingDateJump(date, view) }
        }
    }

    fun consumeEventReveal() {
        _pendingEventReveal.update { null }
    }

    fun setViewedMonth(yearMonth: java.time.YearMonth) {
        _viewedMonth.update { yearMonth }
    }

    fun setViewedDate(date: LocalDate) {
        _viewedDate.update { date }
    }

    fun popView() {
        val prior = _previousView.value ?: return
        currentView.update { prior }
        _previousView.update { null }
    }

    fun toggleCalendarVisibility(calendarId: Long) {
        // toggle the user-only set, not the effective (mode union user) one,
        // or a mode-hidden calendar gets written back into the user set
        viewModelScope.launch {
            val current = userPreferences.prefs.first().hiddenCalendarIds
            val next = if (calendarId in current) current - calendarId else current + calendarId
            userPreferences.setHiddenCalendarIds(next)
        }
    }

    fun hideAccountFromDrawer(accountKey: String) {
        viewModelScope.launch {
            val current = userPreferences.prefs.first().drawerHiddenAccountKeys
            if (accountKey in current) return@launch
            userPreferences.setDrawerHiddenAccountKeys(current + accountKey)
        }
    }

    fun restoreAccountToDrawer(accountKey: String) {
        viewModelScope.launch {
            val current = userPreferences.prefs.first().drawerHiddenAccountKeys
            if (accountKey !in current) return@launch
            userPreferences.setDrawerHiddenAccountKeys(current - accountKey)
        }
    }

    fun toggleAccountCollapsed(account: String) {
        val current = collapsedAccountsFlow.value
        val next = if (account in current) current - account else current + account
        viewModelScope.launch { userPreferences.setCollapsedAccounts(next) }
    }

    // null = sheet closed. backers are internal so the extensions in
    // AppViewModelSheetState.kt can mutate them.
    internal val detailSheetEventBacker = MutableStateFlow<OpenEvent?>(null)
    val detailSheetEvent: StateFlow<OpenEvent?> = detailSheetEventBacker.asStateFlow()

    // raw repo EventDetail (provider DISPLAY_COLOR, no override resolution).
    // loaded async after openEventDetail; null while closed or in flight.
    internal val loadedDetailRawBacker = MutableStateFlow<EventDetail?>(null)

    // displayed EventDetail: raw with overrides re-resolved on every
    // emission, so an override change while the sheet is open updates it
    // live. an eager .value snapshot at open time left colors stale.
    val loadedDetail: StateFlow<EventDetail?> = combine(
        loadedDetailRawBacker,
        calendarColorOverridesFlow,
        eventColorOverridesFlow,
    ) { raw, calOverrides, evtOverrides ->
        raw?.let { d ->
            d.copy(displayColor = resolveEventDetailColor(d, evtOverrides, calOverrides))
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = null,
    )

    // surfaced in the detail sheet when a delete is rejected (read-only calendar,
    // permission revoked mid-session, removed account) so a failed destructive op
    // isn't silent. reset on open / close.
    internal val deleteFailedBacker = MutableStateFlow(false)
    val deleteFailed: StateFlow<Boolean> = deleteFailedBacker.asStateFlow()

    // null = no editor open; -1L = create new; >0 = edit existing
    internal val editEventIdBacker = MutableStateFlow<Long?>(null)
    val editEventId: StateFlow<Long?> = editEventIdBacker.asStateFlow()

    // instance start millis for the tapped event, used by scope-aware edit
    internal val editInstanceMillisBacker = MutableStateFlow<Long?>(null)
    val editInstanceMillis: StateFlow<Long?> = editInstanceMillisBacker.asStateFlow()

    // source event id for a Duplicate-opened editor; null for plain create
    // / edit. snapshotted by EventEditScreen at open.
    internal val editDuplicateSourceIdBacker = MutableStateFlow<Long?>(null)
    val editDuplicateSourceId: StateFlow<Long?> = editDuplicateSourceIdBacker.asStateFlow()

    // create-editor pre-fill date, snapshotted from viewedDate at open so a
    // later view change doesn't shift an open editor. null otherwise.
    internal val editInitialStartDateBacker = MutableStateFlow<LocalDate?>(null)
    val editInitialStartDate: StateFlow<LocalDate?> = editInitialStartDateBacker.asStateFlow()

    // recurring drag-reschedule held until the user picks a scope. null when
    // none in flight; non-recurring drags save immediately, bypassing this.
    internal val pendingRescheduleBacker = MutableStateFlow<PendingReschedule?>(null)
    val pendingReschedule: StateFlow<PendingReschedule?> = pendingRescheduleBacker.asStateFlow()

    // emits an event id whose in-flight drag should snap back (e.g. scope
    // picker cancelled); chips clear their offset via LocalDragRevertSignal.
    // replay = 0 so a late-mounting chip doesn't pick up a stale revert.
    internal val dragRevertSignalBacker = MutableSharedFlow<Long>(extraBufferCapacity = 1)
    val dragRevertSignal: SharedFlow<Long> = dragRevertSignalBacker.asSharedFlow()

    fun createLocalCalendar(name: String, color: Int) {
        viewModelScope.launch {
            calendarRepo.createLocalCalendar(name, color)
        }
    }

    fun deleteLocalCalendar(calendarId: Long) {
        viewModelScope.launch {
            calendarRepo.deleteLocalCalendar(calendarId)
            // drop the orphaned override so a recycled _ID doesn't inherit it
            runCatching { userPreferences.setCalendarColorOverride(calendarId, null) }
                .onFailure { Timber.e(it, "post-delete override cleanup failed for id=%d", calendarId) }
        }
    }

    fun renameLocalCalendar(calendarId: Long, newName: String) {
        viewModelScope.launch {
            calendarRepo.renameLocalCalendar(calendarId, newName)
        }
    }

    // log-only on DataStore write failure (rare: disk full / IO); no UI
    // surfacing yet, revisit if users actually hit it
    fun setAccountAvatarColor(accountKey: String, argb: Int) {
        viewModelScope.launch {
            runCatching { userPreferences.setAccountAvatarColor(accountKey, argb) }
                .onFailure { Timber.e(it, "setAccountAvatarColor failed for key=%s", accountKey) }
        }
    }

    // local calendars also write CALENDAR_COLOR (so other apps/exports see
    // it); synced ones skip the provider write so the sync adapter can't
    // clobber it. provider write goes first: on a mid-pair crash the override
    // map still wins on read, and the next call completes the pair.
    fun setCalendarColorOverride(calendarId: Long, argb: Int) {
        viewModelScope.launch {
            val cal =
                uiState.value.calendars.firstOrNull { it.id == calendarId }
                    ?: calendarRepo.calendars().firstOrNull { it.id == calendarId }
            if (cal != null && cal.accountType == android.provider.CalendarContract.ACCOUNT_TYPE_LOCAL) {
                val ok = calendarRepo.updateLocalCalendarColor(calendarId, argb)
                if (!ok) Timber.w("updateLocalCalendarColor returned false for id=%d", calendarId)
            }
            runCatching { userPreferences.setCalendarColorOverride(calendarId, argb) }
                .onFailure { Timber.e(it, "setCalendarColorOverride failed for id=%d", calendarId) }
        }
    }

    // app-local only: never touches CalendarContract, so it survives sync
    // and works on read-only calendars. null removes the entry.
    fun setEventColorOverride(eventId: Long, argb: Int?) {
        viewModelScope.launch {
            runCatching { userPreferences.setEventColorOverride(eventId, argb) }
                .onFailure { Timber.e(it, "setEventColorOverride failed for id=%d", eventId) }
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            userPreferences.setThemeMode(mode)
        }
    }

    fun markOemAdvisoryShown() {
        viewModelScope.launch {
            userPreferences.setOemAdvisoryShown(true)
        }
    }

    private val _settingsOpen = MutableStateFlow(false)
    val settingsOpen: StateFlow<Boolean> = _settingsOpen.asStateFlow()

    fun openSettings() {
        _settingsOpen.update { true }
    }

    fun closeSettings() {
        _settingsOpen.update { false }
    }

    private val _searchOpen = MutableStateFlow(false)
    val searchOpen: StateFlow<Boolean> = _searchOpen.asStateFlow()

    fun openSearch() {
        _searchOpen.update { true }
    }

    fun closeSearch() {
        _searchOpen.update { false }
    }

    private fun checkNotifPermission(): Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    } else {
        true // pre-Android-13: notifications don't need a runtime grant
    }

    private val _notificationPermissionGranted = MutableStateFlow(checkNotifPermission())
    val notificationPermissionGranted: StateFlow<Boolean> = _notificationPermissionGranted.asStateFlow()

    fun refreshNotificationPermission() {
        _notificationPermissionGranted.value = checkNotifPermission()
    }

    class Factory(private val appContext: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass == AppViewModel::class.java)
            val userPrefs = UserPreferences(appContext.settingsDataStore)
            // block once at startup to read prefs; else first composition
            // paints defaults then recomposes, causing a visible flash
            val initialPrefs = runBlocking { userPrefs.prefs.first() }
            return AppViewModel(
                appContext = appContext,
                calendarRepo = CalendarRepository(appContext.contentResolver),
                eventRepository = EventRepository(appContext.contentResolver),
                userPreferences = userPrefs,
                initialPrefs = initialPrefs,
            ) as T
        }
    }
}
