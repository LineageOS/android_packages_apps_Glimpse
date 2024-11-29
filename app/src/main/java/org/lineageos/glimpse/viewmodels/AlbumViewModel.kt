/*
 * SPDX-FileCopyrightText: 2023 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.glimpse.viewmodels

import android.app.Application
import android.net.Uri
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.lineageos.glimpse.models.Media
import org.lineageos.glimpse.models.RequestStatus
import org.lineageos.glimpse.ui.recyclerview.ThumbnailAdapter
import org.lineageos.glimpse.utils.MediaStoreBuckets
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Date

class AlbumViewModel(application: Application) : GlimpseViewModel(application) {
    private val albumUri = MutableStateFlow<Uri?>(null)
    private val mimeType = MutableStateFlow<String?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val mediaWithHeaders = albumUri
        .filterNotNull()
        .flatMapLatest { mediaRepository.album(it) }
        .map { medias ->
            val data = when (true) {
                true -> mutableListOf<DataType>().apply {
                }

                false -> mutableListOf<DataType>().apply {
                }
            }

            QueryResult.Data(data)
        }
        .flowOn(Dispatchers.IO)
        .stateIn(
            viewModelScope,
            started = SharingStarted.WhileSubscribed(),
            initialValue = QueryResult.Empty(),
        )

    @OptIn(ExperimentalCoroutinesApi::class)
    val album = albumUri
        .filterNotNull()
        .flatMapLatest { albumUri -> mediaRepository.album(albumUri) }
        .flowOn(Dispatchers.IO)
        .stateIn(
            viewModelScope,
            started = SharingStarted.WhileSubscribed(),
            initialValue = RequestStatus.Loading(),
        )

    val inSelectionMode = MutableLiveData(false)

    sealed class DataType(val viewType: Int) {
        class Thumbnail(
            val media: Media,
        ) : DataType(ThumbnailAdapter.ViewTypes.THUMBNAIL.ordinal) {
            override fun equals(other: Any?) = media == other
            override fun hashCode() = media.hashCode()
        }

        class DateHeader(
            val date: Date,
        ) : DataType(ThumbnailAdapter.ViewTypes.DATE_HEADER.ordinal) {
            override fun equals(other: Any?) = date == other
            override fun hashCode() = date.hashCode()
        }
    }
}
