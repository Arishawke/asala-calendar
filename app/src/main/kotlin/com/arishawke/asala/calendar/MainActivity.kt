/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

@file:Suppress("TooManyFunctions")

package com.arishawke.asala.calendar

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.arishawke.asala.calendar.data.syncOccasionsIfEnabled
import com.arishawke.asala.calendar.notifications.ReminderConstants
import com.arishawke.asala.calendar.notifications.ReminderScheduler
import com.arishawke.asala.calendar.ui.App
import com.arishawke.asala.calendar.ui.widget.WidgetDeepLink
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.LocalDate

class MainActivity : ComponentActivity() {
    private var pendingNotificationOpen: Pair<Long, Long>? = null
    private var pendingDateOpen: Pair<LocalDate, CalendarView>? = null
    private var pendingSharedText: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // restore pending deep-link before consuming the intent so a config
        // change or process death between tap and consumption doesn't drop it
        savedInstanceState?.let { restorePendingNotificationOpen(it) }
        savedInstanceState?.let { restorePendingDateOpen(it) }
        savedInstanceState?.let { restorePendingSharedText(it) }
        enableEdgeToEdge()
        handleNotificationDeepLink(intent)
        handleDateDeepLink(intent)
        handleShareDeepLink(intent)
        setContent { App() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNotificationDeepLink(intent)
        handleDateDeepLink(intent)
        handleShareDeepLink(intent)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        pendingNotificationOpen?.let { (eventId, instanceMillis) ->
            outState.putLong(StatePendingEventId, eventId)
            outState.putLong(StatePendingInstanceMillis, instanceMillis)
        }
        pendingDateOpen?.let { (date, view) ->
            outState.putLong(StatePendingEpochDay, date.toEpochDay())
            outState.putString(StatePendingView, view.name)
        }
        pendingSharedText?.let { outState.putString(StatePendingSharedText, it) }
    }

    private fun restorePendingNotificationOpen(savedInstanceState: Bundle) {
        val eventId = savedInstanceState.getLong(StatePendingEventId, -1L)
        val instanceMillis = savedInstanceState.getLong(StatePendingInstanceMillis, -1L)
        if (eventId > 0 && instanceMillis > 0) {
            pendingNotificationOpen = eventId to instanceMillis
        }
    }

    override fun onResume() {
        super.onResume()
        val granted =
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_CALENDAR,
            ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            lifecycleScope.launch(Dispatchers.IO) {
                ReminderScheduler.rescheduleAll(applicationContext)
            }
            lifecycleScope.launch(Dispatchers.IO) {
                // never let one bad contact row (see occasionDtStartMillis) crash a
                // routine foreground re-sync, which onResume would then re-trigger.
                runCatching { syncOccasionsIfEnabled(applicationContext, skipIfFresh = true) }
                    .onFailure {
                        if (it is CancellationException) throw it
                        Timber.e(it, "occasion sync on resume failed")
                    }
            }
        }
    }

    private fun handleNotificationDeepLink(intent: Intent?) {
        if (intent == null) return
        if (!intent.getBooleanExtra(ReminderConstants.EXTRA_OPEN_EVENT_FROM_NOTIF, false)) return
        val eventId = intent.getLongExtra(ReminderConstants.EXTRA_EVENT_ID, -1L)
        val instanceMillis = intent.getLongExtra(ReminderConstants.EXTRA_INSTANCE_MILLIS, -1L)
        if (eventId > 0 && instanceMillis > 0) {
            pendingNotificationOpen = eventId to instanceMillis
        }
        intent.removeExtra(ReminderConstants.EXTRA_OPEN_EVENT_FROM_NOTIF)
    }

    fun consumePendingNotificationOpen(): Pair<Long, Long>? {
        val cur = pendingNotificationOpen
        pendingNotificationOpen = null
        return cur
    }

    private fun handleDateDeepLink(intent: Intent?) {
        if (intent == null) return
        pendingDateOpen = WidgetDeepLink.decode(
            present = intent.getBooleanExtra(ReminderConstants.EXTRA_OPEN_DATE_FROM_WIDGET, false),
            epochDay = intent.getLongExtra(ReminderConstants.EXTRA_OPEN_EPOCHDAY, Long.MIN_VALUE),
            viewName = intent.getStringExtra(ReminderConstants.EXTRA_OPEN_VIEW),
        ) ?: return
        intent.removeExtra(ReminderConstants.EXTRA_OPEN_DATE_FROM_WIDGET)
    }

    fun consumePendingDateOpen(): Pair<LocalDate, CalendarView>? {
        val cur = pendingDateOpen
        pendingDateOpen = null
        return cur
    }

    private fun restorePendingDateOpen(savedInstanceState: Bundle) {
        pendingDateOpen = WidgetDeepLink.decode(
            present = true,
            epochDay = savedInstanceState.getLong(StatePendingEpochDay, Long.MIN_VALUE),
            viewName = savedInstanceState.getString(StatePendingView),
        )
    }

    private fun handleShareDeepLink(intent: Intent?) {
        if (intent == null) return
        // senders may append mime params the filter ignores
        if (intent.action != Intent.ACTION_SEND || intent.type?.substringBefore(';')?.trim() != "text/plain") return
        // styled senders deliver a Spanned; getStringExtra would drop it
        val normalized = ShareTextNormalizer.normalize(intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString())
        if (normalized != null) {
            pendingSharedText = normalized
        }
        intent.removeExtra(Intent.EXTRA_TEXT)
    }

    fun consumePendingSharedText(): String? {
        val cur = pendingSharedText
        pendingSharedText = null
        return cur
    }

    private fun restorePendingSharedText(savedInstanceState: Bundle) {
        pendingSharedText = savedInstanceState.getString(StatePendingSharedText)
    }

    private companion object {
        const val StatePendingEventId = "pending_notification_event_id"
        const val StatePendingInstanceMillis = "pending_notification_instance_millis"
        const val StatePendingEpochDay = "pending_widget_epochday"
        const val StatePendingView = "pending_widget_view"
        const val StatePendingSharedText = "pending_shared_text"
    }
}
