/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.ui.eventedit

import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// compact numeric field that tolerates transient edits: the user can clear
// it or hold an out-of-range value while typing (shown as error); only
// valid values commit, and focus loss restores the last committed one.
@Suppress("LongParameterList")
@Composable
internal fun CompactNumberField(
    value: Int,
    maxDigits: Int,
    range: IntRange,
    width: Dp,
    onCommit: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var text by remember { mutableStateOf(value.toString()) }
    // resync when the committed value changes underneath (frequency switch)
    LaunchedEffect(value) {
        if (value != text.toIntOrNull()) text = value.toString()
    }
    // width follows the font-scale setting so digits never clip at 150%
    val scaledWidth = width * LocalDensity.current.fontScale
    OutlinedTextField(
        value = text,
        onValueChange = { input ->
            val digits = input.filter { it.isDigit() }
            // reject an over-length edit rather than truncate a mid-string insert
            if (digits.length <= maxDigits) {
                text = digits
                digits.toIntOrNull()?.let { if (it in range) onCommit(it) }
            }
        },
        isError = text.isNotEmpty() && text.toIntOrNull() !in range,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier
            .width(scaledWidth)
            .onFocusChanged { focus ->
                if (!focus.isFocused && text != value.toString()) text = value.toString()
            },
    )
}

internal val IntervalFieldWidth = 76.dp
internal val CountFieldWidth = 96.dp
