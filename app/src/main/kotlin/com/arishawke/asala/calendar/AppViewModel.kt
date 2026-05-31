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

enum class CalendarView { Month, Week, ThreeDay, Day, Schedule, Tasks }

// Tasks is gated by the tasksEnabled preference. Filter call sites use this
// to suppress the entry when the toggle is off.
fun CalendarView.isAlwaysVisible(): Boolean = this != CalendarView.Tasks

data class OpenEvent(val eventId: Long, val instanceMillis: Long)

// Holds a drag-reschedule waiting on the recurring scope picker. detail
// captures the loaded event so the save path can build a draft without
// re-fetching. instanceMillis is the original instance start, needed for
// ThisInstance / ThisAndFollowing scopes.
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

// One account hidden from the drawer, resolved with display metadata so the
// Settings "Hidden accounts" section can render it without joining calendars
// at the UI layer.
data class DrawerHiddenAccount(val accountKey: String, val accountName: String, val accountType: String)

// Cross-view jump payload. `view` is the target screen; each screen's
// pendingDate handler ignores jumps whose `view` isn't its own so the
// source screen can't accidentally consume a jump intended for the
// destination during an AnimatedContent transition.
data class PendingDateJump(val date: LocalDate, val view: CalendarView)

// Matches `accountOverrideKey` in ui/month/drawer/AccountSection.kt. Inlined
// here so AppViewModel does not have to import a UI-layer helper.
internal fun drawerAccountKey(accountType: String, accountName: String): String = "$accountType:$accountName"

