/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.data

import org.junit.Assert.assertEquals
import org.junit.Test

class OccasionDetectionTest {
    @Test fun `classifies default calendar names`() {
        assertEquals(OccasionKind.Birthday, OccasionDetection.classify("Birthdays"))
        assertEquals(OccasionKind.Anniversary, OccasionDetection.classify("Anniversaries"))
        assertEquals(OccasionKind.None, OccasionDetection.classify("Work"))
        assertEquals(OccasionKind.None, OccasionDetection.classify(null))
    }

    @Test fun `birthday keywords still win, preserving existing behaviour`() {
        // French "anniversaire" means birthday and must stay Birthday (BirthdayDetection legacy)
        assertEquals(OccasionKind.Birthday, OccasionDetection.classify("Anniversaire"))
        assertEquals(OccasionKind.Birthday, OccasionDetection.classify("Geburtstag"))
    }
}
