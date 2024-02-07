/*
 * SPDX-FileCopyrightText: 2024 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.glimpse.fragments.picker

import android.app.Activity
import android.app.WallpaperManager
import android.content.ClipData
import android.content.Intent
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
import org.lineageos.glimpse.ext.getViewProperty
import org.lineageos.glimpse.models.Album
import org.lineageos.glimpse.models.MediaStoreMedia
import org.lineageos.glimpse.recyclerview.ThumbnailAdapter
import org.lineageos.glimpse.recyclerview.ThumbnailItemDetailsLookup
import org.lineageos.glimpse.recyclerview.ThumbnailLayoutManager
import org.lineageos.glimpse.utils.PermissionsGatedCallback
import org.lineageos.glimpse.utils.PickerUtils
import org.lineageos.glimpse.viewmodels.AlbumViewerViewModel
import org.lineageos.glimpse.viewmodels.QueryResult

/**
 * A fragment showing a list of media from a specific album with thumbnails.
 * Use the [MediaSelectorFragment.newInstance] factory method to
 * create an instance of this fragment.
 */
class MediaSelectorFragment : Fragment(R.layout.fragment_picker_media_selector) {
    // View models
    private val model: AlbumViewerViewModel by viewModels {
        bucketId?.let {
            AlbumViewerViewModel.factory(requireActivity().application, it, mimeType)
        } ?: AlbumViewerViewModel.factory(requireActivity().application, mimeType = mimeType)
    }

    // Views
    private val mediasRecyclerView by getViewProperty<RecyclerView>(R.id.mediasRecyclerView)
    private val noMediaLinearLayout by getViewProperty<LinearLayout>(R.id.noMediaLinearLayout)

    // System services
    private val wallpaperManager by lazy {
        requireContext().getSystemService(WallpaperManager::class.java)
    }

    // Arguments
    private val bucketId by lazy { arguments?.getInt(KEY_BUCKET_ID) }

    // Intent data
    private val mimeType by lazy { PickerUtils.translateMimeType(activity?.intent) }

    // Recyclerview
    private val thumbnailAdapter by lazy {
        ThumbnailAdapter(model) { media ->
            selectionTracker?.select(media)
        }
    }

    // Selection
    private var selectionTracker: SelectionTracker<MediaStoreMedia>? = null

    private val selectionTrackerObserver =
        object : SelectionTracker.SelectionObserver<MediaStoreMedia>() {
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
            MutableSelection<MediaStoreMedia>().apply {
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
                model.mediaWithHeaders.collectLatest {
                    when (it) {
                        is QueryResult.Data -> {
                            thumbnailAdapter.submitList(it.values)

                            val noMedia = it.values.isEmpty()
                            mediasRecyclerView.isVisible = !noMedia
                            noMediaLinearLayout.isVisible = noMedia
                        }

                        is QueryResult.Empty -> Unit
                    }
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val context = requireContext()

        mediasRecyclerView.layoutManager = ThumbnailLayoutManager(
            context, thumbnailAdapter
        )
        mediasRecyclerView.adapter = thumbnailAdapter

        ViewCompat.setOnApplyWindowInsetsListener(view) { _, windowInsets ->
            val insets = windowInsets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )

            mediasRecyclerView.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                leftMargin = insets.left
                rightMargin = insets.right
            }
            mediasRecyclerView.updatePadding(bottom = insets.bottom)

            windowInsets
        }

        selectionTracker = SelectionTracker.Builder(
            "thumbnail-${model.bucketId}",
            mediasRecyclerView,
            thumbnailAdapter.itemKeyProvider,
            ThumbnailItemDetailsLookup(mediasRecyclerView),
            StorageStrategy.createParcelableStorage(MediaStoreMedia::class.java),
        ).withSelectionPredicate(
            when (allowMultipleSelection) {
                true -> SelectionPredicates.createSelectAnything()
                false -> SelectionPredicates.createSelectSingleAnything()
            }
        ).build().also {
            thumbnailAdapter.selectionTracker = it
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
                title = getString(R.string.thumbnail_selection_count, it)
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
    private fun sendResult(vararg medias: MediaStoreMedia) {
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
        private const val KEY_BUCKET_ID = "bucket_id"

        /**
         * Create a [Bundle] to use as the arguments for this fragment.
         * @param bucketId The [Album] to display's bucket ID, if null, reels will be shown
         */
        fun createBundle(
            bucketId: Int? = null,
        ) = bundleOf(
            KEY_BUCKET_ID to bucketId,
        )

        /**
         * Use this factory method to create a new instance of
         * this fragment using the provided parameters.
         *
         * @see createBundle
         * @return A new instance of fragment [MediaSelectorFragment].
         */
        fun newInstance(
            bucketId: Int,
        ) = MediaSelectorFragment().apply {
            arguments = createBundle(
                bucketId,
            )
        }
    }
}
