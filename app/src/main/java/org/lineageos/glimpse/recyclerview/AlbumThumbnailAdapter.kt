/*
 * SPDX-FileCopyrightText: 2023 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.glimpse.recyclerview

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.navigation.NavController
import androidx.recyclerview.widget.RecyclerView
import coil.load
import org.lineageos.glimpse.R
import org.lineageos.glimpse.fragments.AlbumFragment
import org.lineageos.glimpse.models.Album

class AlbumThumbnailAdapter(
    private val navController: NavController,
) : RecyclerView.Adapter<AlbumThumbnailAdapter.AlbumViewHolder>() {
    var data: Array<Album> = arrayOf()
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

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AlbumViewHolder {
        val layoutInflater = LayoutInflater.from(parent.context)

        val view = layoutInflater.inflate(R.layout.album_thumbnail_view, parent, false)

        return AlbumViewHolder(view, navController)
    }

    override fun getItemCount() = data.size

    override fun getItemId(position: Int) = data[position].id.toLong()

    override fun onBindViewHolder(holder: AlbumViewHolder, position: Int) {
        holder.bind(data[position])
    }

    class AlbumViewHolder(
        itemView: View,
        private val navController: NavController,
    ) : RecyclerView.ViewHolder(itemView) {
        // Views
        private val descriptionTextView =
            itemView.findViewById<TextView>(R.id.descriptionTextView)!!
        private val itemsCountTextView = itemView.findViewById<TextView>(R.id.itemsCountTextView)!!
        private val thumbnailImageView = itemView.findViewById<ImageView>(R.id.thumbnailImageView)!!

        fun bind(album: Album) {
            descriptionTextView.text = album.name
            itemsCountTextView.text = itemView.resources.getQuantityString(
                R.plurals.album_thumbnail_items, album.size, album.size
            )

            thumbnailImageView.load(album.thumbnail.externalContentUri) {
                memoryCacheKey("thumbnail_${album.thumbnail.id}")
                size(DisplayAwareGridLayoutManager.MAX_THUMBNAIL_SIZE)
                placeholder(R.drawable.thumbnail_placeholder)
            }

            itemView.setOnClickListener {
                navController.navigate(
                    R.id.action_mainFragment_to_albumFragment,
                    AlbumFragment.createBundle(album)
                )
            }
        }
    }
}
