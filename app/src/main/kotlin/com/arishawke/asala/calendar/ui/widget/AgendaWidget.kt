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
import androidx.core.net.toUri
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.arishawke.asala.calendar.MainActivity
import com.arishawke.asala.calendar.R
import com.arishawke.asala.calendar.notifications.ReminderConstants
import com.arishawke.asala.calendar.ui.settings.UserPreferences
import com.arishawke.asala.calendar.ui.settings.settingsDataStore
import kotlinx.coroutines.flow.first

class AgendaWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Single

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = AgendaWidgetData.load(context)
        val themeMode = UserPreferences(context.settingsDataStore).prefs.first().themeMode
        val resolved = AgendaWidgetTheme.resolve(themeMode, context.resources.configuration.isNight())
        val colors = AgendaWidgetTheme.colors(resolved)
        provideContent { AgendaContent(context, snapshot, colors) }
    }
}

@Composable
private fun AgendaContent(context: Context, snapshot: AgendaSnapshot, colors: AgendaWidgetColors) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(colors.background)
            .cornerRadius(WidgetDimens.corner)
            .padding(WidgetDimens.pad),
    ) {
        Text(
            text = headerText(context),
            style = TextStyle(color = colors.onBackground, fontWeight = FontWeight.Bold),
            modifier = GlanceModifier.fillMaxWidth().clickable(openApp(context)),
        )
        Spacer(GlanceModifier.height(WidgetDimens.headerGap))
        when (snapshot.state) {
            AgendaState.NoPermission ->
                CenteredMessage(context.getString(R.string.widget_grant_access), colors, openApp(context))
            AgendaState.NoCalendars ->
                CenteredMessage(context.getString(R.string.widget_no_calendars), colors, null)
            AgendaState.Empty ->
                CenteredMessage(context.getString(R.string.widget_empty), colors, null)
            AgendaState.Loaded ->
                AgendaList(context, snapshot, colors)
        }
    }
}

@Composable
private fun AgendaList(context: Context, snapshot: AgendaSnapshot, colors: AgendaWidgetColors) {
    LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
        snapshot.sections.forEach { section ->
            item { DayHeader(dayLabel(context, section), colors) }
            items(section.events) { event -> EventRow(context, event, colors) }
        }
        if (snapshot.overflowCount > 0) {
            item { MoreRow(context, snapshot.overflowCount, colors) }
        }
    }
}

@Composable
private fun MoreRow(context: Context, overflowCount: Int, colors: AgendaWidgetColors) {
    Text(
        text = context.resources.getQuantityString(R.plurals.widget_more, overflowCount, overflowCount),
        style = TextStyle(color = colors.secondary),
        modifier = GlanceModifier
            .fillMaxWidth()
            .padding(vertical = WidgetDimens.rowPad)
            .clickable(openApp(context)),
    )
}

@Composable
private fun DayHeader(label: String, colors: AgendaWidgetColors) {
    Text(
        text = label,
        style = TextStyle(color = colors.secondary),
        modifier = GlanceModifier.padding(top = WidgetDimens.dayHeaderTop, bottom = WidgetDimens.dayHeaderBottom),
    )
}

// forces opaque so a zero-alpha provider color can't render the bar invisible.
private const val OpaqueAlpha = 0xFF000000.toInt()

@Composable
private fun EventRow(context: Context, event: AgendaEventRow, colors: AgendaWidgetColors) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = GlanceModifier
            .fillMaxWidth()
            .padding(vertical = WidgetDimens.rowPad)
            .clickable(openEvent(context, event)),
    ) {
        Box(
            modifier = GlanceModifier
                .width(WidgetDimens.barWidth)
                .height(WidgetDimens.barHeight)
                .cornerRadius(WidgetDimens.barCorner)
                .background(ColorProvider(Color(event.colorArgb or OpaqueAlpha))),
        ) {}
        Spacer(GlanceModifier.width(WidgetDimens.gap))
        Text(
            text = eventTime(context, event),
            style = TextStyle(color = colors.secondary),
            modifier = GlanceModifier.width(WidgetDimens.timeWidth),
        )
        Text(text = event.title, maxLines = 1, style = TextStyle(color = colors.onBackground))
    }
}

@Composable
private fun CenteredMessage(text: String, colors: AgendaWidgetColors, onTap: Action?) {
    val base = GlanceModifier.fillMaxSize()
    Box(
        modifier = if (onTap != null) base.clickable(onTap) else base,
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, style = TextStyle(color = colors.secondary))
    }
}

private fun openApp(context: Context): Action =
    actionStartActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))

private fun openEvent(context: Context, event: AgendaEventRow): Action = actionStartActivity(
    Intent(context, MainActivity::class.java).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        // unique data so each row's PendingIntent is distinct; MainActivity
        // reads the extras, not the data.
        data = "asala://event/${event.eventId}/${event.instanceStartMillis}".toUri()
        putExtra(ReminderConstants.EXTRA_OPEN_EVENT_FROM_NOTIF, true)
        putExtra(ReminderConstants.EXTRA_EVENT_ID, event.eventId)
        putExtra(ReminderConstants.EXTRA_INSTANCE_MILLIS, event.instanceStartMillis)
    },
)
