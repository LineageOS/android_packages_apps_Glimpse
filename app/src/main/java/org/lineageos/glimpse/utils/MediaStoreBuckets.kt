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
     * Reels album, contains all medias.
     */
    MEDIA_STORE_BUCKET_REELS,

    /**
     * Reserved bucket ID for placeholders, throw an exception if this value is used.
     */
    MEDIA_STORE_BUCKET_PLACEHOLDER;

    val id = -0x0000DEAD - ((ordinal + 1) shl 16)
}
