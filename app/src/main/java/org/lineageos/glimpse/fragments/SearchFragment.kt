/*
 * SPDX-FileCopyrightText: 2023 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.glimpse.fragments

import android.os.Bundle
import android.view.View
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.shape.MaterialShapeDrawable
import org.lineageos.glimpse.R
import org.lineageos.glimpse.ext.getViewProperty
import org.lineageos.glimpse.ui.ListItem
import org.lineageos.glimpse.utils.MediaStoreBuckets

/**
 * A fragment showing a search bar with categories.
 * Use the [SearchFragment.newInstance] factory method to
 * create an instance of this fragment.
 */
class SearchFragment : Fragment(R.layout.fragment_search) {
    // Views
    private val appBarLayout by getViewProperty<AppBarLayout>(R.id.appBarLayout)
    private val favoritesAlbumListItem by getViewProperty<ListItem>(R.id.favoritesAlbumListItem)
    private val photosAlbumListItem by getViewProperty<ListItem>(R.id.photosAlbumListItem)
    private val trashAlbumListItem by getViewProperty<ListItem>(R.id.trashAlbumListItem)
    private val videosAlbumListItem by getViewProperty<ListItem>(R.id.videosAlbumListItem)

    // Fragments
    private val parentNavController by lazy {
        requireParentFragment().requireParentFragment().findNavController()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val context = requireContext()

        appBarLayout.statusBarForeground = MaterialShapeDrawable.createWithElevationOverlay(context)

        photosAlbumListItem.setOnClickListener {
            openAlbum(MediaStoreBuckets.MEDIA_STORE_BUCKET_PHOTOS)
        }

        videosAlbumListItem.setOnClickListener {
            openAlbum(MediaStoreBuckets.MEDIA_STORE_BUCKET_VIDEOS)
        }

        favoritesAlbumListItem.setOnClickListener {
            openAlbum(MediaStoreBuckets.MEDIA_STORE_BUCKET_FAVORITES)
        }

        trashAlbumListItem.setOnClickListener {
            openAlbum(MediaStoreBuckets.MEDIA_STORE_BUCKET_TRASH)
        }
    }

    private fun openAlbum(mediaStoreBucket: MediaStoreBuckets) {
        parentNavController.navigate(
            R.id.action_mainFragment_to_albumViewerFragment,
            AlbumViewerFragment.createBundle(
                bucketId = mediaStoreBucket.id
            )
        )
    }

    companion object {
        private fun createBundle() = bundleOf()

        /**
         * Use this factory method to create a new instance of
         * this fragment using the provided parameters.
         *
         * @return A new instance of fragment SearchFragment.
         */
        fun newInstance() = SearchFragment().apply {
            arguments = createBundle()
        }
    }
}
