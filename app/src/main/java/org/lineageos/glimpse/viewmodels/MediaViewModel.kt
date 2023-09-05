/*
 * SPDX-FileCopyrightText: 2023 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.glimpse.viewmodels

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
import org.lineageos.glimpse.utils.MediaStoreBuckets

open class MediaViewModel(
    private val savedStateHandle: SavedStateHandle, private val mediaRepository: MediaRepository
) : ViewModel() {
    val media = mediaRepository.media(MediaStoreBuckets.MEDIA_STORE_BUCKET_REELS.id)
    val albums = mediaRepository.albums()

    private val bucketId = MutableStateFlow(MediaStoreBuckets.MEDIA_STORE_BUCKET_REELS.id)
    fun setBucketId(bucketId: Int) {
        this.bucketId.value = bucketId
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val mediaForAlbum = bucketId.flatMapLatest { mediaRepository.media(it) }

    companion object {
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
