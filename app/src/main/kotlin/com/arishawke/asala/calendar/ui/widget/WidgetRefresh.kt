/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.ui.widget

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import androidx.core.content.getSystemService
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.ZoneId

private const val CONTENT_TRIGGER_JOB_ID = 4201
private const val MIDNIGHT_REQUEST_CODE = 4202
private const val TRIGGER_MAX_DELAY_MS = 1000L

object WidgetRefreshScheduler {
    fun scheduleAll(context: Context) {
        scheduleContentTrigger(context)
        scheduleNextMidnight(context)
    }

    // boot/timezone re-arm: only schedule if a widget is actually placed, so a
    // device with no widget doesn't run a self-re-arming content-trigger job.
    fun rearmIfPresent(context: Context) {
        if (hasWidgets(context)) scheduleAll(context)
    }

    private fun hasWidgets(context: Context): Boolean {
        val manager = AppWidgetManager.getInstance(context) ?: return false
        return listOf(AgendaWidgetReceiver::class.java, MonthWidgetReceiver::class.java).any { receiver ->
            val ids = manager.getAppWidgetIds(ComponentName(context, receiver))
            ids != null && ids.isNotEmpty()
        }
    }

    // a single widget type being removed must not stop refresh for the other.
    fun cancelIfNoneRemain(context: Context) {
        if (!hasWidgets(context)) cancelAll(context)
    }

    fun cancelAll(context: Context) {
        context.getSystemService<JobScheduler>()?.cancel(CONTENT_TRIGGER_JOB_ID)
        context.getSystemService<AlarmManager>()?.cancel(midnightPendingIntent(context))
    }

    // re-registered after every fire because content-trigger jobs are one-shot
    // and cannot combine with setPeriodic/setPersisted.
    fun scheduleContentTrigger(context: Context) {
        val job = JobInfo.Builder(
            CONTENT_TRIGGER_JOB_ID,
            ComponentName(context, WidgetRefreshJobService::class.java),
        ).addTriggerContentUri(
            JobInfo.TriggerContentUri(
                CalendarContract.Instances.CONTENT_URI,
                JobInfo.TriggerContentUri.FLAG_NOTIFY_FOR_DESCENDANTS,
            ),
        ).setTriggerContentMaxDelay(TRIGGER_MAX_DELAY_MS).build()
        context.getSystemService<JobScheduler>()?.schedule(job)
    }

    fun scheduleNextMidnight(context: Context) {
        val am = context.getSystemService<AlarmManager>() ?: return
        val at = MidnightRefreshMath.nextLocalMidnight(System.currentTimeMillis(), ZoneId.systemDefault())
        // inexact on purpose: a cosmetic day rollover does not need exact timing.
        am.setAndAllowWhileIdle(AlarmManager.RTC, at, midnightPendingIntent(context))
    }

    private fun midnightPendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        MIDNIGHT_REQUEST_CODE,
        Intent(context, MidnightWidgetRefreshReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}

// app never schedules WorkManager jobs (Glance's updateAll goes through
// AppWidgetManager), and this id is app-reserved, so a WM id-range is moot.
@SuppressLint("SpecifyJobSchedulerIdRange")
class WidgetRefreshJobService : JobService() {
    private val scope = CoroutineScope(Dispatchers.IO)

    override fun onStartJob(params: JobParameters?): Boolean {
        scope.launch {
            // isolate each widget: one type failing must not skip the other's refresh.
            runCatching { AgendaWidget().updateAll(applicationContext) }
                .onFailure { Timber.e(it, "agenda widget content-trigger update failed") }
            runCatching { MonthWidget().updateAll(applicationContext) }
                .onFailure { Timber.e(it, "month widget content-trigger update failed") }
            // re-arm the one-shot trigger for the next change.
            WidgetRefreshScheduler.scheduleContentTrigger(applicationContext)
            jobFinished(params, false)
        }
        return true
    }

    override fun onStopJob(params: JobParameters?): Boolean = true
}

class MidnightWidgetRefreshReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            runCatching { AgendaWidget().updateAll(context.applicationContext) }
                .onFailure { Timber.e(it, "agenda widget midnight update failed") }
            runCatching { MonthWidget().updateAll(context.applicationContext) }
                .onFailure { Timber.e(it, "month widget midnight update failed") }
            WidgetRefreshScheduler.scheduleNextMidnight(context.applicationContext)
            pending.finish()
        }
    }
}
