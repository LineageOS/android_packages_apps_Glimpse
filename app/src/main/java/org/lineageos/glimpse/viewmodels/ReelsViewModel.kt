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
import org.lineageos.glimpse.models.Media
import org.lineageos.glimpse.models.RequestStatus
import org.lineageos.glimpse.models.UniqueItem
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Date
import kotlin.reflect.safeCast

class ReelsViewModel(application: Application) : GlimpseViewModel(application) {
    val reels = mediaRepository.reels()
        .flowOn(Dispatchers.IO)
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(),
            RequestStatus.Loading()
        )

    sealed class AlbumContent(
        val viewType: Int
    ) : UniqueItem<AlbumContent> {
        data class DateHeader(val date: Date) : AlbumContent(1) {
            override fun areItemsTheSame(other: AlbumContent) =
                DateHeader::class.safeCast(other)?.let {
                    date == it.date
                } ?: false

            override fun areContentsTheSame(other: AlbumContent) = true
        }

        class MediaItem(val media: Media) : AlbumContent(0) {
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
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val reelsWithHeader = reels
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
