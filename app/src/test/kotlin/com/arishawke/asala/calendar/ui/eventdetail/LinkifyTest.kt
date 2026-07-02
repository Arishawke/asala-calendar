/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.ui.eventdetail

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.LinkAnnotation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private val LINK_COLOR = Color.Blue

class LinkifyTest {
    private fun urlsOf(text: String): List<String> {
        val result = linkifyAnnotated(text, LINK_COLOR)
        return result.getLinkAnnotations(0, result.length).mapNotNull { (it.item as? LinkAnnotation.Url)?.url }
    }

    // regression pin (see Linkify.kt's own comment): the URL body is matched
    // greedily on purpose. a non-greedy form previously truncated the match at
    // the first char the trailing-quantifier could stop on, cutting a real URL
    // down to a stub. a query string and fragment give the regex plenty of
    // opportunity to stop early if the greediness regresses.
    @Test fun `captures the full url including query string and fragment`() {
        val text = "See https://example.com/a/b?x=1&y=2#frag for details."
        assertEquals(listOf("https://example.com/a/b?x=1&y=2#frag"), urlsOf(text))
    }

    // trailing sentence punctuation must not become part of the tappable link,
    // but the visible text must still show it (it wasn't dropped, just un-linked).
    @Test fun `trailing sentence punctuation is trimmed from the link but stays visible`() {
        val text = "See https://example.com/page, thanks."
        val result = linkifyAnnotated(text, LINK_COLOR)
        val link = result.getLinkAnnotations(0, result.length).single()
        assertEquals("https://example.com/page", (link.item as LinkAnnotation.Url).url)
        assertEquals("visible text is unchanged", text, result.text)
        assertEquals(
            "the comma is excluded from the link's own range",
            "https://example.com/page",
            result.text.substring(link.start, link.end),
        )
    }

    // documented case in Linkify.kt: a trailing close-paren from `(see url)`
    // prose must not be swallowed into the link either.
    @Test fun `trailing close-paren is trimmed for a url wrapped in parens`() {
        val text = "(see https://x.y/z)"
        val result = linkifyAnnotated(text, LINK_COLOR)
        val link = result.getLinkAnnotations(0, result.length).single()
        assertEquals("https://x.y/z", (link.item as LinkAnnotation.Url).url)
        assertEquals(text, result.text)
    }

    // a bare email gets a mailto: link so it's tappable, but the visible run
    // stays the plain address, not the mailto: prefix.
    @Test fun `bare email is linkified with an added mailto scheme`() {
        val text = "Contact jane@example.com for details"
        val result = linkifyAnnotated(text, LINK_COLOR)
        val link = result.getLinkAnnotations(0, result.length).single()
        assertEquals("mailto:jane@example.com", (link.item as LinkAnnotation.Url).url)
        assertEquals(
            "visible run is the bare address, not the added scheme",
            "jane@example.com",
            result.text.substring(link.start, link.end),
        )
    }

    // already-schemed https / tel / mailto tokens pass through with their own
    // scheme untouched, each annotation range pointing at exactly its own token.
    @Test fun `https tel and mailto schemes pass through with correct ranges`() {
        val text = "Site https://ex.com/page, call tel:+15551234567; mail mailto:a@b.co."
        val result = linkifyAnnotated(text, LINK_COLOR)
        val links = result.getLinkAnnotations(0, result.length)
        val urls = links.mapNotNull { (it.item as? LinkAnnotation.Url)?.url }
        assertEquals(listOf("https://ex.com/page", "tel:+15551234567", "mailto:a@b.co"), urls)
        for (link in links) {
            val url = (link.item as LinkAnnotation.Url).url
            assertEquals("range covers exactly this token", url, result.text.substring(link.start, link.end))
        }
    }

    // no recognizable link token: the string must come back byte-identical
    // with no annotations, so plain prose never grows a stray tap target.
    @Test fun `plain text with no link tokens is left unannotated`() {
        val text = "Lunch with the team, bring notes."
        val result = linkifyAnnotated(text, LINK_COLOR)
        assertEquals(text, result.text)
        assertTrue(result.getLinkAnnotations(0, result.length).isEmpty())
    }
}
