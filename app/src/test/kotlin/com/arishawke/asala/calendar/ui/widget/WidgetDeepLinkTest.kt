package com.arishawke.asala.calendar.ui.widget

import com.arishawke.asala.calendar.CalendarView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class WidgetDeepLinkTest {
    @Test
    fun `known view names parse`() {
        assertEquals(CalendarView.Month, WidgetDeepLink.parseView("Month"))
        assertEquals(CalendarView.Schedule, WidgetDeepLink.parseView("Schedule"))
    }

    @Test
    fun `null or unknown falls back to Schedule`() {
        assertEquals(CalendarView.Schedule, WidgetDeepLink.parseView(null))
        assertEquals(CalendarView.Schedule, WidgetDeepLink.parseView("nonsense"))
    }

    @Test
    fun `decode returns null when absent or epoch-day missing`() {
        assertNull(WidgetDeepLink.decode(present = false, epochDay = 20000L, viewName = "Schedule"))
        assertNull(WidgetDeepLink.decode(present = true, epochDay = Long.MIN_VALUE, viewName = "Schedule"))
    }

    @Test
    fun `decode builds the date and view when present`() {
        assertEquals(
            LocalDate.ofEpochDay(20000L) to CalendarView.Month,
            WidgetDeepLink.decode(present = true, epochDay = 20000L, viewName = "Month"),
        )
    }
}
