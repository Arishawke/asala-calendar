/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.notifications

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import com.arishawke.asala.calendar.PendingIntentFlags
import com.arishawke.asala.calendar.data.syncOccasionsIfEnabled
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.Instant
import java.time.ZoneId

// app-reserved, distinct from the widget refresh codes (4201/4202).
private const val RE_ARM_REQUEST_CODE = 4301

// next local midnight. re-arming at the day boundary slides ReminderScheduler's
// 30-day window forward and re-anchors all-day reminders, without depending on
// the app being opened. zone-aware so DST days resolve to the real midnight.
internal fun nextReArmTime(nowMillis: Long, zone: ZoneId): Long = Instant.ofEpochMilli(nowMillis)
    .atZone(zone)
    .toLocalDate()
    .plusDays(1)
    .atStartOfDay(zone)
    .toInstant()
    .toEpochMilli()

// a self-rescheduling daily tick: without it, reminders are only armed for the
// next 30 days and the window slides only when the app is opened or the calendar
// changes, so a far-future reminder could silently never fire. mirrors
// WidgetRefreshScheduler's midnight refresh.
object ReminderReArmScheduler {
    fun scheduleNext(context: Context) {
        val am = context.getSystemService<AlarmManager>() ?: return
        val at = nextReArmTime(System.currentTimeMillis(), ZoneId.systemDefault())
        // inexact on purpose: a daily window-slide does not need exact timing and
        // must not consume the exact-alarm budget. wakeup so the slide still runs
        // with the screen off, else an early-morning reminder could re-arm too late.
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pendingIntent(context))
    }

    private fun pendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        RE_ARM_REQUEST_CODE,
        Intent(context, ReminderReArmReceiver::class.java),
        PendingIntentFlags.UPDATE_IMMUTABLE,
    )
}

class ReminderReArmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        // re-arm tomorrow's tick first so the daily heartbeat survives even when
        // the reschedule below is skipped or fails.
        ReminderReArmScheduler.scheduleNext(app)

        if (ContextCompat.checkSelfPermission(
                app,
                Manifest.permission.READ_CALENDAR,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                runCatching { ReminderScheduler.rescheduleAll(app) }
                    .onFailure { Timber.e(it, "reminder re-arm threw") }
                // reuses this daily tick to keep occasion events fresh rather than
                // adding a second WorkManager job; the function's own gate makes it
                // a no-op unless the feature is on and contacts access is granted.
                runCatching { syncOccasionsIfEnabled(app) }
                    .onFailure { Timber.e(it, "occasion sync re-arm threw") }
            } finally {
                pending.finish()
            }
        }
    }
}
