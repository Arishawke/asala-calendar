/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.ui

import android.content.ContentResolver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.arishawke.asala.calendar.data.EventRepository

// one Factory for the seven per-view event ViewModels (day / week / 3-day /
// month / mini-month / year / schedule). they differed only in the concrete
// type and its constructor call, so each call site passes its class and a
// builder over a fresh EventRepository, closing over the shared flows already
// in scope. de-dupes the byte-identical nested Factory each view carried (F9).
class EventViewModelFactory<VM : ViewModel>(
    private val contentResolver: ContentResolver,
    private val modelClass: Class<VM>,
    private val construct: (EventRepository) -> VM,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass == this.modelClass) { "unexpected ViewModel ${modelClass.name}" }
        return construct(EventRepository(contentResolver)) as T
    }
}
