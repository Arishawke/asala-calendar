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
import androidx.compose.ui.unit.dp
import com.arishawke.asala.calendar.R
import com.arishawke.asala.calendar.data.EventDetail
import com.arishawke.asala.calendar.data.RecurrenceRule
import com.arishawke.asala.calendar.data.RecurringEditScope
import com.arishawke.asala.calendar.ui.components.BirthdayLeadingIcon
import com.arishawke.asala.calendar.ui.components.occasionDisplayTitle
import com.arishawke.asala.calendar.ui.components.reminderLabel
import com.arishawke.asala.calendar.ui.components.statusStyling
import com.arishawke.asala.calendar.ui.notifications.NotificationsOffBanner
import com.arishawke.asala.calendar.ui.theme.LocalIs24Hour
import com.arishawke.asala.calendar.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("LongParameterList")
fun EventDetailSheet(
    detail: EventDetail?,
    instanceMillis: Long?,
    onDismiss: () -> Unit,
    onEdit: (Long) -> Unit,
    onDuplicate: (Long) -> Unit,
    onDelete: (Long, RecurringEditScope) -> Unit,
    deleteFailed: Boolean = false,
    notificationPermissionGranted: Boolean = true,
    onRequestNotificationPermission: () -> Unit = {},
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
                deleteFailed = deleteFailed,
                notificationPermissionGranted = notificationPermissionGranted,
                onRequestNotificationPermission = onRequestNotificationPermission,
                onEdit = { onEdit(detail.eventId) },
                onDuplicate = { onDuplicate(detail.eventId) },
                onDelete = { scope -> onDelete(detail.eventId, scope) },
                onClose = onDismiss,
            )
        }
    }
}

// detekt thresholds suppressed: each branch is one conditional field;
// extracting helpers would obscure the sheet's linear field grouping.
@Suppress("CyclomaticComplexMethod", "LongMethod", "LongParameterList")
@Composable
private fun EventDetailContent(
    detail: EventDetail,
    instanceMillis: Long?,
    deleteFailed: Boolean,
    notificationPermissionGranted: Boolean,
    onRequestNotificationPermission: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
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
    // owned occasion events store the contact name in DESCRIPTION as a machine
    // marker (it drives the age/ordinal title), so don't also surface it as a
    // redundant "Notes" section, e.g. title "Alice turns 30" + Notes "Alice" (F13).
    // row-scoped: a hand-added event in an occasion calendar keeps its notes.
    val hasDescription = !detail.description.isNullOrBlank() && !detail.isOwnedOccasion
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
        // header. title mirrors the chip italic / strikethrough so status reads at
        // a glance (shared with the chips via statusStyling), badge below names it.
        val styling = statusStyling(detail.status)
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (detail.isBirthday) {
                BirthdayLeadingIcon(size = 24.dp)
                Spacer(Modifier.width(Spacing.sm))
            }
            Text(
                text = occasionDisplayTitle(detail, instanceMillis).ifBlank { stringResource(R.string.event_no_title) },
                style = MaterialTheme.typography.headlineSmall,
                fontStyle = styling.titleFontStyle,
                textDecoration = styling.titleDecoration,
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

        // when
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

        // where. linkified so a meeting URL pasted here stays tappable.
        if (hasLocation) {
            HorizontalDivider()
            val linkColor = MaterialTheme.colorScheme.primary
            val locationText = detail.location.orEmpty()
            val locationAnnotated = remember(locationText, linkColor) {
                linkifyAnnotated(locationText, linkColor)
            }
            Text(text = locationAnnotated, style = MaterialTheme.typography.bodyMedium)
        }

        // notes
        if (hasDescription) {
            HorizontalDivider()
            DescriptionText(detail.description.orEmpty())
        }

        // reminders
        if (hasReminder) {
            HorizontalDivider()
            if (!notificationPermissionGranted) {
                NotificationsOffBanner(onTurnOn = onRequestNotificationPermission)
            }
            Text(
                // hasReminder already guards a non-null reminder here.
                text = reminderLabel(detail.reminderMinutesBefore),
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        if (deleteFailed) {
            Text(
                text = stringResource(R.string.error_delete_failed),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        Spacer(Modifier.height(Spacing.sm))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            // edit/delete only for writable calendars; read-only sources
            // (holidays, birthdays, subscriptions) reject the provider write.
            if (detail.isWritable) {
                TextButton(onClick = {
                    if (isRecurring) showDeleteScopeDialog = true else showConfirmDelete = true
                }) { Text(stringResource(R.string.action_delete)) }
            }
            TextButton(onClick = onDuplicate) { Text(stringResource(R.string.action_duplicate)) }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onClose) { Text(stringResource(R.string.action_close)) }
            if (detail.isWritable) {
                Button(onClick = onEdit) { Text(stringResource(R.string.action_edit)) }
            }
        }
    }
}
