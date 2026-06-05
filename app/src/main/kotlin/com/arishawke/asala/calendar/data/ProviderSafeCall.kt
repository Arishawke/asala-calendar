/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.data

import kotlinx.coroutines.CancellationException
import timber.log.Timber

// CalendarContract calls throw on revoked permission, removed accounts, and
// values a sync adapter rejects (e.g. the all-day duration the provider
// NumberFormat-crashed on). The provider signals these by throwing, not by
// returning null, and the read/save coroutines have no catch, so an unguarded
// throw crashes the app. Wrap the boundary so callers get a safe default.
// CancellationException is re-thrown so coroutine cancellation still works.
@Suppress("TooGenericExceptionCaught")
internal inline fun <T> providerCall(operation: String, onError: T, block: () -> T): T = try {
    block()
} catch (e: CancellationException) {
    throw e
} catch (e: RuntimeException) {
    Timber.e(e, "calendar provider call failed: %s", operation)
    onError
}
