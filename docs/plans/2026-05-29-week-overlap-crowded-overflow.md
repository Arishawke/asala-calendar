# Week Crowded-Overlap Overflow Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** In Week view, when 3 or more events overlap at the same time, keep the first column readable at full width and collapse the rest into a "+N" chip that opens the existing overflow sheet, instead of slicing the column into unreadable strips.

**Architecture:** Add an additive `clusterIndex` to the existing `layOutOverlaps` output so clusters can be grouped. A new pure function `crowdedLayout` partitions a day's laid-out events into visible chips plus per-cluster overflow groups, parameterized by a column threshold. `DayColumn` (shared by Week and Day) renders through `crowdedLayout`; Week passes an `onOverflow` callback (threshold 3), Day passes none (threshold effectively infinite, so its behavior is byte-identical to today). `TimelineGrid` (Week only) hosts the reused Month `DayOverflowSheet`. Times in that sheet already follow the 12h/24h + locale setting via `rememberTimeFormatter`, so no formatting work is needed.

**Tech Stack:** Kotlin, Jetpack Compose, Material 3, JUnit (JVM unit tests).

**Design spec:** [docs/specs/2026-05-29-week-overlap-crowded-overflow-design.md](../specs/2026-05-29-week-overlap-crowded-overflow-design.md)

**Branch:** `feat/week-overlap-overflow` (already created off main).

---

## File structure

- Modify `app/src/main/kotlin/com/arishawke/asala/calendar/ui/timeline/OverlapLayout.kt` — add `clusterIndex` to `LaidOutEvent`, assign it in `layOutOverlaps`.
- Create `app/src/main/kotlin/com/arishawke/asala/calendar/ui/timeline/CrowdedOverflow.kt` — pure partition function `crowdedLayout` + `CrowdedLayout` / `OverflowGroup` data types.
- Create `app/src/main/kotlin/com/arishawke/asala/calendar/ui/week/OverflowChip.kt` — the "+N" chip composable.
- Modify `app/src/main/kotlin/com/arishawke/asala/calendar/ui/week/TimelineGrid.kt` — `DayColumn` renders via `crowdedLayout`; add `onOverflow` param; `TimelineGrid` hosts `DayOverflowSheet` and passes `onOverflow`.
- Modify `app/src/main/res/values/strings.xml` — add the "+N" chip label + its content description.
- Create `app/src/test/kotlin/com/arishawke/asala/calendar/ui/timeline/CrowdedOverflowTest.kt` — unit tests for `crowdedLayout`.
- Modify `CHANGELOG.md` — `[Unreleased]` entry.

`DayScreen.kt` is intentionally **not** modified: its `DayColumn` call omits the new `onOverflow` param (defaults to null), so Day view keeps equal columns.

---

## Task 1: Add `clusterIndex` to the overlap layout

**Files:**
- Modify: `app/src/main/kotlin/com/arishawke/asala/calendar/ui/timeline/OverlapLayout.kt`
- Test: `app/src/test/kotlin/com/arishawke/asala/calendar/ui/timeline/OverlapLayoutTest.kt`

- [ ] **Step 1: Add a failing test for cluster indexing**

Append to `OverlapLayoutTest`:

```kotlin
@Test
fun `separate clusters get distinct cluster indices`() {
    val rows = layOutOverlaps(
        listOf(
            ev("A", 0L, 60L * 60_000), // 0:00-1:00
            ev("B", 60L * 60_000, 120L * 60_000), // 1:00-2:00 (no overlap)
        ),
    )
    val byTitle = rows.associateBy { it.clipped.event.title }
    assertEquals(0, byTitle.getValue("A").clusterIndex)
    assertEquals(1, byTitle.getValue("B").clusterIndex)
}

@Test
fun `overlapping events share one cluster index`() {
    val rows = layOutOverlaps(
        listOf(
            ev("A", 0L, 120L * 60_000), // 0:00-2:00
            ev("B", 60L * 60_000, 180L * 60_000), // 1:00-3:00 (overlaps A)
        ),
    )
    val byTitle = rows.associateBy { it.clipped.event.title }
    assertEquals(byTitle.getValue("A").clusterIndex, byTitle.getValue("B").clusterIndex)
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests "com.arishawke.asala.calendar.ui.timeline.OverlapLayoutTest"`
Expected: FAIL to compile (`clusterIndex` is not a member of `LaidOutEvent`).

