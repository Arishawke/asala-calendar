/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.ui.calendars

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.arishawke.asala.calendar.R
import com.arishawke.asala.calendar.ui.theme.PaletteId

// Small dialog hosting the active palette's swatch grid. Used for the
// account avatar long-press recolor, the per-calendar "Change color"
// menu entry, and the per-event "Color" row in the event editor.
// Tapping a swatch fires onPick and dismisses; there is no separate
// save action. If onReset is non-null, a "Reset to calendar color"
// TextButton appears in the dismiss slot (used by the event editor;
// drawer call sites pass null).
@Composable
internal fun RecolorDialog(
    title: String,
    palette: PaletteId,
    currentArgb: Int,
    onPick: (Int) -> Unit,
    onDismiss: () -> Unit,
    onReset: (() -> Unit)? = null,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            ColorSwatchGrid(
                palette = palette,
                selectedArgb = currentArgb,
                onSelect = { argb ->
                    onPick(argb)
                    onDismiss()
                },
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_close))
            }
        },
        dismissButton = if (onReset != null) {
            {
                TextButton(
                    onClick = {
                        onReset()
                        onDismiss()
                    },
                ) {
                    Text(stringResource(R.string.action_reset_to_calendar_color))
                }
            }
        } else {
            null
        },
    )
}
