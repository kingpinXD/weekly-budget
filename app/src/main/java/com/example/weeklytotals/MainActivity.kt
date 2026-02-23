package com.example.weeklytotals

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.weeklytotals.data.BudgetPreferences
import com.example.weeklytotals.data.CategoryEntity
import com.example.weeklytotals.data.Transaction
import com.google.android.material.button.MaterialButton
import com.google.firebase.database.FirebaseDatabase

class MainActivity : AppCompatActivity() {

    private val viewModel: MainViewModel by viewModels()

    private var userCategories: List<CategoryEntity> = emptyList()
    private var categoryColors: Map<String, String> = emptyMap()

    private val smsPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            val prefs = BudgetPreferences(this)
            prefs.setAutoTransactionsEnabled(false)
            Toast.makeText(this, R.string.sms_permission_required, Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Redirect to budget setup if budget is not set locally
        if (!BudgetPreferences(this).isBudgetSet()) {
            // Check Firebase first — another user may have already set the budget
            checkFirebaseBudget()
            return
        }

        initMainScreen()
    }

    private fun checkFirebaseBudget() {
        val budgetRef = FirebaseDatabase.getInstance().getReference("weekly_totals/budget")
        budgetRef.get().addOnSuccessListener { snapshot ->
            val amount = snapshot.child("amount").value?.let {
                when (it) {
                    is Double -> it
                    is Long -> it.toDouble()
                    else -> null
                }
            }
            val isSet = snapshot.child("isSet").value as? Boolean ?: false

            if (amount != null && isSet && amount > 0) {
                // Budget exists in Firebase — save locally and proceed
                BudgetPreferences(this).setBudget(amount)
                initMainScreen()
            } else {
                // No budget anywhere — go to setup
                startActivity(Intent(this, BudgetSetupActivity::class.java))
                finish()
            }
        }.addOnFailureListener {
            // Network error — fall back to setup screen
            startActivity(Intent(this, BudgetSetupActivity::class.java))
            finish()
        }
    }

    private fun initMainScreen() {
        setContentView(R.layout.activity_main)

        val textViewWeekName = findViewById<TextView>(R.id.textViewWeekName)
        val budgetGaugeView = findViewById<BudgetGaugeView>(R.id.budgetGaugeView)
        val spinnerCategory = findViewById<Spinner>(R.id.spinnerCategory)
        val editTextAmount = findViewById<EditText>(R.id.editTextAmount)
        val buttonAdd = findViewById<MaterialButton>(R.id.buttonAdd)
        val recyclerView = findViewById<RecyclerView>(R.id.recyclerViewTransactions)
        val textViewEmpty = findViewById<TextView>(R.id.textViewEmpty)

        // Header
        textViewWeekName.text = viewModel.weekName

        // Tap gauge to open spending history
        budgetGaugeView.setOnClickListener {
            startActivity(Intent(this, HistoryActivity::class.java))
        }

        // Transaction list
        val adapter = TransactionAdapter(
            onItemClick = { transaction -> showEditDialog(transaction) },
            onItemLongClick = { transaction -> showDeleteDialog(transaction) }
        )
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        // Observe categories and update spinner reactively
        viewModel.categories.observe(this) { allCategories ->
            userCategories = allCategories.filter { !it.isSystem }
            categoryColors = allCategories.associate { it.name to it.color }

            val categoryNames = userCategories.map { it.displayName }
            val spinnerAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, categoryNames)
            spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spinnerCategory.adapter = spinnerAdapter

            val displayNames = allCategories.associate { it.name to it.displayName }
            adapter.setCategoryColors(categoryColors, displayNames)
        }

        // Observe transactions
        viewModel.transactions.observe(this) { transactions ->
            adapter.submitList(transactions)
            textViewEmpty.visibility = if (transactions.isEmpty()) View.VISIBLE else View.GONE
        }

        // Observe week total and update budget gauge
        viewModel.weekTotal.observe(this) { total ->
            budgetGaugeView.spent = total ?: 0.0
            budgetGaugeView.budget = viewModel.budget
        }

        // Add button
        buttonAdd.setOnClickListener {
            val text = editTextAmount.text.toString().trim()
            val amount = text.toDoubleOrNull()

            if (amount == null || amount <= 0) {
                Toast.makeText(this, R.string.budget_setup_error, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (userCategories.isEmpty()) return@setOnClickListener

            val selectedCategory = userCategories[spinnerCategory.selectedItemPosition]
            viewModel.addTransaction(selectedCategory.name, amount)
            editTextAmount.text.clear()
        }

        // Settings button
        findViewById<ImageButton>(R.id.buttonSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        // Request SMS permission if auto-transactions is enabled
        requestSmsPermissionIfNeeded()
    }

    private fun requestSmsPermissionIfNeeded() {
        val prefs = BudgetPreferences(this)
        if (!prefs.isAutoTransactionsEnabled()) return

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            smsPermissionLauncher.launch(Manifest.permission.RECEIVE_SMS)
        }
    }

    private fun showEditDialog(transaction: Transaction) {
        val editText = EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            setText(String.format("%.2f", transaction.amount))
            selectAll()
        }

        val container = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(64, 32, 64, 0)
        }

        // Adjustments only allow editing the amount (category is fixed)
        val spinner: Spinner?
        if (transaction.isAdjustment) {
            spinner = null
            container.addView(editText)
        } else {
            val categoryNames = userCategories.map { it.displayName }.toTypedArray()
            val currentCategoryIndex = userCategories.indexOfFirst { it.name == transaction.category }.coerceAtLeast(0)

            spinner = Spinner(this)
            val spinnerAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, categoryNames)
            spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spinner.adapter = spinnerAdapter
            spinner.setSelection(currentCategoryIndex)

            container.addView(spinner)
            container.addView(editText)
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.edit_title)
            .setView(container)
            .setPositiveButton(R.string.save) { _, _ ->
                val newAmount = editText.text.toString().trim().toDoubleOrNull()
                if (newAmount != null && newAmount > 0) {
                    if (transaction.isAdjustment) {
                        viewModel.updateTransaction(transaction.copy(amount = newAmount))
                    } else if (userCategories.isNotEmpty() && spinner != null) {
                        val newCategory = userCategories[spinner.selectedItemPosition]
                        viewModel.updateTransaction(
                            transaction.copy(category = newCategory.name, amount = newAmount)
                        )
                    }
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showDeleteDialog(transaction: Transaction) {
        AlertDialog.Builder(this)
            .setTitle(R.string.delete_title)
            .setMessage(R.string.delete_message)
            .setPositiveButton(R.string.delete) { _, _ ->
                viewModel.deleteTransaction(transaction)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}