- [ ] **Step 3: Add the field and assign it**

In `OverlapLayout.kt`, replace the data class declaration (line 11):

```kotlin
internal data class LaidOutEvent(
    val clipped: DayClippedEvent,
    val columnIndex: Int,
    val clusterWidth: Int,
    val clusterIndex: Int = 0,
)
```

Then thread a counter through `layOutOverlaps`. Add `var clusterIndex = 0` next to `clusterMaxEnd` (after line 23), increment it at the end of `flushCluster` (after `clusterMaxEnd = Long.MIN_VALUE` on line 32 add `clusterIndex++`), and tag each event when added (line 46) with the current index:

```kotlin
clusterStart += LaidOutEvent(ev, col, clusterWidth = 0, clusterIndex = clusterIndex)
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.arishawke.asala.calendar.ui.timeline.OverlapLayoutTest"`
Expected: PASS (all existing assertions plus the two new ones; existing tests are unaffected because they only check `columnIndex` and `clusterWidth`).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/arishawke/asala/calendar/ui/timeline/OverlapLayout.kt \
        app/src/test/kotlin/com/arishawke/asala/calendar/ui/timeline/OverlapLayoutTest.kt
git commit -m "feat(week): tag laid-out events with a cluster index"
```

---

## Task 2: Pure `crowdedLayout` partition function

**Files:**
- Create: `app/src/main/kotlin/com/arishawke/asala/calendar/ui/timeline/CrowdedOverflow.kt`
- Test: `app/src/test/kotlin/com/arishawke/asala/calendar/ui/timeline/CrowdedOverflowTest.kt`

- [ ] **Step 1: Write the failing test**

Create `CrowdedOverflowTest.kt`:

```kotlin
/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.ui.timeline

import com.arishawke.asala.calendar.data.EventItem
import org.junit.Assert.assertEquals
import org.junit.Test

class CrowdedOverflowTest {
    private fun ev(title: String, startMin: Long, endMin: Long): DayClippedEvent {
        val start = startMin * 60_000
        val end = endMin * 60_000
        return DayClippedEvent(
            event = EventItem(
                instanceId = startMin, // unique enough per test row
                eventId = startMin,
                calendarId = 1L,
                title = title,
                startMillis = start,
                endMillis = end,
                allDay = false,
                displayColor = 0,
            ),
            displayStartMillis = start,
            displayEndMillis = end,
            continuedFromPrev = false,
            continuedToNext = false,
        )
    }

    @Test fun `empty input yields empty layout`() {
        val out = crowdedLayout(emptyList())
        assertEquals(0, out.visible.size)
        assertEquals(0, out.overflow.size)
    }

    @Test fun `single event is visible with no overflow`() {
        val out = crowdedLayout(listOf(ev("A", 0, 60)))
        assertEquals(1, out.visible.size)
        assertEquals(0, out.overflow.size)
    }

    @Test fun `two overlapping events stay visible with no overflow`() {
        val out = crowdedLayout(listOf(ev("A", 0, 120), ev("B", 60, 180)))
        assertEquals(2, out.visible.size)
        assertEquals(0, out.overflow.size)
    }

    @Test fun `three concurrent events collapse to column zero plus one overflow group`() {
        val out = crowdedLayout(listOf(ev("A", 0, 120), ev("B", 10, 120), ev("C", 20, 120)))
        // Only column 0 stays visible.
        assertEquals(1, out.visible.size)
        assertEquals(0, out.visible.first().columnIndex)
        assertEquals(1, out.overflow.size)
        assertEquals(2, out.overflow.first().collapsedCount)
        assertEquals(3, out.overflow.first().events.size)
    }

    @Test fun `long chain with max concurrency two does not collapse`() {
        // A,B overlap; B,C overlap; C,D overlap; never 3 at once.
        val out = crowdedLayout(
            listOf(ev("A", 0, 60), ev("B", 30, 90), ev("C", 60, 120), ev("D", 90, 150)),
        )
        assertEquals(4, out.visible.size)
        assertEquals(0, out.overflow.size)
    }

    @Test fun `crowded cluster and a separate event coexist`() {
        val out = crowdedLayout(
            listOf(
                ev("A", 0, 120), ev("B", 10, 120), ev("C", 20, 120), // crowded
                ev("Z", 600, 660), // separate, uncrowded
            ),
        )
        assertEquals(2, out.visible.size) // column-0 of crowded + Z
        assertEquals(1, out.overflow.size)
        assertEquals(2, out.overflow.first().collapsedCount)
    }

