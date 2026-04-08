/*
 * SPDX-FileCopyrightText: 2023-2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.glimpse.models

import android.net.Uri
import android.os.Parcel
import android.os.Parcelable
import org.lineageos.glimpse.ext.readParcelable

/**
 * An album.
 *
 * @param name The name of the album
 * @param thumbnail A thumbnail, usually being the most recent media
 * @param mediaCount The number of elements in this album
 */
data class Album(
    override val uri: Uri,
    val name: String?,
    val thumbnail: Thumbnail?,
    val mediaCount: Int?,
) : MediaItem<Album>, Parcelable {
    override val mediaType = MediaType.ALBUM

    override fun areContentsTheSame(other: Album) = compareValuesBy(
        this, other,
        Album::name,
        Album::thumbnail,
        Album::mediaCount,
    ) == 0

    override fun describeContents() = 0

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeParcelable(uri, 0)
        dest.writeString(name)
        dest.writeParcelable(thumbnail, 0)
        dest.writeInt(mediaCount ?: 0)
    }

    companion object CREATOR : Parcelable.Creator<Album> {
        override fun createFromParcel(parcel: Parcel) = Album(
            uri = parcel.readParcelable(Uri::class)!!,
            name = parcel.readString(),
            thumbnail = parcel.readParcelable(Thumbnail::class),
            mediaCount = parcel.readInt(),
        )

        override fun newArray(size: Int) = arrayOfNulls<Album>(size)
    }
}
