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
    // per dontkillmyapp.com: these OEMs drop exact alarms unless granted battery exemption
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

    // falls back to system per-app notification settings if the OEM activity won't resolve
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
                    // coloros-based; same safecenter componentry as oppo
                    ComponentName(
                        "com.coloros.safecenter",
                        "com.coloros.safecenter.permission.startup.StartupAppListActivity",
                    )
                "honor" ->
                    // still ships the legacy huawei systemmanager activity on most magic os builds
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
