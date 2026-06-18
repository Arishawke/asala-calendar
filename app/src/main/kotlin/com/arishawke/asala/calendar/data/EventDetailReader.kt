/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.data

import android.content.ContentResolver
import android.content.ContentUris
import android.provider.CalendarContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.TimeZone

// all-day rows carry their wall-clock dates in UTC by provider convention, so a
// missing EVENT_TIMEZONE on an all-day row resolves to UTC, not the device zone.
internal fun resolveEventTimezone(stored: String?, allDay: Boolean): String =
    stored ?: if (allDay) "UTC" else TimeZone.getDefault().id

// reads one Events row plus its first reminder. recurring rows store DURATION
// not DTEND, so end millis is reconstructed via EventEndMillis.compute.
internal suspend fun ContentResolver.readEventDetail(eventId: Long): EventDetail? = withContext(Dispatchers.IO) {
    val eventUri =
        ContentUris.withAppendedId(
            CalendarContract.Events.CONTENT_URI,
            eventId,
        )
    val eventProjection =
        arrayOf(
            CalendarContract.Events.CALENDAR_ID,
            CalendarContract.Events.TITLE,
            CalendarContract.Events.DESCRIPTION,
            CalendarContract.Events.EVENT_LOCATION,
            CalendarContract.Events.DTSTART,
            CalendarContract.Events.DTEND,
            CalendarContract.Events.DURATION,
            CalendarContract.Events.ALL_DAY,
            CalendarContract.Events.EVENT_TIMEZONE,
            CalendarContract.Events.RRULE,
            CalendarContract.Events.DISPLAY_COLOR,
            CalendarContract.Events.CALENDAR_DISPLAY_NAME,
            CalendarContract.Events.STATUS,
            CalendarContract.Events.AVAILABILITY,
            CalendarContract.Events.CALENDAR_ACCESS_LEVEL,
        )

    val event =
        query(
            eventUri,
            eventProjection,
            null,
            null,
            null,
        )?.use { c ->
            if (!c.moveToFirst()) return@use null
            val calendarIdIdx = c.getColumnIndexOrThrow(CalendarContract.Events.CALENDAR_ID)
            val titleIdx = c.getColumnIndexOrThrow(CalendarContract.Events.TITLE)
            val descriptionIdx = c.getColumnIndexOrThrow(CalendarContract.Events.DESCRIPTION)
            val locationIdx = c.getColumnIndexOrThrow(CalendarContract.Events.EVENT_LOCATION)
            val dtStartIdx = c.getColumnIndexOrThrow(CalendarContract.Events.DTSTART)
            val dtEndIdx = c.getColumnIndexOrThrow(CalendarContract.Events.DTEND)
            val durationIdx = c.getColumnIndexOrThrow(CalendarContract.Events.DURATION)
            val allDayIdx = c.getColumnIndexOrThrow(CalendarContract.Events.ALL_DAY)
            val timezoneIdx = c.getColumnIndexOrThrow(CalendarContract.Events.EVENT_TIMEZONE)
            val rruleIdx = c.getColumnIndexOrThrow(CalendarContract.Events.RRULE)
            val colorIdx = c.getColumnIndexOrThrow(CalendarContract.Events.DISPLAY_COLOR)
            val calNameIdx = c.getColumnIndexOrThrow(CalendarContract.Events.CALENDAR_DISPLAY_NAME)
            val statusIdx = c.getColumnIndexOrThrow(CalendarContract.Events.STATUS)
            val availabilityIdx = c.getColumnIndexOrThrow(CalendarContract.Events.AVAILABILITY)
            val accessLevelIdx = c.getColumnIndexOrThrow(CalendarContract.Events.CALENDAR_ACCESS_LEVEL)
            val dtStart = c.getLong(dtStartIdx)
            val endMillis =
                EventEndMillis.compute(
                    dtStart = dtStart,
                    dtEnd = if (c.isNull(dtEndIdx)) null else c.getLong(dtEndIdx),
                    durationIso8601 = c.getString(durationIdx),
                )
            val calendarName = c.getString(calNameIdx) ?: ""
            EventDetail(
                eventId = eventId,
                calendarId = c.getLong(calendarIdIdx),
                title = c.getString(titleIdx) ?: "",
                description = c.getString(descriptionIdx).takeUnless { it.isNullOrBlank() },
                location = c.getString(locationIdx).takeUnless { it.isNullOrBlank() },
                startMillis = dtStart,
                endMillis = endMillis,
                allDay = c.getInt(allDayIdx) == 1,
                eventTimezone = resolveEventTimezone(
                    stored = c.getString(timezoneIdx),
                    allDay = c.getInt(allDayIdx) == 1,
                ),
                rrule = c.getString(rruleIdx).takeUnless { it.isNullOrBlank() },
                displayColor = c.getInt(colorIdx),
                calendarDisplayName = calendarName,
                reminderMinutesBefore = null,
                status = if (c.isNull(statusIdx)) {
                    CalendarContract.Events.STATUS_CONFIRMED
                } else {
                    c.getInt(statusIdx)
                },
                availability = if (c.isNull(availabilityIdx)) {
                    CalendarContract.Events.AVAILABILITY_BUSY
                } else {
                    c.getInt(availabilityIdx)
                },
                isBirthday = BirthdayDetection.isBirthdayCalendar(calendarName),
                accessLevel = if (c.isNull(accessLevelIdx)) {
                    CalendarContract.Calendars.CAL_ACCESS_OWNER
                } else {
                    c.getInt(accessLevelIdx)
                },
            )
        } ?: return@withContext null

    val reminderMinutes =
        query(
            CalendarContract.Reminders.CONTENT_URI,
            arrayOf(CalendarContract.Reminders.MINUTES),
            "${CalendarContract.Reminders.EVENT_ID} = ?",
            arrayOf(eventId.toString()),
            null,
        )?.use { c ->
            if (!c.moveToFirst()) return@use null
            c.getInt(c.getColumnIndexOrThrow(CalendarContract.Reminders.MINUTES))
        }

    event.copy(reminderMinutesBefore = reminderMinutes)
}
