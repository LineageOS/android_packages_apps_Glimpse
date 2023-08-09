/*
 * SPDX-FileCopyrightText: 2023 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.glimpse.ext

import android.content.ContentResolver
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.activity.result.IntentSenderRequest
import androidx.annotation.RequiresApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map

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

fun ContentResolver.uriFlow(uri: Uri) = callbackFlow {
    val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            trySend(Unit)
        }
    }
    registerContentObserver(uri, true, observer)

    trySend(Unit)

    awaitClose {
        unregisterContentObserver(observer)
    }
}

fun ContentResolver.queryFlow(
    uri: Uri,
    projection: Array<String>? = null,
    queryArgs: Bundle? = Bundle(),
    cancellationSignal: CancellationSignal? = null
) = uriFlow(uri).map {
    query(
        uri, projection, queryArgs, cancellationSignal
    )
}
