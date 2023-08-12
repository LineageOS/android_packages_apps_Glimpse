/*
 * SPDX-FileCopyrightText: 2023 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.glimpse.models

import android.content.ContentUris
import android.os.Parcel
import android.os.Parcelable
import java.util.Date
import kotlin.reflect.safeCast

data class Media(
    val id: Long,
    val bucketId: Int,
    val displayName: String,
    val isFavorite: Boolean,
    val isTrashed: Boolean,
    val mediaType: MediaType,
    val mimeType: String,
    val dateAdded: Date,
    val dateModified: Date,
    val orientation: Int,
) : Comparable<Media>, Parcelable {
    val externalContentUri = ContentUris.withAppendedId(mediaType.externalContentUri, id)

    constructor(parcel: Parcel) : this(
        parcel.readLong(),
        parcel.readInt(),
        parcel.readString()!!,
        parcel.readInt() == 1,
        parcel.readInt() == 1,
        when (parcel.readInt()) {
            MediaType.IMAGE.ordinal -> MediaType.IMAGE
            MediaType.VIDEO.ordinal -> MediaType.VIDEO
            else -> throw Exception("Invalid media type")
        },
        parcel.readString()!!,
        Date(parcel.readLong()),
        Date(parcel.readLong()),
        parcel.readInt(),
    )

    override fun equals(other: Any?): Boolean {
        val obj = Media::class.safeCast(other) ?: return false
        return compareTo(obj) == 0
    }

    override fun hashCode() = id.hashCode()

    override fun compareTo(other: Media) = compareValuesBy(
        this, other,
        { it.id },
        { it.bucketId },
        { it.isFavorite },
        { it.isTrashed },
        { it.mediaType },
        { it.mimeType },
        { it.dateAdded },
        { it.dateModified },
        { it.orientation },
    )

    override fun describeContents() = 0

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeLong(id)
        dest.writeInt(bucketId)
        dest.writeString(displayName)
        dest.writeInt(if (isFavorite) 1 else 0)
        dest.writeInt(if (isTrashed) 1 else 0)
        dest.writeInt(mediaType.ordinal)
        dest.writeString(mimeType)
        dest.writeLong(dateAdded.time)
        dest.writeLong(dateModified.time)
        dest.writeInt(orientation)
    }

    companion object CREATOR : Parcelable.Creator<Media> {
        override fun createFromParcel(parcel: Parcel) = Media(parcel)

        override fun newArray(size: Int) = arrayOfNulls<Media>(size)

        fun fromMediaStore(
            id: Long,
            bucketId: Int,
            displayName: String,
            isFavorite: Int,
            isTrashed: Int,
            mediaType: Int,
            mimeType: String,
            dateAdded: Long,
            dateModified: Long,
            orientation: Int,
        ) = Media(
            id,
            bucketId,
            displayName,
            isFavorite == 1,
            isTrashed == 1,
            MediaType.fromMediaStoreValue(mediaType),
            mimeType,
            Date(dateAdded * 1000),
            Date(dateModified * 1000),
            orientation,
        )
    }
}
