package com.example.weeklytotals.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "split_categories")
data class SplitCategory(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val displayName: String,
    val color: String,
    val isSystem: Boolean = false
)
