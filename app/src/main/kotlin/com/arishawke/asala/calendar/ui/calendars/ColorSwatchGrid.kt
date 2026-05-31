/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.ui.calendars

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.arishawke.asala.calendar.R
import com.arishawke.asala.calendar.ui.theme.PaletteId
import com.arishawke.asala.calendar.ui.theme.Spacing

// shared swatch grid for the calendar / recolor / per-event color rows.
// if the saved color isn't in the active palette (palette switched after
// saving), a "Custom" pip shows the saved hex so the picker stays honest.
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ColorSwatchGrid(
    palette: PaletteId,
    selectedArgb: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val swatches = palette.swatches
    val selectedInPalette = swatches.any { it.toArgb() == selectedArgb }
    var showCustom by remember { mutableStateOf(false) }
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        swatches.forEach { color ->
            Swatch(
                color = color,
                argb = color.toArgb(),
                selected = color.toArgb() == selectedArgb,
                onSelect = onSelect,
            )
        }
        if (!selectedInPalette) {
            CustomSwatch(argb = selectedArgb, onSelect = onSelect)
        }
        CustomColorButton(onClick = { showCustom = true })
    }
    if (showCustom) {
        CustomColorPickerDialog(
            initialArgb = selectedArgb,
            onConfirm = { argb ->
                showCustom = false
                onSelect(argb)
            },
            onDismiss = { showCustom = false },
        )
    }
}

@Composable
private fun Swatch(color: Color, argb: Int, selected: Boolean, onSelect: (Int) -> Unit) {
    // visual 28dp, tap region 48dp via minimumInteractiveComponentSize to
    // meet the touch-target floor without enlarging the swatch.
    Box(
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .size(28.dp)
            .clip(CircleShape)
            .background(color)
            .then(
                if (selected) {
                    Modifier.border(
                        width = 3.dp,
                        color = MaterialTheme.colorScheme.onSurface,
                        shape = CircleShape,
                    )
                } else {
                    Modifier
                },
            )
            .clickable { onSelect(argb) },
    )
}

// "Custom" pip for a saved color outside the active palette; always
// ringed since the saved hex is the current value by definition.
@Composable
private fun CustomSwatch(argb: Int, onSelect: (Int) -> Unit) {
    val label = stringResource(R.string.swatch_custom)
    val hex = "#%06X".format(argb and 0xFFFFFF)
    Box(
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .size(28.dp)
            .clip(CircleShape)
            .background(Color(argb))
            .border(
                width = 3.dp,
                color = MaterialTheme.colorScheme.onSurface,
                shape = CircleShape,
            )
            .clickable { onSelect(argb) }
            .semantics { contentDescription = "$label $hex" },
    )
}

@Composable
private fun CustomColorButton(onClick: () -> Unit) {
    val label = stringResource(R.string.cd_pick_custom_color)
    Box(
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .size(28.dp)
            .clip(CircleShape)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline,
                shape = CircleShape,
            )
            .clickable(onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "+",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
