/*
 * SPDX-FileCopyrightText: 2023 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.glimpse.ui.recyclerview

import android.view.MotionEvent
import androidx.recyclerview.selection.ItemDetailsLookup
import androidx.recyclerview.widget.RecyclerView
import org.lineageos.glimpse.models.Media
import kotlin.reflect.safeCast

class ThumbnailItemDetailsLookup(
    private val recyclerView: RecyclerView,
) : ItemDetailsLookup<Media>() {
    override fun getItemDetails(e: MotionEvent) =
        recyclerView.findChildViewUnder(e.x, e.y)?.let { childView ->
            recyclerView.getChildViewHolder(childView)?.let { viewHolder ->
                ThumbnailAdapter.ThumbnailViewHolder::class.safeCast(viewHolder)?.itemDetails
            }
        }
}
