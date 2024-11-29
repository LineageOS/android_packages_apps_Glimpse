/*
 * SPDX-FileCopyrightText: 2024 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.glimpse.viewmodels

import android.app.Application
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import org.lineageos.glimpse.models.RequestStatus
import java.time.ZoneId
import java.time.temporal.ChronoUnit

class ReelsViewModel(application: Application) : GlimpseViewModel(application) {
    val reels = mediaRepository.reels()
        .flowOn(Dispatchers.IO)
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(),
            RequestStatus.Loading()
        )

    @OptIn(ExperimentalCoroutinesApi::class)
    val reelsWithHeaders = reels
        .mapLatest {
            when (it) {
                is RequestStatus.Loading -> null

                is RequestStatus.Success -> {
                    val medias = it.data

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
            SharingStarted.WhileSubscribed(),
            listOf()
        )
}
