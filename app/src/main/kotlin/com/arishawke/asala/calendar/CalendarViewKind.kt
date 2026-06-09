/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar

// the three vertical-timeline views; only these get the off-screen pill. the
// rest (Year/Month/Schedule/Tasks) navigate to the date instead.
internal fun CalendarView.isTimelineView(): Boolean =
    this == CalendarView.Day || this == CalendarView.Week || this == CalendarView.ThreeDay
