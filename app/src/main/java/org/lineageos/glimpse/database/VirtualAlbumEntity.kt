/*
 * SPDX-FileCopyrightText: 2025 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.glimpse.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "virtual_albums")
data class VirtualAlbumEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
)
