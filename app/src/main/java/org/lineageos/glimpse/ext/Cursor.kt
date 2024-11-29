/*
 * SPDX-FileCopyrightText: 2023 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.glimpse.ext

import android.database.Cursor
import org.lineageos.glimpse.models.ColumnIndexCache

fun <T> Cursor?.mapEachRow(
    mapping: (ColumnIndexCache) -> T,
) = this?.use { cursor ->
    if (!cursor.moveToFirst()) {
        return@use emptyList<T>()
    }

    val columnIndexCache = ColumnIndexCache(cursor, cursor.columnNames)

    val data = mutableListOf<T>()
    do {
        data.add(mapping(columnIndexCache))
    } while (cursor.moveToNext())

    data.toList()
} ?: emptyList()
