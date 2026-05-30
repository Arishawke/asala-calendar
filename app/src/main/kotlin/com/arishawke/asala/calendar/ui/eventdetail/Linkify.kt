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

// Matches http(s) URLs, bare emails, and tel: links inside plain-text
// notes / location strings. Phone numbers without a tel: prefix are
// intentionally not detected - too many false positives on dates,
// addresses, and event IDs.
//
// The URL body class is matched greedily so the full URL is captured
// (an earlier `+?` non-greedy form truncated URLs after the first
// match-anchor character, e.g. `https://github.co` for the full repo
// URL). Trailing punctuation that's likely sentence noise rather than
// URL content is stripped after the match via `TrailingUrlNoise`.
private val LinkPattern = Regex(
    "(?i)" +
        "(https?://[\\w\\-._~:/?#\\[\\]@!\$&'()*+,;=%]+)" +
        "|(\\bmailto:[\\w._+-]+@[\\w-]+(?:\\.[\\w-]+)+)" +
        "|(\\btel:[+\\d()\\-\\s]+)" +
        "|(\\b[\\w._+-]+@[\\w-]+(?:\\.[\\w-]+)+\\b)",
)

// Punctuation a sentence-end URL is unlikely to actually contain. A
// trailing closing paren is also dropped to handle `(see https://x.y/z)`.
private val TrailingUrlNoise = Regex("[.,;:!?)]+$")

// Build an AnnotatedString with tappable spans for any URLs / emails /
// tel: links inside a plain-text body. Bare email addresses are wrapped
// as mailto: at click time; bare http/https URLs and tel: links pass
// through verbatim.
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
            // Strip trailing sentence punctuation so a URL ending a
            // sentence doesn't include the period; whatever's trimmed gets
            // appended as plain text after the link so the visible string
            // matches the input.
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
