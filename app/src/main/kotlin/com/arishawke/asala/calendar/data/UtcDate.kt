/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

// epoch millis -> calendar date read in UTC. all-day events and occasion rows
// anchor their date at UTC midnight, so their millis must be decoded in UTC; a
// local zone would shift the date across the day boundary.
internal fun utcDate(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
