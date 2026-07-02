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
import com.arishawke.asala.calendar.data.TimeUnits
import com.arishawke.asala.calendar.data.instancesUriFor
import com.arishawke.asala.calendar.data.providerCall
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
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

// outcome of one replan cycle. Abort keeps the armed plan untouched: a failed
// provider read (null reminders) must never be diffed as an empty calendar, or
// every armed alarm and pending snooze would be cancelled and the wipe
// persisted (same rule as OccasionSync.readExisting). a successful zero-row
// read still applies, so deleting the last reminder-bearing event cleans up.
internal sealed interface ReplanDecision {
    data object Abort : ReplanDecision

    data class Apply(
        val newPlan: Set<AlarmKey>,
        val toCancel: Set<AlarmKey>,
        val snoozeCancels: Set<Pair<Long, Long>>,
    ) : ReplanDecision
}

// pure; tested by ReminderSchedulerDiffTest
internal fun replanDecision(
    previousPlan: Set<AlarmKey>,
    now: Long,
    zone: ZoneId,
    reminders: List<ScheduledReminder>?,
): ReplanDecision {
    if (reminders == null) return ReplanDecision.Abort
    val newPlan = ReminderScheduler.computePlan(now, zone, reminders)
    // occurrences still present in the provider this cycle. a reminder can leave
    // the plan because it fired (trigger passed) while its occurrence still
    // lives; only occurrences that genuinely vanished (deleted or rescheduled,
    // so no reminder row remains) should lose their snooze. liveness = "has a
    // reminder row in the window"; residual gap: an occurrence whose event
    // already ended falls out of the window, so a snooze set past the event's
    // own end can still be dropped by a routine refresh. the full fix is the
    // tracked robust-snooze-store work.
    val liveOccurrences = reminders.mapTo(mutableSetOf<Pair<Long, Long>>()) { it.eventId to it.instanceStartMillis }
    val toCancel = previousPlan - newPlan
    return ReplanDecision.Apply(
        newPlan = newPlan,
        toCancel = toCancel,
        snoozeCancels = ReminderScheduler.snoozeKeysToCancel(toCancel, liveOccurrences),
    )
}

internal object ReminderScheduler {
    private const val WINDOW_DAYS = 30L
    private const val WINDOW_MILLIS = WINDOW_DAYS * TimeUnits.MillisPerDay

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
        // synced calendars can store MINUTES = -1 (MINUTES_DEFAULT). a timed one
        // would arm at start - (-1) = one minute AFTER the start, and an all-day
        // negative other than -1 anchors whole days late (-1440 -> 9am the day
        // AFTER). only the all-day -1 is benign (-1/1440 = 0 days -> 9am day-of).
        .filterNot { it.minutesBefore < 0 && !(it.allDay && it.minutesBefore == -1) }
        // no instance-start pre-filter: all-day instances are at 00:00 UTC but fire
        // at 9am local, so a same-day all-day reminder has a past instance start and
        // a future trigger. the trailing triggerAtMillis > now is the real guard.
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

    // pure; tested by ReminderSchedulerDiffTest. one occurrence can carry several
    // reminder rows (different lead times) but a single snooze slot, so dedupe the
    // cancelled keys down to their (eventId, instance) pair. a key can leave the
    // plan because it fired (trigger passed) while its occurrence still exists;
    // those keep their snooze. only occurrences absent from liveOccurrences (event
    // deleted or rescheduled away) drop it.
    internal fun snoozeKeysToCancel(
        cancelled: Set<AlarmKey>,
        liveOccurrences: Set<Pair<Long, Long>>,
    ): Set<Pair<Long, Long>> = cancelled.asSequence()
        .map { it.eventId to it.instanceStartMillis }
        .filterNot { it in liveOccurrences }
        .toMutableSet()

