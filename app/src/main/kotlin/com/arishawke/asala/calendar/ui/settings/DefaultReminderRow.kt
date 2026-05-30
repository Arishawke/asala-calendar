/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.ui.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.arishawke.asala.calendar.ui.eventedit.ReminderPicker

// Settings row for the timed / all-day default reminder. Routes through
// the existing ReminderPicker so the Custom… flow, preset list, and
// label formatting stay identical to the event editor; only the label
// resource differs between the timed and all-day rows.
@Composable
internal fun DefaultReminderRow(labelResId: Int, current: Int?, onChange: (Int?) -> Unit) {
    ReminderPicker(
        minutesBefore = current,
        onChange = onChange,
        labelResId = labelResId,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    )
}
