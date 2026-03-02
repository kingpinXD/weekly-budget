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

    fun getTotalSavings(): Double {
        return Double.fromBits(prefs.getLong(KEY_TOTAL_SAVINGS, (0.0).toRawBits()))
    }

    fun addToSavings(amount: Double) {
        val current = getTotalSavings()
        prefs.edit()
            .putLong(KEY_TOTAL_SAVINGS, (current + amount).toRawBits())
            .apply()
    }

    fun getLastSavingsProcessedWeek(): String? {
        return prefs.getString(KEY_LAST_SAVINGS_WEEK, null)
    }

    fun setLastSavingsProcessedWeek(weekStartDate: String) {
        prefs.edit()
            .putString(KEY_LAST_SAVINGS_WEEK, weekStartDate)
            .apply()
    }

    fun isSavingsBootstrapDone(): Boolean {
        return prefs.getBoolean(KEY_SAVINGS_BOOTSTRAP_DONE, false)
    }

    fun setSavingsBootstrapDone() {
        prefs.edit()
            .putBoolean(KEY_SAVINGS_BOOTSTRAP_DONE, true)
            .apply()
    }

    fun getMonitoredApps(): Set<String> {
        return prefs.getStringSet(KEY_MONITORED_APPS, emptySet()) ?: emptySet()
    }

    fun setMonitoredApps(packages: Set<String>) {
        prefs.edit()
            .putStringSet(KEY_MONITORED_APPS, packages)
            .apply()
    }

    fun getInputCurrency(): String {
        return prefs.getString(KEY_INPUT_CURRENCY, "CAD") ?: "CAD"
    }

    fun setInputCurrency(currency: String) {
        prefs.edit()
            .putString(KEY_INPUT_CURRENCY, currency)
            .apply()
    }

    fun convertToCad(amount: Double): Double {
        return if (getInputCurrency() == "INR") amount / INR_TO_CAD_RATE else amount
    }

    fun clearAll() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val KEY_BUDGET = "budget"
        private const val KEY_PENDING_BUDGET = "pending_budget"
        private const val KEY_IS_BUDGET_SET = "is_budget_set"
        private const val KEY_AUTO_TRANSACTIONS = "auto_transactions_enabled"
        private const val KEY_TOTAL_SAVINGS = "total_savings"
        private const val KEY_LAST_SAVINGS_WEEK = "last_savings_processed_week"
        private const val KEY_SAVINGS_BOOTSTRAP_DONE = "savings_bootstrap_done"
        private const val KEY_MONITORED_APPS = "monitored_app_packages"
        private const val KEY_INPUT_CURRENCY = "input_currency"
        const val INR_TO_CAD_RATE = 61.5
    }
}
