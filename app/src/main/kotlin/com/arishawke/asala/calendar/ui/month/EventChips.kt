/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.ui.month

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.arishawke.asala.calendar.R
import com.arishawke.asala.calendar.data.EventItem
import com.arishawke.asala.calendar.ui.components.EventChipCompact
import java.time.LocalDate

internal const val MaxEventChipsPerCell = 3

// Approximate vertical footprint of a single EventChipCompact (label
// height + vertical padding) and the "+N more" row. Used to derive how
// many chips fit before the +N row needs to take a slot. The previous
// fixed-count approach showed three chips and a +N row regardless of
// cell height; on dense days that pushed the +N row off the bottom of
// the cell and into the next week, making the overflow invisible.
private val ChipRowHeightApprox: Dp = 18.dp

@Composable
internal fun EventChips(
    date: LocalDate,
    events: List<EventItem>,
    onEventClick: ((eventId: Long, instanceMillis: Long) -> Unit)?,
    onOverflowClick: (() -> Unit)?,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val capacityByHeight = (maxHeight / ChipRowHeightApprox).toInt().coerceAtLeast(0)
        val capacity = capacityByHeight.coerceAtMost(MaxEventChipsPerCell)
        // When events exceed capacity, reserve one slot for the +N row so
        // the overflow indicator is always visible when there is overflow.
        val needsOverflow = events.size > capacity
        val shown = if (needsOverflow) {
            events.take((capacity - 1).coerceAtLeast(0))
        } else {
            events
        }
        val overflow = events.size - shown.size

        Column(modifier = Modifier.fillMaxWidth()) {
            shown.forEach { event ->
                EventChipCompact(
                    event = event,
                    onClick = onEventClick?.let { cb -> { cb(event.eventId, event.startMillis) } },
                )
            }
            if (overflow > 0) {
                val totalCount = events.size
                val dateLabel = date.format(rememberOverflowDateFormatter())
                val rowCd = pluralStringResource(R.plurals.cd_show_overflow, totalCount, totalCount, dateLabel)
                val isClickable = onOverflowClick != null
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (onOverflowClick != null) {
                                Modifier
                                    .clickable(onClick = onOverflowClick)
                                    .semantics {
                                        contentDescription = rowCd
                                        role = Role.Button
                                    }
                            } else {
                                Modifier
                            },
                        )
                        .padding(horizontal = 4.dp, vertical = 1.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = pluralStringResource(
                            R.plurals.event_overflow_more,
                            overflow,
                            overflow,
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isClickable) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                    if (isClickable) {
                        Spacer(modifier = Modifier.width(2.dp))
                        // cell budget; below M3's 18dp minimum is intentional.
                        // KeyboardArrowDown stands in for ExpandMore; the latter
                        // lives in material-icons-extended (~10MB APK bloat).
                        Icon(
                            imageVector = Icons.Filled.KeyboardArrowDown,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
            }
        }
    }
}
