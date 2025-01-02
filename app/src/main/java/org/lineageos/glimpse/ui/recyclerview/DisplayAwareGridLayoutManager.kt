/*
 * SPDX-FileCopyrightText: 2023-2025 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.glimpse.ui.recyclerview

import android.content.Context
import androidx.recyclerview.widget.GridLayoutManager
import org.lineageos.glimpse.models.Thumbnail

/**
 * GridLayoutManager that uses a proper span count based on the display orientation and DPI.
 * @param context Context.
 * @param targetSpanCount Target span count, also minimum if there's not enough space,
 * thumbnails will be resized accordingly.
 * @param thumbnailPaddingPx Padding applied to thumbnails.
 */
open class DisplayAwareGridLayoutManager(
    context: Context,
    targetSpanCount: Int,
    thumbnailPaddingPx: Int,
) : GridLayoutManager(context, getSpanCount(context, targetSpanCount, thumbnailPaddingPx)) {
    companion object {
        private enum class Orientation {
            VERTICAL,
            HORIZONTAL,
        }

        private fun getSpanCount(
            context: Context,
            targetSpanCount: Int,
            thumbnailPaddingPx: Int,
        ): Int {
            val displayMetrics = context.resources.displayMetrics

            // Account for thumbnail padding
            val paddingSize = thumbnailPaddingPx * targetSpanCount
            val availableHeight = displayMetrics.heightPixels - paddingSize
            val availableWidth = displayMetrics.widthPixels - paddingSize

            val orientation = when {
                availableWidth > availableHeight -> Orientation.HORIZONTAL
                else -> Orientation.VERTICAL
            }

            val columnsSpace = when (orientation) {
                Orientation.HORIZONTAL -> availableHeight
                Orientation.VERTICAL -> availableWidth
            }

            val thumbnailSize =
                (columnsSpace / targetSpanCount).coerceAtMost(Thumbnail.MAX_THUMBNAIL_SIZE)

            return (availableWidth / thumbnailSize).coerceAtLeast(targetSpanCount)
        }
    }
}
