/*
 * SPDX-FileCopyrightText: 2023 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.glimpse.utils

enum class MediaStoreBuckets {
    /**
     * Favorites album.
     */
    MEDIA_STORE_BUCKET_FAVORITES,

    /**
     * Trash album.
     */
    MEDIA_STORE_BUCKET_TRASH,

    /**
     * Reels album, contains only photos.
     */
    MEDIA_STORE_BUCKET_PHOTOS,

    /**
     * Reels album, contains only videos.
     */
    MEDIA_STORE_BUCKET_VIDEOS;

    val id = -0x0000DEAD - ((ordinal + 1) shl 16)
}
