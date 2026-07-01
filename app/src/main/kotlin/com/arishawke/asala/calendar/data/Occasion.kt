/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.data

enum class OccasionType { Birthday, Anniversary }

data class Occasion(
    val contactId: Long,
    val displayName: String,
    val type: OccasionType,
    val month: Int,
    val day: Int,
    val year: Int?,
)

// one occasion per contact per type, so contactId+type is stable across reloads.
val Occasion.stableId: String
    get() = "$contactId:${type.name}"
