/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.notifications

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import timber.log.Timber

// scheduler bookkeeping, kept out of the user-facing settings store so it can be
// reasoned about (and cleared) on its own.
private val Context.reminderStateDataStore: DataStore<Preferences> by preferencesDataStore(name = "reminder_state")

// persists the set of currently-armed alarms so the diff in rescheduleAll has a
// real previous-plan after process death, instead of an empty in-memory one that
// cancels nothing and leaves stale alarms armed.
internal object ArmedAlarmStore {
    private val ARMED_KEYS = stringSetPreferencesKey("armed_alarms")
    private const val FIELD_COUNT = 4
    private const val TRIGGER_INDEX = 3

    // "eventId:instanceStartMillis:minutesBefore:triggerAtMillis"; all fields are
    // numeric so the colon delimiter is unambiguous.
    fun encodeKey(key: AlarmKey): String =
        "${key.eventId}:${key.instanceStartMillis}:${key.minutesBefore}:${key.triggerAtMillis}"

    // skips any malformed entry so a corrupt write can't crash the reload or
    // poison the whole armed set.
    fun decodeKeys(raw: Set<String>): Set<AlarmKey> = raw.mapNotNullTo(mutableSetOf()) { line ->
        val parts = line.split(":")
        if (parts.size != FIELD_COUNT) return@mapNotNullTo null
        AlarmKey(
            eventId = parts[0].toLongOrNull() ?: return@mapNotNullTo null,
            instanceStartMillis = parts[1].toLongOrNull() ?: return@mapNotNullTo null,
            minutesBefore = parts[2].toIntOrNull() ?: return@mapNotNullTo null,
            triggerAtMillis = parts[TRIGGER_INDEX].toLongOrNull() ?: return@mapNotNullTo null,
        )
    }

    // falls back to empty on read failure: degrades to the prior in-memory-only
    // behavior rather than crashing the scheduler.
    suspend fun load(context: Context): Set<AlarmKey> = runCatching {
        decodeKeys(context.reminderStateDataStore.data.first()[ARMED_KEYS].orEmpty())
    }.getOrElse {
        Timber.w(it, "armed-alarm load failed; treating as empty")
        emptySet()
    }

    suspend fun save(context: Context, plan: Set<AlarmKey>) {
        runCatching {
            context.reminderStateDataStore.edit { it[ARMED_KEYS] = plan.mapTo(mutableSetOf(), ::encodeKey) }
        }.onFailure { Timber.w(it, "armed-alarm save failed; in-memory plan stays authoritative this run") }
    }
}
