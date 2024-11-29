/*
 * SPDX-FileCopyrightText: 2024 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.glimpse.datasources

import android.net.Uri
import kotlinx.coroutines.flow.Flow
import org.lineageos.glimpse.models.Album
import org.lineageos.glimpse.models.Media
import org.lineageos.glimpse.models.MediaType
import org.lineageos.glimpse.models.RequestStatus

typealias MediaRequestStatus<T> = RequestStatus<T, MediaError>

/**
 * A data source for media.
 */
interface MediaDataSource {
    /**
     * Check whether this data source can handle the given media item.
     *
     * @param mediaItemUri The media item to check
     * @return Whether this data source can handle the given media item
     */
    fun isMediaItemCompatible(mediaItemUri: Uri): Boolean

    /**
     * Given a compatible media item URI, get its type.
     *
     * @param mediaItemUri The media item to check
     * @return [RequestStatus.Success] if success, [RequestStatus.Error] with an error otherwise
     */
    suspend fun mediaTypeOf(mediaItemUri: Uri): MediaRequestStatus<MediaType>

    /**
     * Get all the media.
     *
     * @param mediaType The file type to filter for
     * @param mimeType The MIME type to filter for
     */
    fun reels(
        mediaType: MediaType?,
        mimeType: String?,
    ): Flow<MediaRequestStatus<List<Media>>>

    /**
     * Get the list of favorite media.
     */
    fun favorites(): Flow<MediaRequestStatus<List<Media>>>

    /**
     * Get the list of trashed media.
     */
    fun trash(): Flow<MediaRequestStatus<List<Media>>>

    /**
     * Get all the albums.
     *
     * @param mediaType The file type to filter for
     * @param mimeType The MIME type to filter for
     */
    fun albums(
        mediaType: MediaType?,
        mimeType: String?,
    ): Flow<MediaRequestStatus<List<Album>>>

    /**
     * Get the album information and all the medias of the given album.
     */
    fun album(albumUri: Uri): Flow<MediaRequestStatus<Pair<Album, List<Media>>>>

    /**
     * Get the media information of the given media.
     */
    fun media(mediaUri: Uri): Flow<MediaRequestStatus<Media>>
}
