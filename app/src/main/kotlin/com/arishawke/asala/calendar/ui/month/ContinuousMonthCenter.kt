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

// Visible-item info needed for center detection. Shaped to match
// LazyListItemInfo (index / offset / size) without taking a Compose
// dependency in unit tests.
internal data class VisibleItem(val index: Int, val offset: Int, val size: Int)

// Picks the visible item whose vertical center is nearest the viewport
// center. Returns the index of that item, or `fallback` when the list
// is empty (first frame, before layout completes). Ties resolve to the
// first match in `items` order.
internal object ContinuousMonthCenter {
    fun pick(items: List<VisibleItem>, viewportCenter: Int, fallback: Int): Int {
        if (items.isEmpty()) return fallback
        return items.minBy { abs(it.offset + it.size / 2 - viewportCenter) }.index
    }
}
