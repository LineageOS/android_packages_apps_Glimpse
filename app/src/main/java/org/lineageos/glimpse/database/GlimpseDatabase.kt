/*
 * SPDX-FileCopyrightText: 2025 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.glimpse.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import org.lineageos.glimpse.database.converters.UriConverter

@Database(
    entities = [
        VirtualAlbumEntity::class,
        VirtualAlbumMediaEntity::class,
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(
    UriConverter::class,
)
abstract class GlimpseDatabase : RoomDatabase() {
    abstract fun getVirtualAlbumDao(): VirtualAlbumDao

    companion object {
        @Volatile
        private var INSTANCE: GlimpseDatabase? = null

        fun getInstance(context: Context): GlimpseDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    GlimpseDatabase::class.java,
                    "glimpse_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
