package org.lineageos.glimpse.utils

import org.lineageos.glimpse.models.MediaType

object MimeUtils {
    fun mimeTypeToMediaType(mimeType: String) = when {
        mimeType.startsWith("image/") -> MediaType.MEDIA

        mimeType.startsWith("video/") -> MediaType.MEDIA

        else -> when (mimeType) {
            "application/dash+xml",
            "application/vnd.apple.mpegurl",
            "application/vnd.ms-sstr+xml",
            "application/x-mpegurl",
            "audio/mpegurl",
            "audio/x-mpegurl",
            "vnd.android.cursor.item/image",
            "vnd.android.cursor.item/video" -> MediaType.MEDIA

            else -> null
        }
    }
}
