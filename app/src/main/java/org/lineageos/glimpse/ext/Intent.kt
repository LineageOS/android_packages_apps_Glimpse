/*
 * SPDX-FileCopyrightText: 2023 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.glimpse.ext

import android.content.Intent
import org.lineageos.glimpse.models.Media
import org.lineageos.glimpse.models.MediaType.IMAGE
import org.lineageos.glimpse.models.MediaType.VIDEO
import org.lineageos.glimpse.models.UriMedia

fun buildShareIntent(vararg medias: Media) = Intent().apply {
    assert(medias.isNotEmpty()) { "No media" }

    if (medias.size == 1) {
        action = Intent.ACTION_SEND
        medias[0].let {
            putExtra(Intent.EXTRA_STREAM, it.uri)
            type = it.mimeType
        }
    } else {
        action = Intent.ACTION_SEND_MULTIPLE
        putParcelableArrayListExtra(
            Intent.EXTRA_STREAM,
            medias.map { it.uri }.toCollection(ArrayList())
        )
        type = when {
            medias.all { it.mediaType == IMAGE } -> "image/*"
            medias.all { it.mediaType == VIDEO } -> "video/*"
            else -> {
                putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("image/*", "video/*"))
                "*/*"
            }
        }
    }
    flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
}

fun buildShareIntent(vararg uriMedias: UriMedia) = Intent().apply {
    assert(uriMedias.isNotEmpty()) { "No media uri" }

    if (uriMedias.size == 1) {
        action = Intent.ACTION_SEND
        uriMedias[0].let {
            putExtra(Intent.EXTRA_STREAM, it.uri)
            type = it.mimeType
        }
    } else {
        action = Intent.ACTION_SEND_MULTIPLE
        putParcelableArrayListExtra(
            Intent.EXTRA_STREAM,
            uriMedias.map { it.uri }.toCollection(ArrayList())
        )
        type = when {
            uriMedias.all { it.mediaType == IMAGE } -> "image/*"
            uriMedias.all { it.mediaType == VIDEO } -> "video/*"
            else -> {
                putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("image/*", "video/*"))
                "*/*"
            }
        }
    }
}

fun buildEditIntent(media: Media) = Intent().apply {
    action = Intent.ACTION_EDIT
    setDataAndType(media.uri, media.mimeType)
    flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
}