    @Test fun `infinite threshold never collapses (day-view behavior)`() {
        val out = crowdedLayout(
            listOf(ev("A", 0, 120), ev("B", 10, 120), ev("C", 20, 120)),
            threshold = Int.MAX_VALUE,
        )
        assertEquals(3, out.visible.size)
        assertEquals(0, out.overflow.size)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.arishawke.asala.calendar.ui.timeline.CrowdedOverflowTest"`
Expected: FAIL to compile (`crowdedLayout`, `CrowdedLayout`, `OverflowGroup` undefined).

- [ ] **Step 3: Implement the partition function**

Create `CrowdedOverflow.kt`:

```kotlin
/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.ui.timeline

import com.arishawke.asala.calendar.data.EventItem

// At or above this many simultaneously overlapping events, a cluster's
// columns get too thin to read in Week, so all but column 0 collapse
// into a single overflow group. Day view passes Int.MAX_VALUE to opt out.
internal const val CrowdedColumnThreshold = 3

// One collapsed overlap cluster. events is the full cluster (for the
// sheet); collapsedCount is how many are hidden behind the "+N" chip;
// topMillis anchors the chip vertically.
internal data class OverflowGroup(
    val collapsedCount: Int,
    val events: List<EventItem>,
    val topMillis: Long,
)

internal data class CrowdedLayout(
    val visible: List<LaidOutEvent>,
    val overflow: List<OverflowGroup>,
)

// Partitions a day's clipped events into the chips to draw plus the
// overflow groups. Clusters narrower than threshold render unchanged
// (every event visible). Crowded clusters keep only column 0 and emit
// one OverflowGroup for the rest.
internal fun crowdedLayout(
    events: List<DayClippedEvent>,
    threshold: Int = CrowdedColumnThreshold,
): CrowdedLayout {
    val laid = layOutOverlaps(events)
    val visible = ArrayList<LaidOutEvent>(laid.size)
    val overflow = ArrayList<OverflowGroup>()
    laid.groupBy { it.clusterIndex }.forEach { (_, cluster) ->
        val width = cluster.first().clusterWidth
        if (width < threshold) {
            visible += cluster
        } else {
            visible += cluster.filter { it.columnIndex == 0 }
            val collapsed = cluster.filter { it.columnIndex != 0 }
            overflow += OverflowGroup(
                collapsedCount = collapsed.size,
                events = cluster.map { it.clipped.event }.distinctBy { it.instanceId },
                topMillis = cluster.minOf { it.clipped.displayStartMillis },
            )
        }
    }
    return CrowdedLayout(visible = visible, overflow = overflow)
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.arishawke.asala.calendar.ui.timeline.CrowdedOverflowTest"`
Expected: PASS (all seven tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/arishawke/asala/calendar/ui/timeline/CrowdedOverflow.kt \
        app/src/test/kotlin/com/arishawke/asala/calendar/ui/timeline/CrowdedOverflowTest.kt
git commit -m "feat(week): add crowdedLayout overflow partition"
```

---

## Task 3: The "+N" overflow chip composable + strings

**Files:**
- Create: `app/src/main/kotlin/com/arishawke/asala/calendar/ui/week/OverflowChip.kt`
- Modify: `app/src/main/res/values/strings.xml`

- [ ] **Step 1: Add the string resources**

In `strings.xml`, add (near the other Week/schedule strings):

```xml
<string name="week_overflow_count">+%1$d</string>
<string name="week_overflow_content_desc">Show %1$d more overlapping events</string>
```

(`HardcodedText` is promoted to a lint error in this project, so the chip text must come from a resource.)

- [ ] **Step 2: Implement the chip**

Create `OverflowChip.kt`:

```kotlin
/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.ui.week

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.arishawke.asala.calendar.R

// Compact "+N" affordance shown on a crowded overlap cluster. Tapping it
// opens the overflow sheet listing every event at that time.
@Composable
internal fun OverflowChip(
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = stringResource(R.string.week_overflow_count, count)
    val desc = stringResource(R.string.week_overflow_content_desc, count)
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = modifier
            .heightIn(min = 24.dp)
            .semantics { contentDescription = desc },
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}
```

- [ ] **Step 3: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/kotlin/com/arishawke/asala/calendar/ui/week/OverflowChip.kt \
        app/src/main/res/values/strings.xml
git commit -m "feat(week): add overflow count chip"
```

---

## Task 4: Render `DayColumn` through `crowdedLayout`

**Files:**
- Modify: `app/src/main/kotlin/com/arishawke/asala/calendar/ui/week/TimelineGrid.kt`

This task changes `DayColumn` to route through `crowdedLayout`. With `onOverflow == null` (Day view) the threshold is `Int.MAX_VALUE`, so the output is identical to today.

- [ ] **Step 1: Add the `onOverflow` parameter to `DayColumn`**

In the `DayColumn` signature (after `showEndTime: Boolean = false,` on line 132) add:

```kotlin
    onOverflow: ((date: LocalDate, events: List<EventItem>) -> Unit)? = null,
```

- [ ] **Step 2: Replace the render loop**

Replace the existing block (lines 157-181, from `layOutOverlaps(events).forEach { laid ->` through its closing `}` before the now-line comment) with:

```kotlin
        val threshold = if (onOverflow != null) {
            com.arishawke.asala.calendar.ui.timeline.CrowdedColumnThreshold
        } else {
            Int.MAX_VALUE
        }
        val crowded = com.arishawke.asala.calendar.ui.timeline.crowdedLayout(events, threshold)
        crowded.visible.forEach { laid ->
            // A crowded cluster keeps only column 0; render it full width.
            // Everything else divides the column as before.
            val isCrowdedPrimary = laid.clusterWidth >= threshold
            val perColumnWidth = if (isCrowdedPrimary) columnWidth else columnWidth / laid.clusterWidth
            val xOffset = if (isCrowdedPrimary) 0.dp else perColumnWidth * laid.columnIndex
            val originalEvent = laid.clipped.event
            EventBlock(
                clipped = laid.clipped,
                zone = zone,
                dayColumnWidthPx = columnWidthPx,
                weekDayIndex = weekDayIndex,
                weekDayCount = weekDayCount,
                showEndTime = showEndTime,
                onClick = onEventClick?.let { cb ->
                    { cb(originalEvent.eventId, originalEvent.startMillis) }
                },
                onReschedule = onReschedule?.let { cb ->
                    { newStart -> cb(originalEvent.eventId, originalEvent.startMillis, newStart) }
                },
                modifier = Modifier
                    .width(perColumnWidth)
                    .offset(x = xOffset),
            )
        }
        crowded.overflow.forEach { group ->
            val yDp = with(density) { (HourHeight.toPx() * group.topMillis.minutesOfDay(zone) / 60f).toDp() }
            onOverflow?.let { cb ->
                OverflowChip(
                    count = group.collapsedCount,
                    onClick = { cb(date, group.events) },
                    modifier = Modifier
                        .align(androidx.compose.ui.Alignment.TopEnd)
                        .offset(y = yDp)
                        .padding(top = 2.dp, end = 2.dp),
                )
            }
        }
```

- [ ] **Step 3: Add the `minutesOfDay` helper**

The overflow chip anchors at the cluster's start time. Add this private helper at the bottom of `TimelineGrid.kt` (after `WorkingHoursDim`):

```kotlin
// Minutes from local midnight for an epoch-millis instant, used to place
// the overflow chip at the top of its cluster.
private fun Long.minutesOfDay(zone: ZoneId): Int {
    val t = java.time.Instant.ofEpochMilli(this).atZone(zone).toLocalTime()
    return t.hour * 60 + t.minute
}
```

- [ ] **Step 4: Verify the build (Day view behavior unchanged)**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL. With no `onOverflow` passed (Day view, and Week until Task 5), `threshold == Int.MAX_VALUE`, so `crowded.visible` is every event with its original `columnIndex`/`clusterWidth` and `crowded.overflow` is empty — pixel-identical to the previous loop.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/arishawke/asala/calendar/ui/week/TimelineGrid.kt
git commit -m "feat(week): route DayColumn through crowdedLayout (no-op until wired)"
```

---

## Task 5: Wire the overflow sheet into Week

**Files:**
- Modify: `app/src/main/kotlin/com/arishawke/asala/calendar/ui/week/TimelineGrid.kt`

`TimelineGrid` is Week-only (Day view uses `DayColumn` directly). Host the reused Month `DayOverflowSheet` here and pass `onOverflow` down so the crowded behavior activates only in Week.

- [ ] **Step 1: Add imports**

Add to the imports in `TimelineGrid.kt`:

```kotlin
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.arishawke.asala.calendar.ui.month.DayOverflowSheet
```

- [ ] **Step 2: Hold sheet state and pass `onOverflow`**

Inside `TimelineGrid`, just before the `Box(...)` (after line 79 `val nowMinutes = ...`), add:

```kotlin
    var overflowSheet by remember { mutableStateOf<Pair<LocalDate, List<EventItem>>?>(null) }
```

In the `DayColumn(...)` call inside the `days.forEachIndexed` loop, add this argument (alongside `onEventClick`):

```kotlin
                    onOverflow = { date, evs -> overflowSheet = date to evs },
```

- [ ] **Step 3: Render the sheet**

Immediately after the closing brace of the outer `Box { ... }` (the `verticalScroll` container), still inside `TimelineGrid`, add:

```kotlin
    overflowSheet?.let { (date, evs) ->
        DayOverflowSheet(
            date = date,
            events = evs,
            onDismiss = { overflowSheet = null },
            onEventClick = { eventId, instanceMillis ->
                overflowSheet = null
                onEventClick?.invoke(eventId, instanceMillis)
            },
        )
    }
```

- [ ] **Step 4: Verify the build**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Run the full local gate**

Run: `./gradlew :app:spotlessKotlinCheck :app:detekt :app:lintDebug :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL. (If `detekt` flags `DayColumn` for too many parameters, it already carries `@Suppress("LongParameterList")`; keep it.)

- [ ] **Step 6: Commit**

```bash
git add app/src/main/kotlin/com/arishawke/asala/calendar/ui/week/TimelineGrid.kt
git commit -m "feat(week): collapse 3+ overlaps into an overflow sheet"
```

---

## Task 6: Changelog + device smoke test

**Files:**
- Modify: `CHANGELOG.md`

- [ ] **Step 1: Add the changelog entry**

Under `## [Unreleased]` in `CHANGELOG.md`, add an `### Added` entry (create the section if absent):

```markdown
### Added
- Week view collapses crowded overlaps. When three or more events
  overlap at the same time, the first stays readable at full width and
  the rest move into a "+N" chip that opens a sheet listing every event
  at that time, instead of slicing the day column into unreadable
  strips. One and two overlapping events are unchanged, and Day view
  keeps its side-by-side columns.
```

- [ ] **Step 2: Commit**

```bash
git add CHANGELOG.md
git commit -m "docs: changelog for Week crowded-overlap overflow"
```

- [ ] **Step 3: Device smoke test (fresh install)**

```bash
adb uninstall com.arishawke.asala.calendar
./gradlew :app:installDebug
```

Then on the device, verify:
- A Week day with 3+ truly concurrent events shows one readable card plus a "+N" chip; tapping the chip opens the sheet; tapping a row opens that event.
- Times in the sheet render in the active format: toggle Settings time format between 12-hour and 24-hour and reopen the sheet.
- A Week day with exactly 2 overlapping events still shows two side-by-side columns (no chip).
- Open the same crowded day in **Day view**: events show as side-by-side columns, no chip.
- Drag-to-reschedule still works on the visible Week card.

Record the results in the PR description. Note any failures rather than marking the task done.

---

## Self-review

- **Spec coverage:** trigger at 3+ (Task 2 threshold), keep column 0 / collapse rest (Task 2), "+N" chip → reused sheet (Tasks 3+5), Week-only gating (Task 4 `onOverflow`/threshold, Task 5 wiring), time-format adherence (verified, no code), drag/all-day untouched (no changes to those paths), additive `clusterIndex` (Task 1), tests for the partition (Task 2). All covered.
- **Deferred from spec (intentional):** the decorative "deck" shadow behind the primary card and the optional sheet time-range subtitle are not implemented in v1; the functional collapse + chip + sheet is the deliverable. Revisit as polish if desired.
- **Type consistency:** `crowdedLayout(events, threshold)` returns `CrowdedLayout(visible, overflow)`; `OverflowGroup(collapsedCount, events, topMillis)`; `OverflowChip(count, onClick, modifier)`; `DayColumn(..., onOverflow: (LocalDate, List<EventItem>) -> Unit)`; `DayOverflowSheet(date, events, onDismiss, onEventClick)`. Names are consistent across tasks.
- **Placeholder scan:** none; every code step shows full code.
