/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.ui.eventedit

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EventEditLoadingGateTest {
    // an existing event loads its real data asynchronously, then replaces the
    // form wholesale. the editor must gate input until that load lands, else a
    // fast edit during a slow load is overwritten by the loaded values.
    @Test
    fun `editing an existing event gates input until loaded`() {
        assertTrue(shouldGateEditorUntilLoaded(editingEventId = 42L, duplicateFromEventId = null))
    }

    // duplicating seeds the form from a source event fetched the same async
    // way, so it has the same clobber window and must gate too.
    @Test
    fun `duplicating from an event gates input until loaded`() {
        assertTrue(shouldGateEditorUntilLoaded(editingEventId = null, duplicateFromEventId = 7L))
    }

    // a brand-new event has no async data to wait on: its form is editable
    // from the first frame and its edits already survive the calendar load
    // (the load path copies onto the current form rather than replacing it).
    @Test
    fun `a brand-new event does not gate input`() {
        assertFalse(shouldGateEditorUntilLoaded(editingEventId = null, duplicateFromEventId = null))
    }
}
