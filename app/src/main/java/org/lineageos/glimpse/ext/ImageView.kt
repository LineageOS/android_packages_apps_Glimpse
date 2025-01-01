/*
 * SPDX-FileCopyrightText: 2025 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.glimpse.ext

import android.widget.ImageView
import com.bumptech.glide.Glide
import org.lineageos.glimpse.models.Thumbnail

fun ImageView.load(model: Any?) = Glide.with(this)
    .load(model)
    .into(this)

fun ImageView.loadThumbnail(thumbnail: Thumbnail?) = load(thumbnail?.bitmap ?: thumbnail?.uri)
