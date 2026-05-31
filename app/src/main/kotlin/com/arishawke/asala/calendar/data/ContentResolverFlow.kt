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

// emits Unit on subscription and on every provider change at `uri`.
// if registerContentObserver throws (subscribed before permission granted),
// the flow still emits the initial Unit without observing.
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
