/*
 * Copyright (C) 2026 Arishawke
 * GPL v3.
 */
package com.arishawke.asala.calendar.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OccasionReconcileTest {
    private fun title(o: Occasion) = "${o.displayName}'s ${o.type.name.lowercase()}"
    private fun existing(id: Long, o: Occasion) =
        ExistingOccasionEvent(id, o.stableId, title(o), occasionDtStartMillis(o.month, o.day, o.year))

    @Test fun `inserts unseen, deletes vanished, leaves unchanged`() {
        val alice = Occasion(1, "Alice", OccasionType.Birthday, 6, 15, 1990)
        val bob = Occasion(2, "Bob", OccasionType.Birthday, 1, 2, null)
        val d = OccasionReconcile.diff(listOf(alice), listOf(existing(50, bob)), ::title)
        assertEquals(listOf(alice), d.toInsert)
        assertEquals(listOf(50L), d.toDelete)
        assertTrue(d.toUpdate.isEmpty())
    }

    @Test fun `updates on date or name change`() {
        val alice = Occasion(1, "Alice", OccasionType.Birthday, 6, 15, 1990)
        val moved = alice.copy(day = 16)
        val renamed = alice.copy(displayName = "Alicia")
        assertEquals(
            listOf(50L to moved),
            OccasionReconcile.diff(listOf(moved), listOf(existing(50, alice)), ::title).toUpdate,
        )
        assertEquals(
            listOf(50L to renamed),
            OccasionReconcile.diff(listOf(renamed), listOf(existing(50, alice)), ::title).toUpdate,
        )
    }

    @Test fun `unchanged occasion is a no-op`() {
        val alice = Occasion(1, "Alice", OccasionType.Birthday, 6, 15, 1990)
        val d = OccasionReconcile.diff(listOf(alice), listOf(existing(50, alice)), ::title)
        assertTrue(d.toInsert.isEmpty())
        assertTrue(d.toUpdate.isEmpty())
        assertTrue(d.toDelete.isEmpty())
    }

    @Test fun `dedups duplicate desired occasions keeping the first`() {
        val alice = Occasion(1, "Alice", OccasionType.Birthday, 6, 15, 1990)
        val aliceLater = alice.copy(day = 16)
        val d = OccasionReconcile.diff(listOf(alice, aliceLater), emptyList(), ::title)
        assertEquals(1, d.toInsert.size)
        assertEquals(15, d.toInsert.first().day)
    }

    @Test fun `self-heals duplicate existing rows sharing a stableId`() {
        val alice = Occasion(1, "Alice", OccasionType.Birthday, 6, 15, 1990)
        val first = existing(50, alice)
        val duplicate = existing(51, alice)
        val d = OccasionReconcile.diff(listOf(alice), listOf(first, duplicate), ::title)
        assertTrue(d.toInsert.isEmpty())
        assertTrue(d.toUpdate.isEmpty())
        assertEquals(listOf(51L), d.toDelete)
    }
}
