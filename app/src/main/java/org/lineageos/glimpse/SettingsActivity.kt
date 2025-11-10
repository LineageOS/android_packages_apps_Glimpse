/*
 * SPDX-FileCopyrightText: 2025 GuidixX
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.glimpse

import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import org.lineageos.glimpse.ext.doubleTapSeekEnabled
import org.lineageos.glimpse.ext.doubleTapSeekTime
import org.lineageos.glimpse.ext.hideNativeSeekButtons

/**
 * Settings activity for configuration options.
 */
class SettingsActivity : AppCompatActivity(R.layout.activity_settings) {
    
    private val toolbar by lazy { findViewById<MaterialToolbar>(R.id.toolbar) }
    private val doubleTapSeekSwitch by lazy { findViewById<MaterialSwitch>(R.id.doubleTapSeekSwitch) }
    private val seekTimeLayout by lazy { findViewById<LinearLayout>(R.id.seekTimeLayout) }
    private val seekTimeSummary by lazy { findViewById<TextView>(R.id.seekTimeSummary) }
    private val hideNativeSeekButtonsSwitch by lazy { findViewById<MaterialSwitch>(R.id.hideNativeSeekButtonsSwitch) }
    
    private val seekTimeOptions = intArrayOf(5, 10, 15, 30)
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Enable edge-to-edge
        enableEdgeToEdge()
        
        // Set up toolbar
        toolbar.setNavigationOnClickListener {
            finish()
        }
        
        // Initialize preferences
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        
        // Set up double-tap seek switch
        doubleTapSeekSwitch.isChecked = sharedPreferences.doubleTapSeekEnabled
        doubleTapSeekSwitch.setOnCheckedChangeListener { _, isChecked ->
            sharedPreferences.doubleTapSeekEnabled = isChecked
            updateSeekTimeLayoutState()
        }
        
        // Set up seek time selector
        updateSeekTimeSummary()
        updateSeekTimeLayoutState()
        
        seekTimeLayout.setOnClickListener {
            showSeekTimeDialog()
        }
        
        // Set up hide native seek buttons switch
        hideNativeSeekButtonsSwitch.isChecked = sharedPreferences.hideNativeSeekButtons
        hideNativeSeekButtonsSwitch.setOnCheckedChangeListener { _, isChecked ->
            sharedPreferences.hideNativeSeekButtons = isChecked
        }
    }
    
    private fun updateSeekTimeSummary() {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        val currentTime = sharedPreferences.doubleTapSeekTime
        seekTimeSummary.text = getString(R.string.double_tap_seek_time_summary, currentTime)
    }
    
    private fun updateSeekTimeLayoutState() {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        val isEnabled = sharedPreferences.doubleTapSeekEnabled
        seekTimeLayout.isEnabled = isEnabled
        seekTimeLayout.alpha = if (isEnabled) 1.0f else 0.5f
    }
    
    private fun showSeekTimeDialog() {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        val currentTime = sharedPreferences.doubleTapSeekTime
        val currentIndex = seekTimeOptions.indexOf(currentTime).takeIf { it >= 0 } ?: 1
        
        val items = seekTimeOptions.map { 
            getString(R.string.double_tap_seek_time_summary, it)
        }.toTypedArray()
        
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.double_tap_seek_time_dialog_title)
            .setSingleChoiceItems(items, currentIndex) { dialog, which ->
                sharedPreferences.doubleTapSeekTime = seekTimeOptions[which]
                updateSeekTimeSummary()
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}
