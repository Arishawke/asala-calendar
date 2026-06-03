package com.arishawke.asala.calendar.ui.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

class MonthGridBuilderTest {
    private val june = YearMonth.of(2026, 6) // June 1 2026 is a Monday
    private val today = LocalDate.of(2026, 6, 3)

    @Test
    fun `grid start backs up to the chosen week start`() {
        assertEquals(LocalDate.of(2026, 6, 1), MonthGridBuilder.gridStart(june, DayOfWeek.MONDAY))
        assertEquals(LocalDate.of(2026, 5, 31), MonthGridBuilder.gridStart(june, DayOfWeek.SUNDAY))
        assertEquals(LocalDate.of(2026, 5, 30), MonthGridBuilder.gridStart(june, DayOfWeek.SATURDAY))
    }

    @Test
    fun `grid spans only the weeks the month needs`() {
        // June 2026: June 1 is Monday, lead=0, 30 days -> 5 rows
        val juneGrid = MonthGridBuilder.build(june, DayOfWeek.MONDAY, today, emptyMap())
        assertEquals(5, juneGrid.weeks.size)
        assertTrue(juneGrid.weeks.all { it.size == 7 })

        // Feb 2026: Feb 1 is Sunday, lead=0 (week starts Sunday), 28 days -> 4 rows
        val feb = YearMonth.of(2026, 2)
        val febGrid = MonthGridBuilder.build(feb, DayOfWeek.SUNDAY, today, emptyMap())
        assertEquals(4, febGrid.weeks.size)
        assertTrue(febGrid.weeks.all { it.size == 7 })

        // May 2026: May 1 is Friday, lead=5 (week starts Sunday), 31 days -> 6 rows
        val may = YearMonth.of(2026, 5)
        val mayGrid = MonthGridBuilder.build(may, DayOfWeek.SUNDAY, today, emptyMap())
        assertEquals(6, mayGrid.weeks.size)
        assertTrue(mayGrid.weeks.all { it.size == 7 })
    }

    @Test
    fun `grid days matches the rendered week count`() {
        // same months as the grid-span test: load window must equal what build renders
        assertEquals(5 * 7, MonthGridBuilder.gridDays(june, DayOfWeek.MONDAY))
        assertEquals(4 * 7, MonthGridBuilder.gridDays(YearMonth.of(2026, 2), DayOfWeek.SUNDAY))
        assertEquals(6 * 7, MonthGridBuilder.gridDays(YearMonth.of(2026, 5), DayOfWeek.SUNDAY))
    }

    @Test
    fun `in-month flag and today are marked`() {
        val grid = MonthGridBuilder.build(june, DayOfWeek.MONDAY, today, emptyMap())
        val cells = grid.weeks.flatten()
        assertEquals(30, cells.count { it.inMonth })
        assertEquals(today, cells.single { it.isToday }.date)
    }

    @Test
    fun `events are all-day first then by start time`() {
        val d = LocalDate.of(2026, 6, 10)
        val events = listOf(
            MonthEvent(d, startMillis = 500, allDay = false, colorArgb = 0x111111, title = "B"),
            MonthEvent(d, startMillis = 100, allDay = false, colorArgb = 0x222222, title = "C"),
            MonthEvent(d, startMillis = 0, allDay = true, colorArgb = 0x333333, title = "A"),
            MonthEvent(d, startMillis = 900, allDay = false, colorArgb = 0x444444, title = "D"),
        )
        val byDate = MonthGridBuilder.eventsByDate(events)
        // all-day first, then by startMillis ascending; no cap in eventsByDate
        assertEquals(
            listOf(
                MonthCellEvent("A", 0x333333),
                MonthCellEvent("C", 0x222222),
                MonthCellEvent("B", 0x111111),
                MonthCellEvent("D", 0x444444),
            ),
            byDate.getValue(d),
        )
    }

