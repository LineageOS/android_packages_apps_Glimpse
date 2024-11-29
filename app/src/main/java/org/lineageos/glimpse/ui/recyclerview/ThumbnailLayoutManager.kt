/*
 * SPDX-FileCopyrightText: 2023 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.glimpse.ui.recyclerview

import android.content.Context
import androidx.recyclerview.widget.RecyclerView
import org.lineageos.glimpse.ext.px

class ThumbnailLayoutManager(
    context: Context,
    adapter: RecyclerView.Adapter<*>,
) : DisplayAwareGridLayoutManager(context, 4, 4.px) {
    init {
        spanSizeLookup = ThumbnailSpanSizeLookup(adapter, spanCount)
    }

    private class ThumbnailSpanSizeLookup(
        private val adapter: RecyclerView.Adapter<*>,
        private val spanCount: Int,
    ) : SpanSizeLookup() {
        override fun getSpanSize(position: Int) = when (adapter.getItemViewType(position)) {
            ThumbnailAdapter.ViewType.THUMBNAIL.ordinal -> 1
            ThumbnailAdapter.ViewType.DATE_HEADER.ordinal -> spanCount
            else -> throw Exception("Unknown view type")
        }
    }
}
