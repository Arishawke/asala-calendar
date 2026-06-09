/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CalendarViewKindTest {
    @Test fun timeline_views_are_day_week_threeday() {
        assertTrue(CalendarView.Day.isTimelineView())
        assertTrue(CalendarView.Week.isTimelineView())
        assertTrue(CalendarView.ThreeDay.isTimelineView())
    }

    @Test fun non_timeline_views_navigate_instead() {
        assertFalse(CalendarView.Year.isTimelineView())
        assertFalse(CalendarView.Month.isTimelineView())
        assertFalse(CalendarView.Schedule.isTimelineView())
        assertFalse(CalendarView.Tasks.isTimelineView())
    }
}
