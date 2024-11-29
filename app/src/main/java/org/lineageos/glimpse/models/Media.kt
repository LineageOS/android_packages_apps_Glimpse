/*
 * SPDX-FileCopyrightText: 2023-2024 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.glimpse.models

import android.net.Uri
import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.util.Date

/**
 * A generic media representation.
 *
 * @param mimeType The MIME type
 * @param albumUri The URI of the album
 * @param albumName The name of the album
 * @param displayName The display name
 * @param isFavorite Whether the item is a favorite
 * @param isTrashed Whether the item has been trashed
 * @param dateAdded The date the item was added
 * @param dateModified The date the item was last modified
 * @param width The width of the item
 * @param height The height of the item
 * @param orientation The orientation of the item
 */
@Parcelize
data class Media(
    override val uri: Uri,
    override val mediaType: MediaType,
    val mimeType: String,
    val albumUri: Uri,
    val albumName: String?,
    val displayName: String,
    val isFavorite: Boolean,
    val isTrashed: Boolean,
    val dateAdded: Date,
    val dateModified: Date,
    val width: Int,
    val height: Int,
    val orientation: Int,
) : MediaItem<Media>, Parcelable {
    init {
        require(mediaType in allowedMediaTypes) {
            "Invalid media type $mediaType"
        }
    }

    override fun areContentsTheSame(other: Media) = compareValuesBy(
        this, other,
        Media::mediaType,
        Media::mimeType,
        Media::albumUri,
        Media::albumName,
        Media::displayName,
        Media::isFavorite,
        Media::isTrashed,
        Media::dateAdded,
        Media::dateModified,
        Media::width,
        Media::height,
        Media::orientation,
    ) == 0

    companion object {
        private val allowedMediaTypes = listOf(
            MediaType.IMAGE,
            MediaType.VIDEO,
        )
    }
}
