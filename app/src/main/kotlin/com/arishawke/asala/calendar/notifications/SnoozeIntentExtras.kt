/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.notifications

import android.content.Intent

internal fun Intent.putSnoozeSourceExtras(
    alertId: Long,
    eventId: Long,
    instanceMillis: Long,
    originalMinutes: Int,
): Intent = apply {
    putExtra(ReminderConstants.EXTRA_ALERT_ID, alertId)
    putExtra(ReminderConstants.EXTRA_EVENT_ID, eventId)
    putExtra(ReminderConstants.EXTRA_INSTANCE_MILLIS, instanceMillis)
    putExtra(ReminderConstants.EXTRA_REMINDER_MINUTES, originalMinutes.coerceAtLeast(0))
}

// notifications posted by older builds lack this extra.
internal fun Intent.snoozeOriginalMinutes(): Int =
    getIntExtra(ReminderConstants.EXTRA_REMINDER_MINUTES, 0).coerceAtLeast(0)
