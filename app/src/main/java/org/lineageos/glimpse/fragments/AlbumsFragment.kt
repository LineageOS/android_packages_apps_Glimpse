/*
 * SPDX-FileCopyrightText: 2023-2025 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.glimpse.fragments

import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.request.RequestOptions
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.shape.MaterialShapeDrawable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.lineageos.glimpse.R
import org.lineageos.glimpse.ext.getViewProperty
import org.lineageos.glimpse.ext.loadThumbnail
import org.lineageos.glimpse.ext.updatePadding
import org.lineageos.glimpse.models.Album
import org.lineageos.glimpse.models.AlbumType
import org.lineageos.glimpse.models.RequestStatus
import org.lineageos.glimpse.models.Thumbnail
import org.lineageos.glimpse.ui.recyclerview.AlbumThumbnailLayoutManager
import org.lineageos.glimpse.ui.recyclerview.SimpleListAdapter
import org.lineageos.glimpse.ui.recyclerview.UniqueItemDiffCallback
import org.lineageos.glimpse.utils.BiometricHelper
import org.lineageos.glimpse.utils.PermissionsChecker
import org.lineageos.glimpse.utils.PermissionsUtils
import org.lineageos.glimpse.viewmodels.AlbumsViewModel
import org.lineageos.glimpse.viewmodels.IntentsViewModel

/**
 * An albums list visualizer.
 */
class AlbumsFragment : Fragment(R.layout.fragment_albums) {
    // View models
    private val albumsViewModel by viewModels<AlbumsViewModel>()
    private val intentsViewModel by activityViewModels<IntentsViewModel>()

    // Views
    private val appBarLayout by getViewProperty<AppBarLayout>(R.id.appBarLayout)
    private val noMediaLinearLayout by getViewProperty<LinearLayout>(R.id.noMediaLinearLayout)
    private val recyclerView by getViewProperty<RecyclerView>(R.id.recyclerView)
    private val toolbar by getViewProperty<MaterialToolbar>(R.id.toolbar)

    // RecyclerView
    private val adapter by lazy {
        object : SimpleListAdapter<Album, View>(
            UniqueItemDiffCallback(),
            { parent ->
                LayoutInflater.from(parent.context).inflate(
                    R.layout.album_thumbnail_view, parent, false
                )
            }
        ) {
            // Views
            private val ViewHolder.descriptionTextView
                get() = view.findViewById<TextView>(R.id.descriptionTextView)!!
            private val ViewHolder.itemsCountTextView
                get() = view.findViewById<TextView>(R.id.itemsCountTextView)!!
            private val ViewHolder.thumbnailImageView
                get() = view.findViewById<ImageView>(R.id.thumbnailImageView)!!

            override fun ViewHolder.onPrepareView() {
                view.setOnClickListener {
                    item?.let { album ->
                        // Intercept the click using our custom URI
                        if (album.uri.toString() == "glimpse://secure_vault") {
                            val pinManager = org.lineageos.glimpse.utils.VaultPinManager(requireContext())

                            val navigateToVault = {
                                val currentDest = findNavController().currentDestination?.id
                                if (currentDest == R.id.mainFragment) {
                                    findNavController().navigate(R.id.action_mainFragment_to_vaultFragment)
                                } else {
                                    findNavController().navigate(R.id.action_albumsFragment_to_vaultFragment)
                                }
                            }

                            if (!pinManager.hasPinSet()) {
                                // First time opening the vault! Force them to create a PIN.
                                showPinPad(pinManager, isSetup = true, onSuccess = navigateToVault)
                            } else {
                                // Normal usage: Ask for fingerprint, fallback to custom PIN.
                                org.lineageos.glimpse.utils.BiometricHelper.authenticate(
                                    fragment = this@AlbumsFragment,
                                    onSuccess = navigateToVault,
                                    onUseCustomPin = {
                                        showPinPad(pinManager, isSetup = false, onSuccess = navigateToVault)
                                    },
                                    onError = { errorMsg ->
                                        android.widget.Toast.makeText(requireContext(), "Auth Failed: $errorMsg", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                )
                            }
                        } else {
                            // Normal album behavior
                            when (intentsViewModel.isPicking.value) {
                                true -> findNavController().navigate(
                                    R.id.action_albumsFragment_to_fragment_album,
                                    AlbumFragment.createBundle(albumUri = album.uri)
                                )

                                false -> findNavController().navigate(
                                    R.id.action_mainFragment_to_fragment_album,
                                    AlbumFragment.createBundle(albumUri = album.uri)
                                )
                            }
                        }
                    }
                }
            }

            override fun ViewHolder.onBindView(item: Album) {
                descriptionTextView.text = item.name
                item.mediaCount?.let { mediaCount ->
                    itemsCountTextView.text = view.resources.getQuantityString(
                        R.plurals.album_thumbnail_items, mediaCount, mediaCount
                    )
                }

                thumbnailImageView.loadThumbnail(
                    item.thumbnail,
                    options = RequestOptions()
                        .override(
                            Thumbnail.MAX_THUMBNAIL_SIZE,
                            Thumbnail.MAX_THUMBNAIL_SIZE
                        )
                        .centerCrop()
                )
            }
        }
    }

    // Permissions
    private val permissionsChecker = PermissionsChecker(this, PermissionsUtils.mainPermissions)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Insets
        ViewCompat.setOnApplyWindowInsetsListener(toolbar) { _, windowInsets ->
            val insets = windowInsets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )

            toolbar.updatePadding(
                insets,
                start = true,
                end = true,
            )

            windowInsets
        }

        ViewCompat.setOnApplyWindowInsetsListener(recyclerView) { _, windowInsets ->
            val insets = windowInsets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )

            recyclerView.updatePadding(
                insets,
                start = true,
                end = true,
            )

            windowInsets
        }

