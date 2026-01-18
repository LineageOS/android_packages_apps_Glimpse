/*
 * SPDX-FileCopyrightText: The LineageOS Project
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
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.preference.PreferenceManager
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
import org.lineageos.glimpse.ext.doubleTapToSeekEnabled
import org.lineageos.glimpse.ext.doubleTapToSeekSeconds
import org.lineageos.glimpse.ext.edgeTapNavigationEnabled
import org.lineageos.glimpse.ext.getVideoPlaybackPosition
import org.lineageos.glimpse.ext.hideNativeSeekButtons
import org.lineageos.glimpse.ext.isPlayingFlow
import org.lineageos.glimpse.ext.rememberVideoPlaybackPositionEnabled
import org.lineageos.glimpse.ext.removeVideoPlaybackPosition
import org.lineageos.glimpse.ext.setVideoPlaybackPosition
import org.lineageos.glimpse.models.AlbumType
import org.lineageos.glimpse.models.MediaType
import org.lineageos.glimpse.models.MotionPhoto
import org.lineageos.glimpse.models.RequestStatus
import org.lineageos.glimpse.models.RequestStatus.Companion.map
import org.lineageos.glimpse.utils.MotionPhotoExtractor
import org.lineageos.glimpse.utils.media3.ByteBufferDataSource

class LocalPlayerViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle,
) : GlimpseViewModel(application) {
    // Shared preferences
    private val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(
        applicationContext
    )

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
     * Parsed intent.
     */
    private val parsedIntent = MutableStateFlow<IntentsViewModel.ParsedIntent?>(null)

    /**
     * The initial media.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val initialMedia = parsedIntent
        .mapLatest {
            when (it) {
                is IntentsViewModel.ParsedIntent.ReviewIntent -> it.initialMedia
                else -> null
            }
        }
        .flowOn(Dispatchers.IO)
        .stateIn(
            viewModelScope,
            started = SharingStarted.WhileSubscribed(),
            initialValue = null,
        )

    @OptIn(ExperimentalCoroutinesApi::class)
    val album = parsedIntent
        .mapLatest {
            when (it) {
                is IntentsViewModel.ParsedIntent.ReviewIntent -> it.albumRequest
                else -> null
            }
        }
        .filterNotNull()
        .flatMapLatest { albumRequest ->
            when (albumRequest.albumType) {
                AlbumType.REELS -> mediaRepository.reels(albumRequest.mediaType)

                AlbumType.FAVORITES -> mediaRepository.favorites()

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

    /**
     * Collect secure media via Uri to update its list after deletion/restore.
     * Needed because we can't use the album to observe for changes.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val secureMedias = parsedIntent
        .flatMapLatest {
            when (it) {
                is IntentsViewModel.ParsedIntent.SecureReviewIntent -> {
                    mediaRepository.medias(it.medias.map { media ->
                        media.uri
                    })
                }

                else -> flowOf(RequestStatus.Loading())
            }
        }
        .flowOn(Dispatchers.IO)
        .stateIn(
            viewModelScope,
            started = SharingStarted.WhileSubscribed(),
            initialValue = RequestStatus.Loading(),
        )

    @OptIn(ExperimentalCoroutinesApi::class)
    val medias = parsedIntent
        .flatMapLatest {
            when (it) {
                is IntentsViewModel.ParsedIntent.ViewIntent -> {
                    flowOf(RequestStatus.Success(it.medias))
                }

                is IntentsViewModel.ParsedIntent.ReviewIntent -> album

                is IntentsViewModel.ParsedIntent.SecureReviewIntent -> secureMedias

                else -> flowOf(RequestStatus.Loading())
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
    @OptIn(ExperimentalCoroutinesApi::class)
    val secure = parsedIntent
        .mapLatest {
            when (it) {
                is IntentsViewModel.ParsedIntent.SecureReviewIntent -> true
                else -> false
            }
        }
        .flowOn(Dispatchers.IO)
        .stateIn(
            viewModelScope,
            started = SharingStarted.WhileSubscribed(),
            initialValue = false,
        )

    /**
     * Whether we should not be editing displayed media at all.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val readOnly = parsedIntent
        .mapLatest {
            when (it) {
                is IntentsViewModel.ParsedIntent.ReviewIntent,
                is IntentsViewModel.ParsedIntent.SecureReviewIntent -> false

                else -> true
            }
        }
        .flowOn(Dispatchers.IO)
        .stateIn(
            viewModelScope,
            started = SharingStarted.WhileSubscribed(),
            initialValue = true,
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

    val mediasWithInitialPosition = combine(
        medias,
        mediaPosition,
        initialMedia,
    ) { medias, mediaPosition, initialMedia ->
        medias.map { data ->
            val newMediaPosition = when (mediaPosition) {
                null -> initialMedia?.let {
                    data.indexOfFirst { media ->
                        media.uri == it.uri
                    }.takeIf { idx -> idx != -1 }
                } ?: 0

                else -> null
            }

            data to newMediaPosition
        }
    }

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

    /**
     * Motion photo contained in the displayed media.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val motionPhoto = displayedMedia
        .mapLatest { media ->
            media?.takeIf { it.mediaType == MediaType.IMAGE }?.let {
                MotionPhotoExtractor.extractMotionPhoto(applicationContext, it.uri)
            }
        }
        .flowOn(Dispatchers.IO)
        .stateIn(
            viewModelScope,
            started = SharingStarted.WhileSubscribed(),
            initialValue = null,
        )

    /**
     * Whether we're currently playing a motion photo video.
     */
    private val motionPhotoEnabled = MutableStateFlow(false)

    /**
     * Displayed media to motion photo if present and requested.
     */
    val displayedMediaToMotionPhoto = combine(
        displayedMedia,
        motionPhoto,
        motionPhotoEnabled
    ) { displayedMedia, motionPhoto, motionPhotoEnabled ->
        displayedMedia to motionPhoto.takeIf { motionPhotoEnabled }
    }
        .flowOn(Dispatchers.IO)
        .stateIn(
            viewModelScope,
            started = SharingStarted.WhileSubscribed(),
            initialValue = null to null,
        )

    val doubleTapToSeekEnabled: Boolean
        get() = sharedPreferences.doubleTapToSeekEnabled

    val doubleTapToSeekSeconds: Int
        get() = sharedPreferences.doubleTapToSeekSeconds

    val hideNativeSeekButtons: Boolean
        get() = sharedPreferences.hideNativeSeekButtons

    val edgeTapNavigationEnabled: Boolean
        get() = sharedPreferences.edgeTapNavigationEnabled

    override fun onCleared() {
        exoPlayer.release()

        super.onCleared()
    }

    fun setParsedIntent(parsedIntent: IntentsViewModel.ParsedIntent?) {
        this.parsedIntent.value = parsedIntent
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
        val startPositionMs = when (sharedPreferences.rememberVideoPlaybackPositionEnabled) {
            true -> sharedPreferences.getVideoPlaybackPosition(uri)
            false -> 0L
        }

        exoPlayer.apply {
            setMediaItem(MediaItem.fromUri(uri))
            prepare()
            if (startPositionMs > 0L) {
                seekTo(startPositionMs)
            }
            playWhenReady = true
        }
    }

    fun saveCurrentVideoPosition(currentVideoUri: Uri?) {
        val currentVideoUri = currentVideoUri ?: return

        if (!sharedPreferences.rememberVideoPlaybackPositionEnabled) {
            return
        }

        val currentPosition = exoPlayer.currentPosition
        val duration = exoPlayer.duration
        val nearEnd = duration > 0L &&
                currentPosition >= duration - FINISHED_VIDEO_POSITION_TOLERANCE_MS

        when {
            currentPosition <= 0L -> sharedPreferences.removeVideoPlaybackPosition(currentVideoUri)
            nearEnd -> sharedPreferences.removeVideoPlaybackPosition(currentVideoUri)
            else -> sharedPreferences.setVideoPlaybackPosition(
                currentVideoUri, currentPosition
            )
        }
    }

    fun toggleMotionPhotoEnabled(motionPhotoEnabled: Boolean = !this.motionPhotoEnabled.value) {
        this.motionPhotoEnabled.value = motionPhotoEnabled
    }

    @androidx.annotation.OptIn(UnstableApi::class)
    fun playMotionPhoto(motionPhoto: MotionPhoto) {
        val dataSourceFactory = ByteBufferDataSource.Factory(motionPhoto.videoBuffer)
        val mediaSource = ProgressiveMediaSource.Factory(dataSourceFactory)
            .createMediaSource(MediaItem.fromUri("motion-photo://video"))

        exoPlayer.apply {
            setMediaSource(mediaSource)
            prepare()
            motionPhoto.metadata.presentationTimestampUs?.let {
                seekTo(it / 1000)
            }
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

    /**
     * @see ExoPlayer.stop
     */
    fun stop() {
        exoPlayer.stop()
    }

    companion object {
        private const val MEDIA_POSITION_KEY = "media_position"

        private const val FINISHED_VIDEO_POSITION_TOLERANCE_MS = 2_000L
    }
}
