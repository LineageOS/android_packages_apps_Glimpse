/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.glimpse.utils.media3

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import java.io.IOException
import java.nio.ByteBuffer

@OptIn(androidx.media3.common.util.UnstableApi::class)
class ByteBufferDataSource(
    private val buffer: ByteBuffer
) : BaseDataSource(false) {
    class Factory(
        private val buffer: ByteBuffer
    ) : DataSource.Factory {
        override fun createDataSource(): DataSource {
            return ByteBufferDataSource(buffer.duplicate())
        }
    }

    private var bytesRemaining: Int = 0
    private var opened: Boolean = false

    override fun open(dataSpec: DataSpec): Long {
        // Reset buffer position to start
        buffer.position(0)

        val startPosition = dataSpec.position.toInt()
        val endPosition = if (dataSpec.length != C.LENGTH_UNSET.toLong()) {
            (startPosition + dataSpec.length).toInt()
        } else {
            buffer.limit()
        }

        // Validate bounds
        if (startPosition < 0 || startPosition > buffer.limit()) {
            throw IOException("Invalid start position: $startPosition")
        }

        if (endPosition > buffer.limit()) {
            throw IOException("Invalid end position: $endPosition")
        }

        // Set position to start reading from
        buffer.position(startPosition)
        bytesRemaining = endPosition - startPosition
        opened = true

        transferStarted(dataSpec)

        return bytesRemaining.toLong()
    }

    override fun read(target: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) {
            return 0
        }

        if (bytesRemaining == 0) {
            return C.RESULT_END_OF_INPUT
        }

        val bytesToRead = minOf(length, bytesRemaining)
        buffer.get(target, offset, bytesToRead)
        bytesRemaining -= bytesToRead

        bytesTransferred(bytesToRead)

        return bytesToRead
    }

    override fun getUri(): Uri? = null

    override fun close() {
        if (opened) {
            opened = false
            transferEnded()
        }
    }
}
