/*
 * SPDX-FileCopyrightText: 2025 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.glimpse.utils

import android.content.Context
import android.net.Uri
import com.adobe.internal.xmp.XMPMetaFactory
import java.nio.ByteBuffer

data class MotionPhotoMetadata(
    val version: Int,
    val presentationTimestampUs: Long? = null,
    val videoLength: Long,
    val videoMimeType: String,
    val primaryImagePadding: Int? = null
)

data class MotionPhotoData(
    val metadata: MotionPhotoMetadata,
    val videoBuffer: ByteBuffer
)

object MotionPhotoExtractor {

    fun extractMotionPhoto(context: Context, uri: Uri): MotionPhotoData? = runCatching {
        context.contentResolver.openInputStream(uri)?.use { input ->
            val buffer = input.readBytes()

            // Extract metadata from the buffer
            val metadata = extractMetadataFromBuffer(buffer) ?: return null

            // Extract video from the same buffer
            val videoBuffer = extractVideoFromBuffer(buffer, metadata)

            MotionPhotoData(metadata, videoBuffer)
        }
    }.getOrNull()

    private fun extractMetadataFromBuffer(buffer: ByteArray): MotionPhotoMetadata? =
        runCatching {
            // Extract XMP data block from JPEG
            val xmpData = extractXMPFromJPEG(buffer) ?: return null

            // Parse XMP metadata
            val xmpMeta = XMPMetaFactory.parseFromString(xmpData)

            // Register namespaces
            val schemaRegistry = XMPMetaFactory.getSchemaRegistry()
            schemaRegistry.registerNamespace(CAMERA_NAMESPACE, CAMERA_PREFIX)
            schemaRegistry.registerNamespace(CONTAINER_NAMESPACE, CONTAINER_PREFIX)
            schemaRegistry.registerNamespace(ITEM_NAMESPACE, ITEM_PREFIX)

            // Detect if MotionPhoto flag is set
            val motionPhotoValue = runCatching {
                xmpMeta.getPropertyInteger(CAMERA_NAMESPACE, MOTION_PHOTO)
            }.getOrNull()

            if (motionPhotoValue != 1) {
                return null
            }

            val version = runCatching {
                xmpMeta.getPropertyInteger(CAMERA_NAMESPACE, MOTION_PHOTO_VERSION)
            }.getOrElse { 0 }

            val presentationTimestampUs = runCatching {
                xmpMeta.getPropertyLong(CAMERA_NAMESPACE, MOTION_PHOTO_PRESENTATION_TIMESTAMP_US)
            }.getOrNull()

            var videoLength: Long? = null
            var videoMimeType: String? = null
            var primaryImagePadding: Int? = null

            runCatching {
                val itemCount = xmpMeta.countArrayItems(CONTAINER_NAMESPACE, DIRECTORY)

                for (i in 1..itemCount) {
                    val basePath = "$DIRECTORY[$i]/Container:Item"

                    val semantic = runCatching {
                        xmpMeta.getStructField(
                            CONTAINER_NAMESPACE, basePath,
                            ITEM_NAMESPACE, ITEM_SEMANTIC
                        )?.value
                    }.getOrNull()

                    when (semantic) {
                        SEMANTIC_PRIMARY -> {
                            primaryImagePadding = runCatching {
                                xmpMeta.getStructField(
                                    CONTAINER_NAMESPACE, basePath,
                                    ITEM_NAMESPACE, ITEM_PADDING
                                )?.value?.toInt()
                            }.getOrNull()
                        }

                        SEMANTIC_MOTION_PHOTO -> {
                            videoMimeType = runCatching {
                                xmpMeta.getStructField(
                                    CONTAINER_NAMESPACE, basePath,
                                    ITEM_NAMESPACE, ITEM_MIME
                                )?.value
                            }.getOrNull()

                            videoLength = runCatching {
                                xmpMeta.getStructField(
                                    CONTAINER_NAMESPACE, basePath,
                                    ITEM_NAMESPACE, ITEM_LENGTH
                                )?.value?.toLong()
                            }.getOrNull()

                            if (primaryImagePadding == null) {
                                primaryImagePadding = runCatching {
                                    xmpMeta.getStructField(
                                        CONTAINER_NAMESPACE, basePath,
                                        ITEM_NAMESPACE, ITEM_PADDING
                                    )?.value?.toInt()
                                }.getOrNull()
                            }
                        }
                    }
                }
            }

            // Return only if valid video metadata found
            if (videoLength != null && videoLength > 0 && videoMimeType != null) {
                MotionPhotoMetadata(
                    version = version,
                    presentationTimestampUs = presentationTimestampUs,
                    videoLength = videoLength,
                    videoMimeType = videoMimeType,
                    primaryImagePadding = primaryImagePadding
                )
            } else {
                null
            }
        }.getOrNull()

    private fun extractVideoFromBuffer(
        buffer: ByteArray,
        metadata: MotionPhotoMetadata
    ): ByteBuffer {
        val fileSize = buffer.size.toLong()
        val videoStartOffset = fileSize - metadata.videoLength

        // Validate offset
        require(videoStartOffset >= 0) {
            "Invalid video offset: $videoStartOffset (fileSize=$fileSize, videoLength=${metadata.videoLength})"
        }

        // Extract video bytes
        val videoBytes = buffer.copyOfRange(
            videoStartOffset.toInt(),
            buffer.size
        )

        return ByteBuffer.wrap(videoBytes)
    }

    private fun extractXMPFromJPEG(buffer: ByteArray): String? {
        val xmpIdentifier = "http://ns.adobe.com/xap/1.0/\u0000".toByteArray()

        var i = 0
        while (i < buffer.size - 1) {
            if (buffer[i] == 0xFF.toByte() && buffer[i + 1] == 0xE1.toByte()) {
                val segmentSize =
                    ((buffer[i + 2].toInt() and 0xFF) shl 8) or (buffer[i + 3].toInt() and 0xFF)
                val segmentStart = i + 4

                var hasXMPIdentifier = true
                for (j in xmpIdentifier.indices) {
                    if (segmentStart + j >= buffer.size || buffer[segmentStart + j] != xmpIdentifier[j]) {
                        hasXMPIdentifier = false
                        break
                    }
                }

                if (hasXMPIdentifier) {
                    val xmpStart = segmentStart + xmpIdentifier.size
                    val xmpEnd = (segmentStart + segmentSize - 2).coerceAtMost(buffer.size)
                    return String(buffer.copyOfRange(xmpStart, xmpEnd))
                }

                // Skip to next segment
                i += 2 + segmentSize
            } else {
                i++
            }
        }

        return null
    }

    // Camera namespace
    private const val CAMERA_NAMESPACE = "http://ns.google.com/photos/1.0/camera/"
    private const val CAMERA_PREFIX = "Camera"

    private const val MOTION_PHOTO = "MotionPhoto"
    private const val MOTION_PHOTO_VERSION = "MotionPhotoVersion"
    private const val MOTION_PHOTO_PRESENTATION_TIMESTAMP_US =
        "MotionPhotoPresentationTimestampUs"

    // Container namespace
    private const val CONTAINER_NAMESPACE = "http://ns.google.com/photos/1.0/container/"
    private const val CONTAINER_PREFIX = "Container"
    private const val DIRECTORY = "Directory"

    // Item namespace
    private const val ITEM_NAMESPACE = "http://ns.google.com/photos/1.0/container/item/"
    private const val ITEM_PREFIX = "Item"
    private const val ITEM_MIME = "Mime"
    private const val ITEM_SEMANTIC = "Semantic"
    private const val ITEM_LENGTH = "Length"
    private const val ITEM_PADDING = "Padding"

    // Semantic values
    private const val SEMANTIC_PRIMARY = "Primary"
    private const val SEMANTIC_MOTION_PHOTO = "MotionPhoto"
}
