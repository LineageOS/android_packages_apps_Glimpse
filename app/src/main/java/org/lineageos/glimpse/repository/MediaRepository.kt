/*
 * SPDX-FileCopyrightText: 2023 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.glimpse.repository

import android.content.Context
import org.lineageos.glimpse.flow.AlbumFlow
import org.lineageos.glimpse.flow.AlbumsFlow
import org.lineageos.glimpse.flow.LocationsFlow
import org.lineageos.glimpse.flow.MediaFlow

@Suppress("Unused")
object MediaRepository {
    fun media(
        context: Context,
        bucketId: Int,
        mimeType: String? = null,
    ) = MediaFlow(context, bucketId, mimeType).flowData()

    fun mediaCursor(
        context: Context,
        bucketId: Int,
        mimeType: String? = null,
    ) = MediaFlow(context, bucketId, mimeType).flowCursor()

    fun album(
        context: Context,
        bucketId: Int,
        mimeType: String? = null,
    ) = AlbumFlow(context, bucketId, mimeType).flowData()

    fun albumCursor(
        context: Context,
        bucketId: Int,
        mimeType: String? = null,
    ) = AlbumFlow(context, bucketId, mimeType).flowCursor()

    fun albums(
        context: Context,
        mimeType: String? = null,
    ) = AlbumsFlow(context, mimeType).flowData()

    fun albumsCursor(
        context: Context,
        mimeType: String? = null,
    ) = AlbumsFlow(context, mimeType).flowCursor()

    fun locations(
        context: Context,
    ) = LocationsFlow(context).get()
}
