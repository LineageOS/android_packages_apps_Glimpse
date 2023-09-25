/*
 * SPDX-FileCopyrightText: 2023 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.glimpse.recyclerview

import androidx.recyclerview.widget.DiffUtil
import org.lineageos.glimpse.viewmodels.ThumbnailViewModel.DataType
import kotlin.reflect.safeCast

class ThumbnailCallback(
    private val oldArray: Array<DataType>,
    private val newArray: Array<DataType>,
) : DiffUtil.Callback() {
    override fun getOldListSize() = oldArray.size

    override fun getNewListSize() = newArray.size

    override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int) =
        DataType.Thumbnail::class.safeCast(oldArray[oldItemPosition])?.let { oldThumbnail ->
            DataType.Thumbnail::class.safeCast(newArray[newItemPosition])?.let { newThumbnail ->
                oldThumbnail.media.id == newThumbnail.media.id
            } ?: false
        } ?: DataType.DateHeader::class.safeCast(oldArray[oldItemPosition])?.let { oldDateHeader ->
            DataType.DateHeader::class.safeCast(newArray[newItemPosition])?.let { newDateHeader ->
                oldDateHeader.date == newDateHeader.date
            } ?: false
        } ?: false

    override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int) =
        DataType.Thumbnail::class.safeCast(oldArray[oldItemPosition])?.let { oldThumbnail ->
            DataType.Thumbnail::class.safeCast(newArray[newItemPosition])?.let { newThumbnail ->
                oldThumbnail.media.id == newThumbnail.media.id &&
                        oldThumbnail.media.dateModified == newThumbnail.media.dateModified
            } ?: false
        } ?: DataType.DateHeader::class.safeCast(oldArray[oldItemPosition])?.let { oldDateHeader ->
            DataType.DateHeader::class.safeCast(newArray[newItemPosition])?.let { newDateHeader ->
                oldDateHeader.date == newDateHeader.date
            } ?: false
        } ?: false
}
