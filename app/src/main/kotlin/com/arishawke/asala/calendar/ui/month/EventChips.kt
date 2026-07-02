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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.arishawke.asala.calendar.R
import com.arishawke.asala.calendar.data.EventItem
import com.arishawke.asala.calendar.ui.components.ChipVerticalPadding
import com.arishawke.asala.calendar.ui.components.EventChipCompact
import java.time.LocalDate

internal const val MaxEventChipsPerCell = 3

// pure so the +N math is unit-testable without a Compose runtime: how many
// chip rows fit in maxHeightDp given each row's real rendered height.
internal fun chipCapacity(maxHeightDp: Float, chipRowHeightDp: Float, maxChips: Int): Int =
    (maxHeightDp / chipRowHeightDp).toInt().coerceIn(0, maxChips)

// isolated so the toDp() fontScale math is exercisable from a JVM test.
internal fun Density.chipRowHeight(lineHeight: TextUnit): Dp = lineHeight.toDp() + ChipVerticalPadding * 2

@Composable
internal fun EventChips(
    date: LocalDate,
    events: List<EventItem>,
    onEventClick: ((eventId: Long, instanceMillis: Long) -> Unit)?,
    onOverflowClick: (() -> Unit)?,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val density = LocalDensity.current
        val chipRowHeight = density.chipRowHeight(MaterialTheme.typography.bodySmall.lineHeight)
        val capacity = chipCapacity(maxHeight.value, chipRowHeight.value, MaxEventChipsPerCell)
        // reserve a slot for the +N row so overflow stays visible
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
                        // below M3's 18dp min on purpose (cell budget); ExpandMore
                        // avoided to skip material-icons-extended (~10MB)
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
