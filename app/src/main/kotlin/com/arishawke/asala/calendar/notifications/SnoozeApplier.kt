/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.CalendarContract
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.getSystemService
import com.arishawke.asala.calendar.data.TimeUnits
import timber.log.Timber

internal fun applySnooze(context: Context, alertId: Long, intentEventId: Long, instanceMillis: Long, minutes: Int) {
    // pure helper so the fallback path is covered by SnoozeResolutionTest
    val resolved =
        SnoozeResolution.resolve(
            alertId = alertId,
            alertLookup = { readAlert(context, it) },
            intentEventId = intentEventId,
        )
    if (resolved == null) {
        Timber.w("applySnooze: no usable event id, bailing")
        return
    }
    val (eventId, originalMinutes) = resolved
    Timber.d(
        "applySnooze alertId=%d eventId=%d minutes=%d",
        alertId,
        eventId,
        minutes,
    )

    // cancel first so it clears even if the provider writes fail
    NotificationManagerCompat.from(context).cancel(eventId.toInt())

    if (alertId > 0) {
        // mark original dismissed, then insert a fresh SCHEDULED row below
        markAlertState(context, alertId, CalendarContract.CalendarAlerts.STATE_DISMISSED)
    }

    val triggerAt = System.currentTimeMillis() + minutes * TimeUnits.MillisPerMinute
    if (alertId > 0) {
        // dismiss orphan SCHEDULED rows from earlier snooze cycles first, else every
        // re-snooze leaks one row that nothing prunes. WHERE on writes is fine here;
        // the view-join ambiguity that bit v0.7.0 was a read-only quirk.
        try {
            val dismissOrphans =
                ContentValues().apply {
                    put(
                        CalendarContract.CalendarAlerts.STATE,
                        CalendarContract.CalendarAlerts.STATE_DISMISSED,
                    )
                }
            context.contentResolver.update(
                CalendarContract.CalendarAlerts.CONTENT_URI,
                dismissOrphans,
                "${CalendarContract.CalendarAlerts.EVENT_ID} = ? AND " +
                    "${CalendarContract.CalendarAlerts.BEGIN} = ? AND " +
                    "${CalendarContract.CalendarAlerts.STATE} = ?",
                arrayOf(
                    eventId.toString(),
                    instanceMillis.toString(),
                    CalendarContract.CalendarAlerts.STATE_SCHEDULED.toString(),
                ),
            )
        } catch (t: Throwable) {
            Timber.w(t, "orphan SCHEDULED cleanup failed; continuing")
        }

        val newAlertCv =
            ContentValues().apply {
                put(CalendarContract.CalendarAlerts.EVENT_ID, eventId)
                put(CalendarContract.CalendarAlerts.BEGIN, instanceMillis)
                put(CalendarContract.CalendarAlerts.END, instanceMillis)
                put(CalendarContract.CalendarAlerts.ALARM_TIME, triggerAt)
                put(CalendarContract.CalendarAlerts.STATE, CalendarContract.CalendarAlerts.STATE_SCHEDULED)
                put(CalendarContract.CalendarAlerts.MINUTES, originalMinutes)
            }
        try {
            context.contentResolver.insert(CalendarContract.CalendarAlerts.CONTENT_URI, newAlertCv)
        } catch (t: Throwable) {
            Timber.w(t, "insert new alert row failed")
        }
    }

    val fireIntent =
        Intent(context, ReminderAlarmReceiver::class.java).apply {
            action = ReminderConstants.ACTION_FIRE
            putExtra(ReminderConstants.EXTRA_EVENT_ID, eventId)
            putExtra(ReminderConstants.EXTRA_INSTANCE_MILLIS, instanceMillis)
            putExtra(ReminderConstants.EXTRA_REMINDER_MINUTES, originalMinutes)
        }
    // same request-code shape as ReminderScheduler so re-snoozing the same
    // (event, instance, minutes) updates the alarm in place.
    val pi =
        PendingIntent.getBroadcast(
            context,
            PendingIntentRequestCodes.forAlarm(eventId, instanceMillis, originalMinutes),
            fireIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    val am = context.getSystemService<AlarmManager>() ?: return
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        Timber.d("scheduled inexact wake at %d (+%dm)", triggerAt, minutes)
    } else {
        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        Timber.d("scheduled exact wake at %d (+%dm)", triggerAt, minutes)
    }
}

internal fun readAlert(context: Context, alertId: Long): Pair<Long, Int>? {
    // CalendarAlerts joins view_events, so a plain "_id = ?" is ambiguous and
    // throws SQLiteException; append the id to the URI instead.
    val cols =
        arrayOf(
            CalendarContract.CalendarAlerts.EVENT_ID,
            CalendarContract.CalendarAlerts.MINUTES,
        )
    val uri =
        ContentUris.withAppendedId(
            CalendarContract.CalendarAlerts.CONTENT_URI,
            alertId,
        )
    return context.contentResolver.query(uri, cols, null, null, null)?.use { c ->
        if (!c.moveToFirst()) return@use null
        c.getLong(0) to c.getInt(1)
    }
}

internal fun markAlertState(context: Context, alertId: Long, state: Int) {
    val cv =
        ContentValues().apply {
            put(CalendarContract.CalendarAlerts.STATE, state)
        }
    val uri =
        ContentUris.withAppendedId(
            CalendarContract.CalendarAlerts.CONTENT_URI,
            alertId,
        )
    context.contentResolver.update(uri, cv, null, null)
}
