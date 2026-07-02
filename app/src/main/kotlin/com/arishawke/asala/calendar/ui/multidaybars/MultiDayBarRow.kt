/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.ui.multidaybars

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.arishawke.asala.calendar.data.OccasionKind
import com.arishawke.asala.calendar.ui.components.OccasionLeadingIcon
import com.arishawke.asala.calendar.ui.components.occasionDisplayTitle
import com.arishawke.asala.calendar.ui.theme.WcagContrast

private const val DaysPerWeek = 7
private val BarVerticalGap = 2.dp
private val NaturalCorner = 6.dp
private val CutCorner = 0.dp

// multi-day all-day bars for one week; lanes stack, bars offset() across the days they span.
// corners rounded on natural ends, square on week-boundary cuts.
// LongMethod: shape + color + icon all produce one Box per segment; splitting just moves the state around.
@Composable
@Suppress("LongMethod")
fun MultiDayBarRow(
    segments: List<WeekSegment>,
    rowWidth: Dp,
    maxLanes: Int,
    modifier: Modifier = Modifier,
    onSegmentClick: ((eventId: Long) -> Unit)? = null,
) {
    if (segments.isEmpty()) return
    val cellWidth = rowWidth / DaysPerWeek
    val barHeight = with(LocalDensity.current) { MaterialTheme.typography.labelSmall.lineHeight.toDp() }
    val laneSpan = barHeight + BarVerticalGap
    val visibleLanes = (segments.maxOfOrNull { it.lane }?.plus(1) ?: 0).coerceAtMost(maxLanes)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(laneSpan * visibleLanes),
    ) {
        segments.forEach { s ->
            if (s.lane >= maxLanes) return@forEach
            // keyed so slot identity follows the segment when a fling shifts the
            // list; positional slots would misalign every remember below.
            key(s.eventId, s.startCol) {
                val shape = remember(s.isContinuedLeft, s.isContinuedRight) {
                    RoundedCornerShape(
                        topStart = if (s.isContinuedLeft) CutCorner else NaturalCorner,
                        bottomStart = if (s.isContinuedLeft) CutCorner else NaturalCorner,
                        topEnd = if (s.isContinuedRight) CutCorner else NaturalCorner,
                        bottomEnd = if (s.isContinuedRight) CutCorner else NaturalCorner,
                    )
                }
                val bg = Color(s.color)
                val fg = Color(WcagContrast.onColor(s.color))
                Box(
                    modifier = Modifier
                        .offset(
                            x = cellWidth * s.startCol,
                            y = laneSpan * s.lane,
                        )
                        .width(cellWidth * (s.endCol - s.startCol + 1))
                        .height(barHeight)
                        .padding(horizontal = 1.dp)
                        .clip(shape)
                        .background(bg)
                        .then(
                            // expose as a button + merge the title so TalkBack reads
                            // and opens the event, matching the timed EventBlock fix.
                            if (onSegmentClick != null) {
                                Modifier
                                    .semantics(mergeDescendants = true) { role = Role.Button }
                                    .clickable { onSegmentClick(s.eventId) }
                            } else {
                                Modifier
                            },
                        ),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    // title repeats per segment so a continuation row reads on its own
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (s.occasion != OccasionKind.None) {
                            OccasionLeadingIcon(kind = s.occasion, size = 10.dp, tint = fg)
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                        Text(
                            text = occasionDisplayTitle(s),
                            style = MaterialTheme.typography.labelSmall.copy(color = fg),
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}
