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

// an event description is semi-trusted (any WRITE_CALENDAR app or a CalDAV sync
// adapter can set it), so links are restricted to these safe schemes. the
// plain-text linkifier only ever produces these; the HTML path is filtered to
// match (audit F7).
private val AllowedLinkSchemes = setOf("http", "https", "mailto", "tel")

private fun isAllowedLinkScheme(url: String): Boolean =
    url.substringBefore(':', missingDelimiterValue = "").lowercase() in AllowedLinkSchemes

// AnnotatedString.fromHtml turns every <a href> into a clickable Url link with no
// scheme check. rebuild without the disallowed ones so their text stays visible
// but inert, preserving styling and the allowed links. same-instance fast path
// when nothing needs stripping.
internal fun AnnotatedString.stripDisallowedLinkSchemes(): AnnotatedString {
    val source = this
    val links = source.getLinkAnnotations(0, source.length)
    val hasDisallowed = links.any { range ->
        (range.item as? LinkAnnotation.Url)?.let { !isAllowedLinkScheme(it.url) } ?: false
    }
    if (!hasDisallowed) return source
    return buildAnnotatedString {
        append(source.text)
        source.spanStyles.forEach { addStyle(it.item, it.start, it.end) }
        source.paragraphStyles.forEach { addStyle(it.item, it.start, it.end) }
        links.forEach { range ->
            val item = range.item
            if (item is LinkAnnotation.Url && isAllowedLinkScheme(item.url)) {
                addLink(item, range.start, range.end)
            }
        }
    }
}

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
