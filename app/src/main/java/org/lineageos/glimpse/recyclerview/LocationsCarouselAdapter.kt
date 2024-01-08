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
import com.google.android.material.animation.AnimationUtils.lerp
import com.google.android.material.carousel.MaskableFrameLayout
import org.lineageos.glimpse.R
import org.lineageos.glimpse.models.Media

class LocationsCarouselAdapter :
    RecyclerView.Adapter<LocationsCarouselAdapter.LocationCarouselViewHolder>() {
    private val locations = mutableListOf<Pair<String, Media>>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = LocationCarouselViewHolder(
        LayoutInflater.from(parent.context).inflate(
            R.layout.location_carousel_item, parent, false
        )
    )

    override fun getItemCount() = locations.size

    override fun onBindViewHolder(holder: LocationCarouselViewHolder, position: Int) {
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

    @Suppress("RestrictedApi")
    class LocationCarouselViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        // Views
        private val imageView = itemView.findViewById<ImageView>(R.id.imageView)!!
        private val textView = itemView.findViewById<TextView>(R.id.textView)!!
        private val maskableFrameLayout = itemView as MaskableFrameLayout

        init {
            maskableFrameLayout.setOnMaskChangedListener { maskRect ->
                textView.translationX = maskRect.left
                textView.alpha = lerp(
                    1F, 0F, 0F, 80F, maskRect.left
                )
            }
        }

        fun bind(location: String, thumbnail: Media) {
            textView.text = location
            imageView.load(thumbnail.uri)
        }
    }
}
