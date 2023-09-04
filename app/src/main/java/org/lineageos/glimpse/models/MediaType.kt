/*
 * SPDX-FileCopyrightText: 2023 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.glimpse.models

import android.net.Uri
import android.provider.MediaStore

enum class MediaType(
    val externalContentUri: Uri,
) {
    IMAGE(MediaStore.Images.Media.EXTERNAL_CONTENT_URI),
    VIDEO(MediaStore.Video.Media.EXTERNAL_CONTENT_URI);

    companion object {
        fun fromMediaStoreValue(value: Int) = when (value) {
            MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE -> IMAGE
            MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO -> VIDEO
            else -> throw Exception("Unknown value $value")
        }

        fun fromMimeType(mimeType: String) = when {
            mimeType.startsWith("image/") -> IMAGE
            mimeType.startsWith("video/") -> VIDEO
            mimeType == "application/vnd.apple.mpegurl" -> VIDEO // HLS
            else -> null
        }
    }
}
