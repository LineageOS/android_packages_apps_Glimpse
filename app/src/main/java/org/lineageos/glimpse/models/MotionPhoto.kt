/*
 * SPDX-FileCopyrightText: 2025 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.glimpse.models

import java.nio.ByteBuffer

data class MotionPhoto(
    val metadata: MotionPhotoMetadata,
    val videoBuffer: ByteBuffer
)
