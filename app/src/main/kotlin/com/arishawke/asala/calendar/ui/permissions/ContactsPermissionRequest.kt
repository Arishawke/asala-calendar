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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import com.arishawke.asala.calendar.R

// owns the READ_CONTACTS launcher + rationale for the "contact birthdays and
// anniversaries" toggle; returns a trigger the toggle's onChange(true) calls.
// on grant, onGranted provisions the calendars. denial (in-dialog or system)
// is non-destructive by design: nothing was provisioned yet, and the toggle
// reads its checked state from prefs, so it stays off on its own. an
// external revoke after provisioning is deliberately not rechecked here: the
// sync layer no-ops on a failed contacts read, so the calendars just go
// stale until re-granted, per spec, instead of being deleted.
@Composable
fun rememberContactsPermissionRequest(onGranted: () -> Unit): () -> Unit {
    val context = LocalContext.current
    fun hasPermission() = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.READ_CONTACTS,
    ) == PackageManager.PERMISSION_GRANTED

    var showRationale by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { result -> if (result) onGranted() }

    if (showRationale) {
        ContactsRationaleDialog(
            onContinue = {
                showRationale = false
                launcher.launch(Manifest.permission.READ_CONTACTS)
            },
            onNotNow = { showRationale = false },
        )
    }

    return {
        if (hasPermission()) {
            onGranted()
        } else {
            showRationale = true
        }
    }
}

@Composable
private fun ContactsRationaleDialog(onContinue: () -> Unit, onNotNow: () -> Unit) {
    AlertDialog(
        onDismissRequest = onNotNow,
        title = { Text(stringResource(R.string.contacts_rationale_title)) },
        text = { Text(stringResource(R.string.contacts_rationale_body)) },
        confirmButton = {
            TextButton(onClick = onContinue) {
                Text(stringResource(R.string.contacts_rationale_continue))
            }
        },
        dismissButton = {
            TextButton(onClick = onNotNow) {
                Text(stringResource(R.string.contacts_rationale_not_now))
            }
        },
    )
}
