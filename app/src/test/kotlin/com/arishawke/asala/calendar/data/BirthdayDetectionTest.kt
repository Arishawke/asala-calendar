/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BirthdayDetectionTest {
    @Test
    fun `null and blank do not match`() {
        assertFalse(BirthdayDetection.isBirthdayCalendar(null))
        assertFalse(BirthdayDetection.isBirthdayCalendar(""))
        assertFalse(BirthdayDetection.isBirthdayCalendar("   "))
    }

    @Test
    fun `english variants match`() {
        assertTrue(BirthdayDetection.isBirthdayCalendar("Birthdays"))
        assertTrue(BirthdayDetection.isBirthdayCalendar("Birthday"))
        assertTrue(BirthdayDetection.isBirthdayCalendar("Family birthdays"))
        assertTrue(BirthdayDetection.isBirthdayCalendar("Birthdays and events"))
    }

    @Test
    fun `case insensitive match`() {
        assertTrue(BirthdayDetection.isBirthdayCalendar("BIRTHDAYS"))
        assertTrue(BirthdayDetection.isBirthdayCalendar("bIrThDaY"))
    }

    @Test
    fun `non-english variants match`() {
        assertTrue(BirthdayDetection.isBirthdayCalendar("Geburtstage"))
        assertTrue(BirthdayDetection.isBirthdayCalendar("Anniversaires"))
        assertTrue(BirthdayDetection.isBirthdayCalendar("Cumpleaños"))
        assertTrue(BirthdayDetection.isBirthdayCalendar("Compleanni"))
        assertTrue(BirthdayDetection.isBirthdayCalendar("Verjaardagen"))
        assertTrue(BirthdayDetection.isBirthdayCalendar("Aniversários"))
        assertTrue(BirthdayDetection.isBirthdayCalendar("Urodziny"))
        assertTrue(BirthdayDetection.isBirthdayCalendar("誕生日"))
        assertTrue(BirthdayDetection.isBirthdayCalendar("生日"))
        assertTrue(BirthdayDetection.isBirthdayCalendar("생일"))
        assertTrue(BirthdayDetection.isBirthdayCalendar("День рождения"))
    }

    @Test
    fun `regular calendar names do not match`() {
        assertFalse(BirthdayDetection.isBirthdayCalendar("Work"))
        assertFalse(BirthdayDetection.isBirthdayCalendar("Personal"))
        assertFalse(BirthdayDetection.isBirthdayCalendar("Health"))
        assertFalse(BirthdayDetection.isBirthdayCalendar("Holidays in United States"))
        assertFalse(BirthdayDetection.isBirthdayCalendar("Subscriptions"))
        assertFalse(BirthdayDetection.isBirthdayCalendar("General"))
        assertFalse(BirthdayDetection.isBirthdayCalendar("Asala"))
        assertFalse(BirthdayDetection.isBirthdayCalendar("Family"))
    }

    @Test
    fun `ASCII variants match without diacritics`() {
        // Latin American keyboards often skip tildes; some sync adapters
        // strip diacritics on write. Both should still match.
        assertTrue(BirthdayDetection.isBirthdayCalendar("Cumpleanos"))
        assertTrue(BirthdayDetection.isBirthdayCalendar("Aniversarios"))
    }

    @Test
    fun `Contacts calendar does not match by accident`() {
        // The major sync adapters' birthday calendars are named
        // "Birthdays" in current versions; older versions used "Contacts"
        // alone, which
        // is too generic to match without false positives elsewhere.
        // Users on those older versions can rename to include "Birthday".
        assertFalse(BirthdayDetection.isBirthdayCalendar("Contacts"))
    }
}
