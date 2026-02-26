package com.example.weeklytotals

import android.app.Application
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val dao: TransactionDao
    private val categoryDao: CategoryDao
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
            // Apply any pending budget change
            budgetPreferences.applyPendingBudget()

            val previousWeekStart = weekCalculator.getPreviousWeekStart(weekStartDate)
            val previousTotal = getPreviousWeekTotal(previousWeekStart)
            val previousBudget = budget

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

            // Accumulate savings if under budget
            if (budgetPreferences.getLastSavingsProcessedWeek() != weekStartDate) {
                if (previousTotal > 0 && previousTotal < previousBudget) {
                    val savings = previousBudget - previousTotal
                    budgetPreferences.addToSavings(savings)
                }
                budgetPreferences.setLastSavingsProcessedWeek(weekStartDate)
            }
        }
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
