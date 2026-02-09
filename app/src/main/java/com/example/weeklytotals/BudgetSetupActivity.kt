package com.example.weeklytotals

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.weeklytotals.data.AppDatabase
import com.example.weeklytotals.data.BudgetPreferences
import com.example.weeklytotals.data.CategoryEntity
import com.example.weeklytotals.data.FirebaseSyncManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BudgetSetupActivity : AppCompatActivity() {

    private val defaultCategories = listOf(
        CategoryEntity(name = "GAS", displayName = "Gas", color = "#2196F3", isSystem = false),
        CategoryEntity(name = "TRAVEL", displayName = "Travel", color = "#9C27B0", isSystem = false),
        CategoryEntity(name = "DINING_OUT", displayName = "Dining Out", color = "#FF9800", isSystem = false),
        CategoryEntity(name = "ORDERING_IN", displayName = "Ordering In", color = "#4CAF50", isSystem = false),
        CategoryEntity(name = "AMAZON", displayName = "Amazon", color = "#FF5722", isSystem = false),
        CategoryEntity(name = "MISC", displayName = "Misc Purchases", color = "#607D8B", isSystem = false),
        CategoryEntity(name = "ADJUSTMENT", displayName = "Adjustment", color = "#F44336", isSystem = true),
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_budget_setup)

        val editTextBudget = findViewById<TextInputEditText>(R.id.editTextBudget)
        val buttonSetBudget = findViewById<MaterialButton>(R.id.buttonSetBudget)

        buttonSetBudget.setOnClickListener {
            val text = editTextBudget.text?.toString()?.trim() ?: ""
            val amount = text.toDoubleOrNull()

            if (amount == null || amount <= 0) {
                Toast.makeText(this, R.string.budget_setup_error, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            BudgetPreferences(this).setBudget(amount)
            val syncManager = FirebaseSyncManager.getInstance(this)
            syncManager.pushBudget(amount, true)

            lifecycleScope.launch {
                val categoryDao = AppDatabase.getInstance(this@BudgetSetupActivity).categoryDao()

                // Insert any missing default categories
                withContext(Dispatchers.IO) {
                    val existing = categoryDao.getAllCategoriesSync().map { it.name }.toSet()
                    for (cat in defaultCategories) {
                        if (cat.name !in existing) {
                            categoryDao.insert(cat)
                        }
                    }
                }

                // Push all categories to Firebase
                val allCategories = withContext(Dispatchers.IO) {
                    categoryDao.getAllCategoriesSync()
                }
                for (cat in allCategories) {
                    syncManager.pushCategory(cat)
                }

                startActivity(Intent(this@BudgetSetupActivity, MainActivity::class.java))
                finish()
            }
        }
    }
}
