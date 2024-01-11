/*
 * SPDX-FileCopyrightText: 2023 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.glimpse.ext

import android.database.Cursor

fun <T> Cursor?.mapEachRow(
    projection: Array<String>,
    mapping: (Cursor, Array<Int>) -> T,
) = this?.use {
    if (!moveToFirst()) {
        return@use emptyList<T>()
    }

    val indexCache = projection.map {
        getColumnIndexOrThrow(it)
    }.toTypedArray()

    val data = mutableListOf<T>()
    do {
        data.add(mapping(this, indexCache))
    } while (moveToNext())

    data.toList()
} ?: emptyList()
