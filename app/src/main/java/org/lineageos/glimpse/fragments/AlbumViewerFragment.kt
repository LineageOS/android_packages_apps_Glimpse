/*
 * SPDX-FileCopyrightText: 2023 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.glimpse.fragments

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.os.bundleOf
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupWithNavController
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.shape.MaterialShapeDrawable
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.lineageos.glimpse.R
import org.lineageos.glimpse.ViewActivity
import org.lineageos.glimpse.ext.*
import org.lineageos.glimpse.models.Album
import org.lineageos.glimpse.recyclerview.ThumbnailAdapter
import org.lineageos.glimpse.recyclerview.ThumbnailLayoutManager
import org.lineageos.glimpse.utils.PermissionsGatedCallback
import org.lineageos.glimpse.viewmodels.AlbumViewerViewModel
import org.lineageos.glimpse.viewmodels.QueryResult.Data
import org.lineageos.glimpse.viewmodels.QueryResult.Empty

/**
 * A fragment showing a list of media from a specific album with thumbnails.
 * Use the [AlbumViewerFragment.newInstance] factory method to
 * create an instance of this fragment.
 */
class AlbumViewerFragment : Fragment(R.layout.fragment_album_viewer) {
    // View models
    private val model: AlbumViewerViewModel by viewModels {
        album?.let {
            AlbumViewerViewModel.factory(requireActivity().application, it.id)
        } ?: AlbumViewerViewModel.factory(requireActivity().application)
    }

    // Views
    private val appBarLayout by getViewProperty<AppBarLayout>(R.id.appBarLayout)
    private val noMediaLinearLayout by getViewProperty<LinearLayout>(R.id.noMediaLinearLayout)
    private val recyclerView by getViewProperty<RecyclerView>(R.id.recyclerView)
    private val toolbar by getViewProperty<MaterialToolbar>(R.id.toolbar)

    // Permissions
    private val permissionsGatedCallback = PermissionsGatedCallback(this) {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                model.mediaWithHeaders.collectLatest {
                    when (it) {
                        is Data -> {
                            thumbnailAdapter.submitList(it.values)

                            val noMedia = it.values.isEmpty()
                            recyclerView.isVisible = !noMedia
                            noMediaLinearLayout.isVisible = noMedia
                        }

                        is Empty -> Unit
                    }
                }
            }
        }
    }

    // MediaStore
    private val thumbnailAdapter by lazy {
        ThumbnailAdapter { media ->
            startActivity(
                Intent(requireContext(), ViewActivity::class.java).apply {
                    action = MediaStore.ACTION_REVIEW
                    data = media.externalContentUri
                    putExtra(ViewActivity.KEY_ALBUM_ID, model.bucketId)
                }
            )
        }
    }

    // Arguments
    private val album by lazy { arguments?.getParcelable(KEY_ALBUM, Album::class) }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val navController = findNavController()

        appBarLayout.statusBarForeground =
            MaterialShapeDrawable.createWithElevationOverlay(requireContext())

        album?.let {
            toolbar.title = it.name
        }

        val appBarConfiguration = AppBarConfiguration(navController.graph)
        toolbar.setupWithNavController(navController, appBarConfiguration)

        recyclerView.layoutManager = ThumbnailLayoutManager(
            requireContext(), thumbnailAdapter
        )
        recyclerView.adapter = thumbnailAdapter

        ViewCompat.setOnApplyWindowInsetsListener(view) { _, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())

            recyclerView.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                leftMargin = insets.left
                rightMargin = insets.right
            }
            recyclerView.updatePadding(bottom = insets.bottom)

            windowInsets
        }

        permissionsGatedCallback.runAfterPermissionsCheck()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        recyclerView.layoutManager = ThumbnailLayoutManager(
            requireContext(), thumbnailAdapter
        )
    }

    companion object {
        private const val KEY_ALBUM = "album"

        /**
         * Create a [Bundle] to use as the arguments for this fragment.
         * @param album The [Album] to display, if null, reels will be shown
         */
        fun createBundle(
            album: Album? = null,
        ) = bundleOf(
            KEY_ALBUM to album,
        )

        /**
         * Use this factory method to create a new instance of
         * this fragment using the provided parameters.
         *
         * @see createBundle
         * @return A new instance of fragment [AlbumViewerFragment].
         */
        fun newInstance(
            album: Album,
        ) = AlbumViewerFragment().apply {
            arguments = createBundle(album)
        }
    }
}
