/*
 * SPDX-FileCopyrightText: 2025 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.glimpse.utils.media3

import androidx.annotation.OptIn
import androidx.media3.datasource.DataSource
import java.nio.ByteBuffer

@OptIn(androidx.media3.common.util.UnstableApi::class)
class ByteBufferDataSourceFactory(
    private val buffer: ByteBuffer
) : DataSource.Factory {
    override fun createDataSource(): DataSource {
        return ByteBufferDataSource(buffer.duplicate())
    }
}
