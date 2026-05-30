/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
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
import com.arishawke.asala.calendar.notifications.ReminderConstants
import com.arishawke.asala.calendar.notifications.ReminderScheduler
import com.arishawke.asala.calendar.ui.App
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private var pendingNotificationOpen: Pair<Long, Long>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Restore a pending deep-link before consuming the launch intent so
        // a configuration change between notification tap and Compose tree
        // consumption (rotation, dark-mode flip, process death + restart)
        // does not silently drop the open.
        savedInstanceState?.let { restorePendingNotificationOpen(it) }
        enableEdgeToEdge()
        handleNotificationDeepLink(intent)
        setContent { App() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNotificationDeepLink(intent)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        pendingNotificationOpen?.let { (eventId, instanceMillis) ->
            outState.putLong(StatePendingEventId, eventId)
            outState.putLong(StatePendingInstanceMillis, instanceMillis)
        }
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

    private companion object {
        const val StatePendingEventId = "pending_notification_event_id"
        const val StatePendingInstanceMillis = "pending_notification_instance_millis"
    }
}
