/*
 * SPDX-FileCopyrightText: 2023 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.glimpse.fragments

import android.app.Activity
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.provider.MediaStore
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.navigation.fragment.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupWithNavController
import androidx.recyclerview.selection.SelectionPredicates
import androidx.recyclerview.selection.SelectionTracker
import androidx.recyclerview.selection.StorageStrategy
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.lineageos.glimpse.R
import org.lineageos.glimpse.ViewActivity
import org.lineageos.glimpse.ext.*
import org.lineageos.glimpse.models.Album
import org.lineageos.glimpse.models.MediaStoreMedia
import org.lineageos.glimpse.recyclerview.ThumbnailAdapter
import org.lineageos.glimpse.recyclerview.ThumbnailItemDetailsLookup
import org.lineageos.glimpse.recyclerview.ThumbnailLayoutManager
import org.lineageos.glimpse.utils.MediaStoreBuckets
import org.lineageos.glimpse.utils.PermissionsGatedCallback
import org.lineageos.glimpse.viewmodels.AlbumViewerViewModel
import org.lineageos.glimpse.viewmodels.QueryResult.Data
import org.lineageos.glimpse.viewmodels.QueryResult.Empty

/**
 * A fragment showing a list of media from a specific album with thumbnails.
 * Use the [AlbumViewerFragment.newInstance] factory method to
 * create an instance of this fragment.
 */
class AlbumViewerFragment : Fragment(R.layout.fragment_album_viewer) {
    // View models
    private val model: AlbumViewerViewModel by viewModels {
        album?.let {
            AlbumViewerViewModel.factory(requireActivity().application, it.id)
        } ?: AlbumViewerViewModel.factory(requireActivity().application)
    }

    // Views
    private val appBarLayout by getViewProperty<AppBarLayout>(R.id.appBarLayout)
    private val noMediaLinearLayout by getViewProperty<LinearLayout>(R.id.noMediaLinearLayout)
    private val recyclerView by getViewProperty<RecyclerView>(R.id.recyclerView)
    private val toolbar by getViewProperty<MaterialToolbar>(R.id.toolbar)

