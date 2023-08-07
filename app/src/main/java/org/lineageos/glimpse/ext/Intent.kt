/*
 * SPDX-FileCopyrightText: 2023 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.glimpse.ext

import android.content.Intent
import android.net.Uri
import org.lineageos.glimpse.models.Media

fun Intent.shareIntent(vararg uris: Uri) = apply {
    action = Intent.ACTION_SEND_MULTIPLE
    putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris.toCollection(ArrayList()))
    type = "*/*"
}

fun Intent.editIntent(media: Media) = apply {
    action = Intent.ACTION_EDIT
    setDataAndType(media.externalContentUri, media.mimeType)
    flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
}
