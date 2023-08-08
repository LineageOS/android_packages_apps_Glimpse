/*
 * SPDX-FileCopyrightText: 2023 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.glimpse.thumbnail

import android.database.Cursor
import android.provider.MediaStore
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import coil.load
import org.lineageos.glimpse.R
import org.lineageos.glimpse.ext.*
import org.lineageos.glimpse.models.Media
import org.lineageos.glimpse.models.MediaType
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Date

class ThumbnailAdapter(
    private val onItemSelected: (media: Media, position: Int) -> Unit,
) : BaseCursorAdapter<RecyclerView.ViewHolder>() {
    private val headersPositions = sortedSetOf<Int>()

    // Cursor indexes
    private var idIndex = -1
    private var bucketIdIndex = -1
    private var isFavoriteIndex = -1
    private var isTrashedIndex = -1
    private var mediaTypeIndex = -1
    private var mimeTypeIndex = -1
    private var dateAddedIndex = -1

    init {
        setHasStableIds(true)
    }

    override fun getItemCount() =
        super.getItemCount().takeIf { it > 0 }
            ?.let { it + (headersPositions.size.takeIf { headerCount -> headerCount > 0 } ?: 1) }
            ?: 0

    override fun getItemId(position: Int) = getIdFromMediaStore(position)

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

        val before = previousMedia.dateAdded.toInstant().atZone(ZoneId.systemDefault())
        val after = currentMedia.dateAdded.toInstant().atZone(ZoneId.systemDefault())
        val days = ChronoUnit.DAYS.between(after, before)

        if (days >= 1 || before.dayOfMonth != after.dayOfMonth) {
            addHeaderOffset(position)
            return ViewTypes.HEADER.ordinal
        }

        return ViewTypes.ITEM.ordinal
    }

    override fun onChangedCursor(cursor: Cursor?) {
        super.onChangedCursor(cursor)

        headersPositions.clear()

        cursor?.let {
            idIndex = it.getColumnIndex(MediaStore.Files.FileColumns._ID)
            bucketIdIndex = it.getColumnIndex(MediaStore.Files.FileColumns.BUCKET_ID)
            isFavoriteIndex = it.getColumnIndex(MediaStore.Files.FileColumns.IS_FAVORITE)
            isTrashedIndex = it.getColumnIndex(MediaStore.Files.FileColumns.IS_TRASHED)
            mediaTypeIndex = it.getColumnIndex(MediaStore.Files.FileColumns.MEDIA_TYPE)
            mimeTypeIndex = it.getColumnIndex(MediaStore.Files.FileColumns.MIME_TYPE)
            dateAddedIndex = it.getColumnIndex(MediaStore.Files.FileColumns.DATE_ADDED)
        }
    }

    private fun getTruePosition(position: Int) =
        position - headersPositions.filter { it < position }.size

    private fun addHeaderOffset(position: Int) {
        headersPositions.add(position)
    }

    private fun getIdFromMediaStore(position: Int): Long {
        val cursor = cursor ?: return 0
        cursor.moveToPosition(getTruePosition(position))
        return cursor.getLong(idIndex)
    }

    private fun getMediaFromMediaStore(position: Int): Media? {
        val cursor = cursor ?: return null

        cursor.moveToPosition(position)

        val id = cursor.getLong(idIndex)
        val bucketId = cursor.getInt(bucketIdIndex)
        val isFavorite = cursor.getInt(isFavoriteIndex)
        val isTrashed = cursor.getInt(isTrashedIndex)
        val mediaType = cursor.getInt(mediaTypeIndex)
        val mimeType = cursor.getString(mimeTypeIndex)
        val dateAdded = cursor.getLong(dateAddedIndex)

        return Media.fromMediaStore(
            id,
            bucketId,
            isFavorite,
            isTrashed,
            mediaType,
            mimeType,
            dateAdded,
        )
    }

    class ThumbnailViewHolder(
        private val view: View,
        private val onItemSelected: (media: Media, position: Int) -> Unit,
    ) : RecyclerView.ViewHolder(view) {
        // Views
        private val videoOverlayImageView =
            view.findViewById<ImageView>(R.id.videoOverlayImageView)!!
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

            thumbnailImageView.load(media.externalContentUri) {
                size(ThumbnailLayoutManager.MAX_THUMBNAIL_SIZE.px)
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
        ITEM,
        HEADER,
    }
}
