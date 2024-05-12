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
        val sourceBuffer = source.source().readByteArray()
        val info = NativeAvifDecoder.Info()
        if (!NativeAvifDecoder.getInfo(sourceBuffer, info)) {
            throw IllegalArgumentException("Failed to get AVIF info.")
        }
        lateinit var bitmap: Bitmap
        println(info.depth)
        val config = options.config.takeUnless {
            it == Bitmap.Config.HARDWARE
        } ?: when (info.depth) {
            // TODO idk?
            10 -> Bitmap.Config.RGBA_F16
            else -> Bitmap.Config.ARGB_8888
        }
        bitmap = Bitmap.createBitmap(info.width, info.height, config)
        if (!NativeAvifDecoder.decode(sourceBuffer, bitmap)) {
            throw IllegalArgumentException("Failed to decode AVIF.")
        }
        return DecodeResult(
            drawable = BitmapDrawable(options.context.resources, bitmap),
            isSampled = false
        )
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

        private fun isApplicable(source: BufferedSource) =
            AVAILABLE_BRANDS.any { source.rangeEquals(4, it) }

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