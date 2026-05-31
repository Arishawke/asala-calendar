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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink

// http(s) URLs, bare emails, tel: links. bare phone numbers without tel:
// are intentionally skipped (too many false positives on dates / IDs).
// URL body matched greedily: a non-greedy form truncated URLs after the
// first match-anchor char. trailing sentence noise stripped below.
private val LinkPattern = Regex(
    "(?i)" +
        "(https?://[\\w\\-._~:/?#\\[\\]@!\$&'()*+,;=%]+)" +
        "|(\\bmailto:[\\w._+-]+@[\\w-]+(?:\\.[\\w-]+)+)" +
        "|(\\btel:[+\\d()\\-\\s]+)" +
        "|(\\b[\\w._+-]+@[\\w-]+(?:\\.[\\w-]+)+\\b)",
)

// sentence-end punctuation a URL is unlikely to contain. trailing paren
// also dropped to handle `(see https://x.y/z)`.
private val TrailingUrlNoise = Regex("[.,;:!?)]+$")

// tappable spans for URLs / emails / tel: in a plain-text body. bare
// emails get a mailto: prefix at click time; the rest pass through verbatim.
internal fun linkifyAnnotated(text: String, linkColor: Color): AnnotatedString {
    val styles = TextLinkStyles(
        style = SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline),
    )
    return buildAnnotatedString {
        var cursor = 0
        for (match in LinkPattern.findAll(text)) {
            if (match.range.first > cursor) {
                append(text.substring(cursor, match.range.first))
            }
            // trim trailing punctuation off the link, re-append it as plain
            // text so the visible string still matches the input.
            val rawMatched = match.value
            val trailing = TrailingUrlNoise.find(rawMatched)?.value.orEmpty()
            val matched = if (trailing.isNotEmpty()) {
                rawMatched.substring(0, rawMatched.length - trailing.length)
            } else {
                rawMatched
            }
            val url = when {
                matched.startsWith("http", ignoreCase = true) -> matched
                matched.startsWith("mailto:", ignoreCase = true) -> matched
                matched.startsWith("tel:", ignoreCase = true) -> matched
                "@" in matched -> "mailto:$matched"
                else -> matched
            }
            withLink(LinkAnnotation.Url(url, styles = styles)) {
                append(matched)
            }
            if (trailing.isNotEmpty()) {
                append(trailing)
            }
            cursor = match.range.last + 1
        }
        if (cursor < text.length) {
            append(text.substring(cursor))
        }
    }
}
