/*
 * SPDX-FileCopyrightText: 2024 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.glimpse.models

import android.graphics.Bitmap
import android.net.Uri
import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * A thumbnail for a media item. It can be a URI or a bitmap. Both can be defined, in that case the
 * URI should take precedence. At least one of the two should be non-null.
 *
 * @param uri The URI of the thumbnail
 * @param bitmap The bitmap of the thumbnail
 */
@Parcelize
data class Thumbnail(
    val uri: Uri? = null,
    val bitmap: Bitmap? = null,
) : Comparable<Thumbnail>, Parcelable {
    init {
        require(uri != null || bitmap != null) {
            "At least one of the fields should be non-null"
        }
    }

    override fun compareTo(other: Thumbnail) = compareValuesBy(
        this, other,
        Thumbnail::uri,
    ).let {
        if (it == 0) {
            return@let when (this.bitmap?.sameAs(other.bitmap)) {
                true -> 0
                false -> 1
                null -> when (other.bitmap == null) {
                    true -> 0
                    false -> -1
                }
            }
        } else {
            it
        }
    }
}
