package com.arishawke.asala.calendar.ui.widget

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class AgendaDayGroupingTest {
    private val today = LocalDate.of(2026, 6, 3)

    private fun row(
        date: LocalDate,
        startMillis: Long,
        allDay: Boolean = false,
        id: Long = startMillis,
        lastDate: LocalDate = date,
    ) = AgendaEventRow(
        eventId = id,
        instanceStartMillis = startMillis,
        title = "e$id",
        startMillis = startMillis,
        allDay = allDay,
        colorArgb = 0,
        date = date,
        lastDate = lastDate,
    )

    @Test
    fun `empty input yields no sections`() {
        assertEquals(emptyList<AgendaDaySection>(), AgendaDayGrouping.group(emptyList(), today))
    }

    @Test
    fun `labels today, tomorrow, and other, ordered by date`() {
        val sections = AgendaDayGrouping.group(
            listOf(
                row(today.plusDays(2), 300),
                row(today, 100),
                row(today.plusDays(1), 200),
            ),
            today,
        )
        assertEquals(listOf(today, today.plusDays(1), today.plusDays(2)), sections.map { it.date })
        assertEquals(
            listOf(RelativeDay.Today, RelativeDay.Tomorrow, RelativeDay.Other),
            sections.map { it.relativeDay },
        )
    }

    @Test
    fun `within a day, all-day sorts before timed, then by start time`() {
        val sections = AgendaDayGrouping.group(
            listOf(
                row(today, 900, allDay = false, id = 3),
                row(today, 100, allDay = false, id = 2),
                row(today, 0, allDay = true, id = 1),
            ),
            today,
        )
        assertEquals(1, sections.size)
        assertEquals(listOf(1L, 2L, 3L), sections.first().events.map { it.eventId })
    }

    @Test
    fun `fully past events are dropped`() {
        // started and ended before today (lastDate defaults to its start day).
        val sections = AgendaDayGrouping.group(
            listOf(row(today.minusDays(1), 50), row(today, 100)),
            today,
        )
        assertEquals(listOf(today), sections.map { it.date })
    }

    @Test
    fun `cap keeps everything and reports zero overflow when under the limit`() {
        val sections = AgendaDayGrouping.group(
            listOf(row(today, 100), row(today.plusDays(1), 200)),
            today,
        )
        val (capped, overflow) = AgendaDayGrouping.cap(sections, maxEvents = 5)
        assertEquals(sections, capped)
        assertEquals(0, overflow)
    }

    @Test
    fun `cap keeps the soonest events and reports the dropped count`() {
        // four events across two days; cap at 3 keeps day one (2) plus the
        // first of day two, dropping one and reporting overflow 1.
        val sections = AgendaDayGrouping.group(
            listOf(
                row(today, 100, id = 1),
                row(today, 200, id = 2),
                row(today.plusDays(1), 300, id = 3),
                row(today.plusDays(1), 400, id = 4),
            ),
            today,
        )
        val (capped, overflow) = AgendaDayGrouping.cap(sections, maxEvents = 3)
        assertEquals(listOf(1L, 2L, 3L), capped.flatMap { s -> s.events.map { it.eventId } })
        assertEquals(1, overflow)
    }

    @Test
    fun `cap drops fully-emptied trailing sections`() {
        val sections = AgendaDayGrouping.group(
            listOf(row(today, 100, id = 1), row(today.plusDays(1), 200, id = 2)),
            today,
        )
        val (capped, overflow) = AgendaDayGrouping.cap(sections, maxEvents = 1)
        assertEquals(listOf(today), capped.map { it.date })
        assertEquals(1, overflow)
    }

    @Test
    fun `an event that started before today but is still running shows under today`() {
        // multi-day event begun two days ago, still covering today: it must not
        // vanish the way grouping by start day alone would drop it.
        val sections = AgendaDayGrouping.group(
            listOf(
                row(date = today.minusDays(2), startMillis = 50, lastDate = today, id = 1),
                row(date = today, startMillis = 100, id = 2),
            ),
            today,
        )
        assertEquals(listOf(today), sections.map { it.date })
        assertEquals(RelativeDay.Today, sections.first().relativeDay)
        // ongoing event (earlier start) leads, then today's event.
        assertEquals(listOf(1L, 2L), sections.first().events.map { it.eventId })
    }
}
