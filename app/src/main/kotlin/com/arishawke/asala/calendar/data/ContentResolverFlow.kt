/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.data

import android.content.ContentResolver
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import timber.log.Timber

// Emits Unit once on subscription and again whenever the Calendar Provider
// (or any ContentProvider at `uri`) reports a change.
// Defensive: if registerContentObserver throws SecurityException (caller
// subscribed before its runtime permission was granted), the flow stays
// open and emits the initial Unit without observing. Callers that gate
// subscription on permission state never hit this branch; the catch is
// insurance against an upstream regression; follows the established
// upstream-defensive pattern.
fun ContentResolver.observeChanges(uri: Uri): Flow<Unit> = callbackFlow {
    val handler = Handler(Looper.getMainLooper())
    val observer =
        object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean) {
                trySend(Unit)
            }
        }
    val registered =
        try {
            registerContentObserver(uri, true, observer)
            true
        } catch (e: SecurityException) {
            Timber.w(e, "registerContentObserver denied for %s", uri)
            false
        }
    trySend(Unit)
    awaitClose {
        if (registered) unregisterContentObserver(observer)
    }
}
