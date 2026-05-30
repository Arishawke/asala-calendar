/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.ui.eventdetail

import android.provider.CalendarContract
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.arishawke.asala.calendar.R
import com.arishawke.asala.calendar.data.EventDetail
import com.arishawke.asala.calendar.data.RecurrenceRule
import com.arishawke.asala.calendar.data.RecurringEditScope
import com.arishawke.asala.calendar.ui.components.BirthdayLeadingIcon
import com.arishawke.asala.calendar.ui.notifications.NotificationsOffBanner
import com.arishawke.asala.calendar.ui.theme.LocalIs24Hour
import com.arishawke.asala.calendar.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventDetailSheet(
    detail: EventDetail?,
    instanceMillis: Long?,
    notificationPermissionGranted: Boolean = true,
    onRequestNotificationPermission: () -> Unit = {},
    onDismiss: () -> Unit,
    onEdit: (Long) -> Unit,
    onDelete: (Long, RecurringEditScope) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        if (detail == null) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(48.dp),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }
        } else {
            EventDetailContent(
                detail = detail,
                instanceMillis = instanceMillis,
                notificationPermissionGranted = notificationPermissionGranted,
                onRequestNotificationPermission = onRequestNotificationPermission,
                onEdit = { onEdit(detail.eventId) },
                onDelete = { scope -> onDelete(detail.eventId, scope) },
                onClose = onDismiss,
            )
        }
    }
}

// Adding the status badge + decoration branches pushed cyclomatic
// complexity and method length past detekt's defaults. Each branch is a
// single conditional concern (recurring frequency, location, notes,
// reminder, status, etc.); pulling them into helpers would obscure the
// sheet's linear top-to-bottom field grouping.
@Suppress("CyclomaticComplexMethod", "LongMethod")
@Composable
private fun EventDetailContent(
    detail: EventDetail,
    instanceMillis: Long?,
    notificationPermissionGranted: Boolean,
    onRequestNotificationPermission: () -> Unit,
    onEdit: () -> Unit,
    onDelete: (RecurringEditScope) -> Unit,
    onClose: () -> Unit,
) {
    val isRecurring = detail.rrule != null
    var showConfirmDelete by remember { mutableStateOf(false) }
    var showDeleteScopeDialog by remember { mutableStateOf(false) }

    if (showConfirmDelete) {
        DeleteConfirmDialog(
            onConfirm = {
                showConfirmDelete = false
                onDelete(RecurringEditScope.AllEvents)
            },
            onDismiss = { showConfirmDelete = false },
        )
    }

    if (showDeleteScopeDialog) {
        DeleteScopePickerDialog(
            onPick = { scope ->
                showDeleteScopeDialog = false
                onDelete(scope)
            },
            onCancel = { showDeleteScopeDialog = false },
        )
    }

    val recurrenceFrequency = RecurrenceRule.frequencyOf(detail.rrule)
    val hasLocation = !detail.location.isNullOrBlank()
    val hasDescription = !detail.description.isNullOrBlank()
    val hasReminder = detail.reminderMinutesBefore != null

    val statusLabel = when (detail.status) {
        CalendarContract.Events.STATUS_TENTATIVE -> stringResource(R.string.status_tentative)
        CalendarContract.Events.STATUS_CANCELED -> stringResource(R.string.status_cancelled)
        else -> null
    }

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.xl, vertical = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        // Header: title + calendar identifier. No divider above; the sheet's
        // own drag handle marks the top boundary. Title picks up the same
        // italic / strikethrough decoration that chips use so the status is
        // legible at a glance, with a small badge below for explicit naming.
        val titleFontStyle = when (detail.status) {
            CalendarContract.Events.STATUS_TENTATIVE -> FontStyle.Italic
            else -> null
        }
        val titleDecoration = when (detail.status) {
            CalendarContract.Events.STATUS_CANCELED -> TextDecoration.LineThrough
            else -> null
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (detail.isBirthday) {
                BirthdayLeadingIcon(size = 24.dp)
                Spacer(Modifier.width(Spacing.sm))
            }
            Text(
                text = detail.title.ifBlank { stringResource(R.string.event_no_title) },
                style = MaterialTheme.typography.headlineSmall,
                fontStyle = titleFontStyle,
                textDecoration = titleDecoration,
            )
        }
        statusLabel?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(12.dp).background(Color(detail.displayColor), CircleShape),
            )
            Spacer(Modifier.width(Spacing.sm))
            Text(detail.calendarDisplayName, style = MaterialTheme.typography.bodyMedium)
        }

        // When: time range, recurrence summary if recurring.
        HorizontalDivider()
        Text(
            text = formatWhen(detail, instanceMillis, LocalIs24Hour.current),
            style = MaterialTheme.typography.bodyMedium,
        )
        recurrenceFrequency?.let { freq ->
            Text(
                text = stringResource(recurrenceSummaryRes(freq)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // Where (conditional). Plain-text URLs / emails inside the location
        // string are linkified so a meeting URL pasted into "where" stays
        // tappable, matching the notes treatment.
        if (hasLocation) {
            HorizontalDivider()
            val linkColor = MaterialTheme.colorScheme.primary
            val locationText = detail.location.orEmpty()
            val locationAnnotated = remember(locationText, linkColor) {
                linkifyAnnotated(locationText, linkColor)
            }
            Text(text = locationAnnotated, style = MaterialTheme.typography.bodyMedium)
        }

        // Notes (conditional). HTML-bearing descriptions (some sync
        // sources' invites) parsed into an AnnotatedString; plain-text
        // entries left unchanged so their newlines survive.
        if (hasDescription) {
            HorizontalDivider()
            DescriptionText(detail.description.orEmpty())
        }

        // Reminders (conditional). Permission banner shows above the
        // reminder label when notifications are off.
        if (hasReminder) {
            HorizontalDivider()
            if (!notificationPermissionGranted) {
                NotificationsOffBanner(onTurnOn = onRequestNotificationPermission)
            }
            detail.reminderMinutesBefore?.let {
                Text(
                    text = reminderLabel(it),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        Spacer(Modifier.height(Spacing.sm))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            TextButton(onClick = {
                if (isRecurring) showDeleteScopeDialog = true else showConfirmDelete = true
            }) { Text(stringResource(R.string.action_delete)) }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onClose) { Text(stringResource(R.string.action_close)) }
            Button(onClick = onEdit) { Text(stringResource(R.string.action_edit)) }
        }
    }
}
