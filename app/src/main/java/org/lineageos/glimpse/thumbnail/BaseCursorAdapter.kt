/*
 * SPDX-FileCopyrightText: 2023 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.glimpse.thumbnail

import android.annotation.SuppressLint
import android.database.Cursor
import androidx.recyclerview.widget.RecyclerView

abstract class BaseCursorAdapter<T : RecyclerView.ViewHolder> : RecyclerView.Adapter<T>() {
    protected var cursor: Cursor? = null

    override fun getItemCount() = cursor?.count ?: 0

    fun changeCursor(cursor: Cursor?) {
        swapCursor(cursor)
        onChangedCursor(cursor)
    }

    protected open fun onChangedCursor(cursor: Cursor?) {}

    @SuppressLint("NotifyDataSetChanged")
    private fun swapCursor(cursor: Cursor?) {
        if (this.cursor == cursor) {
            return
        }

        val oldCursor = this.cursor
        this.cursor = cursor

        cursor?.let {
            notifyDataSetChanged()
        }

        oldCursor?.close()
    }
}
