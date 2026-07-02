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
import android.provider.ContactsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed interface OccasionReadResult {
    data class Success(val occasions: List<Occasion>) : OccasionReadResult

    data object Failed : OccasionReadResult
}

internal data class RawContactEventRow(
    val contactId: Long,
    val displayName: String?,
    val eventType: Int,
    val dateString: String?,
)

// a null cursor (query failure/revoked permission) is distinct from a
// non-null, zero-row cursor (a genuinely empty address book). Callers that
// reconcile occasion calendars against this list must not treat a failed
// read as "no occasions": that would delete every contact-derived event.
class ContactsRepository(private val contentResolver: ContentResolver) {
    suspend fun readOccasions(): OccasionReadResult = withContext(Dispatchers.IO) {
        when (val rows = providerCall("readOccasions", onError = null) { queryRows() }) {
            null -> OccasionReadResult.Failed
            else -> OccasionReadResult.Success(mapOccasionRows(rows))
        }
    }

    private fun queryRows(): List<RawContactEventRow>? {
        val cursor =
            contentResolver.query(
                ContactsContract.Data.CONTENT_URI,
                Projection,
                Selection,
                SelectionArgs,
                // deterministic row order: a merged contact can carry two rows of
                // the same event type, and the keep-first dedup would otherwise
                // flip dates between syncs on unspecified cursor order.
                "${ContactsContract.Data._ID} ASC",
            ) ?: return null

        return cursor.use {
            val contactIdIdx = it.getColumnIndexOrThrow(ContactsContract.Data.CONTACT_ID)
            val displayNameIdx = it.getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME)
            val typeIdx = it.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Event.TYPE)
            val dateIdx = it.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Event.START_DATE)
            val rows = mutableListOf<RawContactEventRow>()
            while (it.moveToNext()) {
                rows.add(
                    RawContactEventRow(
                        contactId = it.getLong(contactIdIdx),
                        displayName = it.getString(displayNameIdx),
                        eventType = it.getInt(typeIdx),
                        dateString = it.getString(dateIdx),
                    ),
                )
            }
            rows
        }
    }

    private companion object {
        val Projection =
            arrayOf(
                ContactsContract.Data.CONTACT_ID,
                ContactsContract.Contacts.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Event.TYPE,
                ContactsContract.CommonDataKinds.Event.START_DATE,
            )
        val Selection =
            "${ContactsContract.Data.MIMETYPE} = ? AND " +
                "${ContactsContract.CommonDataKinds.Event.TYPE} IN (?, ?)"
        val SelectionArgs =
            arrayOf(
                ContactsContract.CommonDataKinds.Event.CONTENT_ITEM_TYPE,
                ContactsContract.CommonDataKinds.Event.TYPE_BIRTHDAY.toString(),
                ContactsContract.CommonDataKinds.Event.TYPE_ANNIVERSARY.toString(),
            )
    }
}

// pure: no provider access, so it is unit-testable without a live cursor.
// distinctBy keeps the first element per key, matching the keep-first dedup rule.
internal fun mapOccasionRows(rows: List<RawContactEventRow>): List<Occasion> = rows
    .mapNotNull { it.toOccasionOrNull() }
    .distinctBy { it.contactId to it.type }

private fun RawContactEventRow.toOccasionOrNull(): Occasion? {
    val name = displayName?.trim()?.takeUnless { it.isBlank() }
    val type = eventTypeToOccasionType(eventType)
    val parsed = OccasionDateParser.parse(dateString)
    if (name == null || type == null || parsed == null) return null
    return Occasion(contactId, name, type, parsed.month, parsed.day, parsed.year)
}

private fun eventTypeToOccasionType(eventType: Int): OccasionType? = when (eventType) {
    ContactsContract.CommonDataKinds.Event.TYPE_BIRTHDAY -> OccasionType.Birthday
    ContactsContract.CommonDataKinds.Event.TYPE_ANNIVERSARY -> OccasionType.Anniversary
    else -> null
}
