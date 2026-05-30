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

// Settings DAVx5 row tries to launch the installed app first, then falls
// back to Play Store, then F-Droid web. Any failure path is silent so the
// surface never crashes the settings screen on missing app stores.
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
        // fall through to F-Droid web link
    }
    val fdroid = Intent(
        Intent.ACTION_VIEW,
        "https://f-droid.org/packages/$Davx5Package/".toUri(),
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(fdroid)
    } catch (_: ActivityNotFoundException) {
        // No browser installed. Silently no-op rather than crash; the
        // user can install DAVx5 via any other path.
    }
}
