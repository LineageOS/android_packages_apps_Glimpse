/*
 * SPDX-FileCopyrightText: 2023 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.glimpse.viewmodels

import android.app.Application
import androidx.lifecycle.LiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import org.lineageos.glimpse.utils.MediaStoreBuckets

class MediaViewerViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle,
    bucketId: Int,
) : MediaViewModel(application, bucketId) {
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
            bucketId: Int = MediaStoreBuckets.MEDIA_STORE_BUCKET_REELS.id
        ) = viewModelFactory {
            initializer {
                MediaViewerViewModel(
                    application = application,
                    savedStateHandle = createSavedStateHandle(),
                    bucketId = bucketId,
                )
            }
        }
    }
}
