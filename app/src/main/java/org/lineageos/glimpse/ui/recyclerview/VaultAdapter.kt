/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.glimpse.ui.recyclerview

import android.app.Dialog
import android.content.ContentValues
import android.content.Context
import android.graphics.Color
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.MediaController
import android.widget.Toast
import android.widget.VideoView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.bumptech.glide.Glide
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.MaterialToolbar
import org.lineageos.glimpse.R
import java.io.File

class VaultAdapter(
    private val files: MutableList<File>,
    private val onSelectionModeChanged: (Boolean, Int) -> Unit
) : RecyclerView.Adapter<VaultAdapter.ViewHolder>() {

    val selectedFiles = mutableSetOf<File>()
    var isSelectionMode = false

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imageView: ImageView = view.findViewById(R.id.vaultImageView)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_vault_media, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val file = files[position]
        Glide.with(holder.imageView.context).load(file).into(holder.imageView)

        if (selectedFiles.contains(file)) {
            holder.imageView.alpha = 0.6f
            holder.imageView.scaleX = 0.85f
            holder.imageView.scaleY = 0.85f
        } else {
            holder.imageView.alpha = 1.0f
            holder.imageView.scaleX = 1.0f
            holder.imageView.scaleY = 1.0f
        }

        holder.imageView.setOnLongClickListener {
            if (!isSelectionMode) {
                isSelectionMode = true
                toggleSelection(file, holder.bindingAdapterPosition)
            }
            true
        }

        holder.imageView.setOnClickListener {
            if (isSelectionMode) toggleSelection(file, holder.bindingAdapterPosition)
            else showMediaDialog(holder.itemView.context, holder.bindingAdapterPosition)
        }
    }

    private fun toggleSelection(file: File, position: Int) {
        if (selectedFiles.contains(file)) selectedFiles.remove(file) else selectedFiles.add(file)
        notifyItemChanged(position)
        if (selectedFiles.isEmpty()) isSelectionMode = false
        onSelectionModeChanged(isSelectionMode, selectedFiles.size)
    }

    fun clearSelection() {
        selectedFiles.clear()
        isSelectionMode = false
        notifyDataSetChanged()
    }

    fun updateFiles(newFiles: List<File>) {
        files.clear()
        files.addAll(newFiles)
        notifyDataSetChanged()
    }

    private fun showMediaDialog(context: Context, initialPosition: Int) {
        val dialog = Dialog(context, com.google.android.material.R.style.Theme_Material3_DayNight_NoActionBar)
        dialog.setContentView(R.layout.dialog_vault_viewer)

        // Force edge-to-edge drawing
        dialog.window?.let { window ->
            WindowCompat.setDecorFitsSystemWindows(window, false)
            window.statusBarColor = Color.TRANSPARENT
            window.navigationBarColor = Color.TRANSPARENT
        }

        val appBarLayout = dialog.findViewById<AppBarLayout>(R.id.appBarLayout)
        val toolbar = dialog.findViewById<MaterialToolbar>(R.id.dialogToolbar)
        val viewPager = dialog.findViewById<ViewPager2>(R.id.vaultViewPager)

        ViewCompat.setOnApplyWindowInsetsListener(appBarLayout) { v, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            v.setPadding(insets.left, insets.top, insets.right, 0)
            windowInsets
        }

        toolbar.setNavigationOnClickListener { dialog.dismiss() }

        toolbar.inflateMenu(R.menu.menu_vault_actions)

        val pagerAdapter = VaultPagerAdapter(context, files)
        viewPager.adapter = pagerAdapter
        viewPager.setCurrentItem(initialPosition, false)

        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                toolbar.title = files[position].name
            }
        })

        // Standard dropdown click listener
        toolbar.setOnMenuItemClickListener { item ->
            if (item.itemId == R.id.action_unlock) {
                val currentPos = viewPager.currentItem
                val currentFile = files[currentPos]
                val isVideo = currentFile.extension.equals("mp4", ignoreCase = true)

                val prefs = context.getSharedPreferences("VaultPrefs", Context.MODE_PRIVATE)
                val originalPath = prefs.getString(currentFile.name, "DCIM/Restored/") ?: "DCIM/Restored/"

                val collectionUri = if (isVideo) MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                else MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)

                val mimeType = if (isVideo) "video/mp4" else "image/${currentFile.extension}"

                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, currentFile.name)
                    put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, originalPath)
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }

                val resolver = context.contentResolver
                val newUri = resolver.insert(collectionUri, values)

                if (newUri != null) {
                    resolver.openOutputStream(newUri)?.use { outputStream ->
                        currentFile.inputStream().use { inputStream -> inputStream.copyTo(outputStream) }
                    }

                    values.clear()
                    values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                    resolver.update(newUri, values, null, null)

                    currentFile.delete()
                    prefs.edit().remove(currentFile.name).apply()

                    files.removeAt(currentPos)
                    notifyItemRemoved(currentPos)
                    notifyItemRangeChanged(currentPos, files.size)
                    pagerAdapter.notifyItemRemoved(currentPos)

                    Toast.makeText(context, "Restored to $originalPath", Toast.LENGTH_SHORT).show()
                    if (files.isEmpty()) dialog.dismiss()
                } else {
                    Toast.makeText(context, "Failed to restore file.", Toast.LENGTH_SHORT).show()
                }
                true
            } else false
        }
        dialog.show()
    }

    override fun getItemCount() = files.size

    inner class VaultPagerAdapter(private val context: Context, private val pagerFiles: List<File>) : RecyclerView.Adapter<VaultPagerAdapter.PagerViewHolder>() {
        inner class PagerViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val imageView: ImageView = view.findViewById(R.id.fullscreenImageView)
            val videoView: VideoView = view.findViewById(R.id.fullscreenVideoView)
        }
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PagerViewHolder {
            return PagerViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_vault_fullscreen, parent, false))
        }
        override fun onBindViewHolder(holder: PagerViewHolder, position: Int) {
            val file = pagerFiles[position]
            if (file.extension.equals("mp4", ignoreCase = true)) {
                holder.imageView.visibility = View.GONE
                holder.videoView.visibility = View.VISIBLE
                val mediaController = MediaController(context)
                mediaController.setAnchorView(holder.videoView)
                holder.videoView.setMediaController(mediaController)
                holder.videoView.setVideoPath(file.absolutePath)
                holder.videoView.start()
            } else {
                holder.videoView.visibility = View.GONE
                holder.imageView.visibility = View.VISIBLE
                Glide.with(context).load(file).into(holder.imageView)
            }
        }
        override fun getItemCount() = pagerFiles.size
    }
}
