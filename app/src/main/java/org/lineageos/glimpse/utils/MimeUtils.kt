/*
 * SPDX-FileCopyrightText: 2024 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.glimpse.utils

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

    fun mimeTypeToMediaType(mimeType: String) = when (mimeType) {
        "vnd.android.cursor.dir/image" -> MediaType.ALBUM
        "vnd.android.cursor.dir/video" -> MediaType.ALBUM

        "vnd.android.cursor.item/image" -> MediaType.IMAGE
        "vnd.android.cursor.item/video" -> MediaType.VIDEO

        else -> when {
            mimeType.startsWith("image/") -> MediaType.IMAGE
            mimeType.startsWith("video/") -> MediaType.VIDEO
            mimeType in dashMimeTypes -> MediaType.VIDEO
            mimeType in hlsMimeTypes -> MediaType.VIDEO
            mimeType in smoothStreamingMimeTypes -> MediaType.VIDEO
            else -> null
        }
    }
}
