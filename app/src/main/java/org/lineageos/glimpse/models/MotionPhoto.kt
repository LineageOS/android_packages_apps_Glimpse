/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.glimpse.models

import java.nio.ByteBuffer

data class MotionPhoto(
    val metadata: Metadata,
    val videoBuffer: ByteBuffer,
) {
    data class Metadata(
        val version: Int,
        val presentationTimestampUs: Long? = null,
        val videoLength: Int,
        val videoMimeType: String,
    )
}
