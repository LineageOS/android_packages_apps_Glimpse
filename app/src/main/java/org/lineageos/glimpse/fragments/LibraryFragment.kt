/*
 * SPDX-FileCopyrightText: 2023-2024 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.glimpse.fragments

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.widget.NestedScrollView
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
 * Use the [LibraryFragment.newInstance] factory method to
 * create an instance of this fragment.
 */
class LibraryFragment : Fragment(R.layout.fragment_library) {
    // Views
    private val appBarLayout by getViewProperty<AppBarLayout>(R.id.appBarLayout)
    private val favoritesAlbumListItem by getViewProperty<ListItem>(R.id.favoritesAlbumListItem)
    private val photosAlbumListItem by getViewProperty<ListItem>(R.id.photosAlbumListItem)
    private val libraryNestedScrollView by getViewProperty<NestedScrollView>(R.id.libraryNestedScrollView)
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

        ViewCompat.setOnApplyWindowInsetsListener(view) { _, windowInsets ->
            val insets = windowInsets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )

            libraryNestedScrollView.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                leftMargin = insets.left
                rightMargin = insets.right
            }

            windowInsets
        }

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
        fun newInstance() = LibraryFragment().apply {
            arguments = createBundle()
        }
    }
}
