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
import androidx.recyclerview.widget.RecyclerView
import coil.load
import org.lineageos.glimpse.R
import org.lineageos.glimpse.models.Media

class LocationsAdapter : RecyclerView.Adapter<LocationsAdapter.LocationViewHolder>() {
    private val locations = mutableListOf<Pair<String, Media>>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = LocationViewHolder(
        LayoutInflater.from(parent.context).inflate(
            R.layout.location_item, parent, false
        )
    )

    override fun getItemCount() = locations.size

    override fun onBindViewHolder(holder: LocationViewHolder, position: Int) {
        holder.bind(locations[position].first, locations[position].second)
    }

    fun addLocation(location: Pair<String, Media>) {
        locations.add(location)
        notifyItemInserted(locations.size)
    }

    fun clearLocations() {
        val size = locations.size
        locations.clear()
        notifyItemRangeRemoved(0, size)
    }

    class LocationViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        // Views
        private val imageView = itemView.findViewById<ImageView>(R.id.imageView)!!
        private val textView = itemView.findViewById<TextView>(R.id.textView)!!

        fun bind(location: String, thumbnail: Media) {
            textView.text = location
            imageView.load(thumbnail.uri)
        }
    }
}
