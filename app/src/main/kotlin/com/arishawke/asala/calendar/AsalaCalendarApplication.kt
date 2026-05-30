/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import com.arishawke.asala.calendar.data.TodayProvider
import com.arishawke.asala.calendar.notifications.NotificationChannelInitializer
import com.arishawke.asala.calendar.notifications.ReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber

class AsalaCalendarApplication : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var providerObserverRegistered = false

    // Lazy so process startup doesn't pay for the BroadcastReceiver
    // registration before any screen subscribes; the first ViewModel
    // factory or AppShell access triggers it.
    val todayProvider: TodayProvider by lazy {
        TodayProvider().also { provider ->
            registerDateChangedReceiver { provider.refresh() }
        }
    }

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        // Release: no tree planted, so Timber calls compile to no-ops at runtime.
        // Add a release tree here later if we adopt crash reporting.
        Timber.d("AsalaCalendarApplication onCreate")
        NotificationChannelInitializer.ensureCreated(this)
        // Provider observer + initial reschedule are deferred until the user
        // grants calendar permission (called from MainActivity).
    }

    /**
     * Called by MainActivity after the user grants calendar permission.
     * Idempotent: safe to call multiple times. Registers the ContentObserver
     * once and re-runs rescheduleAll on each call.
     */
    fun onCalendarPermissionGranted() {
        Timber.d("calendar permission granted; observer registered=%b", providerObserverRegistered)
        if (!providerObserverRegistered) {
            registerProviderObserver()
            providerObserverRegistered = true
        }
        appScope.launch { ReminderScheduler.rescheduleAll(this@AsalaCalendarApplication) }
    }

    private fun registerProviderObserver() {
        val handler = Handler(Looper.getMainLooper())
        val observer =
            object : ContentObserver(handler) {
                override fun onChange(selfChange: Boolean) {
                    appScope.launch { ReminderScheduler.rescheduleAll(this@AsalaCalendarApplication) }
                }
            }
        contentResolver.registerContentObserver(
            CalendarContract.Events.CONTENT_URI,
            true,
            observer,
        )
        contentResolver.registerContentObserver(
            CalendarContract.Reminders.CONTENT_URI,
            true,
            observer,
        )
    }

    // Forwards system date / time / timezone broadcasts into TodayProvider so
    // every collector picks up the change. These are protected system
    // broadcasts; NOT_EXPORTED is correct since only the system delivers them.
    private fun registerDateChangedReceiver(onChange: () -> Unit) {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_DATE_CHANGED)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                onChange()
            }
        }
        ContextCompat.registerReceiver(
            this,
            receiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }
}
