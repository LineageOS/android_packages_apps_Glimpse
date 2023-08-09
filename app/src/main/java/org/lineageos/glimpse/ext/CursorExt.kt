/*
 * SPDX-FileCopyrightText: 2023 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.glimpse.ext

import android.database.Cursor

fun <T> Cursor?.mapEachRow(mapping: (Cursor) -> T) = this?.use {
    if (!moveToFirst()) {
        emptyList<T>()
    }
    val data = mutableListOf<T>()
    while (!isAfterLast) {
        val element = mapping(this)
        data.add(element)
        moveToNext()
    }
    data.toList()
} ?: emptyList()
