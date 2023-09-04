/*
 * SPDX-FileCopyrightText: 2023 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.glimpse.ext

import android.content.Intent
import org.lineageos.glimpse.models.Media
import org.lineageos.glimpse.models.MediaType.IMAGE
import org.lineageos.glimpse.models.MediaType.VIDEO

fun Intent.shareIntent(vararg medias: Media) = apply {
    assert(medias.isNotEmpty()) { "No media" }

    if (medias.size == 1) {
        action = Intent.ACTION_SEND
        medias[0].let {
            putExtra(Intent.EXTRA_STREAM, it.externalContentUri)
            type = it.mimeType
        }
    } else {
        action = Intent.ACTION_SEND_MULTIPLE
        putParcelableArrayListExtra(
            Intent.EXTRA_STREAM,
            medias.map { it.externalContentUri }.toCollection(ArrayList())
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

fun Intent.editIntent(media: Media) = apply {
    action = Intent.ACTION_EDIT
    setDataAndType(media.externalContentUri, media.mimeType)
    flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
}
