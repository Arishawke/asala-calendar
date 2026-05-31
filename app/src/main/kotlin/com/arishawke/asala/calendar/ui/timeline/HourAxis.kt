/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.ui.timeline

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.arishawke.asala.calendar.ui.theme.LocalIs24Hour
import java.time.LocalTime
import java.time.format.DateTimeFormatter

// shared by day and week timelines. week passes labelOffsetY = -6.dp so labels
// sit on the hour line; day uses 0.dp since its taller rows hold them inside.
@Composable
internal fun HourAxis(labelOffsetY: Dp = 0.dp) {
    val is24Hour = LocalIs24Hour.current
    val locale = LocalLocale.current.platformLocale
    // locale drives the AM/PM marker on 12-hour patterns (ja_JP -> 午前/午後).
    val hourFmt = remember(is24Hour, locale) {
        if (is24Hour) DateTimeFormatter.ofPattern("HH:mm", locale) else DateTimeFormatter.ofPattern("h a", locale)
    }
    Column(modifier = Modifier.width(HourAxisWidth)) {
        for (h in 0..23) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(HourHeight),
                contentAlignment = Alignment.TopCenter,
            ) {
                if (h > 0) {
                    val label = LocalTime.of(h, 0).format(hourFmt)
                    Text(
                        text = if (is24Hour) label else label.lowercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = if (labelOffsetY == 0.dp) Modifier else Modifier.offset(y = labelOffsetY),
                    )
                }
            }
        }
    }
}
