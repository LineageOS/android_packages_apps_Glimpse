/*
 * SPDX-FileCopyrightText: 2023 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.glimpse.viewmodels

import androidx.lifecycle.LiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import org.lineageos.glimpse.GlimpseApplication
import org.lineageos.glimpse.repository.MediaRepository

class MediaViewModel(
    private val savedStateHandle: SavedStateHandle, private val mediaRepository: MediaRepository
) : ViewModel() {
    private val mediaPositionInternal = savedStateHandle.getLiveData<Int>(MEDIA_POSITION_KEY)
    val mediaPositionLiveData: LiveData<Int> = mediaPositionInternal
    var mediaPosition: Int
        set(value) {
            mediaPositionInternal.value = value
        }
        get() = mediaPositionInternal.value!!

    val media = mediaRepository.media(null)
    val albums = mediaRepository.albums()

    private val bucketId = MutableStateFlow<Int?>(null)
    fun setBucketId(bucketId: Int?) {
        this.bucketId.value = bucketId
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val mediaForAlbum = bucketId.flatMapLatest { mediaRepository.media(it) }

    companion object {
        private const val MEDIA_POSITION_KEY = "position"

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                MediaViewModel(
                    savedStateHandle = createSavedStateHandle(),
                    mediaRepository = (this[APPLICATION_KEY] as GlimpseApplication).mediaRepository,
                )
            }
        }
    }
}
