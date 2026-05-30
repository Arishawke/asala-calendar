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
import android.content.ContentValues
import android.provider.CalendarContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class CalendarRepository(private val contentResolver: ContentResolver) {
    fun observeCalendars(): Flow<List<CalendarItem>> = contentResolver
        .observeChanges(CalendarContract.Calendars.CONTENT_URI)
        .map { queryCalendars() }
        .flowOn(Dispatchers.IO)

    suspend fun calendars(): List<CalendarItem> = withContext(Dispatchers.IO) { queryCalendars() }

    suspend fun createLocalCalendar(displayName: String, color: Int): Long? = withContext(Dispatchers.IO) {
        val account = LocalCalendar.AccountName
        val values =
            ContentValues().apply {
                put(CalendarContract.Calendars.ACCOUNT_NAME, account)
                put(CalendarContract.Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
                put(CalendarContract.Calendars.NAME, displayName)
                put(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, displayName)
                put(CalendarContract.Calendars.CALENDAR_COLOR, color)
                put(CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL, CalendarContract.Calendars.CAL_ACCESS_OWNER)
                put(CalendarContract.Calendars.OWNER_ACCOUNT, account)
                put(CalendarContract.Calendars.SYNC_EVENTS, 1)
                put(CalendarContract.Calendars.VISIBLE, 1)
            }
        val uri =
            CalendarContract.Calendars.CONTENT_URI
                .buildUpon()
                .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
                .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME, account)
                .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
                .build()
        contentResolver.insert(uri, values)?.lastPathSegment?.toLongOrNull()
    }

    suspend fun deleteLocalCalendar(calendarId: Long): Boolean = withContext(Dispatchers.IO) {
        val uri =
            ContentUris
                .withAppendedId(
                    CalendarContract.Calendars.CONTENT_URI,
                    calendarId,
                ).buildUpon()
                .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
                .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME, LocalCalendar.AccountName)
                .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
                .build()
        contentResolver.delete(uri, null, null) > 0
    }

    // Persist the new color back to CALENDAR_COLOR for local calendars so
    // exports and other apps that read this provider row see the user's
    // chosen color. The DataStore override is still authoritative on read
    // (so a sync adapter on a future synced calendar can't clobber it), but
    // for local calendars we keep both in sync. CALENDAR_COLOR is sync-
    // adapter-scoped per AOSP (the app-writable set is NAME,
    // CALENDAR_DISPLAY_NAME, VISIBLE, SYNC_EVENTS); we own the local-
    // calendar rows so we apply the sync-adapter URI scoping (matching
    // createLocalCalendar / deleteLocalCalendar) for contract compliance.
    suspend fun updateLocalCalendarColor(calendarId: Long, color: Int): Boolean = withContext(Dispatchers.IO) {
        val uri =
            ContentUris
                .withAppendedId(CalendarContract.Calendars.CONTENT_URI, calendarId)
                .buildUpon()
                .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
                .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME, LocalCalendar.AccountName)
                .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
                .build()
        val values = ContentValues().apply { put(CalendarContract.Calendars.CALENDAR_COLOR, color) }
        contentResolver.update(uri, values, null, null) > 0
    }

    // Apps can update display name without sync-adapter privileges; the
    // CALENDAR_DISPLAY_NAME column is in the app-writable set per AOSP docs.
    // We still scope by id via the URI to avoid ambiguous WHERE clauses.
    suspend fun renameLocalCalendar(calendarId: Long, newDisplayName: String): Boolean = withContext(Dispatchers.IO) {
        val trimmed = newDisplayName.trim()
        if (trimmed.isEmpty()) return@withContext false
        val uri =
            ContentUris.withAppendedId(
                CalendarContract.Calendars.CONTENT_URI,
                calendarId,
            )
        val values =
            ContentValues().apply {
                put(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, trimmed)
                put(CalendarContract.Calendars.NAME, trimmed)
            }
        contentResolver.update(uri, values, null, null) > 0
    }

    private fun queryCalendars(): List<CalendarItem> {
        val cursor =
            contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                Projection,
                null,
                null,
                "${CalendarContract.Calendars.IS_PRIMARY} DESC, " +
                    "${CalendarContract.Calendars.CALENDAR_DISPLAY_NAME} ASC",
            ) ?: return emptyList()

        return cursor.use {
            val items = mutableListOf<CalendarItem>()
            val idIdx = it.getColumnIndexOrThrow(CalendarContract.Calendars._ID)
            val displayIdx = it.getColumnIndexOrThrow(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME)
            val accountIdx = it.getColumnIndexOrThrow(CalendarContract.Calendars.ACCOUNT_NAME)
            val accountTypeIdx = it.getColumnIndexOrThrow(CalendarContract.Calendars.ACCOUNT_TYPE)
            val colorIdx = it.getColumnIndexOrThrow(CalendarContract.Calendars.CALENDAR_COLOR)
            val visibleIdx = it.getColumnIndexOrThrow(CalendarContract.Calendars.VISIBLE)
            val accessLevelIdx = it.getColumnIndexOrThrow(CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL)
            while (it.moveToNext()) {
                items.add(
                    CalendarItem(
                        id = it.getLong(idIdx),
                        displayName = it.getString(displayIdx) ?: "",
                        accountName = it.getString(accountIdx) ?: "",
                        accountType = it.getString(accountTypeIdx) ?: "",
                        color = it.getInt(colorIdx),
                        visible = it.getInt(visibleIdx) == 1,
                        accessLevel = it.getInt(accessLevelIdx),
                    ),
                )
            }
            items
        }
    }

    private companion object {
        // IS_PRIMARY is intentionally not selected: the column is still
        // referenced by the ORDER BY (primaries float to the top) but no
        // call site reads its value off the row.
        val Projection =
            arrayOf(
                CalendarContract.Calendars._ID,
                CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
                CalendarContract.Calendars.ACCOUNT_NAME,
                CalendarContract.Calendars.ACCOUNT_TYPE,
                CalendarContract.Calendars.CALENDAR_COLOR,
                CalendarContract.Calendars.VISIBLE,
                CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL,
            )
    }
}
