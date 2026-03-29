/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.glimpse.fragments

import android.os.Bundle
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import kotlinx.coroutines.launch
import org.lineageos.glimpse.R
import org.lineageos.glimpse.ui.recyclerview.VaultAdapter
import org.lineageos.glimpse.utils.SecureVaultManager

class VaultFragment : Fragment(R.layout.fragment_vault) {

    private var actionMode: ActionMode? = null
    private var vaultAdapter: VaultAdapter? = null

    private var needsReauth = false

    private val actionModeCallback = object : ActionMode.Callback {
        override fun onCreateActionMode(mode: ActionMode?, menu: Menu?): Boolean {
            mode?.menuInflater?.inflate(R.menu.menu_vault_actions, menu)
            return true
        }

        override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?) = false

        override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?): Boolean {
            if (item?.itemId == R.id.action_unlock) {
                restoreSelectedFiles()
                return true
            }
            return false
        }

        override fun onDestroyActionMode(mode: ActionMode?) {
            vaultAdapter?.clearSelection()
            actionMode = null
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val toolbar = view.findViewById<MaterialToolbar>(R.id.toolbar)
        val recyclerView = view.findViewById<RecyclerView>(R.id.vaultRecyclerView)

        toolbar.setNavigationOnClickListener { findNavController().navigateUp() }

        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(toolbar) { v, windowInsets ->
            val insets = windowInsets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            v.setPadding(0, insets.top, 0, 0)
            windowInsets
        }

        recyclerView.layoutManager = GridLayoutManager(requireContext(), 4)

        val vaultManager = SecureVaultManager(requireContext())

        viewLifecycleOwner.lifecycleScope.launch {
            val hiddenFiles = vaultManager.getVaultFiles()

            // Setup Adapter with Selection Callback
            vaultAdapter = VaultAdapter(hiddenFiles.toMutableList()) { inSelection, count ->
                if (inSelection) {
                    if (actionMode == null) {
                        actionMode = toolbar.startActionMode(actionModeCallback)
                    }
                    actionMode?.title = "$count selected"
                } else {
                    actionMode?.finish()
                }
            }
            recyclerView.adapter = vaultAdapter
        }
    }

    override fun onResume() {
        super.onResume()
        // Secure the grid view so it cannot be screenshotted or seen in Recents
        requireActivity().window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)

        if (needsReauth) {
            vaultAdapter?.closeActiveDialog()
            findNavController().navigateUp()
        }
    }

    override fun onPause() {
        super.onPause()
        requireActivity().window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)

        needsReauth = true
    }

    private fun restoreSelectedFiles() {
        viewLifecycleOwner.lifecycleScope.launch {
            val selected = vaultAdapter?.selectedFiles?.toList() ?: return@launch
            var restoredCount = 0

            for (file in selected) {
                val isVideo = file.extension.equals("mp4", ignoreCase = true)
                val prefs = requireContext().getSharedPreferences("VaultPrefs", android.content.Context.MODE_PRIVATE)
                val originalPath = prefs.getString(file.name, "DCIM/Restored/") ?: "DCIM/Restored/"

                val collectionUri = if (isVideo) android.provider.MediaStore.Video.Media.getContentUri(android.provider.MediaStore.VOLUME_EXTERNAL_PRIMARY)
                else android.provider.MediaStore.Images.Media.getContentUri(android.provider.MediaStore.VOLUME_EXTERNAL_PRIMARY)

                val mimeType = if (isVideo) "video/mp4" else "image/${file.extension}"

                val values = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, file.name)
                    put(android.provider.MediaStore.MediaColumns.MIME_TYPE, mimeType)
                    put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, originalPath)
                    put(android.provider.MediaStore.MediaColumns.IS_PENDING, 1)
                }

                val resolver = requireContext().contentResolver
                val newUri = resolver.insert(collectionUri, values)

                if (newUri != null) {
                    resolver.openOutputStream(newUri)?.use { outputStream ->
                        file.inputStream().use { inputStream -> inputStream.copyTo(outputStream) }
                    }
                    values.clear()
                    values.put(android.provider.MediaStore.MediaColumns.IS_PENDING, 0)
                    resolver.update(newUri, values, null, null)

                    file.delete()
                    prefs.edit().remove(file.name).apply()
                    restoredCount++
                }
            }

            Toast.makeText(requireContext(), "Restored $restoredCount files", Toast.LENGTH_SHORT).show()
            actionMode?.finish()

            // Reload the grid with remaining files
            val vaultManager = SecureVaultManager(requireContext())
            vaultAdapter?.updateFiles(vaultManager.getVaultFiles())
        }
    }
}
