/*
 * SPDX-FileCopyrightText: 2023 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.glimpse.models

import android.net.Uri
import android.os.Parcel
import android.os.Parcelable
import org.lineageos.glimpse.ext.*
import kotlin.reflect.safeCast

/**
 * A media album.
 */
data class Album(
    val id: Int,
    val name: String,
    val thumbnail: Uri,
    var size: Int = 0,
) : Parcelable {
    constructor(parcel: Parcel) : this(
        parcel.readInt(),
        parcel.readString()!!,
        parcel.readParcelable(Uri::class)!!,
        parcel.readInt()
    )

    override fun equals(other: Any?): Boolean {
        val obj = Album::class.safeCast(other) ?: return false
        return id == obj.id
    }

    override fun hashCode() = id.hashCode()

    override fun describeContents() = 0

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeInt(id)
        dest.writeString(name)
        dest.writeParcelable(thumbnail, 0)
        dest.writeInt(size)
    }

    companion object CREATOR : Parcelable.Creator<Album> {
        override fun createFromParcel(parcel: Parcel) = Album(parcel)

        override fun newArray(size: Int) = arrayOfNulls<Album>(size)
    }
}
