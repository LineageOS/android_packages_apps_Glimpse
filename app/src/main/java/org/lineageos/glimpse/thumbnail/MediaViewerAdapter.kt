/*
 * SPDX-FileCopyrightText: 2023 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.glimpse.thumbnail

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
) : RecyclerView.Adapter<MediaViewerAdapter.MediaViewHolder>() {
    var data: Array<Media> = arrayOf()
        set(value) {
            if (value.contentEquals(field)) {
                return
            }

            field = value

            field.let {
                @Suppress("NotifyDataSetChanged") notifyDataSetChanged()
            }
        }

    init {
        setHasStableIds(true)
    }

    override fun getItemCount() = data.size

    override fun getItemId(position: Int) = data[position].id

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = MediaViewHolder(
        LayoutInflater.from(parent.context).inflate(R.layout.media_view, parent, false),
        exoPlayer, currentPositionLiveData,
    )

    override fun onBindViewHolder(holder: MediaViewHolder, position: Int) {
        holder.bind(data[position], position)
    }

    override fun onViewAttachedToWindow(holder: MediaViewHolder) {
        super.onViewAttachedToWindow(holder)
        holder.onViewAttachedToWindow()
    }

    override fun onViewDetachedFromWindow(holder: MediaViewHolder) {
        super.onViewDetachedFromWindow(holder)
        holder.onViewDetachedFromWindow()
    }

    fun getItemAtPosition(position: Int) = data[position]

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
