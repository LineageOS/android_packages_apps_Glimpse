/*
 * SPDX-FileCopyrightText: 2024-2025 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.glimpse.models

import android.net.Uri

sealed interface MediaItem<T : MediaItem<T>> : UniqueItem<T> {
    /**
     * The media type.
     */
    val mediaType: MediaType

    /**
     * The mime type of the media item.
     */
    val mimeType: String

    /**
     * A [Uri] identifying this media item.
     */
    val uri: Uri

    override fun areItemsTheSame(other: T) = this.uri == other.uri
}