        val context = requireContext()

        appBarLayout.statusBarForeground = MaterialShapeDrawable.createWithElevationOverlay(context)

        recyclerView.layoutManager = AlbumThumbnailLayoutManager(context)
        recyclerView.adapter = adapter

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                permissionsChecker.withPermissionsGranted {
                    loadData()
                }
            }
        }
    }

    override fun onDestroyView() {
        recyclerView.layoutManager = null
        recyclerView.adapter = null

        super.onDestroyView()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        recyclerView.layoutManager = AlbumThumbnailLayoutManager(requireContext())
    }

    private suspend fun loadData() {
        coroutineScope {
            launch {
                intentsViewModel.parsedIntent.collectLatest {
                    when (it) {
                        is IntentsViewModel.ParsedIntent.PickIntent -> {
                            albumsViewModel.loadAlbums(
                                AlbumsViewModel.AlbumsRequest(
                                    mediaType = it.mediaType,
                                    mimeType = it.mimeType,
                                )
                            )
                        }

                        else -> albumsViewModel.loadAlbums(
                            AlbumsViewModel.AlbumsRequest()
                        )
                    }
                }
            }

            launch {
                albumsViewModel.albums.collectLatest {
                    when (it) {
                        is RequestStatus.Loading -> {
                            // Do nothing
                        }

                        is RequestStatus.Success -> {
                            adapter.submitList(it.data)

                            val isEmpty = it.data.isEmpty()
                            recyclerView.isVisible = !isEmpty
                            noMediaLinearLayout.isVisible = isEmpty
                        }

                        is RequestStatus.Error -> {
                            Log.e(LOG_TAG, "Failed to load albums, error: ${it.error}")

                            recyclerView.isVisible = false
                            noMediaLinearLayout.isVisible = true
                        }
                    }
                }
            }
        }
    }

    companion object {
        private val LOG_TAG = AlbumsFragment::class.simpleName!!
    }

    private fun showPinPad(pinManager: org.lineageos.glimpse.utils.VaultPinManager, isSetup: Boolean, onSuccess: () -> Unit) {
        val bottomSheet = com.google.android.material.bottomsheet.BottomSheetDialog(requireContext())
        val view = layoutInflater.inflate(R.layout.bottom_sheet_pin_pad, null)
        bottomSheet.setContentView(view)

        val titleText = view.findViewById<android.widget.TextView>(R.id.pinTitleText)
        val dots = listOf(
            view.findViewById<android.widget.ImageView>(R.id.dot1),
            view.findViewById<android.widget.ImageView>(R.id.dot2),
            view.findViewById<android.widget.ImageView>(R.id.dot3),
            view.findViewById<android.widget.ImageView>(R.id.dot4)
        )

        var currentPin = ""
        var firstPin = ""
        var setupPhase = if (isSetup) 1 else 0

        titleText.text = if (isSetup) "Create 4-Digit PIN" else "Enter Vault PIN"

        fun updateDots() {
            for (i in 0..3) {
                dots[i].alpha = if (i < currentPin.length) 1.0f else 0.3f
            }
        }
        updateDots()

        // Map buttons to their numbers
        val buttons = mapOf(
            R.id.btn0 to "0", R.id.btn1 to "1", R.id.btn2 to "2",
            R.id.btn3 to "3", R.id.btn4 to "4", R.id.btn5 to "5",
            R.id.btn6 to "6", R.id.btn7 to "7", R.id.btn8 to "8",
            R.id.btn9 to "9"
        )

        buttons.forEach { (id, number) ->
            view.findViewById<View>(id).setOnClickListener {
                if (currentPin.length < 4) {
                    currentPin += number
                    updateDots()

                    if (currentPin.length == 4) {
                        // Tiny delay so the user actually sees the 4th dot fill in
                        view.postDelayed({
                            if (setupPhase == 1) {
                                firstPin = currentPin
                                currentPin = ""
                                setupPhase = 2
                                titleText.text = "Confirm New PIN"
                                updateDots()
                            } else if (setupPhase == 2) {
                                if (currentPin == firstPin) {
                                    pinManager.savePin(currentPin)
                                    android.widget.Toast.makeText(requireContext(), "PIN Saved!", android.widget.Toast.LENGTH_SHORT).show()
                                    bottomSheet.dismiss()
                                    onSuccess()
                                } else {
                                    android.widget.Toast.makeText(requireContext(), "PINs do not match. Try again.", android.widget.Toast.LENGTH_SHORT).show()
                                    currentPin = ""
                                    firstPin = ""
                                    setupPhase = 1
                                    titleText.text = "Create 4-Digit PIN"
                                    updateDots()
                                }
                            } else {
                                if (pinManager.verifyPin(currentPin)) {
                                    bottomSheet.dismiss()
                                    onSuccess()
                                } else {
                                    android.widget.Toast.makeText(requireContext(), "Incorrect PIN", android.widget.Toast.LENGTH_SHORT).show()
                                    currentPin = ""
                                    updateDots()
                                }
                            }
                        }, 150)
                    }
                }
            }
        }

        view.findViewById<View>(R.id.btnBackspace).setOnClickListener {
            if (currentPin.isNotEmpty()) {
                currentPin = currentPin.dropLast(1)
                updateDots()
            }
        }

        bottomSheet.show()
    }
}
