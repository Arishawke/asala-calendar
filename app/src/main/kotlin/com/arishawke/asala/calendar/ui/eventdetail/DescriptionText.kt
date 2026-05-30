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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.style.TextDecoration

// Some sync sources store Events.DESCRIPTION as HTML (<br>, <a href>,
// <b>); CalDAV and hand-edited entries store plain text where \n is
// meaningful. Sniff for a structural tag before parsing so plain-text
// line breaks survive intact.
private val HtmlTagHint =
    Regex("""<\s*/?\s*(br|p|div|span|b|i|u|a|ul|ol|li|strong|em|h[1-6])\b""", RegexOption.IGNORE_CASE)

internal fun looksLikeHtml(description: String): Boolean = HtmlTagHint.containsMatchIn(description)

@Composable
internal fun DescriptionText(description: String, modifier: Modifier = Modifier) {
    val linkColor = MaterialTheme.colorScheme.primary
    val rendered = remember(description, linkColor) {
        if (looksLikeHtml(description)) {
            AnnotatedString.fromHtml(
                htmlString = description,
                linkStyles = TextLinkStyles(
                    style = SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline),
                ),
            )
        } else {
            linkifyAnnotated(description, linkColor)
        }
    }
    Text(text = rendered, style = MaterialTheme.typography.bodyMedium, modifier = modifier)
}
