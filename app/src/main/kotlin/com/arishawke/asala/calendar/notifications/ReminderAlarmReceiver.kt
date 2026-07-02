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
import com.arishawke.asala.calendar.data.instancesUriFor
import com.arishawke.asala.calendar.data.providerCall
import com.arishawke.asala.calendar.ui.settings.UserPreferences
import com.arishawke.asala.calendar.ui.settings.settingsDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber

internal data class FireWindowInstance(val eventId: Long, val beginMillis: Long, val cancelled: Boolean)

// pure; tested by ReminderFireLivenessTest. the armed (eventId, instance) pair
// must still exist exactly as armed: an EXDATE'd or cancelled occurrence, a
// moved one (BEGIN differs), or a deleted event (no instance rows at all) must
// not ring its stale alarm or snooze. the scheduler's snooze-cancel heuristic
// cannot reach a snooze whose plan key already fired, so this fire-time check
// is what actually suppresses those ghosts.
internal fun anyLiveOccurrenceMatches(rows: List<FireWindowInstance>, eventId: Long, instanceMillis: Long): Boolean =
    rows.any { it.eventId == eventId && it.beginMillis == instanceMillis && !it.cancelled }

class ReminderAlarmReceiver : BroadcastReceiver() {
    @SuppressLint("MissingPermission") // notify() is gated on areNotificationsEnabled() at fire time below
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ReminderConstants.ACTION_FIRE) return
        val eventId = intent.getLongExtra(ReminderConstants.EXTRA_EVENT_ID, -1L)
        val instanceMillis = intent.getLongExtra(ReminderConstants.EXTRA_INSTANCE_MILLIS, -1L)
        // coerce -1 default-reminder offsets to 0 so the shade id matches the stored
        // alert MINUTES and the snooze cancel
        val minutesBefore = intent.getIntExtra(ReminderConstants.EXTRA_REMINDER_MINUTES, -1).coerceAtLeast(0)
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
                // log rather than crash the process on a provider hiccup or bad row
                runCatching {
                    if (!occurrenceStillExists(context, eventId, instanceMillis)) {
                        Timber.w("alarm: occurrence (%d, %d) no longer exists; dropping", eventId, instanceMillis)
                        return@runCatching
                    }
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
                            minutesBefore = minutesBefore,
                            alertId = alertId,
                            defaultSnoozeMinutes = defaultSnoozeMinutes,
                        )
                    val notificationId =
                        PendingIntentRequestCodes.forNotification(eventId, instanceMillis, minutesBefore)
                    val manager = NotificationManagerCompat.from(context)
                    // recheck at fire time: notifications can be revoked after the
                    // alarm was armed, in which case notify() silently no-ops.
                    if (manager.areNotificationsEnabled()) {
                        manager.notify(notificationId, notification)
                        Timber.d("posted notification event=%d alertId=%d", eventId, alertId)
                    } else {
                        Timber.w("notifications disabled at fire time; reminder for event %d not shown", eventId)
                    }
                }.onFailure { Timber.e(it, "alarm receiver body threw") }
            } finally {
                pending.finish()
            }
        }
    }

    // one Instances query at fire time; a failed read (throw or null cursor)
    // fails toward RINGING, never toward silence: the inverse of the scheduler's
    // replan-abort rule, because a stale ring beats a silently missed reminder.
    private fun occurrenceStillExists(context: Context, eventId: Long, instanceMillis: Long): Boolean {
        val rows =
            providerCall("readFireWindow", null) {
                val cols =
                    arrayOf(
                        CalendarContract.Instances.EVENT_ID,
                        CalendarContract.Instances.BEGIN,
                        CalendarContract.Instances.STATUS,
                    )
                val cursor =
                    context.contentResolver.query(
                        instancesUriFor(instanceMillis, instanceMillis + 1),
                        cols,
                        null,
                        null,
                        null,
                    ) ?: return@providerCall null
                cursor.use { c ->
                    val out = mutableListOf<FireWindowInstance>()
                    while (c.moveToNext()) {
                        out += FireWindowInstance(
                            eventId = c.getLong(0),
                            beginMillis = c.getLong(1),
                            cancelled = c.getInt(2) == CalendarContract.Instances.STATUS_CANCELED,
                        )
                    }
                    out
                }
            } ?: return true
        return anyLiveOccurrenceMatches(rows, eventId, instanceMillis)
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
