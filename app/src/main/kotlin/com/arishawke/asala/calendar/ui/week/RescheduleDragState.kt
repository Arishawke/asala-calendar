/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.ui.week

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import com.arishawke.asala.calendar.ui.LocalDragRevertSignal
import com.arishawke.asala.calendar.ui.timeline.applyDayAndMinuteDelta
import com.arishawke.asala.calendar.ui.timeline.clampDayDelta
import com.arishawke.asala.calendar.ui.timeline.pxToDayDelta
import com.arishawke.asala.calendar.ui.timeline.pxToMinutes
import com.arishawke.asala.calendar.ui.timeline.snapToGrid
import java.time.ZoneId

// In-flight vertical + horizontal drag offset for a single EventBlock,
// plus a pointerInput modifier that emits onReschedule with the new
// start millis (snapped to 15 minutes vertically and to the day column
// horizontally) when the gesture ends. The remember key on
// event.startMillis resets the offsets atomically when save lands.
// Horizontal shift is clamped to the visible week so drag never
// silently scrolls into the adjacent week.
internal class RescheduleDragState(
    val dragModifier: Modifier,
    val tapModifier: Modifier,
    val dragDeltaXPx: Float,
    val dragDeltaYPx: Float,
    val dragging: Boolean,
)

@Suppress("LongParameterList")
@Composable
internal fun rememberRescheduleDragState(
    eventId: Long,
    originalStartMillis: Long,
    zone: ZoneId,
    hourHeightPx: Float,
    columnWidthPx: Float,
    weekDayIndex: Int,
    weekDayCount: Int,
    onTap: (() -> Unit)?,
    onReschedule: ((newStartMillis: Long) -> Unit)?,
): RescheduleDragState {
    var dragDeltaXPx by remember(eventId, originalStartMillis) { mutableFloatStateOf(0f) }
    var dragDeltaYPx by remember(eventId, originalStartMillis) { mutableFloatStateOf(0f) }
    var dragging by remember(eventId, originalStartMillis) { mutableStateOf(false) }

    val revertSignal = LocalDragRevertSignal.current
    LaunchedEffect(revertSignal, eventId, originalStartMillis) {
        revertSignal?.collect { revertedId ->
            if (revertedId == eventId) {
                dragDeltaXPx = 0f
                dragDeltaYPx = 0f
            }
        }
    }

    // Pass a non-null onLongPress (empty) so detectTapGestures actually
    // honors the platform long-press timeout. Without it the tap detector
    // sets the timeout to MAX_VALUE / 2 and fires onTap on release
    // regardless of how long the press was held.
    val tapModifier = if (onTap == null) {
        Modifier
    } else {
        Modifier.pointerInput(eventId, originalStartMillis) {
            detectTapGestures(
                onLongPress = {},
                onTap = { onTap() },
            )
        }
    }
    val dragModifier = if (onReschedule == null) {
        Modifier
    } else {
        Modifier.pointerInput(eventId, originalStartMillis, columnWidthPx, weekDayIndex, weekDayCount) {
            detectDragGesturesAfterLongPress(
                onDragStart = {
                    dragging = true
                    dragDeltaXPx = 0f
                    dragDeltaYPx = 0f
                },
                onDrag = { _, drag ->
                    dragDeltaXPx += drag.x
                    dragDeltaYPx += drag.y
                },
                onDragCancel = {
                    dragging = false
                    dragDeltaXPx = 0f
                    dragDeltaYPx = 0f
                },
                onDragEnd = {
                    val rawMinutes = pxToMinutes(dragDeltaYPx, hourHeightPx)
                    val snappedMinutes = snapToGrid(rawMinutes)
                    val rawDayDelta = pxToDayDelta(dragDeltaXPx, columnWidthPx)
                    val snappedDayDelta = clampDayDelta(rawDayDelta, weekDayIndex, weekDayCount)
                    dragging = false
                    if (snappedMinutes == 0 && snappedDayDelta == 0) {
                        dragDeltaXPx = 0f
                        dragDeltaYPx = 0f
                    } else {
                        // Snap visually to the resolved grid position so the
                        // chip rests where the save will land. Hold the offset
                        // through the Calendar Provider round-trip; the
                        // remember key resets to zero when event.startMillis
                        // updates, atomically swapping base + delta.
                        dragDeltaYPx = snappedMinutes * hourHeightPx / MinutesPerHour
                        dragDeltaXPx = snappedDayDelta.toFloat() * columnWidthPx
                        onReschedule(
                            applyDayAndMinuteDelta(
                                originalMillis = originalStartMillis,
                                zone = zone,
                                dayDelta = snappedDayDelta,
                                minuteDelta = snappedMinutes,
                            ),
                        )
                    }
                },
            )
        }
    }

    return RescheduleDragState(
        dragModifier = dragModifier,
        tapModifier = tapModifier,
        dragDeltaXPx = dragDeltaXPx,
        dragDeltaYPx = dragDeltaYPx,
        dragging = dragging,
    )
}

// float on purpose: the Int TimeUnits.MinutesPerHour would change drag rounding.
private const val MinutesPerHour = 60f
