/*
 * Copyright (C) 2026 Arishawke
 * GPL v3.
 */
package com.arishawke.asala.calendar.notifications

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SnoozeIntentExtrasTest {
    @Test
    fun snoozeSourceExtrasKeepANonzeroFiringOffset() {
        val intent = Intent().putSnoozeSourceExtras(7L, 42L, 100L, 30)

        assertEquals(7L, intent.getLongExtra(ReminderConstants.EXTRA_ALERT_ID, -1L))
        assertEquals(42L, intent.getLongExtra(ReminderConstants.EXTRA_EVENT_ID, -1L))
        assertEquals(100L, intent.getLongExtra(ReminderConstants.EXTRA_INSTANCE_MILLIS, -1L))
        assertEquals(30, intent.snoozeOriginalMinutes())
    }

    @Test
    fun olderSnoozeIntentWithoutAnOffsetDefaultsToZero() {
        assertEquals(0, Intent().snoozeOriginalMinutes())
    }
}
