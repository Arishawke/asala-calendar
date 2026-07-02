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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.arishawke.asala.calendar.R
import com.arishawke.asala.calendar.data.EventItem
import com.arishawke.asala.calendar.data.OccasionKind
import com.arishawke.asala.calendar.ui.theme.rememberTimeFormatter
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// tentative: italic. cancelled: strikethrough + 50% alpha. past-date
// dimming multiplies on top of this alpha at the day-cell / column level.
internal data class StatusStyling(
    val titleFontStyle: FontStyle?,
    val titleDecoration: TextDecoration?,
    val containerAlpha: Float,
)

// callers on tinted backgrounds pass their own contrast tint so the
// glyph reads against the bar fill, not the sheet surface.
@Composable
internal fun OccasionLeadingIcon(
    kind: OccasionKind,
    size: Dp,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurface,
) {
    val (iconRes, cdRes) = when (kind) {
        OccasionKind.Birthday -> R.drawable.ic_cake to R.string.cd_birthday
        OccasionKind.Anniversary -> R.drawable.ic_heart to R.string.cd_anniversary
        OccasionKind.None -> return
    }
    Icon(
        painter = painterResource(iconRes),
        contentDescription = stringResource(cdRes),
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

// tentative / cancelled is shown only as italic / strikethrough, which a
// screen reader can't perceive, so fold the status into the title's a11y
// label (e.g. "Standup, Cancelled"). null for confirmed: the title reads as-is.
@Composable
internal fun statusContentDescription(title: String, status: Int): String? = when (status) {
    CalendarContract.Events.STATUS_TENTATIVE ->
        stringResource(R.string.cd_event_with_status, title, stringResource(R.string.status_tentative))
    CalendarContract.Events.STATUS_CANCELED ->
        stringResource(R.string.cd_event_with_status, title, stringResource(R.string.status_cancelled))
    else -> null
}

// shared event-chip primitives across Month / Schedule / Search.
// event.displayColor is already resolved (per-event > per-calendar >
// default) by the data layer's applyColorOverrides, so variants render
// it directly. past-date dimming lives at the day-cell / column level,
// not in the chips.

// month grid: stays ~12dp tall so dense days fit multiple chips; the
// surrounding day cell is the 48dp+ touch target.

// shared with EventChips' capacity math (ui/month/EventChips.kt) so the
// predicted chip-row height matches what actually renders.
internal val ChipVerticalPadding: Dp = 1.dp

@Composable
internal fun EventChipCompact(event: EventItem, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val styling = statusStyling(event.status)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (styling.containerAlpha < 1f) Modifier.alpha(styling.containerAlpha) else Modifier)
            .then(
                // button role + merged title so TalkBack opens the event; the
                // day cell remains the 48dp+ visual target.
                if (onClick != null) {
                    Modifier
                        .semantics(mergeDescendants = true) { role = Role.Button }
                        .clickable(onClick = onClick)
                } else {
                    Modifier
                },
            )
            .padding(horizontal = 2.dp, vertical = ChipVerticalPadding),
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
        // base title only: month grid is too narrow for the computed
        // "N turns 30" / "N's 5th anniversary" form without truncation churn.
        val titleText = event.title.ifBlank { stringResource(R.string.event_no_title) }
        val statusCd = statusContentDescription(titleText, event.status)
        Text(
            text = titleText,
            modifier = if (statusCd != null) Modifier.semantics { contentDescription = statusCd } else Modifier,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            fontStyle = styling.titleFontStyle,
            textDecoration = styling.titleDecoration,
        )
    }
}

// week / day timeline. drag + positioning live in the caller
// (ui/week/EventBlock.kt); this renders the content surface only.
// shape: rounded-vs-square corners for midnight-crossing continuation
// chips. heightDp gates the time row (short blocks clip it).
// backgroundAlpha shifts during drag for feedback.
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

// title + (when tall enough) times, inset past the 3dp color bar.
private val EventBlockLabelTopPadding: Dp = 2.dp

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
    val labelLineHeight = with(LocalDensity.current) { MaterialTheme.typography.labelSmall.lineHeight.toDp() }
    val timeLabelMinHeight = EventBlockLabelTopPadding + labelLineHeight * 2
    val multiDay = segmentCount > 1
    val baseTitle = occasionDisplayTitle(event).ifBlank { stringResource(R.string.event_no_title) }
    // midnight crosser gets a "N/total" badge so a later slice reads
    // as a continuation, not a separate event.
    val title = if (multiDay) {
        "$baseTitle ${stringResource(R.string.event_segment_badge, segmentIndex, segmentCount)}"
    } else {
        baseTitle
    }
    val statusCd = statusContentDescription(title, event.status)
    // multi-day pieces show one time via anchorMillis (start on first,
    // end on last, none in the middle); single-day keeps original start.
    val timeLabel: String? = if (multiDay) {
        anchorMillis?.let { timeFmt.format(Instant.ofEpochMilli(it).atZone(zone)) }
    } else {
        val originalStart = Instant.ofEpochMilli(event.startMillis).atZone(zone)
        if (showEndTime) {
            val displayEnd = Instant.ofEpochMilli(displayEndMillis).atZone(zone)
            stringResource(R.string.time_range_format, timeFmt.format(originalStart), timeFmt.format(displayEnd))
        } else {
            timeFmt.format(originalStart)
        }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 6.dp, top = EventBlockLabelTopPadding, end = 4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (event.occasion != OccasionKind.None) {
                OccasionLeadingIcon(kind = event.occasion, size = 12.dp)
                Spacer(modifier = Modifier.width(4.dp))
            }
            Text(
                text = title,
                modifier = if (statusCd != null) Modifier.semantics { contentDescription = statusCd } else Modifier,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                fontWeight = FontWeight.SemiBold,
                fontStyle = styling.titleFontStyle,
                textDecoration = styling.titleDecoration,
            )
        }
        if (timeLabel != null && heightDp >= timeLabelMinHeight) {
            Text(
                text = timeLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

// schedule + search list. min-height 48dp meets the touch-target floor.
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
        if (event.occasion != OccasionKind.None) {
            OccasionLeadingIcon(kind = event.occasion, size = 16.dp)
            Spacer(modifier = Modifier.width(6.dp))
        }
        val titleText = occasionDisplayTitle(event).ifBlank { stringResource(R.string.event_no_title) }
        val statusCd = statusContentDescription(titleText, event.status)
        Text(
            text = titleText,
            modifier = if (statusCd != null) Modifier.semantics { contentDescription = statusCd } else Modifier,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            fontStyle = styling.titleFontStyle,
            textDecoration = styling.titleDecoration,
        )
    }
}
