package com.geoagent.data.local

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class AccountStore(context: Context) {

    private val db: SQLiteDatabase

    init {
        val helper = object : SQLiteOpenHelper(context.applicationContext, "accounts.db", null, 1) {
            override fun onCreate(db: SQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE users (
                        email TEXT PRIMARY KEY,
                        password_hash TEXT NOT NULL,
                        username TEXT UNIQUE,
                        created_at INTEGER NOT NULL
                    )
                """)
            }
            override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
                db.execSQL("DROP TABLE IF EXISTS users")
                onCreate(db)
            }
        }
        db = helper.writableDatabase
    }

    data class StoredUser(val email: String, val passwordHash: String, val username: String?)

    fun saveUser(email: String, passwordHash: String, username: String? = null) {
        db.insertWithOnConflict("users", null, ContentValues().apply {
            put("email", email.trim())
            put("password_hash", passwordHash)
            put("username", username?.trim())
            put("created_at", System.currentTimeMillis())
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun getUserByEmail(email: String): StoredUser? {
        db.rawQuery("SELECT email, password_hash, username FROM users WHERE email = ?", arrayOf(email.trim())).use {
            if (it.moveToFirst()) {
                return StoredUser(
                    it.getString(0),
                    it.getString(1),
                    it.getString(2)
                )
            }
        }
        return null
    }

    fun getUserByUsername(username: String): StoredUser? {
        db.rawQuery("SELECT email, password_hash, username FROM users WHERE username = ?", arrayOf(username.trim())).use {
            if (it.moveToFirst()) {
                return StoredUser(
                    it.getString(0),
                    it.getString(1),
                    it.getString(2)
                )
            }
        }
        return null
    }

    fun getUser(email: String): StoredUser? = getUserByEmail(email)
}
