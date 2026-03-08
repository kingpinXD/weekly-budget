package com.example.weeklytotals.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "split_entries")
data class SplitEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val category: String,
    val amount: Double,           // always positive
    val comment: String,          // required
    val splitType: String,        // EQUAL, I_OWE, THEY_OWE, SETTLEMENT
    val createdByEmail: String,   // who logged it
    val createdAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val TYPE_EQUAL = "EQUAL"
        const val TYPE_I_OWE = "I_OWE"
        const val TYPE_THEY_OWE = "THEY_OWE"
        const val TYPE_SETTLEMENT = "SETTLEMENT"
    }
}
