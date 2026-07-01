/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.data

// CalendarContract has no birthday metadata; birthday calendars are only
// distinguishable by display name. Delegates to OccasionDetection, the
// single source of truth for the keyword sets.
object BirthdayDetection {
    fun isBirthdayCalendar(displayName: String?): Boolean =
        OccasionDetection.classify(displayName) == OccasionKind.Birthday
}
