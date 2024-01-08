/*
 * SPDX-FileCopyrightText: 2023 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.glimpse.fragments

import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.lineageos.glimpse.R
import org.lineageos.glimpse.ext.getViewProperty
import org.lineageos.glimpse.recyclerview.LocationsAdapter
import org.lineageos.glimpse.recyclerview.LocationsLayoutManager
import org.lineageos.glimpse.utils.PermissionsGatedCallback
import org.lineageos.glimpse.viewmodels.LocationViewModel

/**
 * A fragment showing a list of locations.
 * Use the [LocationsFragment.newInstance] factory method to
 * create an instance of this fragment.
 */
class LocationsFragment : Fragment(R.layout.fragment_locations) {
    // View models
    private val locationViewModel: LocationViewModel by viewModels()

    // Views
    private val appBarLayout by getViewProperty<AppBarLayout>(R.id.appBarLayout)
    private val locationsRecyclerView by getViewProperty<RecyclerView>(R.id.locationsRecyclerView)
    private val toolbar by getViewProperty<MaterialToolbar>(R.id.toolbar)

    // Coroutines
    private val mainScope = CoroutineScope(Job() + Dispatchers.Main)

    // MediaStore
    private val locationsAdapter by lazy {
        LocationsAdapter()
    }

    // Permissions
    private val permissionsGatedCallback = PermissionsGatedCallback(this) {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                mainScope.launch {
                    locationsAdapter.clearLocations()
                }
                locationViewModel.locations.collect {
                    mainScope.launch {
                        it?.also { location ->
                            locationsAdapter.addLocation(location)
                        } ?: locationsAdapter.clearLocations()
                    }
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val context = requireContext()
        val navController = findNavController()

        appBarLayout.statusBarForeground = MaterialShapeDrawable.createWithElevationOverlay(context)

        val appBarConfiguration = AppBarConfiguration(navController.graph)
        toolbar.setupWithNavController(navController, appBarConfiguration)

        locationsRecyclerView.layoutManager = LocationsLayoutManager(context)
        locationsRecyclerView.adapter = locationsAdapter

        ViewCompat.setOnApplyWindowInsetsListener(view) { _, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())

            locationsRecyclerView.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                leftMargin = insets.left
                rightMargin = insets.right
            }
            locationsRecyclerView.updatePadding(bottom = insets.bottom)

            windowInsets
        }

        permissionsGatedCallback.runAfterPermissionsCheck()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        locationsRecyclerView.layoutManager = LocationsLayoutManager(requireContext())
    }

    companion object {
        private fun createBundle() = bundleOf()

        /**
         * Use this factory method to create a new instance of
         * this fragment using the provided parameters.
         *
         * @return A new instance of fragment LocationsFragment.
         */
        fun newInstance() = LocationsFragment().apply {
            arguments = createBundle()
        }
    }
}
