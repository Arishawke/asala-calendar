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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.arishawke.asala.calendar.R
import com.arishawke.asala.calendar.ui.theme.Spacing
import com.arishawke.asala.calendar.ui.theme.WcagContrast
import com.github.skydoves.colorpicker.compose.BrightnessSlider
import com.github.skydoves.colorpicker.compose.HsvColorPicker
import com.github.skydoves.colorpicker.compose.rememberColorPickerController

// WCAG AA floor for graphical objects; below it the swatch barely separates
// from the app background, so the color reads as no distinct calendar identity.
private const val MIN_SURFACE_CONTRAST = 3.0

// HSV + hex picker. forces opaque colors so event chips stay legible.
@Composable
@Suppress("LongMethod")
internal fun CustomColorPickerDialog(initialArgb: Int, onConfirm: (Int) -> Unit, onDismiss: () -> Unit) {
    val controller = rememberColorPickerController()
    val opaqueInitial = initialArgb or HexColor.OPAQUE_ALPHA
    var currentArgb by remember { mutableIntStateOf(opaqueInitial) }
    var hexText by remember { mutableStateOf(HexColor.format(opaqueInitial)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.swatch_custom)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                HsvColorPicker(
                    modifier = Modifier.fillMaxWidth().height(200.dp),
                    controller = controller,
                    initialColor = Color(opaqueInitial),
                    onColorChanged = { envelope ->
                        val argb = envelope.color.toArgb() or HexColor.OPAQUE_ALPHA
                        currentArgb = argb
                        // only a wheel/slider drag overwrites the field, so a
                        // hex-driven change keeps the user's in-progress text.
                        if (envelope.fromUser) hexText = HexColor.format(argb)
                    },
                )
                BrightnessSlider(
                    modifier = Modifier.fillMaxWidth().height(28.dp),
                    controller = controller,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier.size(36.dp).clip(CircleShape).background(Color(currentArgb)),
                        contentAlignment = Alignment.Center,
                    ) {
                        // sample text in the contrast-chosen foreground previews legibility
                        Text(
                            text = stringResource(R.string.custom_color_sample),
                            color = Color(WcagContrast.onColor(currentArgb)),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = hexText,
                        onValueChange = { input ->
                            hexText = input
                            HexColor.parse(input)?.let { parsed ->
                                currentArgb = parsed
                                controller.selectByColor(Color(parsed), fromUser = false)
                            }
                        },
                        singleLine = true,
                        label = { Text(stringResource(R.string.custom_color_hex)) },
                    )
                }
                // warn when the color barely separates from the app background
                if (WcagContrast.ratio(
                        currentArgb,
                        MaterialTheme.colorScheme.surface.toArgb(),
                    ) < MIN_SURFACE_CONTRAST
                ) {
                    Text(
                        text = stringResource(R.string.custom_color_low_contrast),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(currentArgb) }) {
                Text(stringResource(R.string.action_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}
