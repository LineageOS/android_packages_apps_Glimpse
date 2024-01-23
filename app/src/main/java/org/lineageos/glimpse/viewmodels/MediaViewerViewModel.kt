/*
 * SPDX-FileCopyrightText: 2023 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.glimpse.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.lineageos.glimpse.ext.*
import org.lineageos.glimpse.repository.MediaRepository
import org.lineageos.glimpse.utils.MediaStoreBuckets

class MediaViewerViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle,
    bucketId: Int,
    mimeType: String? = null,
) : AndroidViewModel(application) {
    val media = MediaRepository.media(context, bucketId, mimeType).flowOn(Dispatchers.IO).map {
        QueryResult.Data(it)
    }.stateIn(
        viewModelScope,
        started = SharingStarted.WhileSubscribed(),
        initialValue = QueryResult.Empty(),
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
