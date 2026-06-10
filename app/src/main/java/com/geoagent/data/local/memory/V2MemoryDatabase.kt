package com.geoagent.data.local.memory

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [V2MemoryEntity::class, V2TaskEntity::class],
    version = 2,
    exportSchema = false
)
abstract class V2MemoryDatabase : RoomDatabase() {
    abstract fun memoryDao(): V2MemoryDao

    companion object {
        fun create(context: Context): V2MemoryDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                V2MemoryDatabase::class.java,
                "geoagent_v2_memory.db"
            )
                .fallbackToDestructiveMigration(false)
                .build()
    }
}
