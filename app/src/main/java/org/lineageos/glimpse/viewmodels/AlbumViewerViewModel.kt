/*
 * SPDX-FileCopyrightText: 2023 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.glimpse.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.stateIn
import org.lineageos.glimpse.ext.*
import org.lineageos.glimpse.models.Album
import org.lineageos.glimpse.models.MediaStoreMedia
import org.lineageos.glimpse.recyclerview.ThumbnailAdapter
import org.lineageos.glimpse.repository.MediaRepository
import org.lineageos.glimpse.utils.MediaStoreBuckets
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Date

class AlbumViewerViewModel(
    application: Application,
    val bucketId: Int,
    mimeType: String? = null,
    addHeaders: Boolean,
) : AndroidViewModel(application) {
    val mediaWithHeaders = MediaRepository.media(context, bucketId, mimeType).flowOn(
        Dispatchers.IO
    ).map { medias ->
        val data = when (addHeaders) {
            true -> mutableListOf<DataType>().apply {
                for (i in medias.indices) {
                    val currentMedia = medias[i]

                    if (i == 0) {
                        // First element must always be a header
                        add(DataType.DateHeader(currentMedia.dateModified))
                        add(DataType.Thumbnail(currentMedia))
                        continue
                    }

                    val previousMedia = medias[i - 1]

                    val before = previousMedia.dateModified.toInstant().atZone(ZoneId.systemDefault())
                    val after = currentMedia.dateModified.toInstant().atZone(ZoneId.systemDefault())
                    val days = ChronoUnit.DAYS.between(after, before)

                    if (days >= 1 || before.dayOfMonth != after.dayOfMonth) {
                        add(DataType.DateHeader(currentMedia.dateModified))
                    }

                    add(DataType.Thumbnail(currentMedia))
                }
            }

            false -> medias.map {
                DataType.Thumbnail(it)
            }
        }

        QueryResult.Data(data)
    }.stateIn(
        viewModelScope,
        started = SharingStarted.WhileSubscribed(),
        initialValue = QueryResult.Empty(),
    )

    val album = MediaRepository.album(
        context, bucketId, mimeType
    ).flowOn(Dispatchers.IO).mapNotNull {
        it.firstOrNull()
    }.stateIn(
        viewModelScope,
        started = SharingStarted.WhileSubscribed(),
        initialValue = Album(-1, ""),
    )

    val inSelectionMode = MutableLiveData(false)

    sealed class DataType(val viewType: Int) {
        class Thumbnail(
            val media: MediaStoreMedia,
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

    companion object {
        fun factory(
            application: Application,
            bucketId: Int = MediaStoreBuckets.MEDIA_STORE_BUCKET_REELS.id,
            mimeType: String? = null,
            showHeaders: Boolean = bucketId != MediaStoreBuckets.MEDIA_STORE_BUCKET_TRASH.id,
        ) = viewModelFactory {
            initializer {
                AlbumViewerViewModel(
                    application = application,
                    bucketId = bucketId,
                    mimeType = mimeType,
                    addHeaders = showHeaders,
                )
            }
        }
    }
}
