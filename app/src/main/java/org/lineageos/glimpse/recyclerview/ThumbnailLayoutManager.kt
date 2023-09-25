/*
 * SPDX-FileCopyrightText: 2023 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.glimpse.recyclerview

import android.content.Context
import org.lineageos.glimpse.ext.*

class ThumbnailLayoutManager(
    context: Context,
    adapter: ThumbnailAdapter,
) : DisplayAwareGridLayoutManager(context, 4, 4.px) {
    init {
        spanSizeLookup = ThumbnailSpanSizeLookup(adapter, spanCount)
    }

    private class ThumbnailSpanSizeLookup(
        private val adapter: ThumbnailAdapter,
        private val spanCount: Int,
    ) : SpanSizeLookup() {
        override fun getSpanSize(position: Int) = when (adapter.getItemViewType(position)) {
            ThumbnailAdapter.ViewTypes.THUMBNAIL.ordinal -> 1
            ThumbnailAdapter.ViewTypes.DATE_HEADER.ordinal -> spanCount
            else -> throw Exception("Unknown view type")
        }
    }
}
