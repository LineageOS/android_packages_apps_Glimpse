/*
 * SPDX-FileCopyrightText: 2023 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.glimpse.utils

enum class MediaStoreBuckets(val id: Int) {
    /**
     * Favorites album.
     */
    MEDIA_STORE_BUCKET_FAVORITES(-0x0001DEAD),

    /**
     * Trash album.
     */
    MEDIA_STORE_BUCKET_TRASH(-0x0002DEAD),

    /**
     * Reels album, contains all medias.
     */
    MEDIA_STORE_BUCKET_REELS(-0x0003DEAD),

    /**
     * Reserved bucket ID for placeholders, throw an exception if this value is used.
     */
    MEDIA_STORE_BUCKET_PLACEHOLDER(-0x0004DEAD),
}
