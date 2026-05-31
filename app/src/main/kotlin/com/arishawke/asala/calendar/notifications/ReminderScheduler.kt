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
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.CalendarContract
import androidx.core.content.getSystemService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.ZoneId

internal data class ScheduledReminder(
    val eventId: Long,
    val instanceStartMillis: Long,
    val minutesBefore: Int,
    val allDay: Boolean,
    val cancelled: Boolean,
)

internal data class AlarmKey(
    val eventId: Long,
    val instanceStartMillis: Long,
    val minutesBefore: Int,
    val triggerAtMillis: Long,
)

internal object ReminderScheduler {
    private const val WINDOW_DAYS = 30L
    private const val WINDOW_MILLIS = WINDOW_DAYS * 24 * 60 * 60 * 1000L

    @Volatile
    private var lastPlan: Set<AlarmKey> = emptySet()
    private val planMutex = Mutex()

    // pure; tested by ReminderSchedulerDiffTest
    fun computePlan(now: Long, zone: ZoneId, reminders: List<ScheduledReminder>): Set<AlarmKey> = reminders
        .asSequence()
        .filterNot { it.cancelled }
        .filter { it.instanceStartMillis > now }
        .map { r ->
            AlarmKey(
                eventId = r.eventId,
                instanceStartMillis = r.instanceStartMillis,
                minutesBefore = r.minutesBefore,
                triggerAtMillis =
                ReminderTimeMath.computeAlarmTime(
                    startMillis = r.instanceStartMillis,
                    allDay = r.allDay,
                    minutesBefore = r.minutesBefore,
                    zone = zone,
                ),
            )
        }.filter { it.triggerAtMillis > now }
        .toSet() // de-dupes identical rows

    // returns (toCancel, toArm)
    internal fun diff(previous: Set<AlarmKey>, current: Set<AlarmKey>): Pair<Set<AlarmKey>, Set<AlarmKey>> =
        (previous - current) to (current - previous)

    // idempotent; planMutex serializes the observer/boot/foreground callers against races
    suspend fun rescheduleAll(context: Context) = withContext(Dispatchers.IO) {
        planMutex.withLock {
            val now = System.currentTimeMillis()
            val zone = ZoneId.systemDefault()
            val reminders = readUpcomingReminders(context.contentResolver, now, now + WINDOW_MILLIS)
            val newPlan = computePlan(now, zone, reminders)

            val am = context.getSystemService<AlarmManager>() ?: return@withLock
            val previousPlan = lastPlan

            (previousPlan - newPlan).forEach { key ->
                am.cancel(buildAlarmPendingIntent(context, key))
            }

            newPlan.forEach { key ->
                val pi = buildAlarmPendingIntent(context, key)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
                    am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, key.triggerAtMillis, pi)
                } else {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, key.triggerAtMillis, pi)
                }
            }

            lastPlan = newPlan
        }
    }

    private fun buildAlarmPendingIntent(context: Context, key: AlarmKey): PendingIntent {
        val intent =
            Intent(context, ReminderAlarmReceiver::class.java).apply {
                action = ReminderConstants.ACTION_FIRE
                putExtra(ReminderConstants.EXTRA_EVENT_ID, key.eventId)
                putExtra(ReminderConstants.EXTRA_INSTANCE_MILLIS, key.instanceStartMillis)
                putExtra(ReminderConstants.EXTRA_REMINDER_MINUTES, key.minutesBefore)
            }
        return PendingIntent.getBroadcast(
            context,
            PendingIntentRequestCodes.forAlarm(
                eventId = key.eventId,
                instanceMillis = key.instanceStartMillis,
                minutesBefore = key.minutesBefore,
            ),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun readUpcomingReminders(
        cr: ContentResolver,
        windowStart: Long,
        windowEnd: Long,
    ): List<ScheduledReminder> {
        val out = mutableListOf<ScheduledReminder>()
        val instancesUri =
            CalendarContract.Instances.CONTENT_URI
                .buildUpon()
                .appendPath(windowStart.toString())
                .appendPath(windowEnd.toString())
                .build()

        val instanceCols =
            arrayOf(
                CalendarContract.Instances.EVENT_ID,
                CalendarContract.Instances.BEGIN,
                CalendarContract.Instances.ALL_DAY,
                CalendarContract.Instances.STATUS,
            )

        cr.query(instancesUri, instanceCols, null, null, "${CalendarContract.Instances.BEGIN} ASC")?.use { ic ->
            while (ic.moveToNext()) {
                val eventId = ic.getLong(0)
                val begin = ic.getLong(1)
                val allDay = ic.getInt(2) == 1
                val status = ic.getInt(3)
                if (status == CalendarContract.Instances.STATUS_CANCELED) continue

                val reminderCols = arrayOf(CalendarContract.Reminders.MINUTES)
                cr
                    .query(
                        CalendarContract.Reminders.CONTENT_URI,
                        reminderCols,
                        "${CalendarContract.Reminders.EVENT_ID} = ?",
                        arrayOf(eventId.toString()),
                        null,
                    )?.use { rc ->
                        while (rc.moveToNext()) {
                            out +=
                                ScheduledReminder(
                                    eventId = eventId,
                                    instanceStartMillis = begin,
                                    minutesBefore = rc.getInt(0),
                                    allDay = allDay,
                                    cancelled = false,
                                )
                        }
                    }
            }
        }
        return out
    }
}
