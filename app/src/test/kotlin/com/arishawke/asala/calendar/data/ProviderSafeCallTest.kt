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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ProviderSafeCallTest {
    @Test fun `returns the block result on success`() {
        assertEquals(42, providerCall("op", onError = -1) { 42 })
    }

    // a provider RuntimeException (revoked permission, a value the provider
    // rejects) is swallowed and reported as the safe default, not propagated.
    @Test fun `returns onError when the block throws a RuntimeException`() {
        assertEquals(-1, providerCall("op", onError = -1) { throw IllegalStateException("provider boom") })
    }

    // CancellationException must propagate so coroutine cancellation still works.
    // It is re-thrown by the catch that PRECEDES the generic RuntimeException
    // catch; since it is itself a RuntimeException, reordering the catches would
    // silently swallow cancellation. This pins that ordering.
    @Test fun `re-throws CancellationException instead of swallowing it`() {
        assertThrows(CancellationException::class.java) {
            providerCall("op", onError = -1) { throw CancellationException("cancelled") }
        }
    }
}
