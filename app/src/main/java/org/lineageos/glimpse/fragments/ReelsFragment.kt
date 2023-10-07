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
import androidx.core.os.bundleOf
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.shape.MaterialShapeDrawable
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.lineageos.glimpse.R
import org.lineageos.glimpse.ViewActivity
import org.lineageos.glimpse.ext.getViewProperty
import org.lineageos.glimpse.recyclerview.ThumbnailAdapter
import org.lineageos.glimpse.recyclerview.ThumbnailLayoutManager
import org.lineageos.glimpse.utils.MediaStoreBuckets
import org.lineageos.glimpse.utils.PermissionsGatedCallback
import org.lineageos.glimpse.utils.PermissionsUtils
import org.lineageos.glimpse.viewmodels.QueryResult.Data
import org.lineageos.glimpse.viewmodels.QueryResult.Empty
import org.lineageos.glimpse.viewmodels.ThumbnailViewModel

/**
 * A fragment showing a list of media with thumbnails.
 * Use the [ReelsFragment.newInstance] factory method to
 * create an instance of this fragment.
 */
class ReelsFragment : Fragment(R.layout.fragment_reels) {
    // View models
    private val model: ThumbnailViewModel by viewModels {
        ThumbnailViewModel.factory(requireActivity().application)
    }

    // Views
    private val appBarLayout by getViewProperty<AppBarLayout>(R.id.appBarLayout)
    private val reelsRecyclerView by getViewProperty<RecyclerView>(R.id.reelsRecyclerView)

    // Permissions
    private val permissionsUtils by lazy { PermissionsUtils(requireContext()) }
    private val permissionsGatedCallback = PermissionsGatedCallback(this) {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                model.mediaWithHeaders.collectLatest {
                    when (it) {
                        is Data -> thumbnailAdapter.submitList(it.values)
                        is Empty -> Unit
                    }
                }
            }
        }
        permissionsUtils.showManageMediaPermissionDialogIfNeeded()
    }

    // MediaStore
    private val thumbnailAdapter by lazy {
        ThumbnailAdapter { media ->
            startActivity(
                Intent(requireContext(), ViewActivity::class.java).apply {
                    action = MediaStore.ACTION_REVIEW
                    data = media.externalContentUri
                    putExtra(
                        ViewActivity.KEY_ALBUM_ID, MediaStoreBuckets.MEDIA_STORE_BUCKET_REELS.id
                    )
                }
            )
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val context = requireContext()

        appBarLayout.statusBarForeground = MaterialShapeDrawable.createWithElevationOverlay(context)

        reelsRecyclerView.layoutManager = ThumbnailLayoutManager(
            requireContext(), thumbnailAdapter
        )
        reelsRecyclerView.adapter = thumbnailAdapter

        ViewCompat.setOnApplyWindowInsetsListener(view) { _, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())

            reelsRecyclerView.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                leftMargin = insets.left
                rightMargin = insets.right
            }

            windowInsets
        }

        permissionsGatedCallback.runAfterPermissionsCheck()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        reelsRecyclerView.layoutManager = ThumbnailLayoutManager(
            requireContext(), thumbnailAdapter
        )
    }


    companion object {
        private fun createBundle() = bundleOf()

        /**
         * Use this factory method to create a new instance of
         * this fragment using the provided parameters.
         *
         * @return A new instance of fragment ReelsFragment.
         */
        fun newInstance() = ReelsFragment().apply {
            arguments = createBundle()
        }
    }
}
