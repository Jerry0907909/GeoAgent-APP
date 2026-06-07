package com.geoagent.data.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class GeoAgentDatabase(context: Context) : SQLiteOpenHelper(
    context.applicationContext, "geoagent.db", null, 2
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
        db.execSQL("""
            CREATE TABLE conversations (
                id INTEGER PRIMARY KEY,
                title TEXT,
                title_edited INTEGER NOT NULL DEFAULT 0,
                updated_at INTEGER NOT NULL
            )
        """)
        db.execSQL("CREATE INDEX idx_chunks_doc ON chunks(document_id)")
        db.execSQL("CREATE INDEX idx_embeddings_doc ON embeddings(document_id)")
        db.execSQL("CREATE INDEX idx_messages_conv ON messages(conversation_id)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS conversations (
                    id INTEGER PRIMARY KEY,
                    title TEXT,
                    title_edited INTEGER NOT NULL DEFAULT 0,
                    updated_at INTEGER NOT NULL
                )
            """)
            db.execSQL("""
                INSERT OR IGNORE INTO conversations (id, title, title_edited, updated_at)
                SELECT conversation_id, NULL, 0, MAX(timestamp)
                FROM messages
                GROUP BY conversation_id
            """)
        }
    }
}
