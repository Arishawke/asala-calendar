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
import android.database.Cursor
import android.provider.CalendarContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.time.LocalDate
import java.time.ZoneId

class EventRepository(private val contentResolver: ContentResolver) {
    // streams instances overlapping [startDate, endExclusive); re-emits on provider change.
    fun observeEvents(
        startDate: LocalDate,
        endExclusive: LocalDate,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Flow<List<EventItem>> = contentResolver
        .observeChanges(CalendarContract.Instances.CONTENT_URI)
        .map { queryInstances(startDate, endExclusive, zone) }
        .flowOn(Dispatchers.IO)

    private fun queryInstances(startDate: LocalDate, endExclusive: LocalDate, zone: ZoneId): List<EventItem> {
        val (startMillis, endMillis) = dayRangeMillis(startDate, endExclusive, zone)

        val uri =
            CalendarContract.Instances.CONTENT_URI.buildUpon().run {
                ContentUris.appendId(this, startMillis)
                ContentUris.appendId(this, endMillis)
                build()
            }

        val cursor =
            contentResolver.query(
                uri,
                Projection,
                null,
                null,
                "${CalendarContract.Instances.BEGIN} ASC",
            ) ?: run {
                Timber.w("queryInstances: null cursor for %s..%s", startDate, endExclusive)
                return emptyList()
            }

        return cursor.use { it.readEventItems() }
    }

    suspend fun searchEvents(
        query: String,
        startDate: LocalDate,
        endExclusive: LocalDate,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<EventItem> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        val (startMillis, endMillis) = dayRangeMillis(startDate, endExclusive, zone)
        val uri =
            CalendarContract.Instances.CONTENT_URI.buildUpon().run {
                ContentUris.appendId(this, startMillis)
                ContentUris.appendId(this, endMillis)
                build()
            }
        val escaped = escapeLikePattern(query.trim())
        val selection =
            "(${CalendarContract.Instances.TITLE} LIKE ? ESCAPE '\\') OR " +
                "(${CalendarContract.Instances.EVENT_LOCATION} LIKE ? ESCAPE '\\') OR " +
                "(${CalendarContract.Instances.DESCRIPTION} LIKE ? ESCAPE '\\')"
        val arg = "%$escaped%"
        val cursor =
            contentResolver.query(
                uri,
                Projection,
                selection,
                arrayOf(arg, arg, arg),
                "${CalendarContract.Instances.BEGIN} ASC",
            ) ?: return@withContext emptyList()
        cursor.use { it.readEventItems() }
    }

    // shared reader for the Projection above; queryInstances and searchEvents
    // differ only in selection but read identical row shapes.
    private fun Cursor.readEventItems(): List<EventItem> {
        val items = mutableListOf<EventItem>()
        val instanceIdIdx = getColumnIndexOrThrow(CalendarContract.Instances._ID)
        val eventIdIdx = getColumnIndexOrThrow(CalendarContract.Instances.EVENT_ID)
        val calendarIdIdx = getColumnIndexOrThrow(CalendarContract.Instances.CALENDAR_ID)
        val titleIdx = getColumnIndexOrThrow(CalendarContract.Instances.TITLE)
        val beginIdx = getColumnIndexOrThrow(CalendarContract.Instances.BEGIN)
        val endIdx = getColumnIndexOrThrow(CalendarContract.Instances.END)
        val allDayIdx = getColumnIndexOrThrow(CalendarContract.Instances.ALL_DAY)
        val colorIdx = getColumnIndexOrThrow(CalendarContract.Instances.DISPLAY_COLOR)
        val statusIdx = getColumnIndexOrThrow(CalendarContract.Instances.STATUS)
        val calNameIdx = getColumnIndexOrThrow(CalendarContract.Instances.CALENDAR_DISPLAY_NAME)
        while (moveToNext()) {
            items.add(
                EventItem(
                    instanceId = getLong(instanceIdIdx),
                    eventId = getLong(eventIdIdx),
                    calendarId = getLong(calendarIdIdx),
                    title = getString(titleIdx) ?: "",
                    startMillis = getLong(beginIdx),
                    endMillis = getLong(endIdx),
                    allDay = getInt(allDayIdx) == 1,
                    displayColor = getInt(colorIdx),
                    // older CalDAV imports may leave STATUS unset; default
                    // CONFIRMED so the chip isn't rendered tentative-by-accident.
                    status = if (isNull(statusIdx)) {
                        CalendarContract.Events.STATUS_CONFIRMED
                    } else {
                        getInt(statusIdx)
                    },
                    isBirthday = BirthdayDetection.isBirthdayCalendar(getString(calNameIdx)),
                ),
            )
        }
        return items
    }

    suspend fun fetchEventDetail(eventId: Long): EventDetail? = contentResolver.readEventDetail(eventId)

    suspend fun insertEvent(draft: EventDraft): Long? = withContext(Dispatchers.IO) {
        val uri =
            contentResolver.insert(
                CalendarContract.Events.CONTENT_URI,
                draft.toContentValues(),
            ) ?: return@withContext null
        ContentUris.parseId(uri)
    }

    suspend fun updateEvent(
        eventId: Long,
        draft: EventDraft,
        scope: RecurringEditScope = RecurringEditScope.AllEvents,
        instanceMillis: Long? = null,
        parentRrule: String? = null,
        parentAllDay: Boolean = false,
    ): Long? = contentResolver.updateEventScoped(
        eventId,
        draft,
        scope,
        instanceMillis,
        parentRrule,
        parentAllDay,
    )

    suspend fun deleteEvent(
        eventId: Long,
        scope: RecurringEditScope = RecurringEditScope.AllEvents,
        instanceMillis: Long? = null,
        parentRrule: String? = null,
        parentCalendarId: Long? = null,
        parentAllDay: Boolean = false,
    ): Boolean = contentResolver.deleteEventScoped(
        eventId,
        scope,
        instanceMillis,
        parentRrule,
        parentCalendarId,
        parentAllDay,
    )

    private companion object {
        val Projection =
            arrayOf(
                CalendarContract.Instances._ID,
                CalendarContract.Instances.EVENT_ID,
                CalendarContract.Instances.CALENDAR_ID,
                CalendarContract.Instances.TITLE,
                CalendarContract.Instances.BEGIN,
                CalendarContract.Instances.END,
                CalendarContract.Instances.ALL_DAY,
                CalendarContract.Instances.DISPLAY_COLOR,
                CalendarContract.Instances.STATUS,
                CalendarContract.Instances.CALENDAR_DISPLAY_NAME,
            )
    }
}

// headroom for the backslash escapes appended to LIKE metacharacters.
private const val LikeEscapeHeadroom = 8

internal fun escapeLikePattern(query: String): String {
    val sb = StringBuilder(query.length + LikeEscapeHeadroom)
    for (c in query) {
        when (c) {
            '\\', '%', '_' -> sb.append('\\').append(c)
            else -> sb.append(c)
        }
    }
    return sb.toString()
}