    @Test
    fun `build caps at MAX_CHIPS minus one and reports overflow`() {
        val d = LocalDate.of(2026, 6, 10)
        // 5 events: should show 2 chips (MAX_CHIPS - 1) and moreCount = 3
        val fiveEvents = listOf(
            MonthCellEvent("E1", 1),
            MonthCellEvent("E2", 2),
            MonthCellEvent("E3", 3),
            MonthCellEvent("E4", 4),
            MonthCellEvent("E5", 5),
        )
        val byDate = mapOf(d to fiveEvents)
        val grid = MonthGridBuilder.build(june, DayOfWeek.MONDAY, today, byDate)
        val cell = grid.weeks.flatten().single { it.date == d }
        assertEquals(MonthGridBuilder.MAX_CHIPS - 1, cell.events.size)
        assertEquals(3, cell.moreCount)
    }

    @Test
    fun `build shows all events and no overflow when three or fewer`() {
        val d = LocalDate.of(2026, 6, 10)
        val threeEvents = (1..3).map { MonthCellEvent("E$it", it) }
        val byDate = mapOf(d to threeEvents)
        val grid = MonthGridBuilder.build(june, DayOfWeek.MONDAY, today, byDate)
        val cell = grid.weeks.flatten().single { it.date == d }
        assertEquals(3, cell.events.size)
        assertEquals(0, cell.moreCount)
    }

    private val rangeStart = LocalDate.of(2026, 6, 1)
    private val rangeLast = LocalDate.of(2026, 7, 12)

    @Test
    fun `coveredRange all-day end is exclusive so a one-day all-day event covers one day`() {
        // all-day Jun 10, endDate Jun 11 (exclusive)
        val r = MonthGridBuilder.coveredRange(
            LocalDate.of(2026, 6, 10), LocalDate.of(2026, 6, 11), allDay = true, rangeStart, rangeLast,
        )
        assertEquals(LocalDate.of(2026, 6, 10) to LocalDate.of(2026, 6, 10), r)
    }

    @Test
    fun `coveredRange all-day multi-day event covers start through end minus one`() {
        val r = MonthGridBuilder.coveredRange(
            LocalDate.of(2026, 6, 10), LocalDate.of(2026, 6, 15), allDay = true, rangeStart, rangeLast,
        )
        assertEquals(LocalDate.of(2026, 6, 10) to LocalDate.of(2026, 6, 14), r)
    }

    @Test
    fun `coveredRange timed event ending after midnight spans two days`() {
        // timed Jun 10 18:00 -> Jun 11 01:00; endDate Jun 11 inclusive for timed
        val r = MonthGridBuilder.coveredRange(
            LocalDate.of(2026, 6, 10), LocalDate.of(2026, 6, 11), allDay = false, rangeStart, rangeLast,
        )
        assertEquals(LocalDate.of(2026, 6, 10) to LocalDate.of(2026, 6, 11), r)
    }

    @Test
    fun `coveredRange event starting before the grid clamps to grid start`() {
        val r = MonthGridBuilder.coveredRange(
            LocalDate.of(2026, 5, 28), LocalDate.of(2026, 6, 3), allDay = true, rangeStart, rangeLast,
        )
        assertEquals(rangeStart to LocalDate.of(2026, 6, 2), r) // all-day end exclusive: Jun 3 - 1 = Jun 2
    }

    @Test
    fun `coveredRange event entirely after the grid is null`() {
        val r = MonthGridBuilder.coveredRange(
            LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 2), allDay = true, rangeStart, rangeLast,
        )
        assertNull(r)
    }

    @Test
    fun `coveredRange malformed end before start collapses to the start day`() {
        val r = MonthGridBuilder.coveredRange(
            LocalDate.of(2026, 6, 10), LocalDate.of(2026, 6, 9), allDay = false, rangeStart, rangeLast,
        )
        assertEquals(LocalDate.of(2026, 6, 10) to LocalDate.of(2026, 6, 10), r)
    }
}
