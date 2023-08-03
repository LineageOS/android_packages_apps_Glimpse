/*
 * SPDX-FileCopyrightText: 2023 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.glimpse.fragments

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.navigation.NavigationBarView
import com.google.android.material.shape.MaterialShapeDrawable
import org.lineageos.glimpse.R
import org.lineageos.glimpse.ext.getViewProperty

class MainFragment : Fragment(R.layout.fragment_main) {
    // Views
    private val appBarLayout by getViewProperty<AppBarLayout>(R.id.appBarLayout)
    private val bottomNavigationView by getViewProperty<NavigationBarView>(R.id.bottomNavigationView)
    private val toolbar by getViewProperty<MaterialToolbar>(R.id.toolbar)

    // Fragments
    private val childNavHostFragment by lazy { childFragmentManager.findFragmentById(R.id.childNavHostFragment) as NavHostFragment }

    private val childNavController by lazy { childNavHostFragment.navController }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        appBarLayout.statusBarForeground =
            MaterialShapeDrawable.createWithElevationOverlay(requireContext())

        val appBarConfiguration = AppBarConfiguration(childNavController.graph)
        toolbar.setupWithNavController(childNavController, appBarConfiguration)
        bottomNavigationView.setupWithNavController(childNavController)
    }
}
