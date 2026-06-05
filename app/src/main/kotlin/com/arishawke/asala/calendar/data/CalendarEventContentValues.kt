/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.data

import android.content.ContentValues

// mirrors EventDraft.toContentValues so map payloads (recurrence truncation,
// EXDATE updates) stay unit-testable as plain Kotlin, crossing to ContentValues
// only at the provider edge.
internal fun Map<String, Any?>.toCalendarEventContentValues(): ContentValues {
    val cv = ContentValues()
    forEach { (key, value) ->
        when (value) {
            null -> cv.putNull(key)
            is Long -> cv.put(key, value)
            is Int -> cv.put(key, value)
            is String -> cv.put(key, value)
            else -> cv.put(key, value.toString())
        }
    }
    return cv
}
