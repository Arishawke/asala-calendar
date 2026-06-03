/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.ui.widget

import android.content.Context
import android.content.res.Configuration
import android.text.format.DateFormat
import android.text.format.DateUtils
import androidx.compose.ui.unit.dp
import com.arishawke.asala.calendar.R
import java.time.ZoneId
import java.util.Date

internal fun Configuration.isNight(): Boolean =
    (uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

private const val DAY_LABEL_FLAGS =
    DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_WEEKDAY or
        DateUtils.FORMAT_ABBREV_ALL or DateUtils.FORMAT_NO_YEAR

internal fun headerText(context: Context): String =
    DateUtils.formatDateTime(context, System.currentTimeMillis(), DAY_LABEL_FLAGS)

internal fun dayLabel(context: Context, section: AgendaDaySection): String = when (section.relativeDay) {
    RelativeDay.Today -> context.getString(R.string.widget_today)
    RelativeDay.Tomorrow -> context.getString(R.string.widget_tomorrow)
    // format from the section's own date (already all-day/UTC-correct), not an
    // event's raw startMillis, so all-day events don't shift the label's day.
    RelativeDay.Other -> DateUtils.formatDateTime(
        context,
        section.date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
        DAY_LABEL_FLAGS,
    )
}

internal fun eventTime(context: Context, event: AgendaEventRow): String = if (event.allDay) {
    context.getString(R.string.schedule_all_day)
} else {
    DateFormat.getTimeFormat(context).format(Date(event.startMillis))
}

@Suppress("MagicNumber")
internal object WidgetDimens {
    val pad = 12.dp
    val corner = 16.dp
    val headerGap = 6.dp
    val dayHeaderTop = 8.dp
    val dayHeaderBottom = 2.dp
    val rowPad = 5.dp
    val barWidth = 3.dp
    val barHeight = 20.dp
    val barCorner = 2.dp
    val gap = 8.dp
    val timeWidth = 56.dp
}
