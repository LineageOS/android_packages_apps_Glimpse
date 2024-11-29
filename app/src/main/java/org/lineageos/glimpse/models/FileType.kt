/*
 * SPDX-FileCopyrightText: 2023 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.glimpse.models

import android.net.Uri
import android.provider.MediaStore

enum class FileType(
    val mediaStoreValue: Int,
) {
    IMAGE(
        MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE,
    ),
    VIDEO(
        MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO,
    );

    companion object {
        private val dashMimeTypes = listOf(
            "application/dash+xml",
        )

        private val hlsMimeTypes = listOf(
            "application/vnd.apple.mpegurl",
            "application/x-mpegurl",
            "audio/mpegurl",
            "audio/x-mpegurl",
        )

        private val smoothStreamingMimeTypes = listOf(
            "application/vnd.ms-sstr+xml",
        )

        fun fromMediaStoreValue(value: Int) = entries.first {
            value == it.mediaStoreValue
        }

        fun fromMimeType(mimeType: String) = when {
            mimeType.startsWith("image/") -> IMAGE
            mimeType.startsWith("video/") -> VIDEO
            mimeType in dashMimeTypes -> VIDEO
            mimeType in hlsMimeTypes -> VIDEO
            mimeType in smoothStreamingMimeTypes -> VIDEO
            else -> null
        }
    }
}
