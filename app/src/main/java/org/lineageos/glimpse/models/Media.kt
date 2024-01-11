/*
 * SPDX-FileCopyrightText: 2023 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.glimpse.models

import android.net.Uri

/**
 * A generic media representation.
 */
interface Media {
    val uri: Uri
    val mediaType: MediaType
    val mimeType: String
}
