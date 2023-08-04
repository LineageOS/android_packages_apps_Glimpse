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
import androidx.core.view.isVisible
import androidx.lifecycle.LiveData
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.recyclerview.widget.RecyclerView
import coil.load
import org.lineageos.glimpse.R
import org.lineageos.glimpse.models.Media
import org.lineageos.glimpse.models.MediaType
import java.util.Date

class MediaViewerAdapter(
    private val exoPlayer: ExoPlayer,
    private val currentPositionLiveData: LiveData<Int>,
) : BaseCursorAdapter<MediaViewerAdapter.MediaViewHolder>() {
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

    fun getMediaFromMediaStore(position: Int): Media? {
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

    private fun getIdFromMediaStore(position: Int): Long {
        val cursor = cursor ?: return 0
        val idIndex = cursor.getColumnIndex(MediaStore.Files.FileColumns._ID)
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
            if (currentPosition == position) {
                imageView.isVisible = media.mediaType == MediaType.IMAGE
                playerView.isVisible = media.mediaType == MediaType.VIDEO
                if (media.mediaType == MediaType.VIDEO) {
                    playerView.player = exoPlayer
                    exoPlayer.setMediaItem(MediaItem.fromUri(media.externalContentUri))
                    exoPlayer.seekTo(C.TIME_UNSET)
                    exoPlayer.prepare()
                    exoPlayer.playWhenReady = true
                }
            } else {
                imageView.isVisible = true
                playerView.isVisible = false
                exoPlayer.stop()
                playerView.player = null
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
