package com.example.weeklytotals.data

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

@Dao
interface SplitCategoryDao {
    @Query("SELECT * FROM split_categories WHERE isSystem = 0 ORDER BY displayName")
    fun getUserCategories(): LiveData<List<SplitCategory>>

    @Query("SELECT * FROM split_categories ORDER BY displayName")
    fun getAllCategories(): LiveData<List<SplitCategory>>

    @Query("SELECT * FROM split_categories ORDER BY displayName")
    suspend fun getAllCategoriesSync(): List<SplitCategory>

    @Query("SELECT * FROM split_categories WHERE name = :name LIMIT 1")
    suspend fun getCategoryByName(name: String): SplitCategory?

    @Insert
    suspend fun insert(category: SplitCategory): Long

    @Update
    suspend fun update(category: SplitCategory)

    @Delete
    suspend fun delete(category: SplitCategory)

    @Query("DELETE FROM split_categories")
    suspend fun deleteAll()
}
