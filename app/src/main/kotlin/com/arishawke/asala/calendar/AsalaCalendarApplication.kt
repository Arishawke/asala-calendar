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
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import com.arishawke.asala.calendar.data.TodayProvider
import com.arishawke.asala.calendar.data.observeChanges
import com.arishawke.asala.calendar.data.syncOccasionsIfEnabled
import com.arishawke.asala.calendar.notifications.NotificationChannelInitializer
import com.arishawke.asala.calendar.notifications.ReminderReArmScheduler
import com.arishawke.asala.calendar.notifications.ReminderScheduler
import com.arishawke.asala.calendar.ui.settings.UserPreferences
import com.arishawke.asala.calendar.ui.settings.settingsDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import timber.log.Timber

class AsalaCalendarApplication : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var providerObserverRegistered = false

    // lazy so startup doesn't pay for receiver registration until first access
    val todayProvider: TodayProvider by lazy {
        TodayProvider().also { provider ->
            registerDateChangedReceiver { provider.refresh() }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        // release: no tree, so Timber calls are no-ops. add one if we adopt
        // crash reporting.
        Timber.d("AsalaCalendarApplication onCreate")
        NotificationChannelInitializer.ensureCreated(this)
        // observe contacts only while the feature is enabled; observeChanges
        // swallows the SecurityException when READ_CONTACTS isn't (yet)
        // granted, so this never crashes, and flatMapLatest tears the
        // observer down (via awaitClose) the moment the pref flips off.
        appScope.launch {
            UserPreferences(settingsDataStore).prefs
                .map { it.contactOccasionsEnabled }
                .distinctUntilChanged()
                .flatMapLatest { enabled ->
                    if (enabled) {
                        contentResolver.observeChanges(ContactsContract.Contacts.CONTENT_URI)
                            .debounce(ContactsSyncDebounceMillis)
                    } else {
                        emptyFlow()
                    }
                }
                .collect { syncOccasionsIfEnabled(this@AsalaCalendarApplication) }
        }
    }

    /** Idempotent: registers the observer once, re-runs rescheduleAll each call. */
    fun onCalendarPermissionGranted() {
        Timber.d("calendar permission granted; observer registered=%b", providerObserverRegistered)
        if (!providerObserverRegistered) {
            registerProviderObserver()
            providerObserverRegistered = true
        }
        appScope.launch { ReminderScheduler.rescheduleAll(this@AsalaCalendarApplication) }
        // start the daily window-slide tick; idempotent on the single alarm slot.
        ReminderReArmScheduler.scheduleNext(this)
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

    // forwards date/time/timezone broadcasts into TodayProvider. NOT_EXPORTED
    // is correct: these are protected broadcasts only the system delivers.
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

    private companion object {
        const val ContactsSyncDebounceMillis = 2_000L
    }
}
