/*
 * SPDX-FileCopyrightText: 2023 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.glimpse.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import org.lineageos.glimpse.ext.*
import org.lineageos.glimpse.repository.MediaRepository

class LocationViewModel(
    application: Application,
) : AndroidViewModel(application) {
    val locations = MediaRepository.locations(context).flowOn(Dispatchers.IO).stateIn(
        viewModelScope,
        started = SharingStarted.WhileSubscribed(),
        initialValue = null,
    )
}
