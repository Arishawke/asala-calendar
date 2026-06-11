/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar

import android.app.PendingIntent

internal object PendingIntentFlags {
    // FLAG_IMMUTABLE is mandatory from API 31; pairing it with UPDATE_CURRENT in
    // one place stops any call site from quietly dropping it.
    val UPDATE_IMMUTABLE: Int =
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
}
