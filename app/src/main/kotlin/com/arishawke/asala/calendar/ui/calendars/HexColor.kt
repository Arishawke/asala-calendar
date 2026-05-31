/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.ui.calendars

// hex <-> ARGB for the color picker. accepts #RRGGBB / #RGB (and without
// the #), case-insensitive; always forces opaque alpha so chips stay legible.
object HexColor {
    private const val OPAQUE_ALPHA = 0xFF000000.toInt()
    private const val RGB_MASK = 0xFFFFFF
    private const val HEX_RADIX = 16
    private const val LEN_FULL = 6
    private const val LEN_SHORT = 3

    fun parse(input: String): Int? {
        val body = input.trim().removePrefix("#")
        val rgb = when (body.length) {
            LEN_FULL -> body
            LEN_SHORT -> buildString { body.forEach { append(it).append(it) } }
            else -> null
        }
        val value = rgb?.toIntOrNull(radix = HEX_RADIX) ?: return null
        return value or OPAQUE_ALPHA
    }

    fun format(argb: Int): String = "#%06X".format(argb and RGB_MASK)
}
