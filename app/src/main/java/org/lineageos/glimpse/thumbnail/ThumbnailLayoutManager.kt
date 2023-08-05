/*
 * SPDX-FileCopyrightText: 2023 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.glimpse.thumbnail

import android.content.Context
import androidx.recyclerview.widget.GridLayoutManager

class ThumbnailLayoutManager(
    context: Context,
    adapter: ThumbnailAdapter,
) : GridLayoutManager(context, SPAN_COUNT) {
    init {
        spanSizeLookup = ThumbnailSpanSizeLookup(adapter, SPAN_COUNT)
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
        private const val SPAN_COUNT = 3
    }
}
