/*
 * SPDX-FileCopyrightText: 2023-2024 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.glimpse.fragments

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.navigation.NavigationBarView
import org.lineageos.glimpse.R
import org.lineageos.glimpse.ext.getViewProperty

class MainFragment : Fragment(R.layout.fragment_main) {
    // Views
    private val navigationBarView by getViewProperty<NavigationBarView>(R.id.navigationBarView)

    // Fragments
    private val childNavHostFragment by lazy { childFragmentManager.findFragmentById(R.id.childNavHostFragment) as NavHostFragment }

    private val childNavController by lazy { childNavHostFragment.navController }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        navigationBarView.setupWithNavController(childNavController)
    }
}
