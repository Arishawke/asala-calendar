/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.ui.eventedit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import com.arishawke.asala.calendar.R
import com.arishawke.asala.calendar.ui.eventedit.naturallanguage.EventTextParser
import com.arishawke.asala.calendar.ui.eventedit.naturallanguage.previewLine
import com.arishawke.asala.calendar.ui.theme.Spacing
import java.time.LocalDateTime

// new-event-only natural-language entry. parses on each change for the preview
// line; commits onto the form via onChange on Fill / keyboard Done. the
// structured fields below stay the source of truth and remain editable.
@Composable
fun QuickAddField(state: EventEditFormState, onChange: (EventEditFormState) -> Unit, is24Hour: Boolean) {
    val locale = LocalLocale.current.platformLocale
    var text by rememberSaveable { mutableStateOf("") }
    val parsed = remember(text, locale) {
        if (text.isBlank()) null else EventTextParser.parse(text, LocalDateTime.now(), locale)
    }
    val preview = remember(parsed, is24Hour) { parsed?.let { previewLine(it, is24Hour, locale) } }

    fun commit() {
        parsed?.let { onChange(state.withParsed(it)) }
    }

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text(stringResource(R.string.quick_add_label)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { commit() }),
            trailingIcon = {
                TextButton(onClick = { commit() }, enabled = parsed != null) {
                    Text(stringResource(R.string.quick_add_apply))
                }
            },
        )
        if (preview != null) {
            Text(
                text = preview,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
