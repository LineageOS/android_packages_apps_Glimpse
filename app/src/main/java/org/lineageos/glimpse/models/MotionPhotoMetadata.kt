/*
 * SPDX-FileCopyrightText: 2025 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.glimpse.models

data class MotionPhotoMetadata(
    val version: Int,
    val presentationTimestampUs: Long? = null,
    val videoLength: Int,
    val videoMimeType: String,
)
