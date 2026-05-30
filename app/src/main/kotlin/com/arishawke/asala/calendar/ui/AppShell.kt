/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.ui

import android.annotation.SuppressLint
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.arishawke.asala.calendar.AppViewModel
import com.arishawke.asala.calendar.AsalaCalendarApplication
import com.arishawke.asala.calendar.CalendarView
import com.arishawke.asala.calendar.MainActivity
import com.arishawke.asala.calendar.R
import com.arishawke.asala.calendar.data.StorageMode
import com.arishawke.asala.calendar.openCreateEditor
import com.arishawke.asala.calendar.openEventDetail
import com.arishawke.asala.calendar.ui.accessibility.rememberAnimationsEnabled
import com.arishawke.asala.calendar.ui.header.HeaderDropdownPanel
import com.arishawke.asala.calendar.ui.month.CalendarDrawerContent
import com.arishawke.asala.calendar.ui.theme.Spacing
import kotlinx.coroutines.launch

@SuppressLint("InlinedApi") // POST_NOTIFICATIONS is a compile-time constant; safe to inline on pre-33 (launcher no-ops)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppShell(vm: AppViewModel) {
    val context = LocalContext.current
    val state by vm.uiState.collectAsStateWithLifecycle()
    val prefs by vm.prefs.collectAsStateWithLifecycle()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var title by rememberSaveable { mutableStateOf("") }
    var showCreateCalendar by remember { mutableStateOf(false) }
    val animationsEnabled by rememberAnimationsEnabled()
    val previousView by vm.previousView.collectAsStateWithLifecycle()

    val activity = (context as? MainActivity)
    val application = context.applicationContext as AsalaCalendarApplication
    val drawerHiddenAccountKeys by vm.drawerHiddenAccountKeysFlow.collectAsStateWithLifecycle()
    val viewedMonth by vm.viewedMonth.collectAsStateWithLifecycle()
    // Shared today source: refreshes on ACTION_DATE_CHANGED and on the
    // ON_RESUME hook below. Without this, the header dropdown's today
    // highlight stays on yesterday when the app stays composed across
    // midnight.
    val today by application.todayProvider.today.collectAsStateWithLifecycle()
    var headerExpanded by remember { mutableStateOf(false) }
    val canExpandHeader = state.currentView != CalendarView.Tasks
    // Collapse the panel whenever the user navigates to a different view so
    // the dropdown does not stay open across view switches.
    LaunchedEffect(state.currentView) { headerExpanded = false }

    val notifPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { _ -> vm.refreshNotificationPermission() }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                vm.refreshNotificationPermission()
                // Belt-and-braces for missed ACTION_DATE_CHANGED broadcasts
                // (e.g., process napped through midnight under doze).
                application.todayProvider.refresh()
                activity?.consumePendingNotificationOpen()?.let { (eventId, instanceMillis) ->
                    vm.openEventDetail(eventId, instanceMillis)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Composed before the drawer / settings handlers so those take priority
    // when their states are also active (LIFO: later handler wins). Header
    // dropdown's BackHandler is added last so a tap-back collapses the
    // panel before any other action runs.
    BackHandler(enabled = previousView != null) { vm.popView() }
    BackHandler(enabled = drawerState.isOpen) { scope.launch { drawerState.close() } }
    BackHandler(enabled = headerExpanded) { headerExpanded = false }

    CompositionLocalProvider(LocalDragRevertSignal provides vm.dragRevertSignal) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            // Only enable gestures while the drawer is open. Closed: swipes are
            // blocked so they cannot steal week-to-week or month-to-month
            // navigation. Open: scrim tap (gated by this flag in Material 3)
            // and swipe-to-close work as expected.
            gesturesEnabled = drawerState.isOpen,
            drawerContent = {
                CalendarDrawerContent(
                    currentView = state.currentView,
                    onSelectView = { view ->
                        vm.selectView(view)
                        scope.launch { drawerState.close() }
                    },
                    calendars = state.calendars,
                    hiddenCalendarIds = state.hiddenCalendarIds,
                    drawerHiddenAccountKeys = drawerHiddenAccountKeys,
                    collapsedAccounts = state.collapsedAccounts,
                    avatarOverrideFor = { key -> prefs.accountAvatarColors[key] },
                    onToggle = vm::toggleCalendarVisibility,
                    onToggleAccount = vm::toggleAccountCollapsed,
                    onSettingsClick = {
                        scope.launch { drawerState.close() }
                        vm.openSettings()
                    },
                    onCreateCalendar = {
                        scope.launch { drawerState.close() }
                        showCreateCalendar = true
                    },
                    onDeleteCalendar = { id -> vm.deleteLocalCalendar(id) },
                    onRenameCalendar = { id, name -> vm.renameLocalCalendar(id, name) },
                    onHideAccountFromDrawer = { key -> vm.hideAccountFromDrawer(key) },
                    onRecolorAccount = { key, argb -> vm.setAccountAvatarColor(key, argb) },
                    onRecolorCalendar = { id, argb -> vm.setCalendarColorOverride(id, argb) },
                    palette = prefs.paletteId,
                    tasksEnabled = prefs.tasksEnabled,
                    localOnly = prefs.storageMode == StorageMode.LocalOnly,
                    syncOnly = prefs.storageMode == StorageMode.SyncOnly,
                )
            },
        ) {
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                floatingActionButton = {
                    FloatingActionButton(onClick = { vm.openCreateEditor() }) {
                        Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.fab_new_event))
                    }
                },
                topBar = {
                    Column {
                        TopAppBar(
                            title = {
                                HeaderTitle(
                                    title = title,
                                    expanded = headerExpanded,
                                    enabled = canExpandHeader,
                                    animationsEnabled = animationsEnabled,
                                    onClick = { headerExpanded = !headerExpanded },
                                )
                            },
                            navigationIcon = {
                                IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                    Icon(
                                        Icons.Filled.Menu,
                                        contentDescription = stringResource(R.string.cd_open_calendars),
                                    )
                                }
                            },
                            actions = {
                                IconButton(onClick = { vm.openSearch() }) {
                                    Icon(
                                        Icons.Filled.Search,
                                        contentDescription = stringResource(R.string.cd_search),
                                    )
                                }
                                IconButton(onClick = { vm.jumpToToday() }) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_today),
                                        contentDescription = stringResource(R.string.cd_jump_to_today),
                                    )
                                }
                            },
                        )
                        AnimatedVisibility(
                            visible = headerExpanded && canExpandHeader,
                            // 180ms tween (down from the default ~250ms): the
                            // chip strip's small height delta felt sluggish
                            // at the default duration since the per-pixel
                            // speed is much lower than the mini-month panel
                            // which expands a much larger range.
                            enter = if (animationsEnabled) {
                                expandVertically(animationSpec = tween(durationMillis = 180)) +
                                    fadeIn(animationSpec = tween(durationMillis = 180))
                            } else {
                                EnterTransition.None
                            },
                            exit = if (animationsEnabled) {
                                shrinkVertically(animationSpec = tween(durationMillis = 180)) +
                                    fadeOut(animationSpec = tween(durationMillis = 180))
                            } else {
                                ExitTransition.None
                            },
                        ) {
                            HeaderDropdownPanel(
                                currentView = state.currentView,
                                viewedMonth = viewedMonth,
                                today = today,
                                firstDayOfWeekOverride = prefs.weekStartsOn,
                                hiddenCalendarIdsFlow = vm.hiddenCalendarIdsFlow,
                                calendarColorOverridesFlow = vm.calendarColorOverridesFlow,
                                eventColorOverridesFlow = vm.eventColorOverridesFlow,
                                onSelectMonth = { ym ->
                                    headerExpanded = false
                                    vm.requestJumpTo(ym.atDay(1), CalendarView.Month)
                                },
                                onSelectDate = { date ->
                                    headerExpanded = false
                                    vm.requestJumpTo(date, state.currentView)
                                },
                            )
                        }
                    }
                },
            ) { innerPadding ->
                CalendarViewSwitcher(
                    vm = vm,
                    currentView = state.currentView,
                    prefs = prefs,
                    animationsEnabled = animationsEnabled,
                    onTitleChange = { title = it },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                )
            }
        }

        AppOverlays(
            vm = vm,
            showCreateCalendar = showCreateCalendar,
            onDismissCreateCalendar = { showCreateCalendar = false },
            notifPermissionLauncher = notifPermissionLauncher,
        )
    }
}

// Top app bar title: text + chevron. Tapping anywhere on the row toggles
// the header dropdown panel below. The chevron rotates 180deg when the
// panel is open. Disabled (chevron hidden) for views with no panel
// content (Tasks today).
@Composable
private fun HeaderTitle(
    title: String,
    expanded: Boolean,
    enabled: Boolean,
    animationsEnabled: Boolean,
    onClick: () -> Unit,
) {
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = if (animationsEnabled) spring() else snap(),
        label = "header-chevron-rotation",
    )
    Row(
        modifier = if (enabled) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Text(text = title, style = MaterialTheme.typography.titleLarge)
        if (enabled) {
            Icon(
                imageVector = Icons.Filled.KeyboardArrowDown,
                contentDescription = stringResource(
                    if (expanded) R.string.cd_header_collapse else R.string.cd_header_expand,
                ),
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.rotate(rotation),
            )
        }
    }
}

@Composable
internal fun ScopedViewModelStore(content: @Composable () -> Unit) {
    val owner = remember {
        object : ViewModelStoreOwner {
            override val viewModelStore = ViewModelStore()
        }
    }
    DisposableEffect(owner) {
        onDispose { owner.viewModelStore.clear() }
    }
    CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
        content()
    }
}
