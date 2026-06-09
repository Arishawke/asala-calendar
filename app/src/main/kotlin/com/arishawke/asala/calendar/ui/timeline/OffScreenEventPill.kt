/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.ui.timeline

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.arishawke.asala.calendar.R

@Composable
internal fun OffScreenEventPill(
    edge: RevealEdge,
    timeText: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = stringResource(R.string.reveal_new_event_pill, timeText)
    val chevron = if (edge == RevealEdge.Above) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown
    Surface(
        shape = RoundedCornerShape(percent = 50),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        tonalElevation = 3.dp,
        shadowElevation = 3.dp,
        modifier = modifier
            .heightIn(min = 48.dp) // Android touch target (frontend.md sec 17)
            .clickable(onClickLabel = label, role = Role.Button, onClick = onClick),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp),
        ) {
            if (edge == RevealEdge.Above) {
                Icon(chevron, contentDescription = null)
                Spacer(Modifier.width(8.dp))
            }
            // text carries the meaning so it never rests on the chevron alone.
            Text(text = label, style = MaterialTheme.typography.labelLarge)
            if (edge == RevealEdge.Below) {
                Spacer(Modifier.width(8.dp))
                Icon(chevron, contentDescription = null)
            }
        }
    }
}
