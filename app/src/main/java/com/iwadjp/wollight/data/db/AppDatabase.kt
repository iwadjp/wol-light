package com.iwadjp.wollight.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [DeviceEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun deviceDao(): DeviceDao
}
