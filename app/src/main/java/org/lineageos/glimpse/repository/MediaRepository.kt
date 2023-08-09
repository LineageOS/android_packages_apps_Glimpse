/*
 * SPDX-FileCopyrightText: 2023 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.glimpse.repository

import android.content.Context
import org.lineageos.glimpse.flow.AlbumsFlow
import org.lineageos.glimpse.flow.MediaFlow

@Suppress("Unused")
class MediaRepository(private val context: Context) {
    fun media(bucketId: Int? = null) = MediaFlow(context, bucketId).flowData()
    fun mediaCursor(bucketId: Int? = null) = MediaFlow(context, bucketId).flowCursor()
    fun albums() = AlbumsFlow(context).flowData()
    fun albumsCursor() = AlbumsFlow(context).flowCursor()
}
