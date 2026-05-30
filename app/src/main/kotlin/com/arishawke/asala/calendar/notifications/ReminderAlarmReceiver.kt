/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.notifications

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import androidx.core.app.NotificationManagerCompat
import com.arishawke.asala.calendar.R
import com.arishawke.asala.calendar.ui.settings.UserPreferences
import com.arishawke.asala.calendar.ui.settings.settingsDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber

class ReminderAlarmReceiver : BroadcastReceiver() {
    @SuppressLint("MissingPermission") // POST_NOTIFICATIONS checked before scheduling; receiver only fires when granted
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ReminderConstants.ACTION_FIRE) return
        val eventId = intent.getLongExtra(ReminderConstants.EXTRA_EVENT_ID, -1L)
        val instanceMillis = intent.getLongExtra(ReminderConstants.EXTRA_INSTANCE_MILLIS, -1L)
        val minutesBefore = intent.getIntExtra(ReminderConstants.EXTRA_REMINDER_MINUTES, -1)
        Timber.d(
            "alarm fired eventId=%d instance=%d minutesBefore=%d",
            eventId,
            instanceMillis,
            minutesBefore,
        )
        if (eventId <= 0 || instanceMillis <= 0) {
            Timber.w("alarm fired with invalid extras; dropping")
            return
        }

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Wrap the body so a thrown exception (provider hiccup, malformed
                // row, missing string resource) is logged rather than killing the
                // process. goAsync() still gives us until pending.finish() to do
                // the work; without runCatching the throwable would propagate up
                // through the launch scope and crash.
                runCatching {
                    val prefs = UserPreferences(context.settingsDataStore).prefs.first()
                    val event =
                        readEvent(
                            context,
                            eventId,
                            prefs.eventColorOverrides,
                            prefs.calendarColorOverrides,
                        ) ?: run {
                            Timber.w("alarm: event %d not found in provider; dropping", eventId)
                            return@runCatching
                        }
                    val alertId = ensureCalendarAlert(context, eventId, instanceMillis, minutesBefore)
                    val defaultSnoozeMinutes = prefs.defaultSnoozeMinutes
                    val notification =
                        buildReminderNotification(
                            context = context,
                            eventId = eventId,
                            event = event,
                            instanceMillis = instanceMillis,
                            alertId = alertId,
                            defaultSnoozeMinutes = defaultSnoozeMinutes,
                        )
                    NotificationManagerCompat.from(context).notify(eventId.toInt(), notification)
                    Timber.d("posted notification id=%d alertId=%d", eventId, alertId)
                }.onFailure { Timber.e(it, "alarm receiver body threw") }
            } finally {
                pending.finish()
            }
        }
    }

    private fun readEvent(
        context: Context,
        eventId: Long,
        eventColorOverrides: Map<Long, Int>,
        calendarColorOverrides: Map<Long, Int>,
    ): ReminderEventSnapshot? {
        val cols =
            arrayOf(
                CalendarContract.Events.CALENDAR_ID,
                CalendarContract.Events.TITLE,
                CalendarContract.Events.EVENT_LOCATION,
                CalendarContract.Events.ALL_DAY,
                CalendarContract.Events.DTSTART,
                CalendarContract.Events.DTEND,
                CalendarContract.Events.DURATION,
                CalendarContract.Events.DISPLAY_COLOR,
            )
        return context.contentResolver
            .query(
                CalendarContract.Events.CONTENT_URI,
                cols,
                "${CalendarContract.Events._ID} = ?",
                arrayOf(eventId.toString()),
                null,
            )?.use { c ->
                if (!c.moveToFirst()) return@use null
                val calendarIdIdx = c.getColumnIndexOrThrow(CalendarContract.Events.CALENDAR_ID)
                val titleIdx = c.getColumnIndexOrThrow(CalendarContract.Events.TITLE)
                val locationIdx = c.getColumnIndexOrThrow(CalendarContract.Events.EVENT_LOCATION)
                val allDayIdx = c.getColumnIndexOrThrow(CalendarContract.Events.ALL_DAY)
                val dtStartIdx = c.getColumnIndexOrThrow(CalendarContract.Events.DTSTART)
                val dtEndIdx = c.getColumnIndexOrThrow(CalendarContract.Events.DTEND)
                val durationIdx = c.getColumnIndexOrThrow(CalendarContract.Events.DURATION)
                val colorIdx = c.getColumnIndexOrThrow(CalendarContract.Events.DISPLAY_COLOR)
                buildReminderEventSnapshot(
                    title = c.getString(titleIdx),
                    location = c.getString(locationIdx),
                    allDay = c.getInt(allDayIdx) == 1,
                    startMillis = c.getLong(dtStartIdx),
                    dtEnd = if (c.isNull(dtEndIdx)) null else c.getLong(dtEndIdx),
                    durationIso8601 = c.getString(durationIdx),
                    displayColor = resolveReminderColor(
                        providerColor = c.getInt(colorIdx),
                        eventId = eventId,
                        calendarId = c.getLong(calendarIdIdx),
                        eventOverrides = eventColorOverrides,
                        calendarOverrides = calendarColorOverrides,
                    ),
                    fallbackTitle = context.getString(R.string.event_no_title),
                )
            }
    }

    private fun ensureCalendarAlert(context: Context, eventId: Long, instanceMillis: Long, minutesBefore: Int): Long {
        val cv =
            ContentValues().apply {
                put(CalendarContract.CalendarAlerts.EVENT_ID, eventId)
                put(CalendarContract.CalendarAlerts.BEGIN, instanceMillis)
                put(CalendarContract.CalendarAlerts.END, instanceMillis)
                put(CalendarContract.CalendarAlerts.ALARM_TIME, System.currentTimeMillis())
                put(CalendarContract.CalendarAlerts.STATE, CalendarContract.CalendarAlerts.STATE_FIRED)
                put(CalendarContract.CalendarAlerts.MINUTES, minutesBefore.coerceAtLeast(0))
            }
        val uri = context.contentResolver.insert(CalendarContract.CalendarAlerts.CONTENT_URI, cv)
        return uri?.lastPathSegment?.toLongOrNull() ?: -1L
    }
}
