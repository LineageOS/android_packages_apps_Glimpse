/*
 * SPDX-FileCopyrightText: 2023 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.glimpse.viewmodels

import android.app.Application
import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import org.lineageos.glimpse.models.RequestStatus
import org.lineageos.glimpse.utils.MediaStoreBuckets

class MediaViewerViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle,
    bucketId: Int,
    mimeType: String? = null,
) : GlimpseViewModel(application) {
    val media = mediaRepository.album(Uri.EMPTY)
        .flowOn(Dispatchers.IO)
        .stateIn(
            viewModelScope,
            started = SharingStarted.WhileSubscribed(),
            initialValue = RequestStatus.Loading(),
        )

    private val mediaPositionInternal = savedStateHandle.getLiveData<Int>(MEDIA_POSITION_KEY)
    val mediaPositionLiveData: LiveData<Int> = mediaPositionInternal
    var mediaPosition: Int?
        get() = mediaPositionInternal.value
        set(value) {
            mediaPositionInternal.value = value
        }

    companion object {
        private const val MEDIA_POSITION_KEY = "position"

        fun factory(
            application: Application,
            bucketId: Int = MediaStoreBuckets.MEDIA_STORE_BUCKET_REELS.id,
            mimeType: String? = null,
        ) = viewModelFactory {
            initializer {
                MediaViewerViewModel(
                    application = application,
                    savedStateHandle = createSavedStateHandle(),
                    bucketId = bucketId,
                    mimeType = mimeType,
                )
            }
        }
    }
}
