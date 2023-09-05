/*
 * SPDX-FileCopyrightText: 2023 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.glimpse.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.shareIn
import org.lineageos.glimpse.GlimpseApplication
import org.lineageos.glimpse.repository.MediaRepository

open class AlbumsViewModel(
    private val mediaRepository: MediaRepository,
    private val externalScope: CoroutineScope,
) : ViewModel() {
    val albums = mediaRepository.albums().shareIn(
        externalScope,
        replay = 1,
        started = SharingStarted.WhileSubscribed()
    )

    companion object {
        fun factory(externalScope: CoroutineScope) = viewModelFactory {
            initializer {
                AlbumsViewModel(
                    mediaRepository = (this[APPLICATION_KEY] as GlimpseApplication).mediaRepository,
                    externalScope = externalScope,
                )
            }
        }
    }
}
