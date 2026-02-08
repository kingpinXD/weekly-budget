package com.example.weeklytotals.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [Transaction::class, CategoryEntity::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun categoryDao(): CategoryDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS categories (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        displayName TEXT NOT NULL,
                        color TEXT NOT NULL,
                        isSystem INTEGER NOT NULL DEFAULT 0
                    )"""
                )
                db.execSQL("INSERT INTO categories (name, displayName, color, isSystem) VALUES ('GROCERY', 'Grocery', '#4CAF50', 0)")
                db.execSQL("INSERT INTO categories (name, displayName, color, isSystem) VALUES ('GAS', 'Gas', '#2196F3', 0)")
                db.execSQL("INSERT INTO categories (name, displayName, color, isSystem) VALUES ('ENTERTAINMENT', 'Entertainment', '#FF9800', 0)")
                db.execSQL("INSERT INTO categories (name, displayName, color, isSystem) VALUES ('TRAVEL', 'Travel', '#9C27B0', 0)")
                db.execSQL("INSERT INTO categories (name, displayName, color, isSystem) VALUES ('ADJUSTMENT', 'Adjustment', '#FF5722', 1)")
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "weekly_totals.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .addCallback(object : Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)
                            db.execSQL("INSERT INTO categories (name, displayName, color, isSystem) VALUES ('GROCERY', 'Grocery', '#4CAF50', 0)")
                            db.execSQL("INSERT INTO categories (name, displayName, color, isSystem) VALUES ('GAS', 'Gas', '#2196F3', 0)")
                            db.execSQL("INSERT INTO categories (name, displayName, color, isSystem) VALUES ('ENTERTAINMENT', 'Entertainment', '#FF9800', 0)")
                            db.execSQL("INSERT INTO categories (name, displayName, color, isSystem) VALUES ('TRAVEL', 'Travel', '#9C27B0', 0)")
                            db.execSQL("INSERT INTO categories (name, displayName, color, isSystem) VALUES ('ADJUSTMENT', 'Adjustment', '#FF5722', 1)")
                        }
                    })
                    .build().also { INSTANCE = it }
            }
        }
    }
}
