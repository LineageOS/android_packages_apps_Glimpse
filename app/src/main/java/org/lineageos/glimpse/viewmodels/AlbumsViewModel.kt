/*
 * SPDX-FileCopyrightText: 2023 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.glimpse.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import org.lineageos.glimpse.GlimpseApplication
import org.lineageos.glimpse.repository.MediaRepository

open class AlbumsViewModel(
    private val mediaRepository: MediaRepository
) : ViewModel() {
    val albums = mediaRepository.albums()

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                AlbumsViewModel(
                    mediaRepository = (this[APPLICATION_KEY] as GlimpseApplication).mediaRepository,
                )
            }
        }
    }
}
