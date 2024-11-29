/*
 * SPDX-FileCopyrightText: 2023-2024 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.glimpse.models

import android.net.Uri
import android.os.Parcelable
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize

/**
 * An album.
 */
@Parcelize
data class Album(
    override val uri: Uri,
    val name: String?,
    val thumbnail: Thumbnail?,
) : MediaItem<Album>, Parcelable {
    @IgnoredOnParcel
    override val mediaType = MediaType.ALBUM

    override fun areContentsTheSame(other: Album) = compareValuesBy(
        this, other,
        Album::name,
        Album::thumbnail,
    ) == 0
}
