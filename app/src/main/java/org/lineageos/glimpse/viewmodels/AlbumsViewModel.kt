/*
 * SPDX-FileCopyrightText: 2023 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.glimpse.viewmodels

import android.app.Application
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import org.lineageos.glimpse.models.RequestStatus

class AlbumsViewModel(application: Application): GlimpseViewModel(application) {
    private val mimeType = MutableStateFlow<String?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val albums = mimeType
        .flatMapLatest { mimeType -> mediaRepository.albums() }
        .flowOn(Dispatchers.IO)
        .stateIn(
            viewModelScope,
            started = SharingStarted.WhileSubscribed(),
            initialValue = RequestStatus.Loading()
        )

    fun setMimeType(mimeType: String?) {
        this.mimeType.value = mimeType
    }
}
