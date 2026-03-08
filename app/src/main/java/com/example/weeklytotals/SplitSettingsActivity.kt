package com.example.weeklytotals

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.weeklytotals.data.AppDatabase
import com.example.weeklytotals.data.SplitEntry
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SplitSettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_split_settings)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.split_settings_title)

        // Manage Split Categories
        findViewById<MaterialButton>(R.id.buttonManageSplitCategories).setOnClickListener {
            startActivity(Intent(this, SplitManageCategoriesActivity::class.java))
        }

        // Load settlement history
        loadSettlementHistory()
    }

    private fun loadSettlementHistory() {
        val layoutHistory = findViewById<LinearLayout>(R.id.layoutSettlementHistory)
        val textViewNoSettlements = findViewById<TextView>(R.id.textViewNoSettlements)

        CoroutineScope(Dispatchers.IO).launch {
            val entries = AppDatabase.getInstance(this@SplitSettingsActivity)
                .splitEntryDao()
                .getAllEntriesSync()
                .filter { it.splitType == SplitEntry.TYPE_SETTLEMENT }

            withContext(Dispatchers.Main) {
                if (entries.isEmpty()) {
                    textViewNoSettlements.visibility = View.VISIBLE
                    return@withContext
                }

                textViewNoSettlements.visibility = View.GONE
                val dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())

                for (entry in entries) {
                    val row = TextView(this@SplitSettingsActivity).apply {
                        text = String.format(
                            "$%.2f — %s (%s)",
                            entry.amount,
                            entry.comment,
                            dateFormat.format(Date(entry.createdAt))
                        )
                        textSize = 14f
                        setPadding(0, 8, 0, 8)
                    }
                    layoutHistory.addView(row)
                }
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
