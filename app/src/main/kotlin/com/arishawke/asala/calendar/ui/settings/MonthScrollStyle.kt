/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.ui.settings

// Selector for the Month-view scroll surface. Paged keeps the historic
// HorizontalPager-of-grids behavior; Continuous swaps it for a vertical
// LazyColumn of month grids with sticky headers. Default Paged so
// existing installs see no behavior change.
enum class MonthScrollStyle {
    Paged,
    Continuous,
}
