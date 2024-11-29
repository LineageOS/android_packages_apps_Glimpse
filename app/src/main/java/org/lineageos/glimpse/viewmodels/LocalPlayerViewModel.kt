/*
 * SPDX-FileCopyrightText: 2023-2024 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.glimpse.viewmodels

import android.app.Application
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import org.lineageos.glimpse.ext.applicationContext
import org.lineageos.glimpse.ext.isPlayingFlow
import org.lineageos.glimpse.models.AlbumType
import org.lineageos.glimpse.models.Media
import org.lineageos.glimpse.models.MediaType
import org.lineageos.glimpse.models.RequestStatus
import org.lineageos.glimpse.models.RequestStatus.Companion.map
import org.lineageos.glimpse.ui.recyclerview.MediaViewerAdapter
import org.lineageos.glimpse.utils.MimeUtils
import java.time.Instant
import java.util.Date

class LocalPlayerViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle,
) : GlimpseViewModel(application) {
    // ExoPlayer
    val exoPlayer = ExoPlayer.Builder(applicationContext)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .setUsage(C.USAGE_MEDIA)
                .build(),
            true
        )
        .setHandleAudioBecomingNoisy(true)
        .build()
        .apply {
            repeatMode = ExoPlayer.REPEAT_MODE_ONE
        }

    val isPlaying = exoPlayer.isPlayingFlow()
        .flowOn(Dispatchers.Main)
        .stateIn(
            viewModelScope,
            started = SharingStarted.WhileSubscribed(),
            initialValue = false
        )

    /**
     * Album request.
     */
    private val albumRequest = MutableStateFlow<AlbumViewModel.AlbumRequest?>(null)

    /**
     * A list of static [Media]s.
     */
    private val staticContents =
        MutableStateFlow<List<IntentsViewModel.ParsedIntent.Content>?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val staticMedias = staticContents
        .mapLatest { staticMediaContents ->
            staticMediaContents?.map {
                Media(
                    uri = it.uri,
                    mediaType = it.mediaType,
                    mimeType = when (it.mediaType) {
                        MediaType.IMAGE -> MimeUtils.MIME_TYPE_IMAGE_ANY
                        MediaType.VIDEO -> MimeUtils.MIME_TYPE_VIDEO_ANY
                        else -> throw Exception("Invalid media type: ${it.mediaType}")
                    },
                    albumUri = Uri.EMPTY,
                    albumName = null,
                    displayName = "",
                    isFavorite = false,
                    isTrashed = false,
                    dateAdded = Date.from(Instant.now()),
                    dateModified = Date.from(Instant.now()),
                    width = 0,
                    height = 0,
                    orientation = 0,
                )
            }
        }
        .flowOn(Dispatchers.IO)
        .stateIn(
            viewModelScope,
            started = SharingStarted.WhileSubscribed(),
            initialValue = null,
        )

    @OptIn(ExperimentalCoroutinesApi::class)
    val album = albumRequest
        .filterNotNull()
        .flatMapLatest { albumRequest ->
            when (albumRequest.albumType) {
                AlbumType.REELS ->
                    mediaRepository.reels(albumRequest.mediaType)

                AlbumType.FAVORITES ->
                    mediaRepository.favorites()

                AlbumType.TRASH -> mediaRepository.trash()

                else -> mediaRepository.album(albumRequest.albumUri).mapLatest { album ->
                    album.map { it.second }
                }
            }
        }
        .flowOn(Dispatchers.IO)
        .stateIn(
            viewModelScope,
            started = SharingStarted.WhileSubscribed(),
            initialValue = RequestStatus.Loading(),
        )

    @OptIn(ExperimentalCoroutinesApi::class)
    val medias = staticMedias
        .flatMapLatest { staticMediaUris ->
            staticMediaUris?.let {
                flowOf(RequestStatus.Success(it))
            } ?: album.mapLatest { album ->
                album.map { it }
            }
        }
        .flowOn(Dispatchers.IO)
        .stateIn(
            viewModelScope,
            started = SharingStarted.WhileSubscribed(),
            initialValue = RequestStatus.Loading(),
        )

    /**
     * Whether we're being displayed on top of the lockscreen.
     */
    private val _secure = MutableStateFlow(false)
    val secure = _secure.asStateFlow()

    /**
     * Whether we should not be editing displayed media at all.
     */
    val readOnly = combine(staticMedias, secure) { staticMedias, secure ->
        staticMedias == null && !secure
    }
        .flowOn(Dispatchers.IO)
        .stateIn(
            viewModelScope,
            started = SharingStarted.WhileSubscribed(),
            initialValue = false,
        )

    /**
     * The latest media position.
     */
    private val _mediaPosition = savedStateHandle.getLiveData<Int?>(MEDIA_POSITION_KEY)
    val mediaPosition = savedStateHandle.getStateFlow<Int?>(MEDIA_POSITION_KEY, null)

    /**
     * The current height of top and bottom sheets, used to apply padding to media view UI.
     */
    private val _sheetsHeight = MutableStateFlow(0 to 0)
    val sheetsHeight = _sheetsHeight.asStateFlow()

    /**
     * Fullscreen mode, set by the user with a single tap on the viewed media.
     */
    private val _fullscreenMode = MutableStateFlow(false)
    val fullscreenMode = _fullscreenMode.asStateFlow()

    /**
     * The currently displayed media, [MediaViewerAdapter] will take care of updating the value.
     */
    private val _displayedMedia = MutableStateFlow<Media?>(null)
    val displayedMedia = _displayedMedia.asStateFlow()

    override fun onCleared() {
        exoPlayer.release()

        super.onCleared()
    }

    fun setStaticContents(staticContents: List<IntentsViewModel.ParsedIntent.Content>?) {
        this.staticContents.value = staticContents
    }

    /**
     * Set whether we're being displayed on top of the lockscreen.
     */
    fun setSecure(secure: Boolean) {
        _secure.value = secure
    }

    /**
     * Set the updated media position.
     */
    fun setMediaPosition(mediaPosition: Int) {
        _mediaPosition.value = mediaPosition
    }

    /**
     * Set the sheets height.
     */
    fun setSheetsHeight(top: Int, bottom: Int) {
        _sheetsHeight.value = top to bottom
    }

    /**
     * Toggle fullscreen mode.
     */
    fun toggleFullscreenMode() {
        _fullscreenMode.value = _fullscreenMode.value.not()
    }

    /**
     * Set the current displayed media.
     */
    fun setDisplayedMedia(displayedMedia: Media?) {
        _displayedMedia.value = displayedMedia
    }

    /**
     * @see ExoPlayer.play
     */
    fun play() {
        exoPlayer.play()
    }

    /**
     * @see ExoPlayer.pause
     */
    fun pause() {
        exoPlayer.pause()
    }

    companion object {
        private const val MEDIA_POSITION_KEY = "media_position"
    }
}
