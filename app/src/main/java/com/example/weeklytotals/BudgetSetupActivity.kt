package com.example.weeklytotals

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.weeklytotals.data.BudgetPreferences
import com.example.weeklytotals.data.FirebaseSyncManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText

class BudgetSetupActivity : AppCompatActivity() {

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
            FirebaseSyncManager.getInstance(this).pushBudget(amount, true)

            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }
}
