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
import android.net.Uri
import android.provider.CalendarContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.time.LocalDate
import java.time.ZoneId

// Instances query URI scoped to the window via the two path ids.
internal fun instancesUriFor(startMillis: Long, endMillis: Long): Uri =
    CalendarContract.Instances.CONTENT_URI.buildUpon().run {
        ContentUris.appendId(this, startMillis)
        ContentUris.appendId(this, endMillis)
        build()
    }

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

    private fun queryInstances(startDate: LocalDate, endExclusive: LocalDate, zone: ZoneId): List<EventItem> =
        providerCall("queryInstances", onError = emptyList()) {
            val (startMillis, endMillis) = dayRangeMillis(startDate, endExclusive, zone)

            val uri = instancesUriFor(startMillis, endMillis)

            val cursor =
                contentResolver.query(
                    uri,
                    Projection,
                    null,
                    null,
                    "${CalendarContract.Instances.BEGIN} ASC",
                ) ?: run {
                    Timber.w("queryInstances: null cursor for %s..%s", startDate, endExclusive)
                    return@providerCall emptyList()
                }

            cursor.use { it.readEventItems() }
        }

    suspend fun searchEvents(
        query: String,
        startDate: LocalDate,
        endExclusive: LocalDate,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<EventItem> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        providerCall("searchEvents", onError = emptyList()) {
            val (startMillis, endMillis) = dayRangeMillis(startDate, endExclusive, zone)
            val uri = instancesUriFor(startMillis, endMillis)
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
                ) ?: return@providerCall emptyList()
            cursor.use { it.readEventItems() }
        }
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
        val dtStartIdx = getColumnIndexOrThrow(CalendarContract.Instances.DTSTART)
        val descIdx = getColumnIndexOrThrow(CalendarContract.Instances.DESCRIPTION)
        while (moveToNext()) {
            val kind = OccasionDetection.classify(getString(calNameIdx))
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
                    isBirthday = kind == OccasionKind.Birthday,
                    occasion = kind,
                    // DTSTART can be null on some provider rows; BEGIN (this
                    // occurrence's start) is the safe fallback.
                    parentDtStartMillis = if (isNull(dtStartIdx)) getLong(beginIdx) else getLong(dtStartIdx),
                    // only occasion rows need the contact name, so non-occasion
                    // reads skip retaining the description string.
                    occasionName = if (kind == OccasionKind.None) null else getString(descIdx),
                ),
            )
        }
        return items
    }

    suspend fun fetchEventDetail(eventId: Long): EventDetail? =
        providerCall("fetchEventDetail", onError = null) { contentResolver.readEventDetail(eventId) }

    suspend fun insertEvent(draft: EventDraft): Long? = withContext(Dispatchers.IO) {
        providerCall("insertEvent", onError = null) {
            contentResolver
                .insert(CalendarContract.Events.CONTENT_URI, draft.toContentValues())
                ?.let(ContentUris::parseId)
        }
    }

    suspend fun updateEvent(
        eventId: Long,
        draft: EventDraft,
        scope: RecurringEditScope = RecurringEditScope.AllEvents,
        instanceMillis: Long? = null,
        parentRrule: String? = null,
        parentAllDay: Boolean = false,
    ): Long? = providerCall("updateEvent", onError = null) {
        contentResolver.updateEventScoped(
            eventId,
            draft,
            scope,
            instanceMillis,
            parentRrule,
            parentAllDay,
        )
    }

    suspend fun deleteEvent(
        eventId: Long,
        scope: RecurringEditScope = RecurringEditScope.AllEvents,
        instanceMillis: Long? = null,
        parentRrule: String? = null,
        parentAllDay: Boolean = false,
    ): Boolean = providerCall("deleteEvent", onError = false) {
        contentResolver.deleteEventScoped(
            eventId,
            scope,
            instanceMillis,
            parentRrule,
            parentAllDay,
        )
    }

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
                CalendarContract.Instances.DTSTART,
                CalendarContract.Instances.DESCRIPTION,
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