    // idempotent; planMutex serializes the observer/boot/foreground callers against races
    suspend fun rescheduleAll(context: Context) = withContext(Dispatchers.IO) {
        planMutex.withLock {
            val now = System.currentTimeMillis()
            val zone = ZoneId.systemDefault()
            val reminders = readUpcomingReminders(context.contentResolver, now, now + WINDOW_MILLIS)

            val am = context.getSystemService<AlarmManager>() ?: return@withLock
            // seed from disk on the first run this process; thereafter the warm
            // in-memory cache is authoritative and equals the last persisted plan.
            val previousPlan = if (planLoaded) lastPlan else ArmedAlarmStore.load(context).also { planLoaded = true }

            val decision = replanDecision(previousPlan, now, zone, reminders)
            if (decision !is ReplanDecision.Apply) {
                Timber.w("reminder replan aborted: provider read failed, keeping the armed plan")
                return@withLock
            }

            decision.toCancel.forEach { key ->
                am.cancel(buildAlarmPendingIntent(context, key))
            }
            // an occurrence that genuinely left the provider (event deleted or
            // rescheduled away) must also drop any pending snooze keyed on its old
            // (eventId, instance): a reschedule would otherwise ring the orphaned
            // snooze at the stale time. a key that left the plan only because it
            // fired is still live, so its snooze survives (audit F1).
            decision.snoozeCancels.forEach { (eventId, instance) ->
                am.cancel(buildSnoozePendingIntent(context, eventId, instance))
            }

            decision.newPlan.forEach { key ->
                val pi = buildAlarmPendingIntent(context, key)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
                    am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, key.triggerAtMillis, pi)
                } else {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, key.triggerAtMillis, pi)
                }
            }

            lastPlan = decision.newPlan
            // arming above stays unconditional: a reboot clears the system alarms
            // while the persisted set still lists them, so a diff-gated arm would
            // skip re-arming them. only the disk write is skipped when nothing
            // changed, to avoid churning DataStore on every no-op observer fire.
            if (decision.newPlan != previousPlan) ArmedAlarmStore.save(context, decision.newPlan)
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

    // matches the slot SnoozeApplier arms (same component, action, request code);
    // extras are ignored by PendingIntent matching, so a bare intent cancels it.
    private fun buildSnoozePendingIntent(context: Context, eventId: Long, instanceMillis: Long): PendingIntent {
        val intent =
            Intent(context, ReminderAlarmReceiver::class.java).apply {
                action = ReminderConstants.ACTION_FIRE
            }
        return PendingIntent.getBroadcast(
            context,
            PendingIntentRequestCodes.forSnoozeAlarm(eventId, instanceMillis),
            intent,
            PendingIntentFlags.UPDATE_IMMUTABLE,
        )
    }

    // null means a provider read failed (distinct from a window with no
    // reminders); the caller aborts the replan rather than diffing against it.
    private fun readUpcomingReminders(
        cr: ContentResolver,
        windowStart: Long,
        windowEnd: Long,
    ): List<ScheduledReminder>? {
        val instances = readInstances(cr, windowStart, windowEnd) ?: return null
        return when {
            instances.isEmpty() -> emptyList()
            else ->
                readReminderMinutes(cr, instances.mapTo(LinkedHashSet()) { it.eventId })
                    ?.let { expandReminders(instances, it) }
        }
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

    // provider reads route through providerCall: CalendarContract.query throws on
    // revoked permission / provider death, and rescheduleAll runs unguarded from
    // onResume, so an unwrapped throw here would crash the app. failure returns
    // null, never an empty list: the caller must be able to tell a failed read
    // from a genuinely empty calendar (see ReplanDecision).
    private fun readInstances(cr: ContentResolver, windowStart: Long, windowEnd: Long): List<ReminderInstance>? =
        providerCall("readInstances", null) {
            val cols =
                arrayOf(
                    CalendarContract.Instances.EVENT_ID,
                    CalendarContract.Instances.BEGIN,
                    CalendarContract.Instances.ALL_DAY,
                    CalendarContract.Instances.STATUS,
                )
            val uri = instancesUriFor(windowStart, windowEnd)
            // provider death presents as a null cursor, not a throw; that is a
            // failed read too.
            val cursor =
                cr.query(uri, cols, null, null, "${CalendarContract.Instances.BEGIN} ASC")
                    ?: return@providerCall null
            cursor.use { ic ->
                val out = mutableListOf<ReminderInstance>()
                while (ic.moveToNext()) {
                    if (ic.getInt(3) == CalendarContract.Instances.STATUS_CANCELED) continue
                    out += ReminderInstance(
                        eventId = ic.getLong(0),
                        instanceStartMillis = ic.getLong(1),
                        allDay = ic.getInt(2) == 1,
                    )
                }
                out
            }
        }

    // one Reminders query per chunk of distinct event ids, replacing the old
    // one-query-per-instance N+1. the whole read shares one providerCall and one
    // null-on-failure contract: a silently dropped chunk would read as "those
    // events lost their reminders" and cancel their alarms.
    private fun readReminderMinutes(cr: ContentResolver, eventIds: Collection<Long>): Map<Long, List<Int>>? =
        providerCall("readReminderMinutes", null) {
            val out = mutableMapOf<Long, MutableList<Int>>()
            val cols = arrayOf(CalendarContract.Reminders.EVENT_ID, CalendarContract.Reminders.MINUTES)
            eventIds.chunked(REMINDER_QUERY_CHUNK).forEach { chunk ->
                val placeholders = chunk.joinToString(",") { "?" }
                val cursor =
                    cr.query(
                        CalendarContract.Reminders.CONTENT_URI,
                        cols,
                        "${CalendarContract.Reminders.EVENT_ID} IN ($placeholders)",
                        chunk.map { it.toString() }.toTypedArray(),
                        null,
                    ) ?: return@providerCall null
                cursor.use { rc ->
                    while (rc.moveToNext()) {
                        out.getOrPut(rc.getLong(0)) { mutableListOf() } += rc.getInt(1)
                    }
                }
            }
            out
        }
}
