/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.ui.header

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.arishawke.asala.calendar.CalendarView
import com.arishawke.asala.calendar.R
import com.arishawke.asala.calendar.label
import com.arishawke.asala.calendar.ui.LocalToolbarPosition
import com.arishawke.asala.calendar.ui.settings.ToolbarPosition

// view-switcher dropdown. top mode uses the stock DropdownMenu below the
// icon; bottom mode positions its own popup because the stock provider
// cannot reach an anchor inside the nav-bar zone: it clamps the menu 48dp
// above the usable window bottom, leaving it afloat over the FAB.
@Composable
internal fun ViewSwitcherMenu(
    currentView: CalendarView,
    onSelectView: (CalendarView) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    // Box anchors the dropdown and keeps a single top-level emitter
    // (compose-lints ComposeMultipleContentEmitters).
    Box(modifier = modifier) {
        IconButton(onClick = { expanded = true }) {
            Icon(
                painter = painterResource(R.drawable.ic_view_switcher),
                contentDescription = stringResource(R.string.cd_switch_view),
            )
        }
        if (LocalToolbarPosition.current == ToolbarPosition.Bottom) {
            if (expanded) {
                val spacingPx = with(LocalDensity.current) { MenuAnchorSpacing.roundToPx() }
                Popup(
                    popupPositionProvider = remember(spacingPx) { AboveAnchorEndAligned(spacingPx) },
                    onDismissRequest = { expanded = false },
                    properties = PopupProperties(focusable = true),
                ) {
                    Surface(
                        shape = MaterialTheme.shapes.extraSmall,
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        shadowElevation = MenuElevation,
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(vertical = MenuVerticalPadding)
                                // items fillMaxWidth; size the column to the
                                // widest item like the stock menu, not the window
                                .width(IntrinsicSize.Max)
                                .verticalScroll(rememberScrollState()),
                        ) {
                            ViewSwitcherItems(currentView) { view ->
                                onSelectView(view)
                                expanded = false
                            }
                        }
                    }
                }
            }
        } else {
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                ViewSwitcherItems(currentView) { view ->
                    onSelectView(view)
                    expanded = false
                }
            }
        }
    }
}

@Composable
private fun ViewSwitcherItems(currentView: CalendarView, onPick: (CalendarView) -> Unit) {
    CalendarView.entries.forEach { view ->
        val isCurrent = view == currentView
        DropdownMenuItem(
            text = { Text(view.label()) },
            onClick = { onPick(view) },
            trailingIcon = if (isCurrent) {
                { Icon(Icons.Filled.Check, contentDescription = null) }
            } else {
                null
            },
            modifier = Modifier.semantics { selected = isCurrent },
        )
    }
}

// end edge on the anchor's end so the menu grows away from the FAB, bottom
// as close above the anchor as the popup window allows (flush to the bar).
private class AboveAnchorEndAligned(private val spacingPx: Int) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val x = if (layoutDirection == LayoutDirection.Ltr) {
            anchorBounds.right - popupContentSize.width
        } else {
            anchorBounds.left
        }
        val y = (anchorBounds.top - spacingPx - popupContentSize.height)
            .coerceAtMost(windowSize.height - popupContentSize.height)
        return IntOffset(x.coerceAtLeast(0), y.coerceAtLeast(0))
    }
}

private val MenuAnchorSpacing = 4.dp
private val MenuVerticalPadding = 8.dp
private val MenuElevation = 3.dp