class AppViewModel(
    private val appContext: Context,
    private val calendarRepo: CalendarRepository,
    internal val eventRepository: EventRepository,
    private val userPreferences: UserPreferences,
    initialPrefs: UserPrefs,
) : ViewModel() {
    private val currentView = MutableStateFlow(initialPrefs.defaultView)

    // Effective hide set = user toggles (drawer) union storage-mode hides
    // (Local only blanks the sync calendars). Mode hides are computed from
    // the live calendar list rather than persisted, so flipping storage
    // modes never overwrites the user's manual drawer choices.
    // WhileSubscribed (not Eagerly) because this combine pulls in
    // calendarRepo.observeCalendars(), which registers a ContentObserver on
    // CalendarProvider. All collectors live downstream of the permission gate,
    // so deferring subscription guarantees the observer never registers
    // before READ_CALENDAR is granted.
    val hiddenCalendarIdsFlow: StateFlow<Set<Long>> =
        combine(
            userPreferences.prefs,
            calendarRepo.observeCalendars(),
        ) { p, cals ->
            // drawer-hidden accounts contribute every calendar they own to
            // the effective hide set: if the user can't see the account in
            // the drawer, they can't toggle its calendars on, so events
            // stay suppressed until restored.
            val accountHiddenIds = cals
                .filter { drawerAccountKey(it.accountType, it.accountName) in p.drawerHiddenAccountKeys }
                .mapTo(mutableSetOf()) { it.id }
            p.hiddenCalendarIds + accountHiddenIds +
                StorageModeFilter.modeHiddenIds(p.storageMode, cals)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = initialPrefs.hiddenCalendarIds,
        )

    // User's explicit drawer-hide set. Drawer consumes this to filter
    // AccountGroups before rendering.
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

    // Ticker that the Today action increments. Each visible screen owns a
    // remembered last-seen value, so switching views does not spuriously
    // re-trigger the jump; only a real increment past the last-seen value
    // scrolls the active screen back to today.
    private val _todayJumpCounter = MutableStateFlow(0)
    val todayJumpCounter: StateFlow<Int> = _todayJumpCounter.asStateFlow()

    // Cross-view target. Set when the user taps a month-view cell or a
    // header-dropdown date / month chip; consumed once the destination
    // screen has scrolled its pager (or list) to the requested date. The
    // target view is bundled with the date so each screen's pendingDate
    // handler can ignore jumps not meant for it (every visible screen
    // observes pendingDateJump, including the source screen during an
    // AnimatedContent transition, so an unfiltered handler caused a
    // race: the source screen's "scroll to month containing date" could
    // consume the value before the destination read it).
    private val _pendingDateJump = MutableStateFlow<PendingDateJump?>(null)
    val pendingDateJump: StateFlow<PendingDateJump?> = _pendingDateJump.asStateFlow()

    // Month-view's currently-visible YearMonth, pushed up so the header
    // dropdown's month-chip strip can highlight the right chip and scroll
    // to it on open. Defaults to today's month; MonthScreen overwrites on
    // every page change. Stale while the user is on other views, but the
    // chip strip only renders in Month view so that does not matter.
    private val _viewedMonth = MutableStateFlow(java.time.YearMonth.from(LocalDate.now()))
    val viewedMonth: StateFlow<java.time.YearMonth> = _viewedMonth.asStateFlow()

    // Currently-focused date across views, used by `openCreateEditor` so
    // the new-event editor opens on the date the user was looking at
    // (the visible day in Day view, the visible week's representative
    // day in Week, etc.) rather than always today. Each view updates this
    // when its pager moves; Schedule sets it to today on entry.
    private val _viewedDate = MutableStateFlow(LocalDate.now())
    val viewedDate: StateFlow<LocalDate> = _viewedDate.asStateFlow()

    // Set when requestJumpTo navigates between views. The system Back gesture
    // pops back to this view rather than exiting the app. Cleared when the
    // user picks a view manually (selectView) so stale history can't strand
    // them on the wrong screen.
    private val _previousView = MutableStateFlow<CalendarView?>(null)
    val previousView: StateFlow<CalendarView?> = _previousView.asStateFlow()

    // Themed separately from uiState so the theme can be applied before the
    // calendar permission has been granted. Collecting uiState triggers
    // calendarRepo.observeCalendars(), which registers a ContentObserver on
    // CalendarProvider and crashes with SecurityException if permission has
    // not yet been granted.
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

    // Per-calendar color override map exposed for screens to thread into
    // their event-side ViewModels. Synced calendars only get the override
    // applied at render time, so each event-loading view model takes this
    // flow alongside hiddenCalendarIdsFlow.
    val calendarColorOverridesFlow: StateFlow<Map<Long, Int>> =
        userPreferences.prefs
            .map { it.calendarColorOverrides }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = initialPrefs.calendarColorOverrides,
            )

    // Per-event color override map. Keyed on Events._ID (not instanceId)
    // so a recurring event's override applies to every instance. Never
    // written to CalendarContract; lives only here. Threaded into the
    // same view models as calendarColorOverridesFlow and resolved with
    // event > calendar > default precedence.
    val eventColorOverridesFlow: StateFlow<Map<Long, Int>> =
        userPreferences.prefs
            .map { it.eventColorOverrides }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = initialPrefs.eventColorOverrides,
            )

    // Apply per-calendar color overrides on the way out of the repo flow so
    // every downstream consumer (drawer, Month/Week/Day/Schedule) sees the
    // user's chosen color via CalendarItem.displayColor without each one
    // having to join against UserPreferences itself.
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

    // Resolved list of drawer-hidden accounts (account key + a display name
    // and type for Settings' restore section). Exposed instead of a raw
    // calendar flow so Settings is decoupled from the live calendar list.
    val drawerHiddenAccountsFlow: StateFlow<List<DrawerHiddenAccount>> =
        combine(
            calendarRepo.observeCalendars(),
            userPreferences.prefs.map { it.drawerHiddenAccountKeys },
        ) { cals, keys ->
            if (keys.isEmpty()) return@combine emptyList()
            // For each hidden key, surface the account once with display
            // metadata from any of its calendars. Sort by display name so
            // the list stays stable across emissions.
            keys.mapNotNull { key ->
                val match = cals.firstOrNull { drawerAccountKey(it.accountType, it.accountName) == key }
                    ?: return@mapNotNull null
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
        // Toggle the user-only set, not the effective (mode union user) set.
        // Otherwise a mode-hidden sync calendar would be written back into
        // the user set on toggle and re-stomp the previous mode hide.
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

    // null = sheet closed. State backers exposed as internal so the
    // sheet-state extensions in AppViewModelSheetState.kt can mutate them.
    internal val detailSheetEventBacker = MutableStateFlow<OpenEvent?>(null)
    val detailSheetEvent: StateFlow<OpenEvent?> = detailSheetEventBacker.asStateFlow()

    // The raw EventDetail for the open sheet, as returned by the repository
    // (provider DISPLAY_COLOR, no override resolution applied). Loaded
    // asynchronously after openEventDetail and used by the delete path to
    // supply parentRrule and parentCalendarId. Null while the sheet is
    // closed or its data is still in flight.
    internal val loadedDetailRawBacker = MutableStateFlow<EventDetail?>(null)

    // The displayed EventDetail: raw + per-event and per-calendar color
    // overrides resolved on every emission. Re-resolves if an override map
    // changes while the sheet is open (e.g., a sync push from another app
    // updating a calendar color). Previously this baked in an eager .value
    // snapshot at open time; that left the chip and accent colors stale
    // until the user closed and re-opened the sheet.
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

    // null = no editor open; -1L = create new; >0 = edit existing
    internal val editEventIdBacker = MutableStateFlow<Long?>(null)
    val editEventId: StateFlow<Long?> = editEventIdBacker.asStateFlow()

    // instance start millis for the tapped event, used by scope-aware edit
    internal val editInstanceMillisBacker = MutableStateFlow<Long?>(null)
    val editInstanceMillis: StateFlow<Long?> = editInstanceMillisBacker.asStateFlow()

    // Source event id when the editor was opened via Duplicate; the editor
    // seeds a new event from it. Null for plain create / edit. Snapshotted
    // by EventEditScreen at open.
    internal val editDuplicateSourceIdBacker = MutableStateFlow<Long?>(null)
    val editDuplicateSourceId: StateFlow<Long?> = editDuplicateSourceIdBacker.asStateFlow()

    // Pre-fill date for the create-event editor. Null when the editor is
    // closed or editing an existing event. Snapshotted from viewedDate at
    // openCreateEditor time so a later view change doesn't retroactively
    // shift an open editor.
    internal val editInitialStartDateBacker = MutableStateFlow<LocalDate?>(null)
    val editInitialStartDate: StateFlow<LocalDate?> = editInitialStartDateBacker.asStateFlow()

    // Pending drag-reschedule on a recurring event: held until the user
    // picks a scope (this / this and following / all) in the dialog. Null
    // when no recurring drag is in flight; non-recurring drags save
    // immediately without going through this state.
    internal val pendingRescheduleBacker = MutableStateFlow<PendingReschedule?>(null)
    val pendingReschedule: StateFlow<PendingReschedule?> = pendingRescheduleBacker.asStateFlow()

    // Emits an event id when its in-flight drag should snap back to the
    // original position (e.g. the recurring scope picker was cancelled).
    // Chips collect this via LocalDragRevertSignal to clear their
    // optimistic drag offset. Replay = 0 so a chip that mounts late does
    // not pick up a stale revert.
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
            // Drop the orphaned per-calendar override so the map doesn't
            // accumulate stale entries (and so a future calendar that
            // reuses the same _ID doesn't silently inherit the color).
            runCatching { userPreferences.setCalendarColorOverride(calendarId, null) }
                .onFailure { Timber.e(it, "post-delete override cleanup failed for id=%d", calendarId) }
        }
    }

    fun renameLocalCalendar(calendarId: Long, newName: String) {
        viewModelScope.launch {
            calendarRepo.renameLocalCalendar(calendarId, newName)
        }
    }

    // Failure on a DataStore write is rare (disk full / IO error) but
    // currently silent. Logging via Timber.e makes it observable for
    // diagnosis if a user reports the wrong chip color after a save.
    // No UI surfacing yet; revisit if users actually hit it.
    fun setAccountAvatarColor(accountKey: String, argb: Int) {
        viewModelScope.launch {
            runCatching { userPreferences.setAccountAvatarColor(accountKey, argb) }
                .onFailure { Timber.e(it, "setAccountAvatarColor failed for key=%s", accountKey) }
        }
    }

    // For local calendars we also write CALENDAR_COLOR so exports and other
    // apps that read the provider see the new color; the override map stays
    // authoritative on read regardless. For synced calendars we skip the
    // provider write so the sync adapter cannot clobber it on next sync.
    // Provider write goes first so if the process dies between the two
    // writes the user-visible state ends up matching whichever write
    // succeeded last (override map wins on read; if only the provider
    // landed, the next setCalendarColorOverride attempt completes the pair).
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

    // Per-event override is app-local only: never touches CalendarContract,
    // so the sync adapter cannot clobber it and the override works even on
    // read-only synced calendars. Passing null removes the entry.
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
            // Block the main thread once at startup to read all persisted prefs.
            // Without this, the first composition paints with defaults and
            // recomposes once DataStore emits, causing a visible flash.
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
