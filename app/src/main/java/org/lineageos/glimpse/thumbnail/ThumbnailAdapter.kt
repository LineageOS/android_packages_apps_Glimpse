/*
 * SPDX-FileCopyrightText: 2023 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.glimpse.thumbnail

import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import org.lineageos.glimpse.R
import org.lineageos.glimpse.models.Media
import org.lineageos.glimpse.models.MediaType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.concurrent.TimeUnit

class ThumbnailAdapter(
    private val onItemSelected: (media: Media, position: Int) -> Unit,
) : BaseCursorAdapter<RecyclerView.ViewHolder>() {
    private val headersPositions = sortedSetOf<Int>()

    private var recyclerView: RecyclerView? = null

    override fun getItemCount() = super.getItemCount() + headersPositions.size

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)

        this.recyclerView = recyclerView
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)

        this.recyclerView = null
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        LayoutInflater.from(parent.context).let { layoutInflater ->
            when (viewType) {
                ViewTypes.ITEM.ordinal -> ThumbnailViewHolder(
                    layoutInflater.inflate(R.layout.thumbnail_view, parent, false),
                    onItemSelected
                )

                ViewTypes.HEADER.ordinal -> DateHeaderViewHolder(
                    layoutInflater.inflate(R.layout.date_header_view, parent, false)
                )

                else -> throw Exception("Unknown view type $viewType")
            }
        }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val truePosition = getTruePosition(position)

        when (holder.itemViewType) {
            ViewTypes.ITEM.ordinal -> {
                val thumbnailViewHolder = holder as ThumbnailViewHolder
                getMediaFromMediaStore(truePosition)?.let {
                    thumbnailViewHolder.bind(it, truePosition)
                }
            }

            ViewTypes.HEADER.ordinal -> {
                val dateHeaderViewHolder = holder as DateHeaderViewHolder
                getMediaFromMediaStore(truePosition)?.let {
                    dateHeaderViewHolder.bind(it.dateAdded)
                }
            }
        }
    }

    override fun getItemViewType(position: Int): Int {
        if (headersPositions.contains(position)) {
            return ViewTypes.HEADER.ordinal
        }

        if (position == 0) {
            // First element must always be a header
            addHeaderOffset(position)
            return ViewTypes.HEADER.ordinal
        }

        val previousPosition = position - 1

        if (headersPositions.contains(previousPosition)) {
            // Before this position we have a header, next up there's a thumbnail
            return ViewTypes.ITEM.ordinal
        }

        val truePosition = getTruePosition(position)
        val previousTruePosition = truePosition - 1

        val currentMedia = getMediaFromMediaStore(truePosition)!!
        val previousMedia = getMediaFromMediaStore(previousTruePosition)!!

        val diff = previousMedia.dateAdded.time - currentMedia.dateAdded.time
        val days = TimeUnit.DAYS.convert(diff, TimeUnit.MILLISECONDS)

        if (days >= 1) {
            addHeaderOffset(position)
            return ViewTypes.HEADER.ordinal
        }

        return ViewTypes.ITEM.ordinal
    }

    private fun getTruePosition(position: Int) =
        position - headersPositions.filter { it < position }.size

    private fun addHeaderOffset(position: Int) {
        headersPositions.add(position)
        val newItemCount = itemCount
        recyclerView?.post { notifyItemInserted(newItemCount) }
    }

    private fun getMediaFromMediaStore(position: Int): Media? {
        val cursor = cursor ?: return null

        val idIndex = cursor.getColumnIndex(MediaStore.Files.FileColumns._ID)
        val mediaTypeIndex = cursor.getColumnIndex(MediaStore.Files.FileColumns.MEDIA_TYPE)
        val dateAddedIndex = cursor.getColumnIndex(MediaStore.Files.FileColumns.DATE_ADDED)

        cursor.moveToPosition(position)

        val id = cursor.getLong(idIndex)
        val mediaType = cursor.getInt(mediaTypeIndex)
        val dateAdded = cursor.getLong(dateAddedIndex)

        return Media(id, MediaType.fromMediaStoreValue(mediaType), Date(dateAdded * 1000))
    }

    class ThumbnailViewHolder(
        private val view: View,
        private val onItemSelected: (media: Media, position: Int) -> Unit,
    ) : RecyclerView.ViewHolder(view) {
        // Views
        private val thumbnailImageView = view.findViewById<ImageView>(R.id.thumbnailImageView)!!

        private lateinit var media: Media
        private var position = -1

        init {
            view.setOnClickListener {
                onItemSelected(media, position)
            }
        }

        fun bind(media: Media, position: Int) {
            this.media = media
            this.position = position

            Glide.with(view)
                .load(media.externalContentUri)
                .placeholder(R.drawable.thumbnail_placeholder)
                .into(thumbnailImageView)
        }
    }

    class DateHeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        // Views
        private val textView = view as TextView

        fun bind(date: Date) {
            textView.text = dateFormatter.format(date)
        }
    }

    companion object {
        enum class ViewTypes {
            ITEM,
            HEADER,
        }

        private val dateFormatter = SimpleDateFormat.getDateInstance()
    }
}
