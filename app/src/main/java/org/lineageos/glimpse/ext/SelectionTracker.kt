/*
 * SPDX-FileCopyrightText: 2024 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.glimpse.ext

import androidx.recyclerview.selection.DefaultSelectionTracker
import androidx.recyclerview.selection.ItemKeyProvider
import androidx.recyclerview.selection.SelectionTracker
import androidx.recyclerview.selection.SelectionTracker.SelectionObserver
import androidx.recyclerview.widget.RecyclerView

private val <K> SelectionTracker<K>.observers: ArrayList<SelectionObserver<K>>
    get() = DefaultSelectionTracker::class.java.getDeclaredField("mObservers").let {
        it.isAccessible = true
        it.get(this) as ArrayList<SelectionObserver<K>>
    }

private val <K> SelectionTracker<K>.adapterDataObserverExt: RecyclerView.AdapterDataObserver
    get() = SelectionTracker::class.java.getDeclaredMethod("getAdapterDataObserver").let {
        it.isAccessible = true
        it.invoke(this) as RecyclerView.AdapterDataObserver
    }

private var <K> SelectionTracker<K>.keyProvider: ItemKeyProvider<K>
    get() = DefaultSelectionTracker::class.java.getDeclaredField("mKeyProvider").let {
        it.isAccessible = true
        it.get(this) as ItemKeyProvider<K>
    }
    set(value) {
        DefaultSelectionTracker::class.java.getDeclaredField("mKeyProvider").let {
            it.isAccessible = true
            it.set(this, value)
        }
    }

/**
 * Destroy this object.
 */
fun <K> SelectionTracker<K>.kill(adapter: RecyclerView.Adapter<*>) {
    adapter.unregisterAdapterDataObserver(adapterDataObserverExt)

    observers.clear()

    keyProvider = object : ItemKeyProvider<K>(SCOPE_CACHED) {
        override fun getKey(position: Int) = null

        override fun getPosition(key: K & Any) = RecyclerView.NO_POSITION
    }
}
