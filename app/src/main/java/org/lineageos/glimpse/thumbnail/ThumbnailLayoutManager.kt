/*
 * SPDX-FileCopyrightText: 2023 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.glimpse.thumbnail

import android.content.Context
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView

class ThumbnailLayoutManager(
    context: Context,
    adapter: RecyclerView.Adapter<RecyclerView.ViewHolder>,
    spanCount: Int,
) : GridLayoutManager(context, spanCount) {
    init {
        spanSizeLookup = ThumbnailSpanSizeLookup(adapter, spanCount)
    }

    private class ThumbnailSpanSizeLookup(
        private val adapter: RecyclerView.Adapter<RecyclerView.ViewHolder>,
        private val spanCount: Int,
    ) : SpanSizeLookup() {
        override fun getSpanSize(position: Int) = when (adapter.getItemViewType(position)) {
            ThumbnailAdapter.Companion.ViewTypes.ITEM.ordinal -> 1
            ThumbnailAdapter.Companion.ViewTypes.HEADER.ordinal -> spanCount
            else -> throw Exception("Unknown view type")
        }
    }
}
