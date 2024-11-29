/*
 * SPDX-FileCopyrightText: 2023-2024 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.glimpse.viewmodels

import android.app.Application
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import org.lineageos.glimpse.models.Media
import org.lineageos.glimpse.models.RequestStatus
import org.lineageos.glimpse.ui.recyclerview.MediaViewerAdapter

class LocalPlayerViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle,
) : GlimpseViewModel(application) {
    private val albumUri = MutableStateFlow<Uri?>(null)

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

    @OptIn(ExperimentalCoroutinesApi::class)
    val media = albumUri
        .filterNotNull()
        .flatMapLatest { albumUri -> mediaRepository.album(albumUri) }
        .flowOn(Dispatchers.IO)
        .stateIn(
            viewModelScope,
            started = SharingStarted.WhileSubscribed(),
            initialValue = RequestStatus.Loading(),
        )

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
        _fullscreenMode.value = !_fullscreenMode.value
    }

    /**
     * Set the current displayed media.
     */
    fun setDisplayedMedia(displayedMedia: Media?) {
        _displayedMedia.value = displayedMedia
    }

    companion object {
        private const val MEDIA_POSITION_KEY = "media_position"
    }
}
