package com.example.weeklytotals.data

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

@Dao
interface SplitEntryDao {
    @Insert
    suspend fun insert(entry: SplitEntry): Long

    @Update
    suspend fun update(entry: SplitEntry)

    @Delete
    suspend fun delete(entry: SplitEntry)

    @Query("SELECT * FROM split_entries ORDER BY createdAt DESC")
    fun getAllEntries(): LiveData<List<SplitEntry>>

    @Query("SELECT * FROM split_entries ORDER BY createdAt DESC")
    suspend fun getAllEntriesSync(): List<SplitEntry>

    @Query("SELECT * FROM split_entries WHERE createdAt = :createdAt LIMIT 1")
    suspend fun getEntryByCreatedAt(createdAt: Long): SplitEntry?

    @Query("DELETE FROM split_entries")
    suspend fun deleteAll()
}
