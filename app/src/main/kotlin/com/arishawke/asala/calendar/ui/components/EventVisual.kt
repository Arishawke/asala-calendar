/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.ui.components

import android.provider.CalendarContract
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.arishawke.asala.calendar.R
import com.arishawke.asala.calendar.data.EventItem
import com.arishawke.asala.calendar.ui.theme.rememberTimeFormatter
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// Visual decoration derived from `CalendarContract.Events.STATUS`.
// Tentative: italic title. Cancelled: strikethrough title + 50%
// container alpha. Confirmed (default): no decoration. Past-date
// dimming is applied separately at the day-cell / column level and
// multiplies on top of this alpha.
internal data class StatusStyling(
    val titleFontStyle: FontStyle?,
    val titleDecoration: TextDecoration?,
    val containerAlpha: Float,
)

// Birthday leading-icon shared across surfaces. Compact Month chips
// skip the icon (too small to read at the ~12dp chip height). Tint
// defaults to onSurface but callers on tinted backgrounds (multi-day
// bars, Day's all-day list) pass their own contrast color so the
// glyph reads against the bar fill rather than the sheet surface.
@Composable
internal fun BirthdayLeadingIcon(
    size: Dp,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurface,
) {
    Icon(
        painter = painterResource(R.drawable.ic_cake),
        contentDescription = stringResource(R.string.cd_birthday),
        modifier = modifier.size(size),
        tint = tint,
    )
}

internal fun statusStyling(status: Int): StatusStyling = when (status) {
    CalendarContract.Events.STATUS_TENTATIVE -> StatusStyling(
        titleFontStyle = FontStyle.Italic,
        titleDecoration = null,
        containerAlpha = 1f,
    )
    CalendarContract.Events.STATUS_CANCELED -> StatusStyling(
        titleFontStyle = null,
        titleDecoration = TextDecoration.LineThrough,
        containerAlpha = 0.5f,
    )
    else -> StatusStyling(
        titleFontStyle = null,
        titleDecoration = null,
        containerAlpha = 1f,
    )
}

// Unified event-chip primitive used across Month / Schedule / Search.
// Each variant is a different layout of the same logical thing
// (calendar color identifier + title + optional time), so color
// resolution and rendering stay consistent across views.
//
// `event.displayColor` is already the resolved color (per-event
// override > per-calendar override > calendar default) because the
// data layer's `applyColorOverrides` runs in every view ViewModel
// before chips render. Variants render that color directly without
// re-resolving.
//
// Past-date dimming is applied at the day-cell / column / header
// level via `Modifier.alpha(PastDateAlpha)`, not inside individual
// chips, so the chip primitives stay independent of that concern.

// EventChipCompact: month grid. 4dp left color bar plus a one-line
// title. Visual size stays compact (around 12dp tall) so dense days
// can fit multiple chips per cell; the surrounding day cell is the
// 48dp+ touch target.
@Composable
internal fun EventChipCompact(event: EventItem, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val styling = statusStyling(event.status)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (styling.containerAlpha < 1f) Modifier.alpha(styling.containerAlpha) else Modifier)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 2.dp, vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(10.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color(event.displayColor)),
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = event.title.ifBlank { stringResource(R.string.event_no_title) },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            fontStyle = styling.titleFontStyle,
            textDecoration = styling.titleDecoration,
        )
    }
}

// EventChipBlock: week / day timeline. Tinted background fill plus a
// 3dp left color bar plus title (and optional time label when the
// block is tall enough). The drag and positioning concerns live in
// the caller (`ui/week/EventBlock.kt`); this primitive renders the
// content surface only and consumes the alpha through the caller so
// the drag-feedback shift (idle vs dragging) stays in one place.
//
// `shape` carries the rounded-vs-square corner choice for midnight-
// crossing continuation chips. `heightDp` controls whether the time
// row renders (short blocks would clip it). `backgroundAlpha` shifts
// during drag for visual feedback.
@Suppress("LongParameterList")
@Composable
internal fun EventChipBlock(
    event: EventItem,
    shape: Shape,
    heightDp: Dp,
    zone: ZoneId,
    backgroundAlpha: Float,
    modifier: Modifier = Modifier,
    showEndTime: Boolean = false,
    displayEndMillis: Long = event.endMillis,
    segmentIndex: Int = 1,
    segmentCount: Int = 1,
    anchorMillis: Long? = null,
) {
    val color = remember(event.displayColor) { Color(event.displayColor) }
    val styling = statusStyling(event.status)
    Box(
        modifier = modifier
            .then(if (styling.containerAlpha < 1f) Modifier.alpha(styling.containerAlpha) else Modifier)
            .clip(shape)
            .background(color.copy(alpha = backgroundAlpha)),
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .fillMaxSize()
                .background(color),
        )
        EventBlockLabels(
            event = event,
            heightDp = heightDp,
            zone = zone,
            styling = styling,
            showEndTime = showEndTime,
            displayEndMillis = displayEndMillis,
            segmentIndex = segmentIndex,
            segmentCount = segmentCount,
            anchorMillis = anchorMillis,
        )
    }
}

