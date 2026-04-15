package com.example.myapplication

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log

class DatabaseHelper(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "url_database.db"
        private const val DATABASE_VERSION = 2

        const val TABLE_BLACKLIST = "blacklist"
        const val TABLE_WHITELIST = "whitelist"
        const val COLUMN_ID = "id"
        const val COLUMN_URL = "url"
        const val COLUMN_TIMESTAMP = "timestamp"
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE $TABLE_BLACKLIST (" +
                    "$COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "$COLUMN_URL TEXT UNIQUE," +
                    "$COLUMN_TIMESTAMP INTEGER DEFAULT (strftime('%s','now') * 1000))"
        )

        db.execSQL(
            "CREATE TABLE $TABLE_WHITELIST (" +
                    "$COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "$COLUMN_URL TEXT UNIQUE," +
                    "$COLUMN_TIMESTAMP INTEGER DEFAULT (strftime('%s','now') * 1000))"
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_BLACKLIST")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_WHITELIST")
        onCreate(db)
    }

    override fun onOpen(db: SQLiteDatabase) {
        super.onOpen(db)
        try {
            deleteOldEntries(db)
        } catch (e: Exception) {
            Log.e("DatabaseHelper", "Error deleting old entries", e)
        }
    }

    fun insertUrl(tableName: String, url: String) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_URL, url)
            put(COLUMN_TIMESTAMP, System.currentTimeMillis())
        }
        db.insertWithOnConflict(tableName, null, values, SQLiteDatabase.CONFLICT_IGNORE)
        db.close()
    }

    fun isUrlExist(tableName: String, url: String): Boolean {
        val db = readableDatabase
        val query = "SELECT 1 FROM $tableName WHERE $COLUMN_URL=? LIMIT 1"
        val cursor: Cursor = db.rawQuery(query, arrayOf(url))
        val exists = cursor.count > 0
        cursor.close()
        db.close()
        return exists
    }

    fun deleteOldEntries(db: SQLiteDatabase) {
        val thirtyDaysAgo = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000)
        val whereClause = "$COLUMN_TIMESTAMP < ?"
        val args = arrayOf(thirtyDaysAgo.toString())
        db.delete(TABLE_BLACKLIST, whereClause, args)
        db.delete(TABLE_WHITELIST, whereClause, args)
        Log.d("DatabaseHelper", "Old entries deleted successfully.")
    }
}
