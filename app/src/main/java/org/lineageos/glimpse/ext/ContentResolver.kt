/*
 * SPDX-FileCopyrightText: 2023 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.glimpse.ext

import android.content.ContentResolver
import android.database.ContentObserver
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.activity.result.IntentSenderRequest
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive

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

fun ContentResolver.uriFlow(uri: Uri) = callbackFlow {
    val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            if (isActive) {
                trySend(Unit)
            }
        }
    }
    registerContentObserver(uri, true, observer)

    trySend(Unit)

    awaitClose {
        unregisterContentObserver(observer)
    }
}.conflate()

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
