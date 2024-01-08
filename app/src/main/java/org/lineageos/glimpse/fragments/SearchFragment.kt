/*
 * SPDX-FileCopyrightText: 2023-2024 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.glimpse.fragments

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.content.res.ResourcesCompat
import androidx.core.os.bundleOf
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.search.SearchBar
import com.google.android.material.shape.MaterialShapeDrawable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.lineageos.glimpse.R
import org.lineageos.glimpse.ext.getViewProperty
import org.lineageos.glimpse.recyclerview.LocationsCarouselAdapter
import org.lineageos.glimpse.ui.ListItem
import org.lineageos.glimpse.utils.MediaStoreBuckets
import org.lineageos.glimpse.utils.PermissionsGatedCallback
import org.lineageos.glimpse.viewmodels.LocationViewModel

/**
 * A fragment showing a search bar with locations and categories.
 * Use the [SearchFragment.newInstance] factory method to
 * create an instance of this fragment.
 */
class SearchFragment : Fragment(R.layout.fragment_search) {
    // View models
    private val locationViewModel: LocationViewModel by viewModels()

    // Views
    private val appBarLayout by getViewProperty<AppBarLayout>(R.id.appBarLayout)
    private val favoritesAlbumListItem by getViewProperty<ListItem>(R.id.favoritesAlbumListItem)
    private val locationsCarouselRecyclerView by getViewProperty<RecyclerView>(R.id.locationsCarouselRecyclerView)
    private val photosAlbumListItem by getViewProperty<ListItem>(R.id.photosAlbumListItem)
    private val searchNestedScrollView by getViewProperty<NestedScrollView>(R.id.searchNestedScrollView)
    private val searchBar by getViewProperty<SearchBar>(R.id.searchBar)
    private val trashAlbumListItem by getViewProperty<ListItem>(R.id.trashAlbumListItem)
    private val videosAlbumListItem by getViewProperty<ListItem>(R.id.videosAlbumListItem)
    private val viewAllLocationsButton by getViewProperty<Button>(R.id.viewAllLocationsButton)

    // Fragments
    private val parentNavController by lazy {
        requireParentFragment().requireParentFragment().findNavController()
    }

    // MediaStore
    private val locationsCarouselAdapter by lazy { LocationsCarouselAdapter() }

    // Permissions
    private val permissionsGatedCallback = PermissionsGatedCallback(this) {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                withContext(Dispatchers.Main) {
                    locationsCarouselAdapter.clearLocations()
                }
                locationViewModel.locations.take(10).collect {
                    withContext(Dispatchers.Main) {
                        it?.also { location ->
                            locationsCarouselAdapter.addLocation(location)
                        } ?: locationsCarouselAdapter.clearLocations()
                    }
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val context = requireContext()

        appBarLayout.statusBarForeground = MaterialShapeDrawable.createWithElevationOverlay(context)

        ViewCompat.setOnApplyWindowInsetsListener(view) { _, windowInsets ->
            val insets = windowInsets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )

            searchBar.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                leftMargin = insets.left
                rightMargin = insets.right
            }

            searchNestedScrollView.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                leftMargin = insets.left
                rightMargin = insets.right
            }

            windowInsets
        }

        locationsCarouselRecyclerView.adapter = locationsCarouselAdapter

        viewAllLocationsButton.setOnClickListener {
            parentNavController.navigate(R.id.action_mainFragment_to_locationsFragment)
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

        permissionsGatedCallback.runAfterPermissionsCheck()
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
