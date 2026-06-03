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
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.arishawke.asala.calendar.ui.widget.WidgetRefreshScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber

class BootRescheduler : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIMEZONE_CHANGED,
            -> {
                // reinstalls fire MY_PACKAGE_REPLACED before READ_CALENDAR is
                // granted; querying the provider would throw SecurityException.
                if (ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.READ_CALENDAR,
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    return
                }

                val pending = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        // swallow throwables so a boot hiccup never crashes the process
                        runCatching {
                            ReminderScheduler.rescheduleAll(context.applicationContext)
                        }.onFailure { Timber.e(it, "boot rescheduler threw") }
                        runCatching {
                            WidgetRefreshScheduler.rearmIfPresent(context.applicationContext)
                        }.onFailure { Timber.e(it, "boot widget re-arm threw") }
                    } finally {
                        pending.finish()
                    }
                }
            }
        }
    }
}
