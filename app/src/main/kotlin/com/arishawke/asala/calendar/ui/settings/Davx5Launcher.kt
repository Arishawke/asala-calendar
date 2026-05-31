/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.ui.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri

private const val Davx5Package = "at.bitfire.davdroid"

// installed app -> Play Store -> F-Droid web. failures silent so a
// missing app store never crashes the settings screen.
internal fun openDavx5(context: Context) {
    val launch = context.packageManager.getLaunchIntentForPackage(Davx5Package)
    if (launch != null) {
        context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return
    }
    val playStore = Intent(
        Intent.ACTION_VIEW,
        "market://details?id=$Davx5Package".toUri(),
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(playStore)
        return
    } catch (_: ActivityNotFoundException) {
        // fall through to F-Droid web
    }
    val fdroid = Intent(
        Intent.ACTION_VIEW,
        "https://f-droid.org/packages/$Davx5Package/".toUri(),
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(fdroid)
    } catch (_: ActivityNotFoundException) {
        // no browser; silently no-op rather than crash.
    }
}
