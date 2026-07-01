/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.data

import android.provider.ContactsContract.CommonDataKinds.Event
import org.junit.Assert.assertEquals
import org.junit.Test

class ContactsReadResultTest {
    @Test fun `routes birthday and anniversary event types`() {
        val rows = listOf(
            RawContactEventRow(1, "Alice", Event.TYPE_BIRTHDAY, "1990-06-15"),
            RawContactEventRow(2, "Bob", Event.TYPE_ANNIVERSARY, "2010-09-01"),
        )
        val occasions = mapOccasionRows(rows)
        assertEquals(
            listOf(
                Occasion(1, "Alice", OccasionType.Birthday, 6, 15, 1990),
                Occasion(2, "Bob", OccasionType.Anniversary, 9, 1, 2010),
            ),
            occasions,
        )
    }

    @Test fun `skips rows with blank or null display name`() {
        val rows = listOf(
            RawContactEventRow(1, null, Event.TYPE_BIRTHDAY, "1990-06-15"),
            RawContactEventRow(2, "   ", Event.TYPE_BIRTHDAY, "1990-06-15"),
            RawContactEventRow(3, "Carol", Event.TYPE_BIRTHDAY, "1990-06-15"),
        )
        assertEquals(listOf(Occasion(3, "Carol", OccasionType.Birthday, 6, 15, 1990)), mapOccasionRows(rows))
    }

    @Test fun `skips rows with unparseable or null date`() {
        val rows = listOf(
            RawContactEventRow(1, "Alice", Event.TYPE_BIRTHDAY, "not a date"),
            RawContactEventRow(2, "Bob", Event.TYPE_BIRTHDAY, null),
            RawContactEventRow(3, "Carol", Event.TYPE_BIRTHDAY, "1990-06-15"),
        )
        assertEquals(listOf(Occasion(3, "Carol", OccasionType.Birthday, 6, 15, 1990)), mapOccasionRows(rows))
    }

    // a contact can have multiple raw rows for the same event type (e.g. a
    // duplicate sync-adapter row); keep only the first so occasions stay unique.
    @Test fun `dedups rows sharing the same contact and type, keeping the first`() {
        val rows = listOf(
            RawContactEventRow(1, "Alice", Event.TYPE_BIRTHDAY, "1990-06-15"),
            RawContactEventRow(1, "Alice", Event.TYPE_BIRTHDAY, "1991-07-16"),
        )
        assertEquals(listOf(Occasion(1, "Alice", OccasionType.Birthday, 6, 15, 1990)), mapOccasionRows(rows))
    }

    @Test fun `mixes valid, skipped, and duplicate rows into the right set`() {
        val rows = listOf(
            RawContactEventRow(1, "Alice", Event.TYPE_BIRTHDAY, "1990-06-15"),
            RawContactEventRow(1, "Alice", Event.TYPE_BIRTHDAY, "2000-01-01"),
            RawContactEventRow(2, null, Event.TYPE_BIRTHDAY, "1990-06-15"),
            RawContactEventRow(3, "Dave", Event.TYPE_ANNIVERSARY, "garbage"),
            RawContactEventRow(4, "Erin", Event.TYPE_ANNIVERSARY, "--12-25"),
        )
        assertEquals(
            listOf(
                Occasion(1, "Alice", OccasionType.Birthday, 6, 15, 1990),
                Occasion(4, "Erin", OccasionType.Anniversary, 12, 25, null),
            ),
            mapOccasionRows(rows),
        )
    }
}
