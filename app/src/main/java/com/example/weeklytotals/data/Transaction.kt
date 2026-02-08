package com.example.weeklytotals.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transactions")
data class Transaction(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val weekStartDate: String,   // "yyyy-MM-dd" of the Saturday
    val category: String,
    val amount: Double,
    val isAdjustment: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
