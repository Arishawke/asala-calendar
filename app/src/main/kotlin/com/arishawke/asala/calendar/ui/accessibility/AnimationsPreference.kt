/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.ui.accessibility

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext

// mirrors the system "Remove animations" toggle, updating live in foreground.
@Composable
fun rememberAnimationsEnabled(): State<Boolean> {
    val context = LocalContext.current
    return produceState(initialValue = isAnimationsEnabled(context), context) {
        val handler = Handler(Looper.getMainLooper())
        val observer =
            object : ContentObserver(handler) {
                override fun onChange(selfChange: Boolean) {
                    value = isAnimationsEnabled(context)
                }
            }
        val uri = Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE)
        context.contentResolver.registerContentObserver(uri, false, observer)
        awaitDispose { context.contentResolver.unregisterContentObserver(observer) }
    }
}

private fun isAnimationsEnabled(context: android.content.Context): Boolean = Settings.Global.getFloat(
    context.contentResolver,
    Settings.Global.ANIMATOR_DURATION_SCALE,
    1f,
) > 0f
