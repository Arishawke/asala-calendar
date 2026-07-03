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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
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
import com.arishawke.asala.calendar.openCreateEditorFromShare
import com.arishawke.asala.calendar.openEventDetail
import com.arishawke.asala.calendar.ui.accessibility.rememberAnimationsEnabled
import com.arishawke.asala.calendar.ui.header.HeaderDropdownPanel
import com.arishawke.asala.calendar.ui.header.ViewSwitcherMenu
import com.arishawke.asala.calendar.ui.month.CalendarDrawerContent
import com.arishawke.asala.calendar.ui.settings.ToolbarPosition
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
    // shared today source; refreshes on ACTION_DATE_CHANGED and ON_RESUME so
    // the header highlight doesn't stick on yesterday across midnight
    val today by application.todayProvider.today.collectAsStateWithLifecycle()
    var headerExpanded by remember { mutableStateOf(false) }
    // Year has no dropdown panel, so suppress the expand chevron
    val canExpandHeader = state.currentView != CalendarView.Year
    // collapse the panel on view switch
    LaunchedEffect(state.currentView) { headerExpanded = false }

    val notifPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { _ -> vm.refreshNotificationPermission() }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                vm.refreshNotificationPermission()
                // backstop for ACTION_DATE_CHANGED missed under doze across midnight
                application.todayProvider.refresh()
                activity?.consumePendingNotificationOpen()?.let { (eventId, instanceMillis) ->
                    vm.openEventDetail(eventId, instanceMillis)
                }
                activity?.consumePendingDateOpen()?.let { (date, view) ->
                    vm.requestJumpTo(date, view)
                }
                activity?.consumePendingSharedText()?.let { vm.openCreateEditorFromShare(it) }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // BackHandlers are LIFO (later wins); order so the header dropdown
    // collapses first, then drawer close, then popView
    BackHandler(enabled = previousView != null) { vm.popView() }
    BackHandler(enabled = drawerState.isOpen) { scope.launch { drawerState.close() } }
    BackHandler(enabled = headerExpanded) { headerExpanded = false }

    CompositionLocalProvider(LocalDragRevertSignal provides vm.dragRevertSignal) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            // gestures only while open, so a closed-drawer swipe can't steal
            // week/month navigation (scrim tap is gated by this flag in M3)
            gesturesEnabled = drawerState.isOpen,
            drawerContent = {
                CalendarDrawerContent(
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
                    localOnly = prefs.storageMode == StorageMode.LocalOnly,
                    syncOnly = prefs.storageMode == StorageMode.SyncOnly,
                    isOccasionCalendar = { id ->
                        id == prefs.birthdaysCalendarId || id == prefs.anniversariesCalendarId
                    },
                )
            },
        ) {
            val toolbarAtBottom = LocalToolbarPosition.current == ToolbarPosition.Bottom
            val panelIntrusion = remember { mutableIntStateOf(0) }
            LaunchedEffect(toolbarAtBottom) {
                if (!toolbarAtBottom) panelIntrusion.intValue = 0
            }
            // bottom bar clears the system nav; top keeps the status-bar default.
            // horizontal included to mirror TopAppBarDefaults, so a landscape
            // side nav bar (3-button) can't sit over the bar's action icons.
            val barInsets = if (toolbarAtBottom) {
                WindowInsets.systemBars.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)
            } else {
                TopAppBarDefaults.windowInsets
            }
            // bar + expandable header panel as one composable so both modes share
            // identical content, differing only in stacking order and slot.
            val barWithPanel: @Composable () -> Unit = {
                val bar = @Composable {
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
                            ViewSwitcherMenu(
                                currentView = state.currentView,
                                onSelectView = vm::selectView,
                            )
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
                        windowInsets = barInsets,
                    )
                }
                val panel = @Composable {
                    HeaderPanelReveal(
                        visible = headerExpanded && canExpandHeader,
                        toolbarAtBottom = toolbarAtBottom,
                        animationsEnabled = animationsEnabled,
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
                            // swiping the mini-month keeps the panel open and
                            // moves the view to the first of that month.
                            onNavigateMonth = { ym ->
                                vm.requestJumpTo(ym.atDay(1), state.currentView)
                            },
                        )
                    }
                }
                Column {
                    // bottom mode stacks the panel above the bar so it grows
                    // upward from it; top keeps the panel below the bar.
                    if (toolbarAtBottom) {
                        // measured per animation frame; timeline scrolls follow
                        // the delta so rows slide up with the panel edge
                        Box(Modifier.onSizeChanged { panelIntrusion.intValue = it.height }) {
                            panel()
                        }
                        bar()
                    } else {
                        bar()
                        panel()
                    }
                }
            }
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                floatingActionButton = {
                    CreateEventFab(
                        visible = !(toolbarAtBottom && headerExpanded),
                        animationsEnabled = animationsEnabled,
                        onClick = { vm.openCreateEditor() },
                    )
                },
                topBar = { if (!toolbarAtBottom) barWithPanel() },
                bottomBar = { if (toolbarAtBottom) barWithPanel() },
            ) { innerPadding ->
                CompositionLocalProvider(LocalBottomPanelIntrusion provides panelIntrusion) {
                    CalendarViewSwitcher(
                        vm = vm,
                        currentView = state.currentView,
                        prefs = prefs,
                        animationsEnabled = animationsEnabled,
                        onTitleChange = { title = it },
                        // consume alongside padding (M3 Scaffold contract) so a future
                        // in-screen insets read can't double-apply what's reserved here.
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .consumeWindowInsets(innerPadding),
                    )
                }
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

// mini-month cap: the largest fraction of the screen the header panel may
// occupy before it scrolls internally (landscape viewports are ~360-410dp).
private const val PanelMaxScreenFraction = 0.6f

// expandable header panel wrapper. reveal grows from the bar's edge: top mode
// downward (title row first), bottom mode upward; the default Bottom alignment
// showed the grid's last row first in top mode. 180ms (down from the ~250ms
// default): the chip strip's small height delta felt sluggish at the default.
@Composable
private fun HeaderPanelReveal(
    visible: Boolean,
    toolbarAtBottom: Boolean,
    animationsEnabled: Boolean,
    content: @Composable () -> Unit,
) {
    val revealEdge = if (toolbarAtBottom) Alignment.Bottom else Alignment.Top
    AnimatedVisibility(
        visible = visible,
        enter = if (animationsEnabled) {
            expandVertically(animationSpec = tween(durationMillis = 180), expandFrom = revealEdge) +
                fadeIn(animationSpec = tween(durationMillis = 180))
        } else {
            EnterTransition.None
        },
        exit = if (animationsEnabled) {
            shrinkVertically(animationSpec = tween(durationMillis = 180), shrinkTowards = revealEdge) +
                fadeOut(animationSpec = tween(durationMillis = 180))
        } else {
            ExitTransition.None
        },
    ) {
        // cap: the mini month's intrinsic height (~350dp) can exceed a
        // landscape viewport; scroll inside the cap rather than squeezing
        // the calendar content to nothing.
        val windowHeightPx = LocalWindowInfo.current.containerSize.height
        val maxPanelHeight = with(LocalDensity.current) {
            (windowHeightPx * PanelMaxScreenFraction).toDp()
        }
        Box(
            modifier = Modifier
                .heightIn(max = maxPanelHeight)
                .verticalScroll(rememberScrollState()),
        ) {
            content()
        }
    }
}

// Scaffold offsets the FAB by the full bottomBar slot height, so in bottom
// mode an expanded header panel would launch it into the middle of the
// content; callers hide it while the panel is open.
@Composable
private fun CreateEventFab(visible: Boolean, animationsEnabled: Boolean, onClick: () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = if (animationsEnabled) fadeIn() else EnterTransition.None,
        exit = if (animationsEnabled) fadeOut() else ExitTransition.None,
    ) {
        FloatingActionButton(onClick = onClick) {
            Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.fab_new_event))
        }
    }
}

// title row toggles the header dropdown; chevron hidden (enabled=false)
// for views with no panel content
@Composable
private fun HeaderTitle(
    title: String,
    expanded: Boolean,
    enabled: Boolean,
    animationsEnabled: Boolean,
    onClick: () -> Unit,
) {
    // chevron points toward where the panel opens: down when the bar is on top,
    // up when it's at the bottom. it flips on expand, so xor the two.
    val atBottom = LocalToolbarPosition.current == ToolbarPosition.Bottom
    val rotation by animateFloatAsState(
        targetValue = if (expanded != atBottom) 180f else 0f,
        animationSpec = if (animationsEnabled) spring() else snap(),
        label = "header-chevron-rotation",
    )
    Row(
        modifier = if (enabled) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        // announce view / date changes politely so TalkBack follows navigation.
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
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
