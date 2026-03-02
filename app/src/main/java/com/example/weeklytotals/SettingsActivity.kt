package com.example.weeklytotals

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
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

    private lateinit var textViewNotificationStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.settings_title)

        findViewById<TextView>(R.id.textViewVersion).text = "v${BuildConfig.VERSION_NAME}"

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

        findViewById<MaterialButton>(R.id.buttonSavings).setOnClickListener {
            val db = AppDatabase.getInstance(this)
            lifecycleScope.launch {
                val savings = withContext(Dispatchers.IO) {
                    db.weeklySavingsDao().getTotalSavingsSync()
                }
                AlertDialog.Builder(this@SettingsActivity)
                    .setTitle(R.string.savings_title)
                    .setMessage(String.format(
                        "%s\n\n%s",
                        getString(R.string.savings_amount_format, savings),
                        getString(R.string.savings_description)
                    ))
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
            }
        }

        // Auto Transactions toggle
        val switchAutoTransactions = findViewById<SwitchMaterial>(R.id.switchAutoTransactions)
        switchAutoTransactions.isChecked = budgetPreferences.isAutoTransactionsEnabled()
        switchAutoTransactions.setOnCheckedChangeListener { _, isChecked ->
            budgetPreferences.setAutoTransactionsEnabled(isChecked)
            if (isChecked && !isNotificationAccessGranted()) {
                showNotificationAccessDialog()
            }
        }

        // Monitored Apps button
        findViewById<MaterialButton>(R.id.buttonMonitoredApps).setOnClickListener {
            startActivity(Intent(this, MonitoredAppsActivity::class.java))
        }

        // Notification status warning
        textViewNotificationStatus = findViewById(R.id.textViewNotificationStatus)
        updateNotificationStatus()

        // Currency spinner
        val spinnerCurrency = findViewById<Spinner>(R.id.spinnerInputCurrency)
        val currencies = arrayOf(getString(R.string.currency_cad), getString(R.string.currency_inr))
        val currencyAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, currencies)
        currencyAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerCurrency.adapter = currencyAdapter
        spinnerCurrency.setSelection(if (budgetPreferences.getInputCurrency() == "INR") 1 else 0)
        spinnerCurrency.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                budgetPreferences.setInputCurrency(if (position == 1) "INR" else "CAD")
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
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

    }

    override fun onResume() {
        super.onResume()
        updateNotificationStatus()
    }

    private fun isNotificationAccessGranted(): Boolean {
        return packageName in NotificationManagerCompat.getEnabledListenerPackages(this)
    }

    private fun updateNotificationStatus() {
        if (isNotificationAccessGranted()) {
            textViewNotificationStatus.visibility = View.GONE
        } else {
            textViewNotificationStatus.visibility = View.VISIBLE
        }
    }

    private fun showNotificationAccessDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.notification_access_title)
            .setMessage(R.string.notification_access_message)
            .setPositiveButton(R.string.open_settings) { _, _ ->
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
