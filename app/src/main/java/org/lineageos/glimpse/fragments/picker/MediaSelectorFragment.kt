/*
 * SPDX-FileCopyrightText: 2024 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.glimpse.fragments.picker

import android.app.Activity
import android.app.WallpaperManager
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.selection.MutableSelection
import androidx.recyclerview.selection.SelectionPredicates
import androidx.recyclerview.selection.SelectionTracker
import androidx.recyclerview.selection.StorageStrategy
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.lineageos.glimpse.R
import org.lineageos.glimpse.ext.getParcelable
import org.lineageos.glimpse.ext.getViewProperty
import org.lineageos.glimpse.models.Album
import org.lineageos.glimpse.models.Media
import org.lineageos.glimpse.ui.recyclerview.ThumbnailAdapter
import org.lineageos.glimpse.ui.recyclerview.ThumbnailItemDetailsLookup
import org.lineageos.glimpse.ui.recyclerview.ThumbnailLayoutManager
import org.lineageos.glimpse.utils.PermissionsGatedCallback
import org.lineageos.glimpse.utils.PickerUtils
import org.lineageos.glimpse.viewmodels.AlbumViewModel

/**
 * A fragment showing a list of media from a specific album with thumbnails.
 */
class MediaSelectorFragment : Fragment(R.layout.fragment_picker_media_selector) {
    // View models
    private val model by viewModels<AlbumViewModel>()

    // Views
    private val noMediaLinearLayout by getViewProperty<LinearLayout>(R.id.noMediaLinearLayout)
    private val recyclerView by getViewProperty<RecyclerView>(R.id.recyclerView)

    // System services
    private val wallpaperManager by lazy {
        requireContext().getSystemService(WallpaperManager::class.java)
    }

    // Arguments
    private val albumUri by lazy { arguments?.getParcelable(ARG_ALBUM_URI, Uri::class) }

    // Intent data
    private val mimeType by lazy { PickerUtils.translateMimeType(activity?.intent) }

    // RecyclerView
    private val adapter by lazy {
        ThumbnailAdapter(model) { media ->
            selectionTracker?.select(media)
        }
    }

    // Selection
    private var selectionTracker: SelectionTracker<Media>? = null

    private val selectionTrackerObserver =
        object : SelectionTracker.SelectionObserver<Media>() {
            override fun onSelectionChanged() {
                super.onSelectionChanged()

                updateSelection()
            }

            override fun onSelectionRefresh() {
                super.onSelectionRefresh()

                updateSelection()
            }

            override fun onSelectionRestored() {
                super.onSelectionRestored()

                updateSelection()
            }
        }

    private var actionMode: ActionMode? = null

    private val actionModeCallback = object : ActionMode.Callback {
        override fun onCreateActionMode(mode: ActionMode?, menu: Menu?): Boolean {
            requireActivity().menuInflater.inflate(
                R.menu.picker_media_selector_action_bar,
                menu
            )
            return true
        }

        override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?) = false

