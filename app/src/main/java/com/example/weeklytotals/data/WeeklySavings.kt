package com.example.weeklytotals.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "weekly_savings")
data class WeeklySavings(
    @PrimaryKey val weekStartDate: String,
    val amount: Double  // 0.0 if over budget (row still exists = processed)
)
