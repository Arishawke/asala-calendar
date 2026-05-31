/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.ui.month

import kotlin.math.abs

// mirrors LazyListItemInfo (index/offset/size) without a Compose dep in tests
internal data class VisibleItem(val index: Int, val offset: Int, val size: Int)

// nearest visible item to viewport center; fallback when empty (first frame); ties to first
internal object ContinuousMonthCenter {
    fun pick(items: List<VisibleItem>, viewportCenter: Int, fallback: Int): Int {
        if (items.isEmpty()) return fallback
        return items.minBy { abs(it.offset + it.size / 2 - viewportCenter) }.index
    }
}
