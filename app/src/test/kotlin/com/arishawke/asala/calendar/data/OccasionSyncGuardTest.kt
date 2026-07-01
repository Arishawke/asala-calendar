/*
 * Copyright (C) 2026 Arishawke
 * GPL v3.
 */
package com.arishawke.asala.calendar.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// planOccasions is the pure seam: no ContentResolver needed, so the
// failed-read-means-no-deletion guard is provable without a provider.
class OccasionSyncGuardTest {
    private fun title(o: Occasion) = "${o.displayName}'s ${o.type.name.lowercase()}"

    private val alice = Occasion(1, "Alice", OccasionType.Birthday, 6, 15, 1990)
    private val existingAlice =
        ExistingOccasionEvent(
            50,
            alice.stableId,
            title(alice),
            occasionDtStartMillis(alice.month, alice.day, alice.year),
        )

    @Test fun `failed read plans no deletion`() {
        val plan = planOccasions(OccasionReadResult.Failed, listOf(existingAlice), emptyList(), ::title)
        assertNull(plan)
    }

    @Test fun `genuinely empty book deletes existing occasion events`() {
        val plan = planOccasions(OccasionReadResult.Success(emptyList()), listOf(existingAlice), emptyList(), ::title)
        assertEquals(listOf(50L), plan?.birthdays?.toDelete)
        assertTrue(plan?.anniversaries?.toDelete.isNullOrEmpty())
    }

    @Test fun `new occasion is queued for insert`() {
        val plan = planOccasions(OccasionReadResult.Success(listOf(alice)), emptyList(), emptyList(), ::title)
        assertEquals(listOf(alice), plan?.birthdays?.toInsert)
        assertTrue(plan?.anniversaries?.toInsert.isNullOrEmpty())
    }
}
