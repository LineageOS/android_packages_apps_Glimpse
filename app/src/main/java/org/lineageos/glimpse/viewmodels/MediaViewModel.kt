/*
 * SPDX-FileCopyrightText: 2023 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.glimpse.viewmodels

import android.app.Application
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.lineageos.glimpse.repository.MediaRepository
import org.lineageos.glimpse.utils.MediaStoreBuckets

open class MediaViewModel(
    application: Application,
    private val bucketId: Int
) : GlimpseViewModel(application) {
    val media = MediaRepository.media(context, bucketId).flowOn(Dispatchers.IO).map {
        QueryResult.Data(it)
    }.stateIn(
        viewModelScope,
        started = SharingStarted.WhileSubscribed(),
        initialValue = QueryResult.Empty(),
    )

    companion object {
        fun factory(
            application: Application,
            bucketId: Int = MediaStoreBuckets.MEDIA_STORE_BUCKET_REELS.id
        ) = viewModelFactory {
            initializer {
                MediaViewModel(
                    application = application,
                    bucketId = bucketId,
                )
            }
        }
    }
}
