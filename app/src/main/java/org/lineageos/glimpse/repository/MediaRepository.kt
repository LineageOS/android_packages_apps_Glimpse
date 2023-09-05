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
class MediaRepository(private val context: Context) {
    fun media(bucketId: Int) = MediaFlow(context, bucketId).flowData().flowOn(Dispatchers.IO)
    fun mediaCursor(bucketId: Int) = MediaFlow(context, bucketId).flowCursor().flowOn(Dispatchers.IO)
    fun albums() = AlbumsFlow(context).flowData().flowOn(Dispatchers.IO)
    fun albumsCursor() = AlbumsFlow(context).flowCursor().flowOn(Dispatchers.IO)
}
