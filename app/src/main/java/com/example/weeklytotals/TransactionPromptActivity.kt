package com.example.weeklytotals

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.weeklytotals.data.AppDatabase
import com.example.weeklytotals.data.CategoryEntity
import com.example.weeklytotals.data.Transaction
import com.example.weeklytotals.data.WeekCalculator
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TransactionPromptActivity : AppCompatActivity() {

    private var userCategories: List<CategoryEntity> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_transaction_prompt)

        val amount = intent.getDoubleExtra(EXTRA_AMOUNT, 0.0)
        val rawMessage = intent.getStringExtra(EXTRA_RAW_MESSAGE) ?: ""

        val textViewRawMessage = findViewById<TextView>(R.id.textViewRawMessage)
        val editTextAmount = findViewById<TextInputEditText>(R.id.editTextAmount)
        val editTextDetails = findViewById<TextInputEditText>(R.id.editTextDetails)
        val spinnerCategory = findViewById<Spinner>(R.id.spinnerCategory)
        val buttonAdd = findViewById<MaterialButton>(R.id.buttonAdd)
        val buttonDismiss = findViewById<MaterialButton>(R.id.buttonDismiss)

        textViewRawMessage.text = rawMessage
        editTextAmount.setText(String.format("%.2f", amount))
        editTextAmount.selectAll()

        // Load categories from DB
        val db = AppDatabase.getInstance(this)
        CoroutineScope(Dispatchers.IO).launch {
            val allCategories = db.categoryDao().getAllCategoriesSync()
            userCategories = allCategories.filter { !it.isSystem }

            withContext(Dispatchers.Main) {
                val categoryNames = userCategories.map { it.displayName }
                val adapter = ArrayAdapter(
                    this@TransactionPromptActivity,
                    android.R.layout.simple_spinner_item,
                    categoryNames
                )
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                spinnerCategory.adapter = adapter
            }
        }

        buttonAdd.setOnClickListener {
            val text = editTextAmount.text?.toString()?.trim() ?: ""
            val parsedAmount = text.toDoubleOrNull()

            if (parsedAmount == null || parsedAmount <= 0) {
                Toast.makeText(this, R.string.budget_setup_error, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (userCategories.isEmpty()) {
                Toast.makeText(this, R.string.prompt_no_categories, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val selectedCategory = userCategories[spinnerCategory.selectedItemPosition]
            val weekCalculator = WeekCalculator()
            val isRefund = selectedCategory.name == "REFUND"
            val effectiveAmount = if (isRefund) -parsedAmount else parsedAmount
            val details = editTextDetails?.text?.toString()?.trim()?.takeIf { it.isNotBlank() }
            val transaction = Transaction(
                weekStartDate = weekCalculator.getCurrentWeekStart(),
                category = selectedCategory.name,
                amount = effectiveAmount,
                details = details
            )

            CoroutineScope(Dispatchers.IO).launch {
                db.transactionDao().insert(transaction)
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@TransactionPromptActivity,
                        getString(R.string.prompt_added, parsedAmount),
                        Toast.LENGTH_SHORT
                    ).show()
                    finish()
                }
            }
        }

        buttonDismiss.setOnClickListener {
            finish()
        }
    }

    companion object {
        const val EXTRA_AMOUNT = "extra_amount"
        const val EXTRA_RAW_MESSAGE = "extra_raw_message"
    }
}
