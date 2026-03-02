package com.example.weeklytotals.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface WeeklySavingsDao {

    @Query("SELECT * FROM weekly_savings WHERE weekStartDate = :weekStart LIMIT 1")
    suspend fun getSavingsForWeek(weekStart: String): WeeklySavings?

    @Query("SELECT COALESCE(SUM(amount), 0.0) FROM weekly_savings")
    suspend fun getTotalSavingsSync(): Double

    @Query("SELECT * FROM weekly_savings ORDER BY weekStartDate ASC")
    suspend fun getAllSavingsSync(): List<WeeklySavings>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(savings: WeeklySavings)

    @Query("DELETE FROM weekly_savings")
    suspend fun deleteAll()
}
