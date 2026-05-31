/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.ui.timeline

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.arishawke.asala.calendar.data.TimeUnits
import com.arishawke.asala.calendar.ui.theme.CalendarTokens
import kotlinx.coroutines.delay
import java.time.LocalTime
import java.time.ZoneId

// shared "now" marker (dot + line in CalendarTokens.nowLine); caller owns
// positioning via the passed modifier.
@Composable
internal fun NowLineRow(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(8.dp)
                .height(8.dp)
                .clip(CircleShape)
                .background(CalendarTokens.nowLine),
        )
        HorizontalDivider(
            color = CalendarTokens.nowLine,
            thickness = 2.dp,
            modifier = Modifier.weight(1f),
        )
    }
}

// minutes-into-day for "now", ticked once a minute. null when disabled so
// callers skip rendering (e.g. a non-today page).
@Composable
internal fun rememberNowMinutes(zone: ZoneId, enabled: Boolean): Int? {
    val state: State<Int?> = produceState<Int?>(initialValue = nowMinutes(zone, enabled), zone, enabled) {
        if (!enabled) {
            value = null
            return@produceState
        }
        value = nowMinutes(zone, true)
        while (true) {
            delay(TickIntervalMillis)
            value = nowMinutes(zone, true)
        }
    }
    return state.value
}

private fun nowMinutes(zone: ZoneId, enabled: Boolean): Int? {
    if (!enabled) return null
    val t = LocalTime.now(zone)
    return t.hour * TimeUnits.MinutesPerHour + t.minute
}

private const val TickIntervalMillis: Long = 60_000L
