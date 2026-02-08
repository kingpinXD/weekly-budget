package com.example.weeklytotals

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.weeklytotals.data.AppDatabase
import com.example.weeklytotals.data.BudgetPreferences
import com.example.weeklytotals.data.FirebaseSyncManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.settings_title)

        val budgetPreferences = BudgetPreferences(this)

        val textViewCurrentBudget = findViewById<TextView>(R.id.textViewCurrentBudget)
        val editTextNewBudget = findViewById<TextInputEditText>(R.id.editTextNewBudget)
        val buttonUpdateBudget = findViewById<MaterialButton>(R.id.buttonUpdateBudget)

        textViewCurrentBudget.text = String.format("Current budget: $%.2f CAD", budgetPreferences.getBudget())

        val buttonHistory = findViewById<MaterialButton>(R.id.buttonHistory)
        buttonHistory.setOnClickListener {
            startActivity(Intent(this, HistoryActivity::class.java))
        }

        val buttonManageCategories = findViewById<MaterialButton>(R.id.buttonManageCategories)
        buttonManageCategories.setOnClickListener {
            startActivity(Intent(this, ManageCategoriesActivity::class.java))
        }

        // Auto Transactions toggle
        val switchAutoTransactions = findViewById<SwitchMaterial>(R.id.switchAutoTransactions)
        switchAutoTransactions.isChecked = budgetPreferences.isAutoTransactionsEnabled()
        switchAutoTransactions.setOnCheckedChangeListener { _, isChecked ->
            budgetPreferences.setAutoTransactionsEnabled(isChecked)
        }

        buttonUpdateBudget.setOnClickListener {
            val text = editTextNewBudget.text?.toString()?.trim() ?: ""
            val amount = text.toDoubleOrNull()

            if (amount == null || amount <= 0) {
                Toast.makeText(this, R.string.budget_update_error, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            budgetPreferences.setPendingBudget(amount)
            FirebaseSyncManager.getInstance(this).pushBudget(amount, true)
            Toast.makeText(this, R.string.budget_updated_message, Toast.LENGTH_SHORT).show()
            editTextNewBudget.text?.clear()
        }

        // Reset button
        findViewById<MaterialButton>(R.id.buttonReset).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(R.string.reset_confirm_title)
                .setMessage(R.string.reset_confirm_message)
                .setPositiveButton(R.string.reset_yes) { _, _ -> performReset() }
                .setNegativeButton(R.string.reset_no, null)
                .show()
        }
    }

    private fun performReset() {
        val db = AppDatabase.getInstance(this)
        val budgetPrefs = BudgetPreferences(this)
        val syncManager = FirebaseSyncManager.getInstance(this)

        lifecycleScope.launch {
            // Clear local DB
            withContext(Dispatchers.IO) {
                db.transactionDao().deleteAll()
                db.categoryDao().deleteAll()
            }

            // Clear SharedPreferences
            budgetPrefs.clearAll()

            // Clear Firebase RTDB — this propagates deletion to all synced devices
            syncManager.clearAllData {
                runOnUiThread {
                    Toast.makeText(this@SettingsActivity, R.string.reset_complete, Toast.LENGTH_SHORT).show()
                    // Go back to budget setup
                    val intent = Intent(this@SettingsActivity, BudgetSetupActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                }
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
