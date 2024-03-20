/*
 * SPDX-FileCopyrightText: 2023 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.glimpse.ext

import android.content.ContentResolver
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.result.IntentSenderRequest

fun ContentResolver.createDeleteRequest(vararg uris: Uri) = IntentSenderRequest.Builder(
    MediaStore.createDeleteRequest(this, uris.toCollection(ArrayList()))
).build()

fun ContentResolver.createFavoriteRequest(value: Boolean, vararg uris: Uri) =
    IntentSenderRequest.Builder(
        MediaStore.createFavoriteRequest(this, uris.toCollection(ArrayList()), value)
    ).build()

fun ContentResolver.createTrashRequest(value: Boolean, vararg uris: Uri) =
    IntentSenderRequest.Builder(
        MediaStore.createTrashRequest(this, uris.toCollection(ArrayList()), value)
    ).build()

fun ContentResolver.createWriteRequest(vararg uris: Uri) =
    IntentSenderRequest.Builder(
        MediaStore.createWriteRequest(this, uris.toCollection(ArrayList()))
    ).build()
