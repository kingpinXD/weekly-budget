package com.example.weeklytotals.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [Transaction::class, CategoryEntity::class, WeeklySavings::class, SplitEntry::class, SplitCategory::class], version = 6, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun categoryDao(): CategoryDao
    abstract fun weeklySavingsDao(): WeeklySavingsDao
    abstract fun splitEntryDao(): SplitEntryDao
    abstract fun splitCategoryDao(): SplitCategoryDao

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

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS weekly_savings (
                        weekStartDate TEXT NOT NULL PRIMARY KEY,
                        amount REAL NOT NULL
                    )"""
                )
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS split_entries (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        category TEXT NOT NULL,
                        amount REAL NOT NULL,
                        comment TEXT NOT NULL,
                        splitType TEXT NOT NULL,
                        createdByEmail TEXT NOT NULL,
                        createdAt INTEGER NOT NULL
                    )"""
                )
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS split_categories (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        displayName TEXT NOT NULL,
                        color TEXT NOT NULL,
                        isSystem INTEGER NOT NULL DEFAULT 0
                    )"""
                )
                seedDefaultSplitCategories(db)
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

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Replace old split defaults with new ones
                db.execSQL("DELETE FROM split_categories WHERE name IN ('FOOD', 'GROCERIES', 'ENTERTAINMENT')")
                db.execSQL("INSERT INTO split_categories (name, displayName, color, isSystem) SELECT 'CREDIT_CARD', 'Credit Card', '#2196F3', 0 WHERE NOT EXISTS (SELECT 1 FROM split_categories WHERE name = 'CREDIT_CARD')")
            }
        }

        private fun seedDefaultSplitCategories(db: SupportSQLiteDatabase) {
            db.execSQL("INSERT INTO split_categories (name, displayName, color, isSystem) VALUES ('CREDIT_CARD', 'Credit Card', '#2196F3', 0)")
            db.execSQL("INSERT INTO split_categories (name, displayName, color, isSystem) VALUES ('TRAVEL', 'Travel', '#9C27B0', 0)")
            db.execSQL("INSERT INTO split_categories (name, displayName, color, isSystem) VALUES ('MISC', 'Misc', '#607D8B', 0)")
        }

        private fun ensureDefaultSplitCategories(db: SupportSQLiteDatabase) {
            db.execSQL("INSERT INTO split_categories (name, displayName, color, isSystem) SELECT 'CREDIT_CARD', 'Credit Card', '#2196F3', 0 WHERE NOT EXISTS (SELECT 1 FROM split_categories WHERE name = 'CREDIT_CARD')")
            db.execSQL("INSERT INTO split_categories (name, displayName, color, isSystem) SELECT 'TRAVEL', 'Travel', '#9C27B0', 0 WHERE NOT EXISTS (SELECT 1 FROM split_categories WHERE name = 'TRAVEL')")
            db.execSQL("INSERT INTO split_categories (name, displayName, color, isSystem) SELECT 'MISC', 'Misc', '#607D8B', 0 WHERE NOT EXISTS (SELECT 1 FROM split_categories WHERE name = 'MISC')")
        }

        val DEFAULT_SPLIT_CATEGORY_NAMES = setOf(
            "CREDIT_CARD", "TRAVEL", "MISC"
        )

        private fun ensureDefaultCategories(db: SupportSQLiteDatabase) {
            db.execSQL("INSERT INTO categories (name, displayName, color, isSystem) SELECT 'GAS', 'Gas', '#2196F3', 0 WHERE NOT EXISTS (SELECT 1 FROM categories WHERE name = 'GAS')")
            db.execSQL("INSERT INTO categories (name, displayName, color, isSystem) SELECT 'TRAVEL', 'Travel', '#9C27B0', 0 WHERE NOT EXISTS (SELECT 1 FROM categories WHERE name = 'TRAVEL')")
            db.execSQL("INSERT INTO categories (name, displayName, color, isSystem) SELECT 'DINING_OUT', 'Dining Out', '#FF9800', 0 WHERE NOT EXISTS (SELECT 1 FROM categories WHERE name = 'DINING_OUT')")
            db.execSQL("INSERT INTO categories (name, displayName, color, isSystem) SELECT 'ORDERING_IN', 'Ordering In', '#4CAF50', 0 WHERE NOT EXISTS (SELECT 1 FROM categories WHERE name = 'ORDERING_IN')")
            db.execSQL("INSERT INTO categories (name, displayName, color, isSystem) SELECT 'AMAZON', 'Amazon', '#FF5722', 0 WHERE NOT EXISTS (SELECT 1 FROM categories WHERE name = 'AMAZON')")
            db.execSQL("INSERT INTO categories (name, displayName, color, isSystem) SELECT 'MISC', 'Misc Purchases', '#607D8B', 0 WHERE NOT EXISTS (SELECT 1 FROM categories WHERE name = 'MISC')")
            db.execSQL("INSERT INTO categories (name, displayName, color, isSystem) SELECT 'ADJUSTMENT', 'Adjustment', '#F44336', 1 WHERE NOT EXISTS (SELECT 1 FROM categories WHERE name = 'ADJUSTMENT')")
            db.execSQL("INSERT INTO categories (name, displayName, color, isSystem) SELECT 'REFUND', 'Refund', '#009688', 0 WHERE NOT EXISTS (SELECT 1 FROM categories WHERE name = 'REFUND')")
        }

        val DEFAULT_CATEGORY_NAMES = setOf(
            "GAS", "TRAVEL", "DINING_OUT", "ORDERING_IN", "AMAZON", "MISC", "ADJUSTMENT", "REFUND"
        )

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
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                    .addCallback(object : Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)
                            seedDefaultCategories(db)
                            seedDefaultSplitCategories(db)
                        }
                        override fun onOpen(db: SupportSQLiteDatabase) {
                            super.onOpen(db)
                            ensureDefaultCategories(db)
                            ensureDefaultSplitCategories(db)
                        }
                    })
                    .build().also { INSTANCE = it }
            }
        }
    }
}
