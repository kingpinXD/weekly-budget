package com.example.weeklytotals

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.AdapterView
import androidx.appcompat.app.AppCompatActivity
import com.example.weeklytotals.data.AppDatabase
import com.example.weeklytotals.data.CategoryTotal
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormatSymbols
import java.util.Calendar

class HistoryActivity : AppCompatActivity() {

    private lateinit var buttonMonth: MaterialButton
    private lateinit var buttonYear: MaterialButton
    private lateinit var spinnerPeriod: Spinner
    private lateinit var pieChartView: PieChartView
    private lateinit var textViewTotal: TextView
    private lateinit var layoutCategoryList: LinearLayout

    private val dao by lazy { AppDatabase.getInstance(this).transactionDao() }
    private val categoryDao by lazy { AppDatabase.getInstance(this).categoryDao() }

    private var isMonthMode = true
    private var availableYears: List<String> = emptyList()
    private var categoryColors: Map<String, Int> = emptyMap()
    private var categoryDisplayNames: Map<String, String> = emptyMap()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.history_title)

        buttonMonth = findViewById(R.id.buttonMonth)
        buttonYear = findViewById(R.id.buttonYear)
        spinnerPeriod = findViewById(R.id.spinnerPeriod)
        pieChartView = findViewById(R.id.pieChartView)
        textViewTotal = findViewById(R.id.textViewTotal)
        layoutCategoryList = findViewById(R.id.layoutCategoryList)

        buttonMonth.setOnClickListener { switchToMonthMode() }
        buttonYear.setOnClickListener { switchToYearMode() }

        spinnerPeriod.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                loadData()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // Load category colors from DB, then available years, then default to month mode
        CoroutineScope(Dispatchers.IO).launch {
            val allCategories = categoryDao.getAllCategoriesSync()
            categoryColors = allCategories.associate { it.name to Color.parseColor(it.color) }
            categoryDisplayNames = allCategories.associate { it.name to it.displayName }
            availableYears = dao.getDistinctYears()
            withContext(Dispatchers.Main) {
                switchToMonthMode()
            }
        }
    }

    private fun switchToMonthMode() {
        isMonthMode = true
        buttonMonth.setBackgroundColor(getColor(com.google.android.material.R.color.design_default_color_primary))
        buttonMonth.setTextColor(Color.WHITE)
        buttonYear.setBackgroundColor(Color.TRANSPARENT)
        buttonYear.setTextColor(getColor(com.google.android.material.R.color.design_default_color_primary))

        val months = DateFormatSymbols().months.filter { it.isNotEmpty() }
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, months)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerPeriod.adapter = adapter

        // Select current month
        val currentMonth = Calendar.getInstance().get(Calendar.MONTH)
        spinnerPeriod.setSelection(currentMonth)
    }

    private fun switchToYearMode() {
        isMonthMode = false
        buttonYear.setBackgroundColor(getColor(com.google.android.material.R.color.design_default_color_primary))
        buttonYear.setTextColor(Color.WHITE)
        buttonMonth.setBackgroundColor(Color.TRANSPARENT)
        buttonMonth.setTextColor(getColor(com.google.android.material.R.color.design_default_color_primary))

        val years = if (availableYears.isEmpty()) {
            listOf(Calendar.getInstance().get(Calendar.YEAR).toString())
        } else {
            availableYears
        }
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, years)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerPeriod.adapter = adapter
    }

    private fun loadData() {
        CoroutineScope(Dispatchers.IO).launch {
            val totals: List<CategoryTotal> = if (isMonthMode) {
                val monthIndex = spinnerPeriod.selectedItemPosition + 1
                val year = Calendar.getInstance().get(Calendar.YEAR)
                val yearMonth = String.format("%04d-%02d", year, monthIndex)
                dao.getCategoryTotalsForMonth(yearMonth)
            } else {
                val year = spinnerPeriod.selectedItem as String
                dao.getCategoryTotalsForYear(year)
            }

            withContext(Dispatchers.Main) {
                updateChart(totals)
            }
        }
    }

    private fun updateChart(totals: List<CategoryTotal>) {
        val slices = totals.map { ct ->
            PieChartView.Slice(
                label = categoryDisplayNames[ct.category] ?: ct.category,
                value = ct.total,
                color = categoryColors[ct.category] ?: Color.GRAY
            )
        }
        pieChartView.slices = slices

        val grandTotal = totals.sumOf { it.total }
        textViewTotal.text = String.format("Total: $%.2f CAD", grandTotal)

        layoutCategoryList.removeAllViews()
        for (ct in totals) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, 12, 0, 12)
            }

            // Color dot
            val dot = android.view.View(this).apply {
                val size = (16 * resources.displayMetrics.density).toInt()
                layoutParams = LinearLayout.LayoutParams(size, size).apply {
                    marginEnd = (12 * resources.displayMetrics.density).toInt()
                }
                setBackgroundColor(categoryColors[ct.category] ?: Color.GRAY)
            }

            val label = TextView(this).apply {
                text = categoryDisplayNames[ct.category] ?: ct.category
                textSize = 16f
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

            val amount = TextView(this).apply {
                text = String.format("$%.2f", ct.total)
                textSize = 16f
                setTextColor(Color.parseColor("#333333"))
            }

            row.addView(dot)
            row.addView(label)
            row.addView(amount)
            layoutCategoryList.addView(row)
        }

        if (totals.isEmpty()) {
            val empty = TextView(this).apply {
                text = getString(R.string.history_no_data)
                textSize = 14f
                gravity = Gravity.CENTER
                setTextColor(Color.parseColor("#999999"))
            }
            layoutCategoryList.addView(empty)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
