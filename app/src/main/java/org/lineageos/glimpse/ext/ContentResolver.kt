/*
 * SPDX-FileCopyrightText: 2023 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.glimpse.ext

import android.content.ContentResolver
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.result.IntentSenderRequest
import androidx.annotation.RequiresApi

@RequiresApi(Build.VERSION_CODES.R)
fun ContentResolver.createDeleteRequest(vararg uris: Uri) = IntentSenderRequest.Builder(
    MediaStore.createDeleteRequest(this, uris.toCollection(ArrayList()))
).build()

@RequiresApi(Build.VERSION_CODES.R)
fun ContentResolver.createFavoriteRequest(value: Boolean, vararg uris: Uri) =
    IntentSenderRequest.Builder(
        MediaStore.createFavoriteRequest(this, uris.toCollection(ArrayList()), value)
    ).build()

@RequiresApi(Build.VERSION_CODES.R)
fun ContentResolver.createTrashRequest(value: Boolean, vararg uris: Uri) =
    IntentSenderRequest.Builder(
        MediaStore.createTrashRequest(this, uris.toCollection(ArrayList()), value)
    ).build()
