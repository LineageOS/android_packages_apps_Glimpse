/*
 * SPDX-FileCopyrightText: 2025 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.glimpse.models

import android.net.Uri
import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * A media representation of an URI.
 */
@Parcelize
open class UriMedia(
    override val uri: Uri,
    override val mediaType: MediaType,
    override val mimeType: String,
) : MediaItem<UriMedia>, Parcelable {
    override fun areContentsTheSame(other: UriMedia) = compareValuesBy(
        this, other,
        UriMedia::mediaType,
        UriMedia::mimeType,
    ) == 0
}