        override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?) =
            MutableSelection<Media>().apply {
                selectionTracker?.let {
                    it.copySelection(this)
                    it.clearSelection()
                }
            }.toList().toTypedArray().takeUnless {
                it.isEmpty()
            }?.let { selection ->
                when (item?.itemId) {
                    R.id.done -> {
                        sendResult(*selection)
                        true
                    }

                    else -> false
                }
            } ?: false

        override fun onDestroyActionMode(mode: ActionMode?) {
            selectionTracker?.clearSelection()
        }
    }

    private val inSelectionModeObserver = Observer { inSelectionMode: Boolean ->
        if (inSelectionMode) {
            startSelectionMode()
        } else {
            endSelectionMode()
        }
    }

    // Permissions
    private val permissionsGatedCallback = PermissionsGatedCallback(this) {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                model.albumWithHeaders.collectLatest {
                    adapter.submitList(it)

                    val isEmpty = it.isEmpty()
                    recyclerView.isVisible = !isEmpty
                    noMediaLinearLayout.isVisible = isEmpty
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val context = requireContext()

        recyclerView.layoutManager = ThumbnailLayoutManager(
            context, adapter
        )
        recyclerView.adapter = adapter

        ViewCompat.setOnApplyWindowInsetsListener(view) { _, windowInsets ->
            val insets = windowInsets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )

            recyclerView.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                leftMargin = insets.left
                rightMargin = insets.right
            }
            recyclerView.updatePadding(bottom = insets.bottom)

            windowInsets
        }

        selectionTracker = SelectionTracker.Builder(
            "thumbnail-${albumUri}",
            recyclerView,
            adapter.itemKeyProvider,
            ThumbnailItemDetailsLookup(recyclerView),
            StorageStrategy.createParcelableStorage(Media::class.java),
        ).withSelectionPredicate(
            when (allowMultipleSelection) {
                true -> SelectionPredicates.createSelectAnything()
                false -> SelectionPredicates.createSelectSingleAnything()
            }
        ).build().also {
            adapter.selectionTracker = it
            it.addObserver(selectionTrackerObserver)
        }

        model.inSelectionMode.observe(viewLifecycleOwner, inSelectionModeObserver)

        permissionsGatedCallback.runAfterPermissionsCheck()
    }

    override fun onDestroyView() {
        super.onDestroyView()

        // Clear action mode if still active
        endSelectionMode()
    }

    private fun updateSelection() {
        model.inSelectionMode.value = selectionTracker?.hasSelection() == true

        selectionTracker?.selection?.count()?.takeIf { it > 0 }?.let {
            startSelectionMode()?.apply {
                title = resources.getQuantityString(
                    R.plurals.thumbnail_selection_count, it, it
                )
            }
        }
    }

    private fun startSelectionMode() = actionMode ?: activity?.startActionMode(
        actionModeCallback
    ).also {
        actionMode = it
    }

    private fun endSelectionMode() {
        actionMode?.finish()
        actionMode = null
    }

    /**
     * Set the activity result and close the activity.
     * @param medias The selected medias
     */
    private fun sendResult(vararg medias: Media) {
        val activity = activity ?: return
        val intent = activity.intent ?: return

        when (intent.action) {
            Intent.ACTION_GET_CONTENT,
            Intent.ACTION_PICK -> activity.setResult(
                Activity.RESULT_OK,
                Intent().apply {
                    if (allowMultipleSelection) {
                        clipData = ClipData.newUri(
                            activity.contentResolver, "", medias.first().uri
                        ).also { clipData ->
                            for (media in 1 until medias.size) {
                                clipData.addItem(
                                    ClipData.Item(medias[media].uri)
                                )
                            }
                        }
                    } else {
                        require(medias.size == 1) {
                            "More than one media provided when only one was requested"
                        }

                        data = medias.first().uri
                    }

                    flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                }
            )

            Intent.ACTION_SET_WALLPAPER -> {
                require(medias.size == 1) {
                    "More than one media provided when only one was requested"
                }

                runCatching {
                    wallpaperManager.getCropAndSetWallpaperIntent(
                        medias.first().uri
                    )
                }.getOrNull()?.also {
                    activity.startActivity(it)
                } ?: Toast.makeText(
                    activity,
                    R.string.intent_no_system_wallpaper_cropper_available,
                    Toast.LENGTH_LONG,
                ).show()
            }

            else -> throw Exception("Unknown action")
        }

        activity.finish()
    }

    /**
     * Whether we can provide multiple items or only one.
     * @see Intent.EXTRA_ALLOW_MULTIPLE
     */
    private val allowMultipleSelection: Boolean
        get() = activity?.intent?.let { intent ->
            when (intent.action) {
                Intent.ACTION_GET_CONTENT -> intent.extras?.getBoolean(
                    Intent.EXTRA_ALLOW_MULTIPLE, false
                )

                else -> false
            }
        } ?: false

    companion object {
        private const val ARG_ALBUM_URI = "album_uri"

        /**
         * Create a [Bundle] to use as the arguments for this fragment.
         * @param albumUri The [Album] to display's URI, if null, reels will be shown
         */
        fun createBundle(
            albumUri: Uri? = null,
        ) = bundleOf(
            ARG_ALBUM_URI to albumUri,
        )
    }
}
