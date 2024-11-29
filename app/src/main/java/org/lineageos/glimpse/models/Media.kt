/*
 * SPDX-FileCopyrightText: 2023-2024 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.glimpse.models

import android.net.Uri
import android.os.Parcelable
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import java.util.Date

/**
 * A generic media representation.
 */
@Parcelize
data class Media(
    override val uri: Uri,
    val mimeType: String,
    val fileType: FileType,
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
    @IgnoredOnParcel
    override val mediaType = MediaType.MEDIA

    override fun areContentsTheSame(other: Media) = compareValuesBy(
        this, other,
        Media::fileType,
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
}
