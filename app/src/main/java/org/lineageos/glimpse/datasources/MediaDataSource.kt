/*
 * SPDX-FileCopyrightText: 2024 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.glimpse.datasources

import android.net.Uri
import kotlinx.coroutines.flow.Flow
import org.lineageos.glimpse.models.Album
import org.lineageos.glimpse.models.Media
import org.lineageos.glimpse.models.RequestStatus

typealias MediaRequestStatus<T> = RequestStatus<T, MediaError>

/**
 * A data source for media.
 */
interface MediaDataSource {
    /**
     * Get all the photos.
     */
    fun reels(): Flow<MediaRequestStatus<List<Media>>>

    /**
     * Get all the albums.
     */
    fun albums(): Flow<MediaRequestStatus<List<Album>>>

    /**
     * Get the album information and all the medias of the given album.
     */
    fun album(albumUri: Uri): Flow<MediaRequestStatus<Pair<Album, List<Media>>>>
}
