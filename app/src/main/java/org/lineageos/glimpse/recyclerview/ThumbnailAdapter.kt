/*
 * SPDX-FileCopyrightText: 2023 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.glimpse.recyclerview

import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import coil.load
import org.lineageos.glimpse.R
import org.lineageos.glimpse.models.Media
import org.lineageos.glimpse.models.MediaType
import org.lineageos.glimpse.viewmodels.ThumbnailViewModel.DataType
import java.util.Date

class ThumbnailAdapter(
    private val onItemSelected: (media: Media) -> Unit,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    var data = arrayOf<DataType>()
        set(value) {
            val diff = DiffUtil.calculateDiff(ThumbnailCallback(field, value))

            field = value

            diff.dispatchUpdatesTo(this)
        }

    override fun getItemCount() = data.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        LayoutInflater.from(parent.context).let { layoutInflater ->
            when (viewType) {
                ViewTypes.THUMBNAIL.ordinal -> ThumbnailViewHolder(
                    layoutInflater.inflate(R.layout.thumbnail_view, parent, false),
                    onItemSelected
                )

                ViewTypes.DATE_HEADER.ordinal -> DateHeaderViewHolder(
                    layoutInflater.inflate(R.layout.date_header_view, parent, false)
                )

                else -> throw Exception("Unknown view type $viewType")
            }
        }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder.itemViewType) {
            ViewTypes.THUMBNAIL.ordinal -> {
                val thumbnailViewHolder = holder as ThumbnailViewHolder
                thumbnailViewHolder.bind((data[position] as DataType.Thumbnail).media)
            }

            ViewTypes.DATE_HEADER.ordinal -> {
                val dateHeaderViewHolder = holder as DateHeaderViewHolder
                dateHeaderViewHolder.bind((data[position] as DataType.DateHeader).date)
            }
        }
    }

    override fun getItemViewType(position: Int) = data[position].viewType

    class ThumbnailViewHolder(
        view: View,
        private val onItemSelected: (media: Media) -> Unit,
    ) : RecyclerView.ViewHolder(view) {
        // Views
        private val videoOverlayImageView =
            itemView.findViewById<ImageView>(R.id.videoOverlayImageView)!!
        private val thumbnailImageView = itemView.findViewById<ImageView>(R.id.thumbnailImageView)!!

        private lateinit var media: Media

        fun bind(media: Media) {
            this.media = media

            itemView.setOnClickListener {
                onItemSelected(media)
            }

            thumbnailImageView.load(media.externalContentUri) {
                memoryCacheKey("thumbnail_${media.id}")
                size(DisplayAwareGridLayoutManager.MAX_THUMBNAIL_SIZE)
                placeholder(R.drawable.thumbnail_placeholder)
            }
            videoOverlayImageView.isVisible = media.mediaType == MediaType.VIDEO
        }
    }

    class DateHeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        // Views
        private val textView = view as TextView

        fun bind(date: Date) {
            textView.text = DateUtils.getRelativeTimeSpanString(
                date.time,
                System.currentTimeMillis(),
                DateUtils.DAY_IN_MILLIS
            )
        }
    }

    enum class ViewTypes {
        THUMBNAIL,
        DATE_HEADER,
    }
}
