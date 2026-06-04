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
            MonthEvent(d, d, startMillis = 500, allDay = false, colorArgb = 0x111111, title = "B"),
            MonthEvent(d, d, startMillis = 100, allDay = false, colorArgb = 0x222222, title = "C"),
            MonthEvent(d, d, startMillis = 0, allDay = true, colorArgb = 0x333333, title = "A"),
            MonthEvent(d, d, startMillis = 900, allDay = false, colorArgb = 0x444444, title = "D"),
        )
        val byDate = MonthGridBuilder.eventsByDate(events, DayOfWeek.MONDAY)
        // all-day first, then by startMillis ascending; single-day events are labels
        assertEquals(
            listOf(
                MonthCellEvent("A", 0x333333, isLabel = true, multiDay = false),
                MonthCellEvent("C", 0x222222, isLabel = true, multiDay = false),
                MonthCellEvent("B", 0x111111, isLabel = true, multiDay = false),
                MonthCellEvent("D", 0x444444, isLabel = true, multiDay = false),
            ),
            byDate.getValue(d),
        )
    }

    @Test
    fun `build caps at MAX_CHIPS minus one and reports overflow`() {
        val d = LocalDate.of(2026, 6, 10)
        // 5 events: should show 2 chips (MAX_CHIPS - 1) and moreCount = 3
        val fiveEvents = listOf(
            MonthCellEvent("E1", 1, isLabel = true, multiDay = false),
            MonthCellEvent("E2", 2, isLabel = true, multiDay = false),
            MonthCellEvent("E3", 3, isLabel = true, multiDay = false),
            MonthCellEvent("E4", 4, isLabel = true, multiDay = false),
            MonthCellEvent("E5", 5, isLabel = true, multiDay = false),
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
        val threeEvents = (1..3).map { MonthCellEvent("E$it", it, isLabel = true, multiDay = false) }
        val byDate = mapOf(d to threeEvents)
        val grid = MonthGridBuilder.build(june, DayOfWeek.MONDAY, today, byDate)
        val cell = grid.weeks.flatten().single { it.date == d }
        assertEquals(3, cell.events.size)
        assertEquals(0, cell.moreCount)
    }

    @Test
    fun `multi-day all-day event spans its days, label then strips`() {
        // all-day Jun 8 (Mon) .. Jun 12 (Fri exclusive end Jun 13). week-start Monday.
        val events = listOf(
            MonthEvent(LocalDate.of(2026, 6, 8), LocalDate.of(2026, 6, 12), 0, true, 0x123456, "Trip"),
        )
        val byDate = MonthGridBuilder.eventsByDate(events, DayOfWeek.MONDAY)
        // Jun 8 is the band start AND the week's first column -> label
        assertEquals(
            listOf(MonthCellEvent("Trip", 0x123456, isLabel = true, multiDay = true)),
            byDate.getValue(LocalDate.of(2026, 6, 8)),
        )
        // Jun 9..12 are continuation strips (no title)
        (9..12).forEach { day ->
            assertEquals(
                listOf(MonthCellEvent("Trip", 0x123456, isLabel = false, multiDay = true)),
                byDate.getValue(LocalDate.of(2026, 6, day)),
            )
        }
    }

    @Test
    fun `band re-labels at the start of each week`() {
        // Jun 11 (Thu) .. Jun 15 (Mon). week-start Monday: band crosses into the next week on Jun 15.
        val events = listOf(
            MonthEvent(LocalDate.of(2026, 6, 11), LocalDate.of(2026, 6, 15), 0, false, 0x222222, "Conf"),
        )
        val byDate = MonthGridBuilder.eventsByDate(events, DayOfWeek.MONDAY)
        assertTrue(byDate.getValue(LocalDate.of(2026, 6, 11)).single().isLabel) // band start
        assertTrue(!byDate.getValue(LocalDate.of(2026, 6, 12)).single().isLabel) // strip
        assertTrue(byDate.getValue(LocalDate.of(2026, 6, 15)).single().isLabel) // Monday, new week -> label again
    }

    @Test
    fun `bands keep their slots and single-day events overflow`() {
        val d = LocalDate.of(2026, 6, 10) // Wednesday, mid-week, mid-band
        val events = listOf(
            MonthEvent(LocalDate.of(2026, 6, 9), LocalDate.of(2026, 6, 11), 100, false, 0x111111, "Band A"),
            MonthEvent(LocalDate.of(2026, 6, 9), LocalDate.of(2026, 6, 11), 200, false, 0x222222, "Band B"),
            MonthEvent(d, d, 300, true, 0x333333, "S1"),
            MonthEvent(d, d, 400, false, 0x444444, "S2"),
            MonthEvent(d, d, 500, false, 0x555555, "S3"),
        )
        val grid = MonthGridBuilder.build(
            june,
            DayOfWeek.MONDAY,
            today,
            MonthGridBuilder.eventsByDate(events, DayOfWeek.MONDAY),
        )
        val cell = grid.weeks.flatten().single { it.date == d }
        // bands win the two visible slots; the three single-day events fall under "+N"
        assertEquals(listOf("Band A", "Band B"), cell.events.map { it.title })
        assertEquals(3, cell.moreCount)
        // d is mid-band and not a week-start, so both bands render as strips here
        assertTrue(cell.events.all { !it.isLabel })
    }

    // coveredRange is a pure clamp of [firstDate, lastDate] to the grid window.
    // all-day exclusivity / the midnight-end rule live in EventItem.lastDate
    // (see EventItemVisibilityTest), so these tests only exercise the clamp.
    private val rangeStart = LocalDate.of(2026, 6, 1)
    private val rangeLast = LocalDate.of(2026, 7, 12)

    @Test
    fun `coveredRange keeps an in-grid single day`() {
        val r = MonthGridBuilder.coveredRange(
            LocalDate.of(2026, 6, 10),
            LocalDate.of(2026, 6, 10),
            rangeStart,
            rangeLast,
        )
        assertEquals(LocalDate.of(2026, 6, 10) to LocalDate.of(2026, 6, 10), r)
    }

    @Test
    fun `coveredRange clamps a band that starts before the grid to grid start`() {
        val r = MonthGridBuilder.coveredRange(
            LocalDate.of(2026, 5, 28),
            LocalDate.of(2026, 6, 4),
            rangeStart,
            rangeLast,
        )
        assertEquals(rangeStart to LocalDate.of(2026, 6, 4), r)
    }

    @Test
    fun `coveredRange clamps a band that ends after the grid to grid last`() {
        val r = MonthGridBuilder.coveredRange(
            LocalDate.of(2026, 7, 10),
            LocalDate.of(2026, 7, 20),
            rangeStart,
            rangeLast,
        )
        assertEquals(LocalDate.of(2026, 7, 10) to rangeLast, r)
    }

    @Test
    fun `coveredRange is null when entirely after the grid`() {
        val r = MonthGridBuilder.coveredRange(
            LocalDate.of(2026, 8, 1),
            LocalDate.of(2026, 8, 2),
            rangeStart,
            rangeLast,
        )
        assertNull(r)
    }

    @Test
    fun `coveredRange is null when entirely before the grid`() {
        val r = MonthGridBuilder.coveredRange(
            LocalDate.of(2026, 5, 1),
            LocalDate.of(2026, 5, 31),
            rangeStart,
            rangeLast,
        )
        assertNull(r)
    }
}
