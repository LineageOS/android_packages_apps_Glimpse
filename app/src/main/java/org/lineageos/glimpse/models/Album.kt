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
 *
 * @param name The name of the album
 * @param thumbnail A thumbnail, usually being the most recent media
 * @param mediaCount The number of elements in this album
 */
@Parcelize
data class Album(
    override val uri: Uri,
    val name: String?,
    val thumbnail: Thumbnail?,
    val mediaCount: Int?,
) : MediaItem<Album>, Parcelable {
    @IgnoredOnParcel
    override val mediaType = MediaType.ALBUM

    override fun areContentsTheSame(other: Album) = compareValuesBy(
        this, other,
        Album::name,
        Album::thumbnail,
        Album::mediaCount,
    ) == 0
}
