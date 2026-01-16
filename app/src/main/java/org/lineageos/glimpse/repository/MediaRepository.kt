/*
 * SPDX-FileCopyrightText: 2023-2025 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.glimpse.repository

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.combine
import org.lineageos.glimpse.database.GlimpseDatabase
import org.lineageos.glimpse.datasources.LocalDataSource
import org.lineageos.glimpse.datasources.MediaDataSource
import org.lineageos.glimpse.datasources.VirtualDataSource
import org.lineageos.glimpse.models.MediaType
import org.lineageos.glimpse.models.RequestStatus

/**
 * Media repository. This class coordinates all the providers and their data source.
 * All methods that involves a URI as a parameter will be redirected to the
 * proper data source that can handle the media item.
 */
class MediaRepository(
    context: Context,
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
     * Virtual data source singleton.
     */
    private val virtualDataSource = VirtualDataSource(
        GlimpseDatabase.getInstance(context).getVirtualAlbumDao(),
        localDataSource,
    )

    private val dataSources = listOf(localDataSource, virtualDataSource)

    /**
     * @see MediaDataSource.mediaTypeOf
     */
    suspend fun mediaTypeOf(mediaItemUri: Uri) =
        dataSources.find { it.isMediaItemCompatible(mediaItemUri) }?.mediaTypeOf(mediaItemUri)!!

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
    @OptIn(ExperimentalCoroutinesApi::class)
    fun albums(
        mediaType: MediaType? = null,
        mimeType: String? = null,
    ) = combine(
        localDataSource.albums(mediaType, mimeType),
        virtualDataSource.albums(mediaType, mimeType)
    ) { local, virtual ->
        when {
            local is RequestStatus.Success && virtual is RequestStatus.Success -> {
                RequestStatus.Success(local.data + virtual.data)
            }

            local is RequestStatus.Error -> local
            virtual is RequestStatus.Error -> virtual
            else -> RequestStatus.Loading()
        }
    }

    /**
     * @see MediaDataSource.albums
     */
    fun virtualAlbums(
        mediaType: MediaType? = null,
        mimeType: String? = null,
    ) = virtualDataSource.albums(mediaType, mimeType)

    /**
     * @see MediaDataSource.album
     */
    fun album(albumUri: Uri) =
        dataSources.find { it.isMediaItemCompatible(albumUri) }?.album(albumUri)!!

    /**
     * @see MediaDataSource.media
     */
    fun media(mediaUri: Uri) =
        dataSources.find { it.isMediaItemCompatible(mediaUri) }?.media(mediaUri)!!

    /**
     * @see MediaDataSource.medias
     */
    fun medias(mediaUris: List<Uri>) = localDataSource.medias(mediaUris)

    /**
     * @see MediaDataSource.createAlbum
     */
    suspend fun createAlbum(name: String) = virtualDataSource.createAlbum(name)

    /**
     * @see MediaDataSource.renameAlbum
     */
    suspend fun renameAlbum(albumUri: Uri, name: String) =
        virtualDataSource.renameAlbum(albumUri, name)

    /**
     * @see MediaDataSource.deleteAlbum
     */
    suspend fun deleteAlbum(albumUri: Uri) = virtualDataSource.deleteAlbum(albumUri)

    /**
     * @see MediaDataSource.addMediaToAlbum
     */
    suspend fun addMediaToAlbum(albumUri: Uri, mediaUris: List<Uri>) =
        virtualDataSource.addMediaToAlbum(albumUri, mediaUris)

    /**
     * @see MediaDataSource.removeMediaFromAlbum
     */
    suspend fun removeMediaFromAlbum(albumUri: Uri, mediaUris: List<Uri>) =
        virtualDataSource.removeMediaFromAlbum(albumUri, mediaUris)
}
