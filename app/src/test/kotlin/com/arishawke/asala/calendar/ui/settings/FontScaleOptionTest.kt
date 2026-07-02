/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class FontScaleOptionTest {
    @Test
    fun `steps match the five approved percentages in order`() {
        assertEquals(listOf(85, 100, 115, 130, 150), FontScaleOption.entries.map { it.percent })
    }

    @Test
    fun `default option is scale-neutral`() {
        // factor 1.0 is load-bearing: every screen must render pixel-identical to
        // a build without this preference when the user has not touched it.
        assertEquals(1.0f, FontScaleOption.Default.factor, 0f)
    }

    @Test
    fun `every option round-trips through the stored name`() {
        // UserPreferences writes option.name and reads it back via valueOf(it);
        // this pins that exact contract without needing a live DataStore.
        FontScaleOption.entries.forEach { option ->
            assertEquals(option, FontScaleOption.valueOf(option.name))
        }
    }
}
