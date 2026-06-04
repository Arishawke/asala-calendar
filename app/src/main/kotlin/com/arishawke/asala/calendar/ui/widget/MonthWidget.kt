/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.ui.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.arishawke.asala.calendar.CalendarView
import com.arishawke.asala.calendar.MainActivity
import com.arishawke.asala.calendar.R
import com.arishawke.asala.calendar.notifications.ReminderConstants
import com.arishawke.asala.calendar.ui.settings.UserPreferences
import com.arishawke.asala.calendar.ui.settings.settingsDataStore
import kotlinx.coroutines.flow.first
import java.time.LocalDate

private const val WEEK_COLUMNS = 7

class MonthWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = MonthWidgetData.load(context)
        val themeMode = UserPreferences(context.settingsDataStore).prefs.first().themeMode
        val resolved = AgendaWidgetTheme.resolve(themeMode, context.resources.configuration.isNight())
        val colors = AgendaWidgetTheme.colors(resolved)
        provideContent { MonthContent(context, snapshot, colors, resolved) }
    }
}

@Composable
private fun MonthContent(
    context: Context,
    snapshot: MonthWidgetSnapshot,
    colors: AgendaWidgetColors,
    theme: ResolvedTheme,
) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(colors.background)
            .cornerRadius(MonthDimens.corner)
            .padding(MonthDimens.pad),
    ) {
        when (snapshot.state) {
            MonthWidgetState.NoPermission ->
                CenteredMessage(context.getString(R.string.widget_grant_access), colors, openApp(context))
            MonthWidgetState.Ready -> {
                val grid = snapshot.grid!!
                // shrink chips-per-cell as the widget gets shorter (down to one + "+N").
                val maxChips = maxChipsPerCell(LocalSize.current.height.value, grid.weeks.size)
                Text(
                    text = monthLabel(grid.month),
                    style = TextStyle(color = colors.onBackground, fontWeight = FontWeight.Bold),
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .clickable(openDate(context, grid.month.atDay(1), CalendarView.Month)),
                )
                WeekdayRow(grid, colors)
                // defaultWeight on each row applied inside Column scope here
                grid.weeks.forEach { week ->
                    WeekRow(context, week, colors, theme, maxChips, GlanceModifier.fillMaxWidth().defaultWeight())
                }
            }
        }
    }
}

@Composable
private fun WeekdayRow(grid: MonthGridData, colors: AgendaWidgetColors) {
    Row(modifier = GlanceModifier.fillMaxWidth().padding(top = MonthDimens.headerGap)) {
        // defaultWeight is RowScope member; applied inside Row lambda
        (0 until WEEK_COLUMNS).forEach { i ->
            Text(
                text = weekdayNarrow(grid.weekStart.plus(i.toLong())),
                style = TextStyle(color = colors.secondary, textAlign = TextAlign.Center),
                modifier = GlanceModifier.defaultWeight(),
            )
        }
    }
}

// suppress: List<MonthDayCell> trips ComposeUnstableCollections; widget
// recomposes on alarm/boot, not per frame, so stability is irrelevant here.
@Suppress("ComposeUnstableCollections")
@Composable
private fun WeekRow(
    context: Context,
    week: List<MonthDayCell>,
    colors: AgendaWidgetColors,
    theme: ResolvedTheme,
    maxChips: Int,
    modifier: GlanceModifier = GlanceModifier,
) {
    Row(modifier = modifier) {
        // defaultWeight on each cell applied inside Row scope here
        week.forEach { cell ->
            // vertical-only cell padding: no horizontal gap, so multi-day band
            // segments in adjacent cells meet edge-to-edge.
            DayCell(context, cell, colors, theme, maxChips, GlanceModifier.defaultWeight().fillMaxHeight().padding(vertical = MonthDimens.cellPad))
        }
    }
}

// suppress: List<MonthCellEvent> is flagged non-stable; widget recomposes on alarm/boot,
// not per frame, so stability is irrelevant here.
@Suppress("ComposeUnstableCollections")
@Composable
private fun DayCell(
    context: Context,
    cell: MonthDayCell,
    colors: AgendaWidgetColors,
    theme: ResolvedTheme,
    maxChips: Int,
    modifier: GlanceModifier = GlanceModifier,
) {
    val numberColor = if (cell.inMonth) colors.onBackground else colors.secondary
    Column(
        horizontalAlignment = Alignment.Start,
        modifier = modifier.clickable(openDate(context, cell.date, CalendarView.Schedule)),
    ) {
        if (cell.isToday) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = GlanceModifier
                    .size(MonthDimens.todayCircle)
                    .cornerRadius(MonthDimens.todayCircle)
                    .background(monthAccent(theme)),
            ) {
                Text(
                    text = cell.date.dayOfMonth.toString(),
                    style = TextStyle(color = ColorProvider(Color.White), fontWeight = FontWeight.Bold),
                )
            }
        } else {
            Text(text = cell.date.dayOfMonth.toString(), style = TextStyle(color = numberColor))
        }
        val shown = cell.events.take(maxChips)
        shown.forEach { chip ->
            // band segments are square and full-bleed so adjacent days fuse into
            // one bar; single-day chips stay rounded and inset as pills.
            val corner = if (chip.multiDay) 0.dp else MonthDimens.chipCorner
            val outer = if (chip.multiDay) 0.dp else MonthDimens.cellPad
            Text(
                text = if (chip.isLabel) chip.title else " ",
                maxLines = 1,
                style = TextStyle(color = colors.onBackground, fontSize = 9.sp),
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .padding(start = outer, end = outer, bottom = MonthDimens.chipGap)
                    .cornerRadius(corner)
                    .background(ColorProvider(Color(chip.colorArgb).copy(alpha = 0.35f)))
                    .padding(horizontal = MonthDimens.chipPadH, vertical = MonthDimens.chipPadV),
            )
        }
        // overflow combines what build dropped with chips hidden by the size cap.
        val more = cell.moreCount + (cell.events.size - shown.size)
        if (more > 0) {
            Text(
                text = "+$more",
                style = TextStyle(color = colors.secondary, fontSize = 9.sp),
            )
        }
    }
}

@Composable
private fun CenteredMessage(text: String, colors: AgendaWidgetColors, onTap: Action) {
    Box(
        modifier = GlanceModifier.fillMaxSize().clickable(onTap),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, style = TextStyle(color = colors.secondary))
    }
}

private fun openApp(context: Context): Action =
    actionStartActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))

private fun openDate(context: Context, date: LocalDate, view: CalendarView): Action = actionStartActivity(
    Intent(context, MainActivity::class.java).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        // unique data per (date, view) so each cell's PendingIntent is distinct;
        // MainActivity reads the extras, not the data.
        data = "asala://date/${date.toEpochDay()}/${view.name}".toUri()
        putExtra(ReminderConstants.EXTRA_OPEN_DATE_FROM_WIDGET, true)
        putExtra(ReminderConstants.EXTRA_OPEN_EPOCHDAY, date.toEpochDay())
        putExtra(ReminderConstants.EXTRA_OPEN_VIEW, view.name)
    },
)
