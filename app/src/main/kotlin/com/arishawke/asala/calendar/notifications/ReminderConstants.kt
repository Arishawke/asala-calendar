/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.notifications

internal object ReminderConstants {
    const val CHANNEL_ID = "reminders"

    // Intent extras shared across receivers + the notification PendingIntent + MainActivity deep-link.
    const val EXTRA_EVENT_ID = "asala_event_id"
    const val EXTRA_INSTANCE_MILLIS = "asala_instance_millis"
    const val EXTRA_REMINDER_MINUTES = "asala_reminder_minutes"
    const val EXTRA_ALERT_ID = "asala_alert_id"
    const val EXTRA_SNOOZE_MINUTES = "asala_snooze_minutes"

    // Notification action intent identity strings.
    const val ACTION_SNOOZE = "com.arishawke.asala.calendar.action.SNOOZE"
    const val ACTION_SNOOZE_DEFAULT = "com.arishawke.asala.calendar.action.SNOOZE_DEFAULT"
    const val ACTION_DISMISS = "com.arishawke.asala.calendar.action.DISMISS"
    const val ACTION_FIRE = "com.arishawke.asala.calendar.action.FIRE"

    // Main activity deep-link key.
    const val EXTRA_OPEN_EVENT_FROM_NOTIF = "notif_open_event"
}
