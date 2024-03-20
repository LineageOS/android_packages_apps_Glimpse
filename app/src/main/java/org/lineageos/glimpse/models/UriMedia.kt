/*
 * SPDX-FileCopyrightText: 2023 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.glimpse.models

import android.net.Uri
import android.os.Parcel
import android.os.Parcelable
import android.os.Parcelable.Creator
import lineagex.core.ext.readParcelable
import lineagex.core.ext.readSerializable
import kotlin.reflect.safeCast

/**
 * A generic [Media] with unspecified provider.
 * Could be local or remote.
 */
data class UriMedia(
    override val uri: Uri,
    override val mediaType: MediaType,
    override val mimeType: String,
) : Media, Comparable<UriMedia>, Parcelable {
    constructor(parcel: Parcel) : this(
        parcel.readParcelable(Uri::class)!!,
        parcel.readSerializable(MediaType::class)!!,
        parcel.readString()!!
    )

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeParcelable(uri, flags)
        dest.writeSerializable(mediaType)
        dest.writeString(mimeType)
    }

    override fun describeContents() = 0

    override fun hashCode(): Int {
        var result = uri.hashCode()
        result = 31 * result + mediaType.hashCode()
        result = 31 * result + mimeType.hashCode()
        return result
    }

    override fun equals(other: Any?): Boolean {
        val obj = UriMedia::class.safeCast(other) ?: return false
        return compareTo(obj) == 0
    }

    override fun compareTo(other: UriMedia) = compareValuesBy(
        this, other,
        { it.uri },
        { it.mediaType },
        { it.mimeType },
    )

    companion object CREATOR : Creator<UriMedia> {
        override fun createFromParcel(parcel: Parcel) = UriMedia(parcel)

        override fun newArray(size: Int) = arrayOfNulls<UriMedia>(size)
    }
}
