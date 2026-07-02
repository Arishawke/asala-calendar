/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.ui.eventdetail

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.style.TextDecoration

// some sources store DESCRIPTION as HTML, others as plain text where \n
// matters. sniff for a structural tag first so plain-text breaks survive.
private val HtmlTagHint =
    Regex("""<\s*/?\s*(br|p|div|span|b|i|u|a|ul|ol|li|strong|em|h[1-6])\b""", RegexOption.IGNORE_CASE)

internal fun looksLikeHtml(description: String): Boolean = HtmlTagHint.containsMatchIn(description)

// the looksLikeHtml branch IS the security control (audit F7/T3): AnnotatedString.fromHtml
// turns any <a href> into a clickable link with no scheme check, so the html path is routed
// through the same allowlist stripDisallowedLinkSchemes already enforces for it.
internal fun descriptionAnnotated(text: String, linkColor: Color): AnnotatedString = if (looksLikeHtml(text)) {
    AnnotatedString.fromHtml(
        htmlString = text,
        linkStyles = TextLinkStyles(
            style = SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline),
        ),
    ).stripDisallowedLinkSchemes()
} else {
    linkifyAnnotated(text, linkColor)
}

@Composable
internal fun DescriptionText(description: String, modifier: Modifier = Modifier) {
    val linkColor = MaterialTheme.colorScheme.primary
    val rendered = remember(description, linkColor) { descriptionAnnotated(description, linkColor) }
    Text(text = rendered, style = MaterialTheme.typography.bodyMedium, modifier = modifier)
}
