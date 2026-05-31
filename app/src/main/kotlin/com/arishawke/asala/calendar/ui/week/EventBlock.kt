/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.ui.week

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.arishawke.asala.calendar.data.EventItem
import com.arishawke.asala.calendar.ui.components.EventChipBlock
import com.arishawke.asala.calendar.ui.theme.AsalaCalendarTheme
import com.arishawke.asala.calendar.ui.theme.Spacing
import com.arishawke.asala.calendar.ui.timeline.DayClippedEvent
import com.arishawke.asala.calendar.ui.timeline.HourHeight
import com.arishawke.asala.calendar.ui.timeline.MinEventHeight
import com.arishawke.asala.calendar.ui.timeline.segmentAnchorMillis
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

private const val PreviewEventDurationMin = 90L
private const val DraggingBackgroundAlpha = 0.55f

// low tint so the title wins over the fill; drag bumps to DraggingBackgroundAlpha.
private const val IdleBackgroundAlpha = 0.18f

@Suppress("LongParameterList")
@Composable
internal fun EventBlock(
    clipped: DayClippedEvent,
    zone: ZoneId,
    dayColumnWidthPx: Float,
    weekDayIndex: Int,
    weekDayCount: Int,
    modifier: Modifier = Modifier,
    showEndTime: Boolean = false,
    onClick: (() -> Unit)? = null,
    onReschedule: ((newStartMillis: Long) -> Unit)? = null,
) {
    val event = clipped.event
    val density = LocalDensity.current
    val hourHeightPx = with(density) { HourHeight.toPx() }
    val sizing = remember(clipped, hourHeightPx, density, zone) {
        clippedSizing(clipped, hourHeightPx, density, zone)
    }

    val drag = rememberRescheduleDragState(
        eventId = event.eventId,
        originalStartMillis = event.startMillis,
        zone = zone,
        hourHeightPx = hourHeightPx,
        columnWidthPx = dayColumnWidthPx,
        weekDayIndex = weekDayIndex,
        weekDayCount = weekDayCount,
        onTap = onClick,
        onReschedule = onReschedule,
    )
    val totalTopDp = with(density) { (sizing.topPx + drag.dragDeltaYPx).toDp() }
    val totalLeftDp = with(density) { drag.dragDeltaXPx.toDp() }

    val shape = continuationShape(clipped)
    val backgroundAlpha = if (drag.dragging) DraggingBackgroundAlpha else IdleBackgroundAlpha
    val draggingZ = if (drag.dragging) 2f else 0f

    EventChipBlock(
        event = event,
        shape = shape,
        heightDp = sizing.heightDp,
        zone = zone,
        backgroundAlpha = backgroundAlpha,
        showEndTime = showEndTime,
        displayEndMillis = clipped.displayEndMillis,
        segmentIndex = clipped.segmentIndex,
        segmentCount = clipped.segmentCount,
        anchorMillis = segmentAnchorMillis(clipped),
        modifier = modifier
            .offset(x = totalLeftDp, y = totalTopDp)
            .height(sizing.heightDp)
            .zIndex(draggingZ)
            .padding(horizontal = 1.dp, vertical = 1.dp)
            .then(drag.dragModifier)
            .then(drag.tapModifier),
    )
}

private data class EventBlockSizing(val topPx: Float, val heightPx: Float, val heightDp: androidx.compose.ui.unit.Dp)

private fun clippedSizing(
    clipped: DayClippedEvent,
    hourHeightPx: Float,
    density: androidx.compose.ui.unit.Density,
    zone: ZoneId,
): EventBlockSizing {
    val displayStart = Instant.ofEpochMilli(clipped.displayStartMillis).atZone(zone)
    val displayStartMin = displayStart.hour * 60 + displayStart.minute
    val displayDurationMin = ((clipped.displayEndMillis - clipped.displayStartMillis) / 60_000L)
        .toInt()
        .coerceAtLeast(15)
    val topPx = hourHeightPx * displayStartMin / 60f
    val minHeightPx = with(density) { MinEventHeight.toPx() }
    val heightPx = (hourHeightPx * displayDurationMin / 60f).coerceAtLeast(minHeightPx)
    val heightDp = with(density) { heightPx.toDp() }
    return EventBlockSizing(topPx = topPx, heightPx = heightPx, heightDp = heightDp)
}

private fun continuationShape(clipped: DayClippedEvent): RoundedCornerShape {
    val cornerRadius = Spacing.xs
    val topRadius = if (clipped.continuedFromPrev) 0.dp else cornerRadius
    val bottomRadius = if (clipped.continuedToNext) 0.dp else cornerRadius
    return RoundedCornerShape(
        topStart = topRadius,
        topEnd = topRadius,
        bottomStart = bottomRadius,
        bottomEnd = bottomRadius,
    )
}

@Preview(name = "EventBlock, light", widthDp = 120, heightDp = 240)
@Preview(name = "EventBlock, dark", widthDp = 120, heightDp = 240, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun EventBlockPreview() {
    val zone = ZoneId.of("UTC")
    val start = ZonedDateTime.of(LocalDate.of(2026, 5, 21), LocalTime.of(10, 0), zone)
        .toInstant()
        .toEpochMilli()
    val event = EventItem(
        instanceId = 1L,
        eventId = 1L,
        calendarId = 1L,
        title = "Design review",
        startMillis = start,
        endMillis = start + PreviewEventDurationMin * 60_000L,
        allDay = false,
        displayColor = 0xFF1A73E8.toInt(),
    )
    val clipped = DayClippedEvent(
        event = event,
        displayStartMillis = event.startMillis,
        displayEndMillis = event.endMillis,
        continuedFromPrev = false,
        continuedToNext = false,
        segmentIndex = 1,
        segmentCount = 1,
    )
    AsalaCalendarTheme(dynamicColor = false) {
        Box(modifier = Modifier.size(120.dp, 240.dp)) {
            EventBlock(
                clipped = clipped,
                zone = zone,
                dayColumnWidthPx = 120f,
                weekDayIndex = 0,
                weekDayCount = 1,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
