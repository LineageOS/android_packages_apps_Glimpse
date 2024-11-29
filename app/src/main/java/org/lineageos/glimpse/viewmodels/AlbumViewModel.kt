/*
 * SPDX-FileCopyrightText: 2023-2024 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.glimpse.viewmodels

import android.app.Application
import android.net.Uri
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import org.lineageos.glimpse.datasources.MediaError
import org.lineageos.glimpse.models.Album
import org.lineageos.glimpse.models.AlbumType
import org.lineageos.glimpse.models.Media
import org.lineageos.glimpse.models.MediaType
import org.lineageos.glimpse.models.RequestStatus
import org.lineageos.glimpse.models.RequestStatus.Companion.map
import org.lineageos.glimpse.models.UniqueItem
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Date
import kotlin.reflect.safeCast

class AlbumViewModel(application: Application) : GlimpseViewModel(application) {
    sealed class AlbumContent(val viewType: Int) : UniqueItem<AlbumContent> {
        data class DateHeader(val date: Date) : AlbumContent(ViewType.DATE_HEADER.ordinal) {
            override fun areItemsTheSame(other: AlbumContent) =
                DateHeader::class.safeCast(other)?.let {
                    date == it.date
                } ?: false

            override fun areContentsTheSame(other: AlbumContent) = true
        }

        class MediaItem(val media: Media) : AlbumContent(ViewType.THUMBNAIL.ordinal) {
            override fun areItemsTheSame(other: AlbumContent) = MediaItem::class.safeCast(
                other
            )?.let {
                media.areItemsTheSame(it.media)
            } ?: false

            override fun areContentsTheSame(other: AlbumContent) = MediaItem::class.safeCast(
                other
            )?.let {
                media.areContentsTheSame(it.media)
            } ?: false
        }

        enum class ViewType {
            THUMBNAIL,
            DATE_HEADER,
        }
    }

    data class AlbumRequest(
        val albumType: AlbumType? = null,
        val albumUri: Uri? = null,
        val mediaType: MediaType? = null,
        val mimeType: String? = null,
    )

    private val _albumRequest = MutableStateFlow<AlbumRequest?>(null)
    val albumRequest = _albumRequest.asStateFlow()

    private val _inSelectionMode = MutableStateFlow(false)
    val inSelectionMode = _inSelectionMode.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val album = albumRequest
        .filterNotNull()
        .flatMapLatest { albumRequest ->
            when (albumRequest.albumType) {
                AlbumType.REELS ->
                    mediaRepository.reels(albumRequest.mediaType).addAlbum(REELS_ALBUM)

                AlbumType.FAVORITES ->
                    mediaRepository.favorites().addAlbum(FAVORITES_ALBUM)

                AlbumType.TRASH -> mediaRepository.trash().addAlbum(TRASH_ALBUM)

                else -> albumRequest.albumUri?.let { albumUri ->
                    mediaRepository.album(albumUri)
                } ?: flowOf(RequestStatus.Loading())
            }
        }
        .mapLatest {
            it.map { data ->
                val (album, medias) = data

                album to mutableListOf<AlbumContent>().apply {
                    val addHeaders = album !== TRASH_ALBUM

                    if (addHeaders) {
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
                    } else {
                        medias.forEach { media ->
                            add(AlbumContent.MediaItem(media))
                        }
                    }
                }
            }
        }
        .flowOn(Dispatchers.IO)
        .stateIn(
            viewModelScope,
            started = SharingStarted.WhileSubscribed(),
            initialValue = RequestStatus.Loading(),
        )

    fun loadAlbum(albumRequest: AlbumRequest) {
        _albumRequest.value = albumRequest
    }

    fun setInSelectionMode(inSelectionMode: Boolean) {
        _inSelectionMode.value = inSelectionMode
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun <T> Flow<RequestStatus<T, MediaError>>.addAlbum(album: Album) = mapLatest {
        it.map { data -> album to data }
    }

    companion object {
        private val REELS_ALBUM = dummyAlbum("Reels")
        private val FAVORITES_ALBUM = dummyAlbum("Favorites")
        private val TRASH_ALBUM = dummyAlbum("Trash")

        private fun dummyAlbum(name: String) = Album(
            Uri.EMPTY,
            name,
            null,
            null,
        )
    }
}
