package com.example.weeklytotals

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.viewModelScope
import com.example.weeklytotals.data.AppDatabase
import com.example.weeklytotals.data.SplitCategory
import com.example.weeklytotals.data.SplitCategoryDao
import com.example.weeklytotals.data.SplitEntry
import com.example.weeklytotals.data.SplitEntryDao
import com.example.weeklytotals.data.SplitSyncManager
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SplitViewModel(application: Application) : AndroidViewModel(application) {

    private val entryDao: SplitEntryDao
    private val categoryDao: SplitCategoryDao
    private val syncManager = SplitSyncManager.getInstance(application)

    val currentUserEmail: String = FirebaseAuth.getInstance().currentUser?.email ?: ""

    val otherUserName: String = if (currentUserEmail == "sar.anwesha@gmail.com") "Tanu" else "Anu"

    val entries: LiveData<List<SplitEntry>>
    val categories: LiveData<List<SplitCategory>>

    init {
        val db = AppDatabase.getInstance(application)
        entryDao = db.splitEntryDao()
        categoryDao = db.splitCategoryDao()
        entries = entryDao.getAllEntries()
        categories = categoryDao.getAllCategories()
        syncManager.startListening()
    }

    /**
     * Compute balance from the current user's perspective.
     * Positive = other owes me; Negative = I owe them.
     */
    fun computeBalance(entries: List<SplitEntry>): Double {
        var balance = 0.0
        for (entry in entries) {
            val isMine = entry.createdByEmail == currentUserEmail
            when (entry.splitType) {
                SplitEntry.TYPE_EQUAL -> {
                    if (isMine) balance += entry.amount / 2.0
                    else balance -= entry.amount / 2.0
                }
                SplitEntry.TYPE_I_OWE -> {
                    // "I owe" is relative to whoever created it.
                    // If I created it: I owe the full amount → negative for me
                    // If they created it: they owe me → positive for me
                    if (isMine) balance -= entry.amount
                    else balance += entry.amount
                }
                SplitEntry.TYPE_THEY_OWE -> {
                    // "They owe" is relative to whoever created it.
                    // If I created it: they owe me → positive for me
                    // If they created it: I owe → negative for me
                    if (isMine) balance += entry.amount
                    else balance -= entry.amount
                }
                SplitEntry.TYPE_SETTLEMENT -> {
                    // Settlement: creator paid the other person.
                    // If I created it: I paid → positive (they owed me less now... actually settlement reduces debt)
                    // Settlement always reduces debt: creator paid → credit to creator
                    if (isMine) balance += entry.amount
                    else balance -= entry.amount
                }
            }
        }
        return balance
    }

    fun formatBalanceLabel(balance: Double): String {
        val app = getApplication<Application>()
        val absBalance = Math.abs(balance)
        return when {
            balance > 0.005 -> app.getString(R.string.split_balance_get, absBalance, otherUserName)
            balance < -0.005 -> app.getString(R.string.split_balance_owe, absBalance, otherUserName)
            else -> app.getString(R.string.split_balance_settled)
        }
    }

    fun addEntry(categoryName: String, amount: Double, comment: String, splitType: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val entry = SplitEntry(
                category = categoryName,
                amount = amount,
                comment = comment,
                splitType = splitType,
                createdByEmail = currentUserEmail
            )
            entryDao.insert(entry)
            syncManager.pushEntry(entry)
        }
    }

    fun updateEntry(entry: SplitEntry) {
        viewModelScope.launch(Dispatchers.IO) {
            entryDao.update(entry)
            syncManager.pushEntry(entry)
        }
    }

    fun deleteEntry(entry: SplitEntry) {
        viewModelScope.launch(Dispatchers.IO) {
            entryDao.delete(entry)
            syncManager.deleteEntry(entry)
        }
    }

    fun settleUp(amount: Double, comment: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val entry = SplitEntry(
                category = "SETTLEMENT",
                amount = amount,
                comment = comment,
                splitType = SplitEntry.TYPE_SETTLEMENT,
                createdByEmail = currentUserEmail
            )
            entryDao.insert(entry)
            syncManager.pushEntry(entry)
        }
    }
}
