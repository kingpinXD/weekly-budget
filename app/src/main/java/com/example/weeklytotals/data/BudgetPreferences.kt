package com.example.weeklytotals.data

import android.content.Context

class BudgetPreferences(context: Context) {

    private val prefs = context.getSharedPreferences("weekly_totals_prefs", Context.MODE_PRIVATE)

    fun getBudget(): Double {
        return Double.fromBits(prefs.getLong(KEY_BUDGET, (0.0).toRawBits()))
    }

    fun setBudget(amount: Double) {
        prefs.edit()
            .putLong(KEY_BUDGET, amount.toRawBits())
            .putBoolean(KEY_IS_BUDGET_SET, true)
            .apply()
    }

    fun getPendingBudget(): Double? {
        if (!prefs.contains(KEY_PENDING_BUDGET)) return null
        return Double.fromBits(prefs.getLong(KEY_PENDING_BUDGET, 0L))
    }

    fun setPendingBudget(amount: Double) {
        prefs.edit()
            .putLong(KEY_PENDING_BUDGET, amount.toRawBits())
            .apply()
    }

    fun applyPendingBudget() {
        val pending = getPendingBudget() ?: return
        prefs.edit()
            .putLong(KEY_BUDGET, pending.toRawBits())
            .putBoolean(KEY_IS_BUDGET_SET, true)
            .remove(KEY_PENDING_BUDGET)
            .apply()
    }

    fun isBudgetSet(): Boolean {
        return prefs.getBoolean(KEY_IS_BUDGET_SET, false)
    }

    fun isAutoTransactionsEnabled(): Boolean {
        return prefs.getBoolean(KEY_AUTO_TRANSACTIONS, true)
    }

    fun setAutoTransactionsEnabled(enabled: Boolean) {
        prefs.edit()
            .putBoolean(KEY_AUTO_TRANSACTIONS, enabled)
            .apply()
    }

    fun clearAll() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val KEY_BUDGET = "budget"
        private const val KEY_PENDING_BUDGET = "pending_budget"
        private const val KEY_IS_BUDGET_SET = "is_budget_set"
        private const val KEY_AUTO_TRANSACTIONS = "auto_transactions_enabled"
    }
}
