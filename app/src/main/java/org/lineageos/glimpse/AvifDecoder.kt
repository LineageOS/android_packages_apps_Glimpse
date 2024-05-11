/*
 * SPDX-FileCopyrightText: 2024 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.glimpse

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import coil.ImageLoader
import coil.decode.DecodeResult
import coil.decode.Decoder
import coil.decode.ImageSource
import coil.fetch.SourceResult
import coil.request.Options
import okio.Buffer
import okio.BufferedSource
import okio.ByteString.Companion.encodeUtf8
import java.nio.ByteBuffer
import org.aomedia.avif.android.AvifDecoder as NativeAvifDecoder

class AvifDecoder @JvmOverloads constructor(
    private val source: ImageSource,
    private val options: Options,
    private val enforceMinimumFrameDelay: Boolean = true
) : Decoder {
    override suspend fun decode(): DecodeResult {
        val sourceBuffer = ByteBuffer.wrap(source.source().readByteArray())
        val buffer = maybeCopyBuffer(sourceBuffer)
        val info = NativeAvifDecoder.Info()
        if (!NativeAvifDecoder.getInfo(buffer, buffer.remaining(), info)) {
            throw IllegalArgumentException("Failed to get AVIF info.")
        }
        println(options.config)
        val config = options.config.takeUnless {
            it == Bitmap.Config.HARDWARE
        } ?: when (info.depth) {
            8 -> Bitmap.Config.ARGB_8888
            else -> Bitmap.Config.RGBA_F16
        }
        val bitmap = Bitmap.createBitmap(info.width, info.height, config).apply {
            if (!NativeAvifDecoder.decode(buffer, buffer.remaining(), this)) {
                throw IllegalArgumentException("Failed to decode AVIF.")
            }
        }
        return DecodeResult(
            drawable = BitmapDrawable(options.context.resources, bitmap),
            isSampled = false
        )
    }

    companion object {
        private fun maybeCopyBuffer(buffer: ByteBuffer) =
            if (!buffer.isDirect) {
                val copy = ByteBuffer.allocateDirect(buffer.remaining())
                copy.put(buffer)
                copy.rewind()
                copy
            } else {
                buffer
            }
    }

    class Factory @JvmOverloads constructor(
        private val enforceMinimumFrameDelay: Boolean = true
    ) : Decoder.Factory {
        override fun create(
            result: SourceResult, options: Options, imageLoader: ImageLoader
        ): Decoder? {
            if (!isApplicable(result.source.source())) {
                return null
            }
            return AvifDecoder(result.source, options, enforceMinimumFrameDelay)
        }

        private fun isApplicable(source: BufferedSource): Boolean {
            val peek = source.peek()
            val peekBuffer = Buffer()
            peek.read(peekBuffer, 32)
            val sourceBuffer = ByteBuffer.wrap(peekBuffer.readByteArray())
            val buffer = maybeCopyBuffer(sourceBuffer)
            return NativeAvifDecoder.isAvifImage(buffer)
        }

        override fun equals(other: Any?) = other is Factory

        override fun hashCode() = javaClass.hashCode()

        companion object {
            private val MIF = "ftypmif1".encodeUtf8()
            private val MSF = "ftypmsf1".encodeUtf8()
            private val HEIC = "ftypheic".encodeUtf8()
            private val HEIX = "ftypheix".encodeUtf8()
            private val HEVC = "ftyphevc".encodeUtf8()
            private val HEVX = "ftyphevx".encodeUtf8()
            private val AVIF = "ftypavif".encodeUtf8()
            private val AVIS = "ftypavis".encodeUtf8()

            private val AVAILABLE_BRANDS = listOf(MIF, MSF, HEIC, HEIX, HEVC, HEVX, AVIF, AVIS)
        }
    }
}