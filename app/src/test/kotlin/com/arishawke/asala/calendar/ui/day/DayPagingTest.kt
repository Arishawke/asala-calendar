package com.arishawke.asala.calendar.ui.day

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class DayPagingTest {
    private val today = LocalDate.of(2026, 6, 3)

    @Test
    fun `today maps to the center page and back`() {
        assertEquals(DayPaging.todayPageIndex, DayPaging.pageForDate(today, today))
        assertEquals(today, DayPaging.dateForPage(today, DayPaging.todayPageIndex))
    }

    @Test
    fun `a far-but-in-range date maps to its exact page, not a clamped edge`() {
        // the reported bug: tapping Sep 9 (98 days out) clamped to Aug 2 under the
        // old 60-day window. the wide window maps it exactly and round-trips.
        val target = LocalDate.of(2026, 9, 9)
        val page = DayPaging.pageForDate(today, target)
        assertEquals(DayPaging.todayPageIndex + 98, page)
        assertEquals(target, DayPaging.dateForPage(today, page))
    }

    @Test
    fun `a past date maps below center and round-trips`() {
        val target = LocalDate.of(2026, 1, 1)
        val page = DayPaging.pageForDate(today, target)
        assertEquals(target, DayPaging.dateForPage(today, page))
    }

    @Test
    fun `dates beyond the window clamp to the edges`() {
        assertEquals(DayPaging.pageCount - 1, DayPaging.pageForDate(today, today.plusYears(10)))
        assertEquals(0, DayPaging.pageForDate(today, today.minusYears(10)))
    }
}
