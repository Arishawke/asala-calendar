/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import androidx.core.app.NotificationManagerCompat
import com.arishawke.asala.calendar.ui.settings.UserPreferences
import com.arishawke.asala.calendar.ui.settings.settingsDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber

class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != ReminderConstants.ACTION_SNOOZE &&
            action != ReminderConstants.ACTION_SNOOZE_DEFAULT &&
            action != ReminderConstants.ACTION_DISMISS
        ) {
            return
        }

        Timber.d(
            "onReceive action=%s alertId=%d eventId=%d minutes=%d",
            action,
            intent.getLongExtra(ReminderConstants.EXTRA_ALERT_ID, -1L),
            intent.getLongExtra(ReminderConstants.EXTRA_EVENT_ID, -1L),
            intent.getIntExtra(ReminderConstants.EXTRA_SNOOZE_MINUTES, -1),
        )

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (action) {
                    ReminderConstants.ACTION_SNOOZE -> handleSnooze(context, intent)
                    ReminderConstants.ACTION_SNOOZE_DEFAULT -> handleSnoozeDefault(context, intent)
                    ReminderConstants.ACTION_DISMISS -> handleDismiss(context, intent)
                }
            } catch (t: Throwable) {
                Timber.e(t, "action=%s handler threw", action)
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun handleSnoozeDefault(context: Context, intent: Intent) {
        val alertId = intent.getLongExtra(ReminderConstants.EXTRA_ALERT_ID, -1L)
        val eventId = intent.getLongExtra(ReminderConstants.EXTRA_EVENT_ID, -1L)
        val instanceMillis = intent.getLongExtra(ReminderConstants.EXTRA_INSTANCE_MILLIS, -1L)
        val minutes = UserPreferences(context.settingsDataStore).prefs.first().defaultSnoozeMinutes
        applySnooze(context, alertId, eventId, instanceMillis, minutes)
        SnoozeSession.lastChoiceMinutes = minutes
    }

    // only reached via the picker's sendBackToReceiver callback now
    private fun handleSnooze(context: Context, intent: Intent) {
        val alertId = intent.getLongExtra(ReminderConstants.EXTRA_ALERT_ID, -1L)
        val eventId = intent.getLongExtra(ReminderConstants.EXTRA_EVENT_ID, -1L)
        val instanceMillis = intent.getLongExtra(ReminderConstants.EXTRA_INSTANCE_MILLIS, -1L)
        val chosen = intent.getIntExtra(ReminderConstants.EXTRA_SNOOZE_MINUTES, -1)
        if (chosen <= 0) {
            Timber.w("ACTION_SNOOZE without minutes; ignoring")
            return
        }
        applySnooze(context, alertId, eventId, instanceMillis, chosen)
        SnoozeSession.lastChoiceMinutes = chosen
    }

    private fun handleDismiss(context: Context, intent: Intent) {
        val alertId = intent.getLongExtra(ReminderConstants.EXTRA_ALERT_ID, -1L)
        val eventId = intent.getLongExtra(ReminderConstants.EXTRA_EVENT_ID, -1L)
        // cancel first so it clears even if the provider write fails
        if (eventId > 0) NotificationManagerCompat.from(context).cancel(eventId.toInt())
        if (alertId > 0) markAlertState(context, alertId, CalendarContract.CalendarAlerts.STATE_DISMISSED)
    }
}

/** In-process session cache for the user's last-chosen snooze minutes. Reset on process death. */
internal object SnoozeSession {
    @Volatile
    var lastChoiceMinutes: Int? = null
}
