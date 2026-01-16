/*
 * SPDX-FileCopyrightText: 2025 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.glimpse.datasources

import android.net.Uri
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import org.lineageos.glimpse.database.VirtualAlbumDao
import org.lineageos.glimpse.database.VirtualAlbumEntity
import org.lineageos.glimpse.database.VirtualAlbumMediaEntity
import org.lineageos.glimpse.models.Album
import org.lineageos.glimpse.models.Media
import org.lineageos.glimpse.models.MediaType
import org.lineageos.glimpse.models.RequestStatus
import org.lineageos.glimpse.models.RequestStatus.Companion.map
import org.lineageos.glimpse.models.Thumbnail

@OptIn(ExperimentalCoroutinesApi::class)
class VirtualDataSource(
    private val virtualAlbumDao: VirtualAlbumDao,
    private val localDataSource: MediaDataSource,
) : MediaDataSource {
    override fun isMediaItemCompatible(mediaItemUri: Uri): Boolean {
        return mediaItemUri.scheme == SCHEME && mediaItemUri.host == HOST_ALBUM
    }

    override suspend fun mediaTypeOf(mediaItemUri: Uri): MediaRequestStatus<MediaType> {
        return if (isMediaItemCompatible(mediaItemUri)) {
            RequestStatus.Success(MediaType.ALBUM)
        } else {
            RequestStatus.Error(MediaError.NOT_FOUND)
        }
    }

    override fun reels(
        mediaType: MediaType?,
        mimeType: String?
    ): Flow<MediaRequestStatus<List<Media>>> {
        return flowOf(RequestStatus.Success(emptyList()))
    }

    override fun favorites(): Flow<MediaRequestStatus<List<Media>>> {
        return flowOf(RequestStatus.Success(emptyList()))
    }

    override fun trash(): Flow<MediaRequestStatus<List<Media>>> {
        return flowOf(RequestStatus.Success(emptyList()))
    }

    private inline fun <reified T> List<Flow<T>>.combineToList(): Flow<List<T>> {
        return combine(this) { it.toList() }
    }

    override fun albums(
        mediaType: MediaType?,
        mimeType: String?
    ): Flow<MediaRequestStatus<List<Album>>> {
        return virtualAlbumDao.getAllAlbums().flatMapLatest { albums ->
            if (albums.isEmpty()) {
                return@flatMapLatest flowOf(RequestStatus.Success(emptyList()))
            }

            albums
                .map {
                    combine(
                        virtualAlbumDao.getLatestMediaUriForAlbum(it.id),
                        virtualAlbumDao.getMediaCountForAlbum(it.id)
                    ) { latestMediaUri, count ->
                        Album(
                            uri = buildAlbumUri(it.id),
                            name = it.name,
                            thumbnail = latestMediaUri?.let { uri -> Thumbnail(uri) },
                            mediaCount = count
                        )
                    }
                }
                .combineToList()
                .map { RequestStatus.Success(it) }
        }
    }

    override fun album(albumUri: Uri): Flow<MediaRequestStatus<Pair<Album, List<Media>>>> {
        val albumId = albumUri.lastPathSegment?.toLongOrNull()
            ?: return flowOf(RequestStatus.Error(MediaError.NOT_FOUND))

        return virtualAlbumDao.getAlbumById(albumId).flatMapLatest { albumEntity ->
            albumEntity?.let {
                getAlbumWithMedia(albumUri, albumId, it)
            } ?: flowOf(RequestStatus.Error(MediaError.NOT_FOUND))
        }
    }

    private fun getAlbumWithMedia(
        albumUri: Uri,
        albumId: Long,
        albumEntity: VirtualAlbumEntity
    ) = virtualAlbumDao.getMediaUrisForAlbum(albumId).flatMapLatest { mediaUris ->
        localDataSource.medias(mediaUris).map { status ->
            status.map { mediaList ->
                val album = Album(
                    uri = albumUri,
                    name = albumEntity.name,
                    thumbnail = mediaList.firstOrNull()?.let { Thumbnail(it.uri) },
                    mediaCount = mediaList.size
                )
                album to mediaList
            }
        }
    }

    override fun media(mediaUri: Uri): Flow<MediaRequestStatus<Media>> {
        return flowOf(RequestStatus.Error(MediaError.NOT_FOUND))
    }

    override fun medias(mediaUris: List<Uri>): Flow<MediaRequestStatus<List<Media>>> {
        return flowOf(RequestStatus.Success(emptyList()))
    }

    override suspend fun createAlbum(name: String): MediaRequestStatus<Uri> {
        val id = virtualAlbumDao.insert(VirtualAlbumEntity(name = name))
        return RequestStatus.Success(buildAlbumUri(id))
    }

    override suspend fun renameAlbum(albumUri: Uri, name: String): MediaRequestStatus<Unit> {
        val albumId = albumUri.lastPathSegment?.toLongOrNull()
            ?: return RequestStatus.Error(MediaError.NOT_FOUND)
        virtualAlbumDao.rename(albumId, name)
        return RequestStatus.Success(Unit)
    }

    override suspend fun deleteAlbum(albumUri: Uri): MediaRequestStatus<Unit> {
        val albumId = albumUri.lastPathSegment?.toLongOrNull()
            ?: return RequestStatus.Error(MediaError.NOT_FOUND)
        virtualAlbumDao.delete(albumId)
        return RequestStatus.Success(Unit)
    }

    override suspend fun addMediaToAlbum(
        albumUri: Uri,
        mediaUris: List<Uri>
    ): MediaRequestStatus<Unit> {
        val albumId = albumUri.lastPathSegment?.toLongOrNull()
            ?: return RequestStatus.Error(MediaError.NOT_FOUND)
        virtualAlbumDao.insertMedia(mediaUris.map {
            VirtualAlbumMediaEntity(albumId, it)
        })
        return RequestStatus.Success(Unit)
    }

    override suspend fun removeMediaFromAlbum(
        albumUri: Uri,
        mediaUris: List<Uri>
    ): MediaRequestStatus<Unit> {
        val albumId = albumUri.lastPathSegment?.toLongOrNull()
            ?: return RequestStatus.Error(MediaError.NOT_FOUND)
        virtualAlbumDao.deleteMedia(albumId, mediaUris)
        return RequestStatus.Success(Unit)
    }

    private fun buildAlbumUri(id: Long): Uri {
        return Uri.Builder()
            .scheme(SCHEME)
            .authority(HOST_ALBUM)
            .appendPath(id.toString())
            .build()
    }

    companion object {
        private const val SCHEME = "glimpse"
        private const val HOST_ALBUM = "virtual-album"
    }
}
