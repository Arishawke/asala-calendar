/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.ui.eventdetail

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// Guards the heuristic that decides whether to send a description through
// HtmlCompat (collapses whitespace, parses tags) or render it as-is.
// Misclassifying a plain-text note as HTML collapses real newlines; the
// reverse leaves meeting invites showing literal <br>.
class DescriptionTextTest {
    @Test fun `empty string is plain text`() {
        assertFalse(looksLikeHtml(""))
    }

    @Test fun `plain prose with no tags is plain text`() {
        assertFalse(looksLikeHtml("Buy milk, eggs, and bread"))
    }

    @Test fun `multi-line plain text is plain text`() {
        assertFalse(looksLikeHtml("Line one\nLine two\nLine three"))
    }

    @Test fun `comparison operators are plain text`() {
        assertFalse(looksLikeHtml("1 < 2 and 3 > 2"))
    }

    @Test fun `unknown angle-bracketed token is plain text`() {
        assertFalse(looksLikeHtml("Status: <pending> review"))
    }

    @Test fun `br tag is HTML`() {
        assertTrue(looksLikeHtml("Hello<br>world"))
    }

    @Test fun `self-closing br is HTML`() {
        assertTrue(looksLikeHtml("Hello<br/>world"))
    }

    @Test fun `closing br with space is HTML`() {
        assertTrue(looksLikeHtml("Hello<br />world"))
    }

    @Test fun `uppercase BR is HTML`() {
        assertTrue(looksLikeHtml("Hello<BR>world"))
    }

    @Test fun `anchor tag is HTML`() {
        assertTrue(looksLikeHtml("""Click <a href="https://example.com">here</a>"""))
    }

    @Test fun `bold tag is HTML`() {
        assertTrue(looksLikeHtml("This is <b>important</b>"))
    }

    @Test fun `paragraph tag is HTML`() {
        assertTrue(looksLikeHtml("<p>First paragraph</p><p>Second</p>"))
    }

    @Test fun `heading tag is HTML`() {
        assertTrue(looksLikeHtml("<h2>Section</h2>"))
    }

    @Test fun `realistic meeting invite is HTML`() {
        // The shape that motivated this fix: meeting invites combine
        // <br>, <a href>, and <b> in the description column.
        val invite = """~-~-~-~-~-~-~-~-~-~-~-~<br><a href="https://meet.google.com/abc-defg-hij">Join</a><br>""" +
            """<br>Booked by <b>Justin</b>"""
        assertTrue(looksLikeHtml(invite))
    }
}
