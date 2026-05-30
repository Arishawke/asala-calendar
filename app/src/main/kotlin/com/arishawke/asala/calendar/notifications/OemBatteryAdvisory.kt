/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.notifications

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings

object OemBatteryAdvisory {
    // dontkillmyapp.com 2026 data: ColorOS-derived (Realme) and post-Huawei-split
    // (Honor) both ship aggressive background-restriction defaults that drop
    // exact alarms unless the user grants battery exemption.
    private val AFFECTED = setOf(
        "samsung",
        "xiaomi",
        "oneplus",
        "huawei",
        "oppo",
        "vivo",
        "realme",
        "honor",
    )

    fun isAffected(): Boolean = Build.MANUFACTURER.lowercase() in AFFECTED

    // Returns an Intent that opens the manufacturer's battery / background-restriction
    // settings screen if the activity is resolvable; otherwise falls back to the
    // system per-app notification settings.
    fun batterySettingsIntent(context: Context): Intent {
        val target =
            when (Build.MANUFACTURER.lowercase()) {
                "samsung" ->
                    ComponentName(
                        "com.samsung.android.lool",
                        "com.samsung.android.sm.battery.ui.BatteryActivity",
                    )
                "xiaomi" ->
                    ComponentName(
                        "com.miui.securitycenter",
                        "com.miui.permcenter.permissions.PermissionsEditorActivity",
                    )
                "oneplus" ->
                    ComponentName(
                        "com.android.settings",
                        "com.android.settings.Settings\$HighPowerApplicationsActivity",
                    )
                "huawei" ->
                    ComponentName(
                        "com.huawei.systemmanager",
                        "com.huawei.systemmanager.appcontrol.activity.StartupAppControlActivity",
                    )
                "oppo" ->
                    ComponentName(
                        "com.coloros.safecenter",
                        "com.coloros.safecenter.permission.startup.StartupAppListActivity",
                    )
                "vivo" ->
                    ComponentName(
                        "com.iqoo.secure",
                        "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager",
                    )
                "realme" ->
                    // Realme is ColorOS-based; same safecenter componentry as Oppo.
                    ComponentName(
                        "com.coloros.safecenter",
                        "com.coloros.safecenter.permission.startup.StartupAppListActivity",
                    )
                "honor" ->
                    // Post-Huawei-split Honor still ships the legacy systemmanager
                    // StartupAppControlActivity on most Magic OS builds.
                    ComponentName(
                        "com.huawei.systemmanager",
                        "com.huawei.systemmanager.appcontrol.activity.StartupAppControlActivity",
                    )
                else -> null
            }
        if (target != null) {
            val explicit =
                Intent().apply {
                    component = target
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
            if (explicit.resolveActivity(context.packageManager) != null) return explicit
        }
        return Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
    }
}
