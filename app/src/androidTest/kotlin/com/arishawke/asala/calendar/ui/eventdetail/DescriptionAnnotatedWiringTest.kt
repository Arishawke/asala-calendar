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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

// DescriptionLinkTest (unit) already covers stripDisallowedLinkSchemes itself in
// isolation; this covers the one-line wiring in descriptionAnnotated that actually
// routes the html branch through it (audit F7/T3) - the wiring IS the security
// control. AnnotatedString.fromHtml needs the real Android HTML parser, so this
// runs as instrumentation rather than a plain JVM unit test.
@RunWith(AndroidJUnit4::class)
class DescriptionAnnotatedWiringTest {
    private fun urlsOf(result: AnnotatedString): List<String> =
        result.getLinkAnnotations(0, result.length).mapNotNull { (it.item as? LinkAnnotation.Url)?.url }

    @Test
    fun htmlDescriptionWithAJavascriptHrefKeepsNoUrlAnnotation() {
        val html = """an <a href="javascript:alert(1)">x</a> link"""

        val result = descriptionAnnotated(html, Color.Blue)

        assertEquals("javascript: href must not survive as a tappable link", emptyList<String>(), urlsOf(result))
    }

    @Test
    fun htmlDescriptionWithAnHttpsHrefKeepsExactlyOneLink() {
        val html = """an <a href="https://example.com">x</a> link"""

        val result = descriptionAnnotated(html, Color.Blue)

        assertEquals("an allowlisted https href survives", listOf("https://example.com"), urlsOf(result))
    }
}
