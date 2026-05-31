/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.ui.permissions

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arishawke.asala.calendar.AsalaCalendarApplication
import com.arishawke.asala.calendar.R
import com.arishawke.asala.calendar.data.CalendarRepository
import com.arishawke.asala.calendar.data.StorageMode
import com.arishawke.asala.calendar.data.StorageModeSetup
import com.arishawke.asala.calendar.ui.settings.UserPreferences
import com.arishawke.asala.calendar.ui.settings.settingsDataStore

@Composable
fun CalendarPermissionGate(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val required = remember {
        arrayOf(
            Manifest.permission.READ_CALENDAR,
            Manifest.permission.WRITE_CALENDAR,
        )
    }
    val prefs = remember(context) {
        UserPreferences(context.applicationContext.settingsDataStore)
    }
    val prefsState by prefs.prefs.collectAsStateWithLifecycle(initialValue = null)

    var granted by remember {
        mutableStateOf(
            required.all { perm ->
                ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
            },
        )
    }
    var permissionDenied by remember { mutableStateOf(false) }
    // pendingMode runs post-permission setup once access is granted; held
    // while the spinner shows, cleared when setup completes.
    var pendingMode by remember { mutableStateOf<StorageMode?>(null) }
    var setupInFlight by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        val ok = required.all { result[it] == true }
        granted = ok
        permissionDenied = !ok
        if (!ok) pendingMode = null
    }

    // re-check on resume so revoking permission in system settings flips
    // back to the rationale screen; else the next provider read throws
    // SecurityException.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                granted = required.all { perm ->
                    ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(granted, pendingMode) {
        if (!granted) return@LaunchedEffect
        val mode = pendingMode
        if (mode != null) {
            setupInFlight = true
            runStorageSetup(context, mode)
            prefs.setStorageMode(mode)
            pendingMode = null
            setupInFlight = false
        }
        (context.applicationContext as AsalaCalendarApplication).onCalendarPermissionGranted()
    }

    val storedMode = prefsState?.storageMode
    val showOnboarding = !granted && storedMode == StorageMode.Unset

    when {
        // wait for the first prefs read, else a returning user flashes the
        // onboarding screen before their stored mode loads.
        prefsState == null -> LoadingScreen(modifier)
        setupInFlight -> LoadingScreen(modifier)
        granted -> content()
        showOnboarding -> StorageModeOnboarding(
            modifier = modifier,
            onModeChosen = { mode ->
                permissionDenied = false
                pendingMode = mode
                launcher.launch(required)
            },
            footer = {
                if (permissionDenied) {
                    Text(
                        text = stringResource(R.string.permission_rationale),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Start,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            },
        )
        else -> RationaleScreen(modifier, onGrant = {
            permissionDenied = false
            // returning user with a stored mode still needs permission.
            pendingMode = storedMode?.takeIf { it != StorageMode.Unset }
            launcher.launch(required)
        })
    }
}

@Composable
private fun LoadingScreen(modifier: Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(PaddingValues(24.dp)),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun RationaleScreen(modifier: Modifier, onGrant: () -> Unit) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.permission_title),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.permission_rationale),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 16.dp, bottom = 24.dp),
        )
        Button(onClick = onGrant) {
            Text(stringResource(R.string.action_grant_access))
        }
    }
}

// mode-driven hiding is computed on read by StorageModeFilter, not
// persisted here, so later toggles don't overwrite manual drawer choices.
private suspend fun runStorageSetup(context: android.content.Context, mode: StorageMode) {
    val repo = CalendarRepository(context.applicationContext.contentResolver)
    StorageModeSetup.ensureLocalCalendarIfNeeded(repo, mode)
}