// Title + (when tall enough) start time (and optional end time), inset to
// leave room for the 3dp color bar on the left. Private to EventVisual
// since both EventChipBlock and any future block variant want the same
// label vocabulary.
@Suppress("LongParameterList")
@Composable
private fun EventBlockLabels(
    event: EventItem,
    heightDp: Dp,
    zone: ZoneId,
    styling: StatusStyling,
    showEndTime: Boolean,
    displayEndMillis: Long,
    segmentIndex: Int,
    segmentCount: Int,
    anchorMillis: Long?,
) {
    val timeFmt = rememberTimeFormatter()
    val multiDay = segmentCount > 1
    val baseTitle = event.title.ifBlank { stringResource(R.string.event_no_title) }
    // A midnight crosser carries a "N/total" badge so the second-day slice
    // reads as a continuation, not a separate event.
    val title = if (multiDay) {
        "$baseTitle ${stringResource(R.string.event_segment_badge, segmentIndex, segmentCount)}"
    } else {
        baseTitle
    }
    // Multi-day pieces show one meaningful time via anchorMillis (start on the
    // first piece, end on the last, none on a middle piece). Single-day events
    // keep the original start, plus the clipped end when showEndTime.
    val timeLabel: String? = if (multiDay) {
        anchorMillis?.let { timeFmt.format(Instant.ofEpochMilli(it).atZone(zone)) }
    } else {
        val originalStart = Instant.ofEpochMilli(event.startMillis).atZone(zone)
        if (showEndTime) {
            val displayEnd = Instant.ofEpochMilli(displayEndMillis).atZone(zone)
            "${timeFmt.format(originalStart)} - ${timeFmt.format(displayEnd)}"
        } else {
            timeFmt.format(originalStart)
        }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 6.dp, top = 2.dp, end = 4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (event.isBirthday) {
                BirthdayLeadingIcon(size = 12.dp)
                Spacer(modifier = Modifier.width(4.dp))
            }
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                fontWeight = FontWeight.SemiBold,
                fontStyle = styling.titleFontStyle,
                textDecoration = styling.titleDecoration,
            )
        }
        if (timeLabel != null && heightDp >= TimeLabelMinHeight) {
            Text(
                text = timeLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

// Below this rendered height a second text row would clip; only the
// title shows on small blocks.
private val TimeLabelMinHeight: Dp = 32.dp

// EventChipRow: schedule + search list. 8dp calendar color dot plus
// start time (or "All day" label) plus title, in a horizontal row.
// Min-height 48dp meets the Android touch-target floor.
@Composable
internal fun EventChipRow(
    event: EventItem,
    timeFmt: DateTimeFormatter,
    zone: ZoneId,
    onEventClick: (eventId: Long, instanceMillis: Long) -> Unit,
    verticalPadding: Dp = 6.dp,
) {
    val styling = statusStyling(event.status)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .then(if (styling.containerAlpha < 1f) Modifier.alpha(styling.containerAlpha) else Modifier)
            .clickable { onEventClick(event.eventId, event.startMillis) }
            .padding(horizontal = 16.dp, vertical = verticalPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(Color(event.displayColor)),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Box(
            modifier = Modifier.width(72.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (event.allDay) {
                Text(
                    text = stringResource(R.string.schedule_all_day),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                val start = Instant.ofEpochMilli(event.startMillis).atZone(zone)
                Text(
                    text = timeFmt.format(start),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        if (event.isBirthday) {
            BirthdayLeadingIcon(size = 16.dp)
            Spacer(modifier = Modifier.width(6.dp))
        }
        Text(
            text = event.title.ifBlank { stringResource(R.string.event_no_title) },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            fontStyle = styling.titleFontStyle,
            textDecoration = styling.titleDecoration,
        )
    }
}
