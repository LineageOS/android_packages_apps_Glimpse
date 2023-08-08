/*
 * SPDX-FileCopyrightText: 2023 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.glimpse.thumbnail

import android.database.Cursor
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.core.view.isVisible
import androidx.lifecycle.LiveData
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.recyclerview.widget.RecyclerView
import coil.load
import org.lineageos.glimpse.R
import org.lineageos.glimpse.models.Media
import org.lineageos.glimpse.models.MediaType

class MediaViewerAdapter(
    private val exoPlayer: ExoPlayer,
    private val currentPositionLiveData: LiveData<Int>,
) : BaseCursorAdapter<MediaViewerAdapter.MediaViewHolder>() {
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

    override fun getItemId(position: Int) = getIdFromMediaStore(position)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = MediaViewHolder(
        LayoutInflater.from(parent.context).inflate(R.layout.media_view, parent, false),
        exoPlayer, currentPositionLiveData,
    )

    override fun onBindViewHolder(holder: MediaViewHolder, position: Int) {
        getMediaFromMediaStore(position)?.let { holder.bind(it, position) }
    }

    override fun onViewAttachedToWindow(holder: MediaViewHolder) {
        super.onViewAttachedToWindow(holder)
        holder.onViewAttachedToWindow()
    }

    override fun onViewDetachedFromWindow(holder: MediaViewHolder) {
        super.onViewDetachedFromWindow(holder)
        holder.onViewDetachedFromWindow()
    }

    override fun onChangedCursor(cursor: Cursor?) {
        super.onChangedCursor(cursor)

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

    fun getMediaFromMediaStore(position: Int): Media? {
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

    private fun getIdFromMediaStore(position: Int): Long {
        val cursor = cursor ?: return 0
        cursor.moveToPosition(position)
        return cursor.getLong(idIndex)
    }

    class MediaViewHolder(
        private val view: View,
        private val exoPlayer: ExoPlayer,
        private val currentPositionLiveData: LiveData<Int>,
    ) : RecyclerView.ViewHolder(view) {
        // Views
        private val imageView = view.findViewById<ImageView>(R.id.imageView)
        private val playerView = view.findViewById<PlayerView>(R.id.playerView)

        private lateinit var media: Media
        private var position = -1

        private val observer = { currentPosition: Int ->
            val isNowVideoPlayer = currentPosition == position && media.mediaType == MediaType.VIDEO

            imageView.isVisible = !isNowVideoPlayer
            playerView.isVisible = isNowVideoPlayer

            playerView.player = when (isNowVideoPlayer) {
                true -> exoPlayer
                false -> null
            }
        }

        fun bind(media: Media, position: Int) {
            this.media = media
            this.position = position
            imageView.load(media.externalContentUri)
        }

        fun onViewAttachedToWindow() {
            view.findViewTreeLifecycleOwner()?.let {
                currentPositionLiveData.observe(it, observer)
            }
        }

        fun onViewDetachedFromWindow() {
            currentPositionLiveData.removeObserver(observer)
        }
    }
}
