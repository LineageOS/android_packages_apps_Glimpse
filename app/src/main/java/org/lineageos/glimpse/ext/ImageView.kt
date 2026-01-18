/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.glimpse.ext

import android.widget.ImageView
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import org.lineageos.glimpse.models.Thumbnail

fun ImageView.load(
    model: Any?,
    options: RequestOptions = RequestOptions(),
) = Glide.with(this)
    .load(model)
    .apply(options)
    .into(this)

fun ImageView.loadThumbnail(
    thumbnail: Thumbnail?,
    options: RequestOptions = RequestOptions(),
) = load(thumbnail?.bitmap ?: thumbnail?.uri, options)
