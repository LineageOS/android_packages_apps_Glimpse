/*
 * SPDX-FileCopyrightText: 2023-2024 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.glimpse.fragments

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.navigation.NavigationBarView
import org.lineageos.glimpse.R
import org.lineageos.glimpse.ext.getViewProperty
import org.lineageos.glimpse.models.AlbumType

class MainFragment : Fragment(R.layout.fragment_main) {
    // Views
    private val navigationBarView by getViewProperty<NavigationBarView>(R.id.navigationBarView)
    private val viewPager2 by getViewProperty<ViewPager2>(R.id.viewPager2)

    private val onPageChangeCallback by lazy {
        object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)

                navigationBarView.menu.getItem(position).isChecked = true
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // ViewPager2
        viewPager2.isUserInputEnabled = false
        viewPager2.adapter = object : FragmentStateAdapter(this) {
            override fun getItemCount() = fragments.size
            override fun createFragment(position: Int) = fragments[position]()
        }
        viewPager2.offscreenPageLimit = fragments.size
        viewPager2.registerOnPageChangeCallback(onPageChangeCallback)

        navigationBarView.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.reelsFragment -> {
                    viewPager2.currentItem = 0
                    true
                }

                R.id.albumsFragment -> {
                    viewPager2.currentItem = 1
                    false
                }

                R.id.libraryFragment -> {
                    viewPager2.currentItem = 2
                    true
                }

                else -> false
            }
        }
    }

    override fun onDestroyView() {
        // ViewPager2
        viewPager2.unregisterOnPageChangeCallback(onPageChangeCallback)
        viewPager2.adapter = null

        super.onDestroyView()
    }

    companion object {
        // Keep in sync with the NavigationBarView menu
        private val fragments = arrayOf(
            { AlbumFragment().apply { arguments = AlbumFragment.createBundle(AlbumType.REELS) } },
            { AlbumsFragment() },
            { LibraryFragment() },
        )
    }
}
