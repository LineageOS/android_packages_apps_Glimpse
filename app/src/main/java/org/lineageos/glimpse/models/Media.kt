/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.glimpse.models

import android.net.Uri
import android.os.Parcel
import android.os.Parcelable
import org.lineageos.glimpse.ext.readParcelable
import org.lineageos.glimpse.ext.readSerializable
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
 * @param sizeBytes The size of the item in bytes
 */
data class Media(
    override val uri: Uri,
    override val mediaType: MediaType,
    val mimeType: String,
    val albumUri: Uri,
    val albumName: String?,
    val displayName: String?,
    val isFavorite: Boolean,
    val isTrashed: Boolean,
    val dateAdded: Date,
    val dateModified: Date,
    val width: Int,
    val height: Int,
    val orientation: Int,
    val sizeBytes: Long,
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
        Media::sizeBytes,
    ) == 0

    override fun describeContents() = 0

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeParcelable(uri, 0)
        dest.writeSerializable(mediaType)
        dest.writeString(mimeType)
        dest.writeParcelable(albumUri, 0)
        dest.writeString(albumName)
        dest.writeString(displayName)
        dest.writeBoolean(isFavorite)
        dest.writeBoolean(isTrashed)
        dest.writeSerializable(dateAdded)
        dest.writeSerializable(dateModified)
        dest.writeInt(width)
        dest.writeInt(height)
        dest.writeInt(orientation)
        dest.writeLong(sizeBytes)
    }

    companion object CREATOR : Parcelable.Creator<Media> {
        private val allowedMediaTypes = listOf(
            MediaType.IMAGE,
            MediaType.VIDEO,
        )

        override fun createFromParcel(parcel: Parcel) = Media(
            uri = parcel.readParcelable(Uri::class)!!,
            mediaType = parcel.readSerializable(MediaType::class)!!,
            mimeType = parcel.readString()!!,
            albumUri = parcel.readParcelable(Uri::class)!!,
            albumName = parcel.readString(),
            displayName = parcel.readString(),
            isFavorite = parcel.readBoolean(),
            isTrashed = parcel.readBoolean(),
            dateAdded = parcel.readSerializable(Date::class)!!,
            dateModified = parcel.readSerializable(Date::class)!!,
            width = parcel.readInt(),
            height = parcel.readInt(),
            orientation = parcel.readInt(),
            sizeBytes = parcel.readLong(),
        )

        override fun newArray(size: Int) = arrayOfNulls<Media>(size)
    }
}
