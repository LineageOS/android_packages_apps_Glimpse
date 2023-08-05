/*
 * SPDX-FileCopyrightText: 2023 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.glimpse.thumbnail

import android.content.Context
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.lineageos.glimpse.ext.px

class ThumbnailLayoutManager(
    context: Context,
    adapter: ThumbnailAdapter,
) : GridLayoutManager(context, getSpanCount(context)) {
    init {
        spanSizeLookup = ThumbnailSpanSizeLookup(adapter, spanCount)
    }

    override fun onLayoutChildren(recycler: RecyclerView.Recycler?, state: RecyclerView.State?) {
        try {
            super.onLayoutChildren(recycler, state)
        } catch (_: IndexOutOfBoundsException) {
        }
    }

    private class ThumbnailSpanSizeLookup(
        private val adapter: ThumbnailAdapter,
        private val spanCount: Int,
    ) : SpanSizeLookup() {
        override fun getSpanSize(position: Int) = when (adapter.getItemViewType(position)) {
            ThumbnailAdapter.ViewTypes.ITEM.ordinal -> 1
            ThumbnailAdapter.ViewTypes.HEADER.ordinal -> spanCount
            else -> throw Exception("Unknown view type")
        }
    }

    companion object {
        /**
         * Target span count, also minimum if there's not enough space, thumbnails will be
         * resized accordingly.
         */
        private const val TARGET_SPAN_COUNT = 4

        /**
         * Padding applied to thumbnails.
         */
        private const val THUMBNAIL_PADDING = 4

        /**
         * Maximum thumbnail size, useful for high density screens.
         */
        private const val MAX_THUMBNAIL_SIZE = 128

        private enum class Orientation {
            VERTICAL,
            HORIZONTAL,
        }

        private fun getSpanCount(context: Context): Int {
            val displayMetrics = context.resources.displayMetrics

            // Account for thumbnail padding
            val paddingSize = THUMBNAIL_PADDING.px * TARGET_SPAN_COUNT
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
                (columnsSpace / TARGET_SPAN_COUNT).coerceAtMost(MAX_THUMBNAIL_SIZE.px)

            return (availableWidth / thumbnailSize).coerceAtLeast(TARGET_SPAN_COUNT)
        }
    }
}
