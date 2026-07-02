/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.ui.eventdetail

import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import org.junit.Assert.assertEquals
import org.junit.Test

class DescriptionLinkTest {
    // audit F7: descriptions rendered from HTML (AnnotatedString.fromHtml) turned
    // any <a href> into a clickable link with no scheme check, unlike the plain-text
    // path which only ever links http/https/mailto/tel. drop the rest so a calendar
    // description another app wrote can't launch sms:/market:/javascript: etc.
    @Test fun `strips non-allowlisted link schemes and keeps http https mailto tel`() {
        val source = buildAnnotatedString {
            withLink(LinkAnnotation.Url("https://ok.example")) { append("site") }
            append(" ")
            withLink(LinkAnnotation.Url("tel:+15551234")) { append("call") }
            append(" ")
            withLink(LinkAnnotation.Url("mailto:a@b.co")) { append("mail") }
            append(" ")
            withLink(LinkAnnotation.Url("javascript:alert(1)")) { append("x") }
            append(" ")
            withLink(LinkAnnotation.Url("market://details?id=x")) { append("app") }
        }
        val filtered = source.stripDisallowedLinkSchemes()
        val urls = filtered.getLinkAnnotations(0, filtered.length)
            .mapNotNull { (it.item as? LinkAnnotation.Url)?.url }
        assertEquals(listOf("https://ok.example", "tel:+15551234", "mailto:a@b.co"), urls)
        // visible text is unchanged; only the link-ness of the bad ones is removed.
        assertEquals(source.text, filtered.text)
    }

    @Test fun `keeps every link when all schemes are allowed`() {
        val source = buildAnnotatedString {
            withLink(LinkAnnotation.Url("https://ok")) { append("ok") }
            withLink(LinkAnnotation.Url("HTTP://caps")) { append("caps") }
        }
        assertEquals(2, source.stripDisallowedLinkSchemes().getLinkAnnotations(0, source.length).size)
    }
}
