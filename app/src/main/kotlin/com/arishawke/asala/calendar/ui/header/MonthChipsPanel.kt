/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.ui.header

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import java.time.YearMonth
import java.time.format.DateTimeFormatter

private const val MonthsEitherSide = 12

@Composable
internal fun MonthChipsPanel(
    viewedMonth: YearMonth,
    today: YearMonth,
    onSelectMonth: (YearMonth) -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = LocalConfiguration.current.locales.get(0)
    // "MMM yy" so a chip like "Jan 26" stays under ~52dp wide. Two-digit
    // year keeps the chip strip scannable; the full year shows in the
    // top app bar title.
    val fmt = remember(locale) { DateTimeFormatter.ofPattern("MMM yy", locale) }

    // 25-month window centered on today. Selecting a chip outside this
    // window is rare; user can swipe the Month pager for further jumps.
    val months = remember(today) {
        (-MonthsEitherSide..MonthsEitherSide).map { offset -> today.plusMonths(offset.toLong()) }
    }
    val selectedIndex = remember(months, viewedMonth) {
        months.indexOf(viewedMonth).coerceAtLeast(0)
    }

    val listState = rememberLazyListState()
    LaunchedEffect(selectedIndex) {
        // Land the selected chip a few slots from the left so adjacent
        // months are visible on either side instead of jamming the chip
        // against the edge.
        listState.scrollToItem(index = (selectedIndex - 2).coerceAtLeast(0))
    }

    LazyRow(
        modifier = modifier.fillMaxWidth(),
        state = listState,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
    ) {
        items(items = months, key = { it.toString() }) { month ->
            val isSelected = month == viewedMonth
            FilterChip(
                selected = isSelected,
                onClick = { onSelectMonth(month) },
                label = { Text(fmt.format(month)) },
                colors = FilterChipDefaults.filterChipColors(),
            )
            Spacer(modifier = Modifier.width(6.dp))
        }
    }
}
