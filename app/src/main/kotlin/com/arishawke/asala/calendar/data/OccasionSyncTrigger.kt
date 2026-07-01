/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.arishawke.asala.calendar.R
import com.arishawke.asala.calendar.ui.settings.UserPreferences
import com.arishawke.asala.calendar.ui.settings.UserPrefs
import com.arishawke.asala.calendar.ui.settings.settingsDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

private data class OccasionCalendarIds(val birthdays: Long, val anniversaries: Long)

// single shared base-title formatter: the stored title seam depends on every
// sync path (background, enable-time) producing byte-identical titles.
fun occasionBaseTitle(context: Context, occasion: Occasion): String = when (occasion.type) {
    OccasionType.Birthday -> context.getString(R.string.occasion_birthday_base, occasion.displayName)
    OccasionType.Anniversary -> context.getString(R.string.occasion_anniversary_base, occasion.displayName)
}

// single gated entry point that keeps the two occasion calendars fresh: called
// on app foreground, the daily reminder re-arm tick, and (debounced) on a
// contacts change. a no-op whenever the feature is off, either calendar id is
// missing, or READ_CONTACTS has since been revoked, so every call site can
// fire it unconditionally without repeating those checks itself.
suspend fun syncOccasionsIfEnabled(context: Context) {
    val appContext = context.applicationContext
    val prefs = UserPreferences(appContext.settingsDataStore).prefs.first()
    val ids = readyOccasionCalendarIds(prefs, appContext) ?: return

    withContext(Dispatchers.IO) {
        val contentResolver = appContext.contentResolver
        val sync = OccasionSync(
            contentResolver = contentResolver,
            contacts = ContactsRepository(contentResolver),
            events = EventRepository(contentResolver),
            reminders = RemindersRepository(contentResolver),
            appPackage = appContext.packageName,
        )
        // same base-title formatter as SettingsViewModel, so a title stored by
        // a background sync matches one stored by the initial enable-time sync.
        val titleFor: (Occasion) -> String = { occasion -> occasionBaseTitle(appContext, occasion) }
        sync.sync(ids.birthdays, ids.anniversaries, prefs.contactReminderMinutesBefore, titleFor)
    }
}

// null unless the feature is on, both calendars are provisioned, and
// READ_CONTACTS is currently granted; the caller treats a null result as a
// silent no-op rather than a partial sync.
private fun readyOccasionCalendarIds(prefs: UserPrefs, context: Context): OccasionCalendarIds? {
    val hasContactsPermission = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.READ_CONTACTS,
    ) == PackageManager.PERMISSION_GRANTED
    if (!prefs.contactOccasionsEnabled || !hasContactsPermission) return null

    val birthdaysId = prefs.birthdaysCalendarId
    val anniversariesId = prefs.anniversariesCalendarId
    return if (birthdaysId != null && anniversariesId != null) {
        OccasionCalendarIds(birthdaysId, anniversariesId)
    } else {
        null
    }
}
