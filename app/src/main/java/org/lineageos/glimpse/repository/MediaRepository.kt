/*
 * SPDX-FileCopyrightText: 2023 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.glimpse.repository

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn
import org.lineageos.glimpse.flow.AlbumsFlow
import org.lineageos.glimpse.flow.MediaFlow

@Suppress("Unused")
object MediaRepository {
    fun media(context: Context, bucketId: Int) =
        MediaFlow(context, bucketId).flowData().flowOn(Dispatchers.IO)

    fun mediaCursor(context: Context, bucketId: Int) =
        MediaFlow(context, bucketId).flowCursor().flowOn(Dispatchers.IO)

    fun albums(context: Context) = AlbumsFlow(context).flowData().flowOn(Dispatchers.IO)
    fun albumsCursor(context: Context) = AlbumsFlow(context).flowCursor().flowOn(Dispatchers.IO)
}
