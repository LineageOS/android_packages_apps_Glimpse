/*
 * SPDX-FileCopyrightText: 2023 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.glimpse.viewmodels

import android.app.Application
import android.net.Uri
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import org.lineageos.glimpse.models.Media
import org.lineageos.glimpse.models.RequestStatus
import org.lineageos.glimpse.ui.recyclerview.ThumbnailAdapter
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Date

class AlbumViewModel(application: Application) : GlimpseViewModel(application) {
    private val albumUri = MutableStateFlow<Uri?>(null)
    private val mimeType = MutableStateFlow<String?>(null)

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

    @OptIn(ExperimentalCoroutinesApi::class)
    val albumWithHeaders = album
        .mapLatest {
            when (it) {
                is RequestStatus.Loading -> null

                is RequestStatus.Success -> {
                    val (_, medias) = it.data

                    mutableListOf<AlbumContent>().apply {
                        for (i in medias.indices) {
                            val currentMedia = medias[i]

                            if (i == 0) {
                                // First element must always be a header
                                add(AlbumContent.DateHeader(currentMedia.dateModified))
                                add(AlbumContent.MediaItem(currentMedia))
                                continue
                            }

                            val previousMedia = medias[i - 1]

                            val before = previousMedia.dateModified.toInstant().atZone(
                                ZoneId.systemDefault()
                            )
                            val after = currentMedia.dateModified.toInstant().atZone(
                                ZoneId.systemDefault()
                            )
                            val days = ChronoUnit.DAYS.between(after, before)

                            if (days >= 1 || before.dayOfMonth != after.dayOfMonth) {
                                add(AlbumContent.DateHeader(currentMedia.dateModified))
                            }

                            add(AlbumContent.MediaItem(currentMedia))
                        }
                    }
                }

                is RequestStatus.Error -> listOf()
            }
        }
        .filterNotNull()
        .flowOn(Dispatchers.IO)
        .stateIn(
            viewModelScope,
            started = SharingStarted.WhileSubscribed(),
            initialValue = listOf(),
        )

    val inSelectionMode = MutableLiveData(false)

    sealed class DataType(val viewType: Int) {
        class Thumbnail(
            val media: Media,
        ) : DataType(ThumbnailAdapter.ViewType.THUMBNAIL.ordinal) {
            override fun equals(other: Any?) = media == other
            override fun hashCode() = media.hashCode()
        }

        class DateHeader(
            val date: Date,
        ) : DataType(ThumbnailAdapter.ViewType.DATE_HEADER.ordinal) {
            override fun equals(other: Any?) = date == other
            override fun hashCode() = date.hashCode()
        }
    }

    fun loadAlbum(albumUri: Uri) {
        this.albumUri.value = albumUri
    }
}
