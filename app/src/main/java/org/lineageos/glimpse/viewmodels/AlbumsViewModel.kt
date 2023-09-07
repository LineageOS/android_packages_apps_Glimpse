/*
 * SPDX-FileCopyrightText: 2023 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.glimpse.viewmodels

import android.app.Application
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.shareIn
import org.lineageos.glimpse.repository.MediaRepository

open class AlbumsViewModel(
    application: Application,
) : GlimpseViewModel(application) {
    val albums = MediaRepository.albums(context).flowOn(Dispatchers.IO).shareIn(
        viewModelScope,
        replay = 1,
        started = SharingStarted.WhileSubscribed()
    )
}
