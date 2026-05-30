/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.ui.permissions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.arishawke.asala.calendar.R
import com.arishawke.asala.calendar.data.StorageMode

@Composable
fun StorageModeOnboarding(
    onModeChosen: (StorageMode) -> Unit,
    modifier: Modifier = Modifier,
    footer: @Composable (() -> Unit)? = null,
) {
    var selectedMode by remember { mutableStateOf<StorageMode?>(null) }

    // Surface anchors LocalContentColor to the theme's onBackground so the
    // title and any plain-styled Text inherit the right tone in dark mode.
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.storage_onboarding_title),
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = stringResource(R.string.storage_onboarding_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(4.dp))

            ModeCard(
                mode = StorageMode.LocalOnly,
                titleRes = R.string.storage_mode_local_only,
                descRes = R.string.storage_mode_local_only_desc,
                selected = selectedMode == StorageMode.LocalOnly,
                onSelect = { selectedMode = it },
            )
            ModeCard(
                mode = StorageMode.SyncOnly,
                titleRes = R.string.storage_mode_sync_only,
                descRes = R.string.storage_mode_sync_only_desc,
                selected = selectedMode == StorageMode.SyncOnly,
                onSelect = { selectedMode = it },
            )
            ModeCard(
                mode = StorageMode.Hybrid,
                titleRes = R.string.storage_mode_hybrid,
                descRes = R.string.storage_mode_hybrid_desc,
                selected = selectedMode == StorageMode.Hybrid,
                onSelect = { selectedMode = it },
            )

            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = {
                    val mode = selectedMode ?: return@Button
                    onModeChosen(mode)
                },
                enabled = selectedMode != null,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.action_continue))
            }

            footer?.invoke()
        }
    }
}

@Composable
private fun ModeCard(
    mode: StorageMode,
    titleRes: Int,
    descRes: Int,
    selected: Boolean,
    onSelect: (StorageMode) -> Unit,
) {
    val containerColor = if (selected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect(mode) },
        colors = CardDefaults.cardColors(containerColor = containerColor),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(titleRes),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(descRes),
                style = MaterialTheme.typography.bodyMedium,
                color = if (selected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}
