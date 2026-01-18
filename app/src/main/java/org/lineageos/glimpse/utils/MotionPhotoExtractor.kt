/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.glimpse.utils

import android.content.Context
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.adobe.internal.xmp.XMPMeta
import com.adobe.internal.xmp.XMPMetaFactory
import org.lineageos.glimpse.models.MotionPhoto
import java.io.FileInputStream
import java.nio.ByteBuffer

object MotionPhotoExtractor {
    // Camera namespace
    private const val CAMERA_NAMESPACE = "http://ns.google.com/photos/1.0/camera/"
    private const val CAMERA_PREFIX = "Camera"

    private const val MOTION_PHOTO = "MotionPhoto"
    private const val MOTION_PHOTO_VERSION = "MotionPhotoVersion"
    private const val MOTION_PHOTO_PRESENTATION_TIMESTAMP_US = "MotionPhotoPresentationTimestampUs"

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

    // Semantic values
    private const val SEMANTIC_MOTION_PHOTO = "MotionPhoto"

    fun extractMotionPhoto(context: Context, uri: Uri): MotionPhoto? = runCatching {
        context.contentResolver.openFileDescriptor(uri, "r")?.use { parcel ->
            FileInputStream(parcel.fileDescriptor).use { fis ->
                val exif = ExifInterface(fis)
                val xmpBytes = exif.getAttributeBytes(ExifInterface.TAG_XMP) ?: return null

                val xmpMeta = XMPMetaFactory.parseFromBuffer(xmpBytes)
                val metadata = extractMetadata(xmpMeta) ?: return null

                val videoBuffer = ByteBuffer.allocate(metadata.videoLength)

                val channel = fis.channel
                channel.position(channel.size() - metadata.videoLength)
                val bytesRead = channel.read(videoBuffer)
                if (bytesRead != metadata.videoLength) {
                    return null
                }

                MotionPhoto(metadata, videoBuffer)
            }
        }
    }.getOrNull()

    private fun extractMetadata(xmpMeta: XMPMeta): MotionPhoto.Metadata? {
        // Register namespaces
        val schemaRegistry = XMPMetaFactory.getSchemaRegistry()
        runCatching {
            schemaRegistry.registerNamespace(CAMERA_NAMESPACE, CAMERA_PREFIX)
            schemaRegistry.registerNamespace(CONTAINER_NAMESPACE, CONTAINER_PREFIX)
            schemaRegistry.registerNamespace(ITEM_NAMESPACE, ITEM_PREFIX)
        }.getOrNull() ?: return null

        // Name: Camera:MotionPhoto
        // Type: Integer
        // 0: Indicates that the file shouldn't be treated as a Motion Photo.
        // 1: Indicates that the file should be treated as a Motion Photo.
        // All other values are undefined and are treated equivalently to 0.
        val motionPhoto = xmpMeta.getPropertyIntegerOrNull(CAMERA_NAMESPACE, MOTION_PHOTO) ?: 0

        if (motionPhoto != 1) {
            return null
        }

        // Name: Camera:MotionPhotoVersion
        // Type: Integer
        // This specification defines version "1".
        val version = xmpMeta.getPropertyIntegerOrNull(
            CAMERA_NAMESPACE, MOTION_PHOTO_VERSION
        ) ?: return null

        if (version != 1) {
            return null
        }

        // Name: Camera:MotionPhotoPresentationTimestampUs
        // Type: Long
        // Value can be -1 to denote unset/unspecified.
        val presentationTimestampUs = xmpMeta.getPropertyLongOrNull(
            CAMERA_NAMESPACE, MOTION_PHOTO_PRESENTATION_TIMESTAMP_US
        )?.takeIf { it != -1L }

        var videoLength: Int? = null
        var videoMimeType: String? = null

        // Element name: Container:Directory
        // Type: Ordered Array of Structures
        val itemCount = runCatching {
            xmpMeta.countArrayItems(CONTAINER_NAMESPACE, DIRECTORY)
        }.getOrNull() ?: return null

        for (i in 1..itemCount) {
            val basePath = "$DIRECTORY[$i]/Container:Item"

            // Element name: Item:Semantic
            // Type: String
            // Required.
            val semantic = xmpMeta.getStructFieldOrNull(
                CONTAINER_NAMESPACE, basePath,
                ITEM_NAMESPACE, ITEM_SEMANTIC
            )?.value

            // We only care about Semantic == MotionPhoto
            if (semantic != SEMANTIC_MOTION_PHOTO) {
                continue
            }

            // Attribute name: Item:Mime
            // Type: String
            // Required.
            videoMimeType = xmpMeta.getStructFieldOrNull(
                CONTAINER_NAMESPACE, basePath,
                ITEM_NAMESPACE, ITEM_MIME
            )?.value

            // Attribute name: Item:Length
            // Type: Integer
            // Required for secondary media items, including the video container.
            videoLength = xmpMeta.getStructFieldOrNull(
                CONTAINER_NAMESPACE, basePath,
                ITEM_NAMESPACE, ITEM_LENGTH
            )?.value?.toInt()
        }

        return MotionPhoto.Metadata(
            version = version,
            presentationTimestampUs = presentationTimestampUs,
            videoLength = videoLength ?: return null,
            videoMimeType = videoMimeType ?: return null,
        )
    }

    private fun XMPMeta.getPropertyIntegerOrNull(
        namespace: String,
        name: String,
    ) = runCatching { getPropertyInteger(namespace, name) }.getOrNull()

    private fun XMPMeta.getPropertyLongOrNull(
        namespace: String,
        name: String,
    ) = runCatching { getPropertyLong(namespace, name) }.getOrNull()

    private fun XMPMeta.getStructFieldOrNull(
        namespace: String,
        basePath: String,
        fieldNamespace: String,
        fieldName: String,
    ) = runCatching { getStructField(namespace, basePath, fieldNamespace, fieldName) }.getOrNull()
}
