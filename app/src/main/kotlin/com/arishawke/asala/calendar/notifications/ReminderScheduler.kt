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
import com.arishawke.asala.calendar.PendingIntentFlags
import com.arishawke.asala.calendar.data.instancesUriFor
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

internal data class ReminderInstance(val eventId: Long, val instanceStartMillis: Long, val allDay: Boolean)

internal object ReminderScheduler {
    private const val WINDOW_DAYS = 30L
    private const val WINDOW_MILLIS = WINDOW_DAYS * 24 * 60 * 60 * 1000L

    // SQLite caps bound args near 999; chunk distinct event ids well under it.
    private const val REMINDER_QUERY_CHUNK = 900

    @Volatile
    private var lastPlan: Set<AlarmKey> = emptySet()

    // false until the persisted plan is loaded once per process, so a cold start
    // diffs against what was actually armed before death, not an empty set.
    @Volatile
    private var planLoaded = false
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
            // seed from disk on the first run this process; thereafter the warm
            // in-memory cache is authoritative and equals the last persisted plan.
            val previousPlan = if (planLoaded) lastPlan else ArmedAlarmStore.load(context).also { planLoaded = true }

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
            ArmedAlarmStore.save(context, newPlan)
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
            PendingIntentFlags.UPDATE_IMMUTABLE,
        )
    }

    private fun readUpcomingReminders(
        cr: ContentResolver,
        windowStart: Long,
        windowEnd: Long,
    ): List<ScheduledReminder> {
        val instances = readInstances(cr, windowStart, windowEnd)
        if (instances.isEmpty()) return emptyList()
        val minutesByEvent = readReminderMinutes(cr, instances.mapTo(LinkedHashSet()) { it.eventId })
        return expandReminders(instances, minutesByEvent)
    }

    // pure join: one ScheduledReminder per (instance, reminder of its event).
    // events with no reminders contribute nothing. tested by ReminderExpandTest.
    internal fun expandReminders(
        instances: List<ReminderInstance>,
        minutesByEvent: Map<Long, List<Int>>,
    ): List<ScheduledReminder> = instances.flatMap { row ->
        minutesByEvent[row.eventId].orEmpty().map { minutes ->
            ScheduledReminder(
                eventId = row.eventId,
                instanceStartMillis = row.instanceStartMillis,
                minutesBefore = minutes,
                allDay = row.allDay,
                cancelled = false,
            )
        }
    }

    private fun readInstances(cr: ContentResolver, windowStart: Long, windowEnd: Long): List<ReminderInstance> {
        val out = mutableListOf<ReminderInstance>()
        val cols =
            arrayOf(
                CalendarContract.Instances.EVENT_ID,
                CalendarContract.Instances.BEGIN,
                CalendarContract.Instances.ALL_DAY,
                CalendarContract.Instances.STATUS,
            )
        val uri = instancesUriFor(windowStart, windowEnd)
        cr.query(uri, cols, null, null, "${CalendarContract.Instances.BEGIN} ASC")?.use { ic ->
            while (ic.moveToNext()) {
                if (ic.getInt(3) == CalendarContract.Instances.STATUS_CANCELED) continue
                out += ReminderInstance(
                    eventId = ic.getLong(0),
                    instanceStartMillis = ic.getLong(1),
                    allDay = ic.getInt(2) == 1,
                )
            }
        }
        return out
    }

    // one Reminders query per chunk of distinct event ids, replacing the old
    // one-query-per-instance N+1.
    private fun readReminderMinutes(cr: ContentResolver, eventIds: Collection<Long>): Map<Long, List<Int>> {
        val out = mutableMapOf<Long, MutableList<Int>>()
        val cols = arrayOf(CalendarContract.Reminders.EVENT_ID, CalendarContract.Reminders.MINUTES)
        eventIds.chunked(REMINDER_QUERY_CHUNK).forEach { chunk ->
            val placeholders = chunk.joinToString(",") { "?" }
            cr.query(
                CalendarContract.Reminders.CONTENT_URI,
                cols,
                "${CalendarContract.Reminders.EVENT_ID} IN ($placeholders)",
                chunk.map { it.toString() }.toTypedArray(),
                null,
            )?.use { rc ->
                while (rc.moveToNext()) {
                    out.getOrPut(rc.getLong(0)) { mutableListOf() } += rc.getInt(1)
                }
            }
        }
        return out
    }
}
