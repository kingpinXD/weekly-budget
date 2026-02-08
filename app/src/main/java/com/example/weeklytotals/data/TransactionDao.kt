package com.example.weeklytotals.data

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

@Dao
interface TransactionDao {
    @Insert
    suspend fun insert(transaction: Transaction): Long

    @Update
    suspend fun update(transaction: Transaction)

    @Delete
    suspend fun delete(transaction: Transaction)

    @Query("SELECT * FROM transactions WHERE weekStartDate = :weekStart ORDER BY createdAt DESC")
    fun getTransactionsForWeek(weekStart: String): LiveData<List<Transaction>>

    @Query("SELECT COALESCE(SUM(amount), 0.0) FROM transactions WHERE weekStartDate = :weekStart")
    fun getTotalForWeek(weekStart: String): LiveData<Double>

    @Query("SELECT EXISTS(SELECT 1 FROM transactions WHERE weekStartDate = :weekStart AND isAdjustment = 1)")
    suspend fun hasAdjustmentForWeek(weekStart: String): Boolean

    @Query("SELECT category, SUM(amount) as total FROM transactions WHERE substr(weekStartDate, 1, 7) = :yearMonth AND isAdjustment = 0 GROUP BY category")
    suspend fun getCategoryTotalsForMonth(yearMonth: String): List<CategoryTotal>

    @Query("SELECT category, SUM(amount) as total FROM transactions WHERE substr(weekStartDate, 1, 4) = :year AND isAdjustment = 0 GROUP BY category")
    suspend fun getCategoryTotalsForYear(year: String): List<CategoryTotal>

    @Query("SELECT DISTINCT substr(weekStartDate, 1, 4) as year FROM transactions ORDER BY year DESC")
    suspend fun getDistinctYears(): List<String>
}
