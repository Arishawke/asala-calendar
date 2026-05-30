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
                // Fresh installs and reinstalls fire MY_PACKAGE_REPLACED before
                // the user has granted READ_CALENDAR; querying the provider
                // would throw SecurityException and crash the receiver.
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
                        // Log and swallow any throwable so the receiver does not
                        // crash the process on boot. goAsync() already keeps the
                        // process alive until pending.finish().
                        runCatching {
                            ReminderScheduler.rescheduleAll(context.applicationContext)
                        }.onFailure { Timber.e(it, "boot rescheduler threw") }
                    } finally {
                        pending.finish()
                    }
                }
            }
        }
    }
}
