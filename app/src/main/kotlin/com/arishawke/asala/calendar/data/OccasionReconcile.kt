/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.data

data class ExistingOccasionEvent(val eventId: Long, val stableId: String, val title: String, val dtStartMillis: Long)

data class OccasionDiff(
    val toInsert: List<Occasion>,
    val toUpdate: List<Pair<Long, Occasion>>,
    val toDelete: List<Long>,
)

// pure set-diff between contact-derived occasions and their generated events.
// no provider access: the caller reads/writes, this only decides what changed.
object OccasionReconcile {
    fun diff(
        desired: List<Occasion>,
        existing: List<ExistingOccasionEvent>,
        expectedTitle: (Occasion) -> String,
    ): OccasionDiff {
        val deduped = desired.distinctBy { it.stableId }
        // one event per stableId is assumed; a stray duplicate would keep the last
        val existingById = existing.associateBy { it.stableId }

        val toInsert = mutableListOf<Occasion>()
        val toUpdate = mutableListOf<Pair<Long, Occasion>>()
        for (o in deduped) {
            val match = existingById[o.stableId]
            if (match == null) {
                toInsert += o
            } else if (needsUpdate(match, o, expectedTitle)) {
                toUpdate += match.eventId to o
            }
        }

        val desiredIds = deduped.mapTo(mutableSetOf()) { it.stableId }
        val toDelete = existing.filter { it.stableId !in desiredIds }.map { it.eventId }

        return OccasionDiff(toInsert, toUpdate, toDelete)
    }

    private fun needsUpdate(match: ExistingOccasionEvent, o: Occasion, expectedTitle: (Occasion) -> String): Boolean =
        match.title != expectedTitle(o) || match.dtStartMillis != occasionDtStartMillis(o.month, o.day, o.year)
}
