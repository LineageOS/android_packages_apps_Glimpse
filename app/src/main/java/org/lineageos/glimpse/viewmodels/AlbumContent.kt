/*
 * SPDX-FileCopyrightText: 2024 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.glimpse.viewmodels

import org.lineageos.glimpse.models.Media
import org.lineageos.glimpse.models.UniqueItem
import org.lineageos.glimpse.ui.recyclerview.ThumbnailAdapter
import java.util.Date
import kotlin.reflect.safeCast

sealed class AlbumContent(val viewType: Int) : UniqueItem<AlbumContent> {
    data class DateHeader(val date: Date) :
        AlbumContent(ThumbnailAdapter.ViewType.DATE_HEADER.ordinal) {
        override fun areItemsTheSame(other: AlbumContent) =
            DateHeader::class.safeCast(other)?.let {
                date == it.date
            } ?: false

        override fun areContentsTheSame(other: AlbumContent) = true
    }

    class MediaItem(val media: Media) : AlbumContent(ThumbnailAdapter.ViewType.THUMBNAIL.ordinal) {
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
