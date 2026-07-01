/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.ui

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf

// the app's two provisioned occasion calendars (birthdays, anniversaries), or
// empty when the feature is off. render code gates the age/ordinal relabel on
// membership here, so a third-party calendar merely NAMED "Birthdays" keeps its
// own event titles instead of being retitled "<desc> turns N" (audit F4). the
// cake icon stays name-based and is unaffected.
val LocalOccasionCalendarIds: ProvidableCompositionLocal<Set<Long>> = compositionLocalOf { emptySet() }
