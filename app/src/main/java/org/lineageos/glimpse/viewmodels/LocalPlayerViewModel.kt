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
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import org.lineageos.glimpse.ext.applicationContext
import org.lineageos.glimpse.ext.isPlayingFlow
import org.lineageos.glimpse.models.AlbumType
import org.lineageos.glimpse.models.Media
import org.lineageos.glimpse.models.RequestStatus
import org.lineageos.glimpse.models.RequestStatus.Companion.map

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
    private val staticMedias = MutableStateFlow<List<Media>?>(null)

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

                else -> albumRequest.albumUri?.let { albumUri ->
                    mediaRepository.album(albumUri).mapLatest { album ->
                        album.map { it.second }
                    }
                } ?: flowOf(RequestStatus.Loading())
            }
        }
        .flowOn(Dispatchers.IO)
        .stateIn(
            viewModelScope,
            started = SharingStarted.WhileSubscribed(),
            initialValue = RequestStatus.Loading(),
        )

    @OptIn(ExperimentalCoroutinesApi::class)
    val medias = combine(albumRequest, staticMedias) { albumRequest, staticMedias ->
        albumRequest?.let { album } ?: flowOf(RequestStatus.Success(staticMedias.orEmpty()))
    }
        .flatMapLatest { it }
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
     * The currently displayed media.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val displayedMedia = combine(medias, mediaPosition.filterNotNull()) { medias, mediaPosition ->
        medias.map { it.getOrNull(mediaPosition) }
    }
        .flatMapLatest {
            flow {
                when (it) {
                    is RequestStatus.Loading -> {
                        // Do nothing
                    }

                    is RequestStatus.Success -> emit(it.data)

                    is RequestStatus.Error -> emit(null)
                }
            }
        }
        .flowOn(Dispatchers.IO)
        .stateIn(
            viewModelScope,
            started = SharingStarted.WhileSubscribed(),
            initialValue = null,
        )

    override fun onCleared() {
        exoPlayer.release()

        super.onCleared()
    }

    fun setAlbumRequest(albumRequest: AlbumViewModel.AlbumRequest?) {
        this.albumRequest.value = albumRequest
    }

    fun setStaticMedias(staticMedias: List<Media>?) {
        this.staticMedias.value = staticMedias
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

    fun setCurrentVideoUri(uri: Uri) {
        exoPlayer.apply {
            setMediaItem(MediaItem.fromUri(uri))
            prepare()
            playWhenReady = true
        }
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

    fun stop() {
        exoPlayer.stop()
    }

    companion object {
        private const val MEDIA_POSITION_KEY = "media_position"
    }
}
