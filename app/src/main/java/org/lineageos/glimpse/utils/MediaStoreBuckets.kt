/*
 * SPDX-FileCopyrightText: 2023 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.glimpse.utils

enum class MediaStoreBuckets(val id: Int) {
    MEDIA_STORE_BUCKET_FAVORITES(-0x0001DEAD),
    MEDIA_STORE_BUCKET_TRASH(-0x0002DEAD),
    MEDIA_STORE_BUCKET_REELS(-0x0003DEAD),
}
