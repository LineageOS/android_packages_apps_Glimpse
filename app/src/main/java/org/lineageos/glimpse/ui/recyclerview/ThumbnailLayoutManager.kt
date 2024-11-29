/*
 * SPDX-FileCopyrightText: 2023-2024 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.glimpse.ui.recyclerview

import android.content.Context
import androidx.recyclerview.widget.RecyclerView
import org.lineageos.glimpse.ext.px
import org.lineageos.glimpse.viewmodels.AlbumViewModel

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
            AlbumViewModel.AlbumContent.ViewType.THUMBNAIL.ordinal -> 1
            AlbumViewModel.AlbumContent.ViewType.DATE_HEADER.ordinal -> spanCount
            else -> throw Exception("Unknown view type")
        }
    }
}
