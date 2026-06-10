package com.geoagent.data.local

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

class AccountStore(context: Context) {

    private val db: SQLiteDatabase

    companion object {
        private const val PBKDF2_ITERATIONS = 10000
        private const val PBKDF2_KEY_LENGTH = 256
        private const val SALT_LENGTH = 16

        fun hashPassword(password: String): String {
            val salt = ByteArray(SALT_LENGTH)
            SecureRandom().nextBytes(salt)
            val spec = PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITERATIONS, PBKDF2_KEY_LENGTH)
            val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            val hash = factory.generateSecret(spec).encoded
            val saltHex = salt.joinToString("") { "%02x".format(it) }
            val hashHex = hash.joinToString("") { "%02x".format(it) }
            return "$saltHex:$hashHex"
        }

        fun verifyPassword(password: String, stored: String): Boolean {
            val parts = stored.split(":")
            if (parts.size != 2) return false
            val salt = parts[0].chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            val spec = PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITERATIONS, PBKDF2_KEY_LENGTH)
            val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            val hash = factory.generateSecret(spec).encoded
            val hashHex = hash.joinToString("") { "%02x".format(it) }
            return hashHex == parts[1]
        }
    }

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
