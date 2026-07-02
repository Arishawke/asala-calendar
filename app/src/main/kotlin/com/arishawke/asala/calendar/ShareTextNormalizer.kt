/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar

// normalizes ACTION_SEND text/plain before it reaches the natural-language
// parser: the parser is single-line regex-based, so a shared paragraph must
// collapse to one line rather than misparsing. blank input (missing
// EXTRA_TEXT, or all whitespace) normalizes to null so the caller can drop
// the share intent entirely.
object ShareTextNormalizer {
    private const val MaxLength = 500
    private val WHITESPACE_RUN = Regex("\\s+")

    fun normalize(raw: String?): String? {
        if (raw == null) return null
        val collapsed = raw.replace(WHITESPACE_RUN, " ").trim()
        return collapsed.ifEmpty { null }?.take(MaxLength)
    }
}
