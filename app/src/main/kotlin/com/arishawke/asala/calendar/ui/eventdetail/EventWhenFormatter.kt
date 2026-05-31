/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.ui.eventdetail

import android.icu.text.DateIntervalFormat
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.arishawke.asala.calendar.R
import com.arishawke.asala.calendar.data.EventDetail
import com.arishawke.asala.calendar.data.RecurrenceFrequency
import com.arishawke.asala.calendar.data.TimeUnits
import java.text.FieldPosition
import java.time.ZoneId
import java.util.Locale
import android.icu.text.DateFormat as IcuDateFormat
import android.icu.util.Calendar as IcuCalendar
import android.icu.util.TimeZone as IcuTimeZone

private const val MinutesPerDay = 24 * 60
private const val DateSkeleton = "yMMMMEEEEd"

internal fun recurrenceSummaryRes(freq: RecurrenceFrequency): Int = when (freq) {
    RecurrenceFrequency.Daily -> R.string.repeats_daily
    RecurrenceFrequency.Weekly -> R.string.repeats_weekly
    RecurrenceFrequency.Monthly -> R.string.repeats_monthly
    RecurrenceFrequency.Yearly -> R.string.repeats_yearly
}

@Composable
internal fun reminderLabel(minutesBefore: Int): String = when (minutesBefore) {
    0 -> stringResource(R.string.reminder_at_time)
    TimeUnits.MinutesPerHour -> stringResource(R.string.reminder_one_hour)
    MinutesPerDay -> stringResource(R.string.reminder_one_day)
    else -> stringResource(R.string.reminder_minutes_before, minutesBefore)
}

internal fun formatWhen(d: EventDetail, instanceMillis: Long?, is24Hour: Boolean): String {
    val locale = Locale.getDefault()
    // recurring opened from an occurrence: shift the range by (instance -
    // parent dtstart) so the sheet describes the tapped instance.
    val offset = if (d.rrule != null && instanceMillis != null) instanceMillis - d.startMillis else 0L
    val startMillis = d.startMillis + offset
    val endMillis = d.endMillis + offset
    return if (d.allDay) {
        formatAllDayRange(startMillis, endMillis, locale)
    } else {
        formatTimedRange(startMillis, endMillis, ZoneId.systemDefault(), is24Hour, locale)
    }
}

private fun formatAllDayRange(startMillis: Long, endMillisExclusive: Long, locale: Locale): String {
    val utc = IcuTimeZone.getTimeZone("UTC")
    val startCal = IcuCalendar.getInstance(utc, locale).apply { timeInMillis = startMillis }
    // end is exclusive (00:00 next day); roll back so the range closes on
    // the last visible date. UTC throughout, dtstart is 00:00 UTC.
    val endCal = IcuCalendar.getInstance(utc, locale).apply {
        timeInMillis = endMillisExclusive
        add(IcuCalendar.DATE, -1)
    }
    return if (sameDay(startCal, endCal)) {
        IcuDateFormat.getPatternInstance(DateSkeleton, locale).format(startCal)
    } else {
        formatInterval(DateIntervalFormat.getInstance(DateSkeleton, locale), startCal, endCal)
    }
}

private fun formatTimedRange(
    startMillis: Long,
    endMillis: Long,
    zone: ZoneId,
    is24Hour: Boolean,
    locale: Locale,
): String {
    val icuZone = IcuTimeZone.getTimeZone(zone.id)
    val startCal = IcuCalendar.getInstance(icuZone, locale).apply { timeInMillis = startMillis }
    val endCal = IcuCalendar.getInstance(icuZone, locale).apply { timeInMillis = endMillis }
    val timeSkeleton = if (is24Hour) "Hm" else "hm"
    return if (sameDay(startCal, endCal)) {
        val dateLine = IcuDateFormat.getPatternInstance(DateSkeleton, locale).format(startCal)
        val timeLine = formatInterval(DateIntervalFormat.getInstance(timeSkeleton, locale), startCal, endCal)
        "$dateLine\n$timeLine"
    } else {
        // interval formatter has no two-line layout, so compose the newline
        // between date+time sides ourselves.
        val dateFmt = IcuDateFormat.getPatternInstance(DateSkeleton, locale)
        val timeFmt = IcuDateFormat.getPatternInstance(timeSkeleton, locale)
        val startSide = "${dateFmt.format(startCal)} ${timeFmt.format(startCal)}"
        val endSide = "${dateFmt.format(endCal)} ${timeFmt.format(endCal)}"
        "$startSide\n$endSide"
    }
}

private fun sameDay(a: IcuCalendar, b: IcuCalendar): Boolean = a.get(IcuCalendar.YEAR) == b.get(IcuCalendar.YEAR) &&
    a.get(IcuCalendar.DAY_OF_YEAR) == b.get(IcuCalendar.DAY_OF_YEAR)

// wraps ICU's StringBuffer + FieldPosition boilerplate.
private fun formatInterval(fmt: DateIntervalFormat, a: IcuCalendar, b: IcuCalendar): String =
    fmt.format(a, b, StringBuffer(), FieldPosition(0)).toString()
