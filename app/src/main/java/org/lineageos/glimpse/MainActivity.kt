/*
 * SPDX-FileCopyrightText: 2023 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.glimpse

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.navigation.fragment.NavHostFragment
import org.lineageos.glimpse.utils.CommonNavigationArguments

class MainActivity : AppCompatActivity() {
    // Fragments
    private val navHostFragment by lazy { supportFragmentManager.findFragmentById(R.id.navHostFragment) as NavHostFragment }

    private val navController by lazy { navHostFragment.navController }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        val windowInsetsController =
            WindowCompat.getInsetsController(window, window.decorView)

        windowInsetsController.isAppearanceLightNavigationBars = true

        navController.addOnDestinationChangedListener { _, _, bundle ->
            val commonNavigationArguments = bundle?.let {
                CommonNavigationArguments.fromBundle(it)
            } ?: CommonNavigationArguments()
        }
    }
}
