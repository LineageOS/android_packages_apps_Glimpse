/*
 * SPDX-FileCopyrightText: 2025 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.glimpse.database

import android.net.Uri
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface VirtualAlbumDao {
    @Insert
    suspend fun insert(album: VirtualAlbumEntity): Long

    @Query("UPDATE virtual_albums SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)

    @Query("DELETE FROM virtual_albums WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM virtual_albums ORDER BY id DESC")
    fun getAllAlbums(): Flow<List<VirtualAlbumEntity>>

    @Query("SELECT * FROM virtual_albums WHERE id = :id")
    fun getAlbumById(id: Long): Flow<VirtualAlbumEntity?>

    @Insert
    suspend fun insertMedia(media: List<VirtualAlbumMediaEntity>)

    @Query("DELETE FROM virtual_album_media WHERE albumId = :albumId AND mediaUri IN (:mediaUris)")
    suspend fun deleteMedia(albumId: Long, mediaUris: List<Uri>)

    @Query("SELECT mediaUri FROM virtual_album_media WHERE albumId = :albumId")
    fun getMediaUrisForAlbum(albumId: Long): Flow<List<Uri>>

    @Query("SELECT COUNT(*) FROM virtual_album_media WHERE albumId = :albumId")
    fun getMediaCountForAlbum(albumId: Long): Flow<Int>

    @Query("SELECT mediaUri FROM virtual_album_media WHERE albumId = :albumId ORDER BY ROWID DESC LIMIT 1")
    fun getLatestMediaUriForAlbum(albumId: Long): Flow<Uri?>
}
