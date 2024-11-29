/*
 * SPDX-FileCopyrightText: 2023 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.glimpse.models

import android.provider.MediaStore
import org.lineageos.glimpse.utils.MimeUtils

enum class FileType(
    val mediaStoreValue: Int,
) {
    IMAGE(
        MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE,
    ),
    VIDEO(
        MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO,
    );

    companion object {
        fun fromMediaStoreValue(value: Int) = entries.first {
            value == it.mediaStoreValue
        }

        fun fromMimeType(mimeType: String) = MimeUtils.mimeTypeToFileType(mimeType)
    }
}
