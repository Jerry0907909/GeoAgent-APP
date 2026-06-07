package com.geoagent.data.local.search

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [TavilySearchCacheEntity::class],
    version = 1,
    exportSchema = false
)
abstract class SearchCacheDatabase : RoomDatabase() {
    abstract fun tavilySearchCacheDao(): TavilySearchCacheDao

    companion object {
        fun create(context: Context): SearchCacheDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                SearchCacheDatabase::class.java,
                "geoagent_search_cache.db"
            ).build()
    }
}
