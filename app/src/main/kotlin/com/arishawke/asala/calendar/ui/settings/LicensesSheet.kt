/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import com.arishawke.asala.calendar.R
import com.arishawke.asala.calendar.ui.theme.Spacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// name must match the copyNoticeAsset task in app/build.gradle.kts that
// stages the repo-root NOTICE file into assets.
private const val NOTICE_ASSET_NAME = "NOTICE.txt"

// bundled asset, not a remote fetch, so the notices read with no connection.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LicensesSheet(onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    var noticeText by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        noticeText = withContext(Dispatchers.IO) {
            context.assets.open(NOTICE_ASSET_NAME).bufferedReader().use { it.readText() }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Text(
            text = stringResource(R.string.settings_about_licenses),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md),
        )
        val text = noticeText
        if (text == null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Spacing.xl),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }
        } else {
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
            )
        }
    }
}
