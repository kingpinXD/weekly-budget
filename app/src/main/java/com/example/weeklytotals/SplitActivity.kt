package com.example.weeklytotals

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import android.widget.ImageButton
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.weeklytotals.data.SplitCategory
import com.example.weeklytotals.data.SplitEntry
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButton

class SplitActivity : AppCompatActivity() {

    private val viewModel: SplitViewModel by viewModels()

    private var userCategories: List<SplitCategory> = emptyList()
    private var categoryColors: Map<String, String> = emptyMap()
    private var currentBalance = 0.0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_split)

        val textViewBalance = findViewById<TextView>(R.id.textViewBalance)
        val spinnerCategory = findViewById<Spinner>(R.id.spinnerSplitCategory)
        val editTextAmount = findViewById<EditText>(R.id.editTextSplitAmount)
        val editTextComment = findViewById<EditText>(R.id.editTextSplitComment)
        val buttonEqual = findViewById<MaterialButton>(R.id.buttonSplitEqual)
        val buttonIOwe = findViewById<MaterialButton>(R.id.buttonIOwe)
        val buttonTheyOwe = findViewById<MaterialButton>(R.id.buttonTheyOwe)
        val buttonSettleUp = findViewById<MaterialButton>(R.id.buttonSettleUp)
        val recyclerView = findViewById<RecyclerView>(R.id.recyclerViewSplitEntries)
        val textViewEmpty = findViewById<TextView>(R.id.textViewSplitEmpty)

        // Adapter
        val adapter = SplitAdapter(
            onItemClick = { entry -> showEditDialog(entry) },
            onItemLongClick = { entry -> showDeleteDialog(entry) }
        )
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        // Observe categories
        viewModel.categories.observe(this) { allCategories ->
            userCategories = allCategories.filter { !it.isSystem }
            categoryColors = allCategories.associate { it.name to it.color }

            val categoryNames = userCategories.map { it.displayName }
            val spinnerAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, categoryNames)
            spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spinnerCategory.adapter = spinnerAdapter

            val displayNames = allCategories.associate { it.name to it.displayName }
            adapter.setMeta(categoryColors, displayNames, viewModel.currentUserEmail)
        }

        // Observe entries
        viewModel.entries.observe(this) { entries ->
            adapter.submitList(entries)
            textViewEmpty.visibility = if (entries.isEmpty()) View.VISIBLE else View.GONE

            // Update balance
            currentBalance = viewModel.computeBalance(entries)
            textViewBalance.text = viewModel.formatBalanceLabel(currentBalance)

            // Show/hide settle up button
            buttonSettleUp.visibility = if (Math.abs(currentBalance) > 0.005) View.VISIBLE else View.GONE
        }

        // Add entry helper
        fun addEntry(splitType: String) {
            val text = editTextAmount.text.toString().trim()
            val amount = text.toDoubleOrNull()
            val comment = editTextComment.text.toString().trim()

            if (amount == null || amount <= 0) {
                Toast.makeText(this, R.string.budget_setup_error, Toast.LENGTH_SHORT).show()
                return
            }
            if (comment.isEmpty()) {
                Toast.makeText(this, R.string.split_comment_required, Toast.LENGTH_SHORT).show()
                return
            }
            if (userCategories.isEmpty()) return

            val selectedCategory = userCategories[spinnerCategory.selectedItemPosition]
            viewModel.addEntry(selectedCategory.name, amount, comment, splitType)
            editTextAmount.text.clear()
            editTextComment.text.clear()
        }

        buttonEqual.setOnClickListener { addEntry(SplitEntry.TYPE_EQUAL) }
        buttonIOwe.setOnClickListener { addEntry(SplitEntry.TYPE_I_OWE) }
        buttonTheyOwe.setOnClickListener { addEntry(SplitEntry.TYPE_THEY_OWE) }

        // Settle Up
        buttonSettleUp.setOnClickListener { showSettleUpDialog() }

        // Settings
        findViewById<ImageButton>(R.id.buttonSplitSettings).setOnClickListener {
            startActivity(Intent(this, SplitSettingsActivity::class.java))
        }

        // Bottom navigation — set selected BEFORE listener to avoid loop
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNavigation)
        bottomNav.selectedItemId = R.id.nav_split
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_weekly -> {
                    val intent = Intent(this, MainActivity::class.java)
                    intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                    startActivity(intent)
                    overridePendingTransition(0, 0)
                    true
                }
                R.id.nav_split -> true
                else -> false
            }
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        // Re-select split tab when returning via REORDER_TO_FRONT
        findViewById<BottomNavigationView>(R.id.bottomNavigation)?.selectedItemId = R.id.nav_split
    }

    private fun showSettleUpDialog() {
        val absBalance = Math.abs(currentBalance)

        val container = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(64, 32, 64, 0)
        }

        val editAmount = EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            setText(String.format("%.2f", absBalance))
            selectAll()
        }

        val editComment = EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_TEXT
            hint = getString(R.string.split_settle_comment_hint)
        }

        container.addView(editAmount)
        container.addView(editComment)

        AlertDialog.Builder(this)
            .setTitle(R.string.split_settle_title)
            .setView(container)
            .setPositiveButton(R.string.split_settle_up) { _, _ ->
                val amount = editAmount.text.toString().trim().toDoubleOrNull()
                val comment = editComment.text.toString().trim()

                if (amount == null || amount <= 0) {
                    Toast.makeText(this, R.string.budget_setup_error, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                if (comment.isEmpty()) {
                    Toast.makeText(this, R.string.split_comment_required, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                viewModel.settleUp(amount, comment)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showEditDialog(entry: SplitEntry) {
        val container = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(64, 32, 64, 0)
        }

        // Category spinner (only for non-settlement entries)
        val spinner: Spinner?
        if (entry.splitType == SplitEntry.TYPE_SETTLEMENT) {
            spinner = null
        } else {
            val categoryNames = userCategories.map { it.displayName }.toTypedArray()
            val currentCategoryIndex = userCategories.indexOfFirst { it.name == entry.category }.coerceAtLeast(0)

            spinner = Spinner(this)
            val spinnerAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, categoryNames)
            spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spinner.adapter = spinnerAdapter
            spinner.setSelection(currentCategoryIndex)
            container.addView(spinner)
        }

        val editAmount = EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            setText(String.format("%.2f", entry.amount))
            selectAll()
        }

        val editComment = EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_TEXT
            setText(entry.comment)
        }

        container.addView(editAmount)
        container.addView(editComment)

        AlertDialog.Builder(this)
            .setTitle(R.string.split_edit_title)
            .setView(container)
            .setPositiveButton(R.string.save) { _, _ ->
                val newAmount = editAmount.text.toString().trim().toDoubleOrNull()
                val newComment = editComment.text.toString().trim()

                if (newAmount == null || newAmount <= 0) return@setPositiveButton
                if (newComment.isEmpty()) {
                    Toast.makeText(this, R.string.split_comment_required, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val newCategory = if (spinner != null && userCategories.isNotEmpty()) {
                    userCategories[spinner.selectedItemPosition].name
                } else {
                    entry.category
                }

                viewModel.updateEntry(
                    entry.copy(
                        category = newCategory,
                        amount = newAmount,
                        comment = newComment
                    )
                )
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showDeleteDialog(entry: SplitEntry) {
        AlertDialog.Builder(this)
            .setTitle(R.string.split_delete_title)
            .setMessage(R.string.split_delete_message)
            .setPositiveButton(R.string.delete) { _, _ ->
                viewModel.deleteEntry(entry)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}
