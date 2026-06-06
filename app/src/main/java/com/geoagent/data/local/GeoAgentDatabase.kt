package com.geoagent.data.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class GeoAgentDatabase(context: Context) : SQLiteOpenHelper(
    context.applicationContext, "geoagent.db", null, 1
) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE documents (
                id TEXT PRIMARY KEY,
                name TEXT NOT NULL,
                file_type TEXT NOT NULL,
                size_bytes INTEGER NOT NULL,
                created_at INTEGER NOT NULL,
                chunk_count INTEGER NOT NULL DEFAULT 0
            )
        """)
        db.execSQL("""
            CREATE TABLE chunks (
                id TEXT PRIMARY KEY,
                document_id TEXT NOT NULL,
                chunk_index INTEGER NOT NULL,
                text TEXT NOT NULL,
                char_offset INTEGER NOT NULL,
                FOREIGN KEY (document_id) REFERENCES documents(id) ON DELETE CASCADE
            )
        """)
        db.execSQL("""
            CREATE TABLE embeddings (
                chunk_id TEXT PRIMARY KEY,
                document_id TEXT NOT NULL,
                vector_json TEXT NOT NULL,
                FOREIGN KEY (chunk_id) REFERENCES chunks(id) ON DELETE CASCADE,
                FOREIGN KEY (document_id) REFERENCES documents(id) ON DELETE CASCADE
            )
        """)
        db.execSQL("""
            CREATE TABLE messages (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                conversation_id INTEGER NOT NULL,
                role TEXT NOT NULL,
                content TEXT NOT NULL,
                timestamp INTEGER NOT NULL
            )
        """)
        db.execSQL("CREATE INDEX idx_chunks_doc ON chunks(document_id)")
        db.execSQL("CREATE INDEX idx_embeddings_doc ON embeddings(document_id)")
        db.execSQL("CREATE INDEX idx_messages_conv ON messages(conversation_id)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS embeddings")
        db.execSQL("DROP TABLE IF EXISTS chunks")
        db.execSQL("DROP TABLE IF EXISTS documents")
        db.execSQL("DROP TABLE IF EXISTS messages")
        onCreate(db)
    }
}
