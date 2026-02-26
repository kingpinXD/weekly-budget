package com.example.weeklytotals.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [Transaction::class, CategoryEntity::class], version = 3, exportSchema = false)
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
                seedDefaultCategories(db)
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE transactions ADD COLUMN details TEXT DEFAULT NULL")
                db.execSQL("INSERT INTO categories (name, displayName, color, isSystem) SELECT 'REFUND', 'Refund', '#009688', 0 WHERE NOT EXISTS (SELECT 1 FROM categories WHERE name = 'REFUND')")
            }
        }

        private fun seedDefaultCategories(db: SupportSQLiteDatabase) {
            db.execSQL("INSERT INTO categories (name, displayName, color, isSystem) VALUES ('GAS', 'Gas', '#2196F3', 0)")
            db.execSQL("INSERT INTO categories (name, displayName, color, isSystem) VALUES ('TRAVEL', 'Travel', '#9C27B0', 0)")
            db.execSQL("INSERT INTO categories (name, displayName, color, isSystem) VALUES ('DINING_OUT', 'Dining Out', '#FF9800', 0)")
            db.execSQL("INSERT INTO categories (name, displayName, color, isSystem) VALUES ('ORDERING_IN', 'Ordering In', '#4CAF50', 0)")
            db.execSQL("INSERT INTO categories (name, displayName, color, isSystem) VALUES ('AMAZON', 'Amazon', '#FF5722', 0)")
            db.execSQL("INSERT INTO categories (name, displayName, color, isSystem) VALUES ('MISC', 'Misc Purchases', '#607D8B', 0)")
            db.execSQL("INSERT INTO categories (name, displayName, color, isSystem) VALUES ('ADJUSTMENT', 'Adjustment', '#F44336', 1)")
            db.execSQL("INSERT INTO categories (name, displayName, color, isSystem) VALUES ('REFUND', 'Refund', '#009688', 0)")
        }

        fun reseedDefaultCategories(context: Context) {
            val db = getInstance(context).openHelper.writableDatabase
            seedDefaultCategories(db)
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "weekly_totals.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .addCallback(object : Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)
                            seedDefaultCategories(db)
                        }
                    })
                    .build().also { INSTANCE = it }
            }
        }
    }
}
