/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.content.getSystemService
import com.arishawke.asala.calendar.R

internal object NotificationChannelInitializer {
    fun ensureCreated(context: Context) {
        val nm = context.getSystemService<NotificationManager>() ?: return
        val existing = nm.getNotificationChannel(ReminderConstants.CHANNEL_ID)
        if (existing != null) return

        val channel =
            NotificationChannel(
                ReminderConstants.CHANNEL_ID,
                context.getString(R.string.notif_channel_reminders_name),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = context.getString(R.string.notif_channel_reminders_description)
                enableLights(true)
                enableVibration(true)
            }
        nm.createNotificationChannel(channel)
    }
}
