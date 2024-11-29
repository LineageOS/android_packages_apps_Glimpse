/*
 * SPDX-FileCopyrightText: 2024 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.glimpse.utils

import org.lineageos.glimpse.models.FileType
import org.lineageos.glimpse.models.MediaType

object MimeUtils {
    const val MIME_TYPE_IMAGE_ANY = "image/*"
    const val MIME_TYPE_VIDEO_ANY = "video/*"
    const val MIME_TYPE_ANY = "*/*"

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

    fun mimeTypeToFileType(mimeType: String) = when (mimeType) {
        "vnd.android.cursor.item/image" -> FileType.IMAGE
        "vnd.android.cursor.item/video" -> FileType.VIDEO

        else -> when {
            mimeType.startsWith("image/") -> FileType.IMAGE
            mimeType.startsWith("video/") -> FileType.VIDEO
            mimeType in dashMimeTypes -> FileType.VIDEO
            mimeType in hlsMimeTypes -> FileType.VIDEO
            mimeType in smoothStreamingMimeTypes -> FileType.VIDEO
            else -> null
        }
    }

    fun mimeTypeToMediaType(mimeType: String) = when (mimeType) {
        "vnd.android.cursor.dir/image" -> MediaType.ALBUM
        "vnd.android.cursor.dir/video" -> MediaType.ALBUM

        else -> when {
            mimeTypeToFileType(mimeType) != null -> MediaType.MEDIA
            else -> null
        }
    }
}
