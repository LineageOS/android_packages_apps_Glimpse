/*
 * SPDX-FileCopyrightText: 2025 GuidixX
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.glimpse

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar

/**
 * Settings activity for future configuration options.
 * Currently serves as a placeholder for upcoming features.
 */
class SettingsActivity : AppCompatActivity(R.layout.activity_settings) {
    
    private val toolbar by lazy { findViewById<MaterialToolbar>(R.id.toolbar) }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Enable edge-to-edge
        enableEdgeToEdge()
        
        // Set up toolbar
        toolbar.setNavigationOnClickListener {
            finish()
        }
    }
}
