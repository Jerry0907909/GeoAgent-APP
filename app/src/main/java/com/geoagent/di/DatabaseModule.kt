package com.geoagent.di

import org.koin.dsl.module

// Room is currently disabled due to KSP compatibility with Kotlin 2.0 + AGP 9.x.
// Conversation caching will use DataStore serialized JSON instead.
val databaseModule = module {
    // Placeholder for future Room database
}