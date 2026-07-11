/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.notifications

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.arishawke.asala.calendar.MainActivity
import com.arishawke.asala.calendar.PendingIntentFlags
import com.arishawke.asala.calendar.R
import com.arishawke.asala.calendar.data.EventEndMillis
import com.arishawke.asala.calendar.data.resolveOverrideColor
import java.text.DateFormat
import java.util.Date

// decoupled from the cursor so the builder does no I/O and stays unit-testable
internal data class ReminderEventSnapshot(
    val title: String,
    val location: String?,
    val allDay: Boolean,
    val startMillis: Long,
    val endMillis: Long,
    val displayColor: Int,
)

// synced calendars never write overrides back to the provider, so apply both here
// for the accent to match the in-app chip. precedence: event > calendar > provider.
internal fun resolveReminderColor(
    providerColor: Int,
    eventId: Long,
    calendarId: Long,
    eventOverrides: Map<Long, Int>,
    calendarOverrides: Map<Long, Int>,
): Int = resolveOverrideColor(eventId, calendarId, providerColor, eventOverrides, calendarOverrides)

// recurring rows write DURATION not DTEND; routing both through EventEndMillis.compute
// keeps the receiver and the detail sheet on the same end-time fallback.
// param list mirrors the row columns, so it stays long.
@Suppress("LongParameterList")
internal fun buildReminderEventSnapshot(
    title: String?,
    location: String?,
    allDay: Boolean,
    startMillis: Long,
    dtEnd: Long?,
    durationIso8601: String?,
    displayColor: Int,
    fallbackTitle: String,
): ReminderEventSnapshot = ReminderEventSnapshot(
    title = title?.takeIf { it.isNotBlank() } ?: fallbackTitle,
    location = location?.takeIf { it.isNotBlank() },
    allDay = allDay,
    startMillis = startMillis,
    endMillis = EventEndMillis.compute(startMillis, dtEnd, durationIso8601),
    displayColor = displayColor,
)

// extracted from the receiver so the three PendingIntent shapes and the
// BigTextStyle layout stay under the 200-line threshold.
internal fun buildReminderNotification(
    context: Context,
    eventId: Long,
    event: ReminderEventSnapshot,
    instanceMillis: Long,
    minutesBefore: Int,
    alertId: Long,
    defaultSnoozeMinutes: Int,
): Notification {
    val timeLabel =
        if (event.allDay) {
            context.getString(R.string.field_all_day)
        } else {
            val df = DateFormat.getTimeInstance(DateFormat.SHORT)
            context.getString(
                R.string.time_range_format,
                df.format(Date(event.startMillis)),
                df.format(Date(event.endMillis)),
            )
        }

    val openPi = openMainActivityPendingIntent(context, eventId, instanceMillis)
    val snoozeDefaultPi = snoozeDefaultPendingIntent(context, eventId, instanceMillis, minutesBefore, alertId)
    val snoozePickerPi = snoozePickerPendingIntent(context, eventId, instanceMillis, minutesBefore, alertId)
    val dismissPi = dismissPendingIntent(context, eventId, instanceMillis, minutesBefore, alertId)

    return NotificationCompat
        .Builder(context, ReminderConstants.CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_stat_calendar)
        .setColor(event.displayColor)
        .setContentTitle(event.title)
        .setContentText(timeLabel)
        .setStyle(
            NotificationCompat.BigTextStyle().bigText(
                buildString {
                    append(timeLabel)
                    event.location?.let { append('\n').append(it) }
                },
            ),
        ).setPriority(NotificationCompat.PRIORITY_HIGH)
        .setCategory(NotificationCompat.CATEGORY_EVENT)
        .setAutoCancel(true)
        .setContentIntent(openPi)
        .addAction(
            0,
            context.getString(R.string.notif_action_snooze_default_format, defaultSnoozeMinutes),
            snoozeDefaultPi,
        ).addAction(0, context.getString(R.string.notif_action_snooze_picker), snoozePickerPi)
        .addAction(0, context.getString(R.string.notif_action_dismiss), dismissPi)
        .build()
}

private fun openMainActivityPendingIntent(context: Context, eventId: Long, instanceMillis: Long): PendingIntent {
    val intent =
        Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            putExtra(ReminderConstants.EXTRA_OPEN_EVENT_FROM_NOTIF, true)
            putExtra(ReminderConstants.EXTRA_EVENT_ID, eventId)
            putExtra(ReminderConstants.EXTRA_INSTANCE_MILLIS, instanceMillis)
        }
    return PendingIntent.getActivity(
        context,
        PendingIntentRequestCodes.forOpen(eventId, instanceMillis),
        intent,
        PendingIntentFlags.UPDATE_IMMUTABLE,
    )
}

private fun snoozeDefaultPendingIntent(
    context: Context,
    eventId: Long,
    instanceMillis: Long,
    minutesBefore: Int,
    alertId: Long,
): PendingIntent {
    val intent =
        Intent(context, NotificationActionReceiver::class.java).apply {
            action = ReminderConstants.ACTION_SNOOZE_DEFAULT
        }.putSnoozeSourceExtras(alertId, eventId, instanceMillis, minutesBefore)
    return PendingIntent.getBroadcast(
        context,
        PendingIntentRequestCodes.forSnoozeDefault(eventId, instanceMillis, minutesBefore),
        intent,
        PendingIntentFlags.UPDATE_IMMUTABLE,
    )
}

// launch the activity directly so the shade collapses; a broadcast trampoline
// leaves the shade open over the picker (user-reported confusion).
private fun snoozePickerPendingIntent(
    context: Context,
    eventId: Long,
    instanceMillis: Long,
    minutesBefore: Int,
    alertId: Long,
): PendingIntent {
    val intent =
        Intent(context, SnoozePickerActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }.putSnoozeSourceExtras(alertId, eventId, instanceMillis, minutesBefore)
    return PendingIntent.getActivity(
        context,
        PendingIntentRequestCodes.forSnoozePicker(eventId, instanceMillis, minutesBefore),
        intent,
        PendingIntentFlags.UPDATE_IMMUTABLE,
    )
}

private fun dismissPendingIntent(
    context: Context,
    eventId: Long,
    instanceMillis: Long,
    minutesBefore: Int,
    alertId: Long,
): PendingIntent {
    val intent =
        Intent(context, NotificationActionReceiver::class.java).apply {
            action = ReminderConstants.ACTION_DISMISS
            putExtra(ReminderConstants.EXTRA_ALERT_ID, alertId)
            putExtra(ReminderConstants.EXTRA_EVENT_ID, eventId)
            putExtra(ReminderConstants.EXTRA_INSTANCE_MILLIS, instanceMillis)
            // the notification id is offset-scoped, so dismiss must know which
            // offset fired to cancel the right shade entry.
            putExtra(ReminderConstants.EXTRA_REMINDER_MINUTES, minutesBefore)
        }
    return PendingIntent.getBroadcast(
        context,
        PendingIntentRequestCodes.forDismiss(eventId, instanceMillis, minutesBefore),
        intent,
        PendingIntentFlags.UPDATE_IMMUTABLE,
    )
}
