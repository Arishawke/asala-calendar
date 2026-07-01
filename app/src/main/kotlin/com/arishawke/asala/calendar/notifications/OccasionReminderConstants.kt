/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.notifications

// 0 minutes = 9 AM on the day, per the all-day reminder time math
const val OCCASION_REMINDER_DEFAULT_MINUTES = 0

// stored value meaning "None" (reminder off). real reminder offsets are always
// >= 0, so a negative sentinel is unambiguous and lets an absent key mean the
// on-by-default value instead of colliding with an explicit None.
const val OCCASION_REMINDER_NONE_SENTINEL = -1
