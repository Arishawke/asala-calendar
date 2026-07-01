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
        // a racing sync (see OccasionSync.syncMutex) can leave more than one
        // existing row per stableId; group so every stray gets self-healed below
        val existingByStableId = existing.groupBy { it.stableId }
        val desiredIds = deduped.mapTo(mutableSetOf()) { it.stableId }

        val toInsert = mutableListOf<Occasion>()
        val toUpdate = mutableListOf<Pair<Long, Occasion>>()
        val toDelete = mutableListOf<Long>()
        for (o in deduped) {
            val group = existingByStableId[o.stableId]
            if (group == null) {
                toInsert += o
            } else {
                matchGroup(group, o, expectedTitle, toUpdate, toDelete)
            }
        }

        // stableIds no longer desired: every row under that id is stale, duplicates included
        for ((stableId, group) in existingByStableId) {
            if (stableId !in desiredIds) group.mapTo(toDelete) { it.eventId }
        }

        return OccasionDiff(toInsert, toUpdate, toDelete)
    }

    // the first existing row (arrival order) drives the update/no-op decision;
    // any others sharing the stableId are stray duplicates and get deleted
    private fun matchGroup(
        group: List<ExistingOccasionEvent>,
        o: Occasion,
        expectedTitle: (Occasion) -> String,
        toUpdate: MutableList<Pair<Long, Occasion>>,
        toDelete: MutableList<Long>,
    ) {
        val first = group.first()
        if (needsUpdate(first, o, expectedTitle)) toUpdate += first.eventId to o
        group.drop(1).mapTo(toDelete) { it.eventId }
    }

    private fun needsUpdate(match: ExistingOccasionEvent, o: Occasion, expectedTitle: (Occasion) -> String): Boolean =
        match.title != expectedTitle(o) || match.dtStartMillis != occasionDtStartMillis(o.month, o.day, o.year)
}
