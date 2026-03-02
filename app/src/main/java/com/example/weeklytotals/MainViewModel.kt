package com.example.weeklytotals

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.viewModelScope
import com.example.weeklytotals.data.AppDatabase
import com.example.weeklytotals.data.BudgetPreferences
import com.example.weeklytotals.data.CategoryDao
import com.example.weeklytotals.data.CategoryEntity
import com.example.weeklytotals.data.Transaction
import com.example.weeklytotals.data.FirebaseSyncManager
import com.example.weeklytotals.data.TransactionDao
import com.example.weeklytotals.data.WeekCalculator
import com.example.weeklytotals.data.WeeklySavings
import com.example.weeklytotals.data.WeeklySavingsDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val dao: TransactionDao
    private val categoryDao: CategoryDao
    private val weeklySavingsDao: WeeklySavingsDao
    private val weekCalculator = WeekCalculator()
    private val budgetPreferences = BudgetPreferences(application)
    private val syncManager = FirebaseSyncManager.getInstance(application)

    val weekStartDate: String = weekCalculator.getCurrentWeekStart()
    val weekName: String = weekCalculator.getWeekName(weekStartDate)
    val budget: Double get() = budgetPreferences.getBudget()

    val transactions: LiveData<List<Transaction>>
    val weekTotal: LiveData<Double>
    val categories: LiveData<List<CategoryEntity>>

    init {
        val db = AppDatabase.getInstance(application)
        dao = db.transactionDao()
        categoryDao = db.categoryDao()
        weeklySavingsDao = db.weeklySavingsDao()
        transactions = dao.getTransactionsForWeek(weekStartDate)
        weekTotal = dao.getTotalForWeek(weekStartDate)
        categories = categoryDao.getAllCategories()
        syncManager.startListening()
        checkWeekRollover()
    }

    fun addTransaction(categoryName: String, amount: Double, details: String? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            val effectiveAmount = if (categoryName == "REFUND") -amount else amount
            val transaction = Transaction(
                weekStartDate = weekStartDate,
                category = categoryName,
                amount = effectiveAmount,
                details = details?.takeIf { it.isNotBlank() }
            )
            dao.insert(transaction)
            syncManager.pushTransaction(transaction)
        }
    }

    fun updateTransaction(transaction: Transaction) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.update(transaction)
            syncManager.pushTransaction(transaction)
        }
    }

    fun deleteTransaction(transaction: Transaction) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.delete(transaction)
            syncManager.deleteTransaction(transaction)
        }
    }

    private fun checkWeekRollover() {
        viewModelScope.launch(Dispatchers.IO) {
            // Wait for Firebase transaction sync so we calculate from up-to-date data.
            // Timeout after 5s to avoid hanging if offline.
            withTimeoutOrNull(5000L) {
                syncManager.initialTransactionSyncComplete.await()
            }

            // Run one-time bootstrap to backfill per-week savings from historical transactions
            bootstrapHistoricalSavings()

            val previousWeekStart = weekCalculator.getPreviousWeekStart(weekStartDate)
            val previousTotal = getPreviousWeekTotal(previousWeekStart)
            // Read budget BEFORE applying pending change so savings/overage
            // are calculated against the budget that was active last week.
            val previousBudget = budget

            // Apply any pending budget change (takes effect for the new week)
            budgetPreferences.applyPendingBudget()

            // Carry over previous week's overage as an adjustment
            if (previousTotal > previousBudget) {
                val overage = previousTotal - previousBudget
                val adjustment = Transaction(
                    weekStartDate = weekStartDate,
                    category = "ADJUSTMENT",
                    amount = overage,
                    isAdjustment = true
                )
                val id = dao.insertAdjustmentIfNotExists(adjustment)
                if (id != -1L) {
                    syncManager.pushTransaction(adjustment)
                }
            }

            // Accumulate savings: per-week row acts as the boolean guard
            if (weeklySavingsDao.getSavingsForWeek(previousWeekStart) == null) {
                val savingsAmount = if (previousTotal > 0 && previousTotal < previousBudget) {
                    previousBudget - previousTotal
                } else {
                    0.0 // over budget or zero spending — still insert to mark as processed
                }
                val record = WeeklySavings(weekStartDate = previousWeekStart, amount = savingsAmount)
                weeklySavingsDao.upsert(record)
                syncManager.pushSavings(record)
            }
        }
    }

    /**
     * One-time backfill: creates WeeklySavings rows for all historical weeks
     * that have transactions but no savings record yet.
     */
    private suspend fun bootstrapHistoricalSavings() {
        if (budgetPreferences.isSavingsBootstrapDone()) return

        val allTransactions = getAllLocalTransactions()
        if (allTransactions.isEmpty()) {
            budgetPreferences.setSavingsBootstrapDone()
            return
        }

        val currentBudget = budget
        // Group by weekStartDate, excluding the current week
        val weekTotals = allTransactions
            .filter { it.weekStartDate != weekStartDate }
            .groupBy { it.weekStartDate }
            .mapValues { (_, txns) -> txns.sumOf { it.amount } }

        for ((week, total) in weekTotals) {
            if (weeklySavingsDao.getSavingsForWeek(week) == null) {
                val savingsAmount = if (total > 0 && total < currentBudget) {
                    currentBudget - total
                } else {
                    0.0
                }
                val record = WeeklySavings(weekStartDate = week, amount = savingsAmount)
                weeklySavingsDao.upsert(record)
                syncManager.pushSavings(record)
            }
        }

        budgetPreferences.setSavingsBootstrapDone()
        Log.d("MainViewModel", "Savings bootstrap complete: ${weekTotals.size} weeks processed")
    }

    private fun getAllLocalTransactions(): List<Transaction> {
        val db = AppDatabase.getInstance(getApplication())
        val cursor = db.openHelper.readableDatabase.query(
            "SELECT id, weekStartDate, category, amount, isAdjustment, createdAt, details FROM transactions"
        )
        val results = mutableListOf<Transaction>()
        while (cursor.moveToNext()) {
            results.add(
                Transaction(
                    id = cursor.getLong(0),
                    weekStartDate = cursor.getString(1),
                    category = cursor.getString(2),
                    amount = cursor.getDouble(3),
                    isAdjustment = cursor.getInt(4) == 1,
                    createdAt = cursor.getLong(5),
                    details = if (cursor.isNull(6)) null else cursor.getString(6)
                )
            )
        }
        cursor.close()
        return results
    }

    private fun getPreviousWeekTotal(previousWeekStart: String): Double {
        val db = AppDatabase.getInstance(getApplication())
        val cursor = db.openHelper.readableDatabase.query(
            "SELECT COALESCE(SUM(amount), 0.0) FROM transactions WHERE weekStartDate = ?",
            arrayOf<Any>(previousWeekStart)
        )
        var total = 0.0
        if (cursor.moveToFirst()) {
            total = cursor.getDouble(0)
        }
        cursor.close()
        return total
    }
}