    // Permissions
    private val permissionsGatedCallback = PermissionsGatedCallback(this) {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                model.mediaWithHeaders.collectLatest {
                    when (it) {
                        is Data -> {
                            thumbnailAdapter.submitList(it.values)

                            val noMedia = it.values.isEmpty()
                            recyclerView.isVisible = !noMedia
                            noMediaLinearLayout.isVisible = noMedia
                        }

                        is Empty -> Unit
                    }
                }
            }
        }
    }

    // MediaStore
    private val thumbnailAdapter by lazy {
        ThumbnailAdapter(model) { media ->
            startActivity(
                Intent(requireContext(), ViewActivity::class.java).apply {
                    action = MediaStore.ACTION_REVIEW
                    data = media.uri
                    putExtra(ViewActivity.KEY_ALBUM_ID, model.bucketId)
                }
            )
        }
    }

    // Selection
    private var selectionTracker: SelectionTracker<MediaStoreMedia>? = null

    private val selectionTrackerObserver = object : SelectionTracker.SelectionObserver<MediaStoreMedia>() {
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
                when (album?.id) {
                    MediaStoreBuckets.MEDIA_STORE_BUCKET_TRASH.id -> R.menu.album_action_bar_trash
                    else -> R.menu.album_action_bar
                },
                menu
            )
            return true
        }

        override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?) = false

        override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?) =
            selectionTracker?.selection?.toList()?.toTypedArray()?.takeUnless {
                it.isEmpty()
            }?.let { selection ->
                val count = selection.count()

                when (item?.itemId) {
                    R.id.deleteForever -> {
                        MaterialAlertDialogBuilder(requireContext())
                            .setTitle(R.string.file_action_delete_forever)
                            .setMessage(
                                resources.getQuantityString(
                                    R.plurals.delete_file_forever_confirm_message, count, count
                                )
                            ).setPositiveButton(android.R.string.ok) { _, _ ->
                                deleteForeverContract.launch(
                                    requireContext().contentResolver.createDeleteRequest(
                                        *selection.map { media ->
                                            media.uri
                                        }.toTypedArray()
                                    )
                                )
                            }
                            .setNegativeButton(android.R.string.cancel) { _, _ ->
                                // Do nothing
                            }
                            .show()

                        true
                    }

                    R.id.restoreFromTrash -> {
                        MaterialAlertDialogBuilder(requireContext())
                            .setTitle(R.string.file_action_restore_from_trash)
                            .setMessage(
                                resources.getQuantityString(
                                    R.plurals.restore_file_from_trash_confirm_message, count, count
                                )
                            ).setPositiveButton(android.R.string.ok) { _, _ ->
                                trashMedias(false, *selection)
                            }
                            .setNegativeButton(android.R.string.cancel) { _, _ ->
                                // Do nothing
                            }
                            .show()

                        true
                    }

                    R.id.share -> {
                        requireActivity().startActivity(buildShareIntent(*selection))

                        true
                    }

                    R.id.moveToTrash -> {
                        MaterialAlertDialogBuilder(requireContext())
                            .setTitle(R.string.file_action_move_to_trash)
                            .setMessage(
                                resources.getQuantityString(
                                    R.plurals.move_file_to_trash_confirm_message, count, count
                                )
                            ).setPositiveButton(android.R.string.ok) { _, _ ->
                                trashMedias(true, *selection)
                            }
                            .setNegativeButton(android.R.string.cancel) { _, _ ->
                                // Do nothing
                            }
                            .show()

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

    // Contracts
    private var lastProcessedSelection: Array<out MediaStoreMedia>? = null
    private var undoTrashSnackbar: Snackbar? = null

    private val deleteForeverContract =
        registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
            val count = lastProcessedSelection?.count() ?: 1

            Snackbar.make(
                requireView(),
                resources.getQuantityString(
                    if (it.resultCode == Activity.RESULT_CANCELED) {
                        R.plurals.delete_file_forever_unsuccessful
                    } else {
                        R.plurals.delete_file_forever_successful
                    },
                    count, count
                ),
                Snackbar.LENGTH_LONG,
            ).show()

            lastProcessedSelection = null
            selectionTracker?.clearSelection()
        }

    private val trashContract =
        registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
            val succeeded = it.resultCode != Activity.RESULT_CANCELED
            val count = lastProcessedSelection?.count() ?: 1

            Snackbar.make(
                requireView(),
                resources.getQuantityString(
                    if (succeeded) {
                        R.plurals.move_file_to_trash_successful
                    } else {
                        R.plurals.move_file_to_trash_unsuccessful
                    },
                    count, count
                ),
                Snackbar.LENGTH_LONG,
            ).apply {
                lastProcessedSelection?.takeIf { succeeded }?.let { trashedMedias ->
                    setAction(R.string.move_file_to_trash_undo) {
                        trashMedias(false, *trashedMedias)
                    }
                }
                undoTrashSnackbar = this
            }.show()

            lastProcessedSelection = null
            selectionTracker?.clearSelection()
        }

    private val restoreFromTrashContract =
        registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
            val count = lastProcessedSelection?.count() ?: 1

            Snackbar.make(
                requireView(),
                resources.getQuantityString(
                    if (it.resultCode == Activity.RESULT_CANCELED) {
                        R.plurals.restore_file_from_trash_unsuccessful
                    } else {
                        R.plurals.restore_file_from_trash_successful
                    },
                    count, count
                ),
                Snackbar.LENGTH_LONG,
            ).show()

            lastProcessedSelection = null
            selectionTracker?.clearSelection()
        }

    // Arguments
    private val album by lazy { arguments?.getParcelable(KEY_ALBUM, Album::class) }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val navController = findNavController()

        appBarLayout.statusBarForeground =
            MaterialShapeDrawable.createWithElevationOverlay(requireContext())

        album?.let {
            toolbar.title = it.name
        }

        val appBarConfiguration = AppBarConfiguration(navController.graph)
        toolbar.setupWithNavController(navController, appBarConfiguration)

        recyclerView.layoutManager = ThumbnailLayoutManager(
            requireContext(), thumbnailAdapter
        )
        recyclerView.adapter = thumbnailAdapter

        ViewCompat.setOnApplyWindowInsetsListener(view) { _, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())

            recyclerView.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                leftMargin = insets.left
                rightMargin = insets.right
            }
            recyclerView.updatePadding(bottom = insets.bottom)

            windowInsets
        }

        selectionTracker = SelectionTracker.Builder(
            "thumbnail-${model.bucketId}",
            recyclerView,
            thumbnailAdapter.itemKeyProvider,
            ThumbnailItemDetailsLookup(recyclerView),
            StorageStrategy.createParcelableStorage(MediaStoreMedia::class.java),
        ).withSelectionPredicate(
            SelectionPredicates.createSelectAnything()
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

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        recyclerView.layoutManager = ThumbnailLayoutManager(
            requireContext(), thumbnailAdapter
        )
    }

    private fun updateSelection() {
        model.inSelectionMode.value = selectionTracker?.hasSelection() == true

        selectionTracker?.selection?.count()?.takeIf { it > 0 }?.let {
            startSelectionMode().apply {
                title = getString(R.string.thumbnail_selection_count, it)
            }
        }
    }

    private fun startSelectionMode() = actionMode ?: toolbar.startActionMode(
        actionModeCallback
    ).also {
        actionMode = it
    }

    private fun endSelectionMode() {
        actionMode?.finish()
        actionMode = null
    }

    private fun trashMedias(trash: Boolean, vararg medias: MediaStoreMedia) {
        lastProcessedSelection = medias

        val contract = when (trash) {
            true -> trashContract
            false -> restoreFromTrashContract
        }

        contract.launch(
            requireContext().contentResolver.createTrashRequest(
                trash, *medias.map { it.uri }.toTypedArray()
            )
        )
    }

    companion object {
        private const val KEY_ALBUM = "album"

        /**
         * Create a [Bundle] to use as the arguments for this fragment.
         * @param album The [Album] to display, if null, reels will be shown
         */
        fun createBundle(
            album: Album? = null,
        ) = bundleOf(
            KEY_ALBUM to album,
        )

        /**
         * Use this factory method to create a new instance of
         * this fragment using the provided parameters.
         *
         * @see createBundle
         * @return A new instance of fragment [AlbumViewerFragment].
         */
        fun newInstance(
            album: Album,
        ) = AlbumViewerFragment().apply {
            arguments = createBundle(album)
        }
    }
}
