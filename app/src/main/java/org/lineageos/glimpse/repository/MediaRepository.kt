/*
 * SPDX-FileCopyrightText: 2023-2024 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.glimpse.repository

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import kotlinx.coroutines.CoroutineScope
import org.lineageos.glimpse.datasources.LocalDataSource
import org.lineageos.glimpse.datasources.MediaDataSource
import org.lineageos.glimpse.models.MediaType

/**
 * Media repository. This class coordinates all the providers and their data source.
 * All methods that involves a URI as a parameter will be redirected to the
 * proper data source that can handle the media item.
 */
class MediaRepository(
    private val context: Context,
    scope: CoroutineScope,
) {
    /**
     * Content resolver.
     */
    private val contentResolver = context.contentResolver

    /**
     * Local data source singleton.
     */
    private val localDataSource = LocalDataSource(
        contentResolver,
        MediaStore.VOLUME_EXTERNAL,
    ) as MediaDataSource

    /**
     * @see MediaDataSource.isMediaItemCompatible
     */
    fun isMediaItemCompatible(mediaItemUri: Uri) =
        localDataSource.isMediaItemCompatible(mediaItemUri)

    /**
     * @see MediaDataSource.mediaTypeOf
     */
    suspend fun mediaTypeOf(mediaItemUri: Uri) = localDataSource.mediaTypeOf(mediaItemUri)

    /**
     * @see MediaDataSource.reels
     */
    fun reels(
        mediaType: MediaType? = null,
        mimeType: String? = null,
    ) = localDataSource.reels(mediaType, mimeType)

    /**
     * @see MediaDataSource.favorites
     */
    fun favorites() = localDataSource.favorites()

    /**
     * @see MediaDataSource.trash
     */
    fun trash() = localDataSource.trash()

    /**
     * @see MediaDataSource.albums
     */
    fun albums(
        mediaType: MediaType? = null,
        mimeType: String? = null,
    ) = localDataSource.albums(mediaType, mimeType)

    /**
     * @see MediaDataSource.album
     */
    fun album(albumUri: Uri) = localDataSource.album(albumUri)

    /**
     * @see MediaDataSource.media
     */
    fun media(mediaUri: Uri) = localDataSource.media(mediaUri)
}
