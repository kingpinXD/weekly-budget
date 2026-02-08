package com.example.weeklytotals

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.weeklytotals.data.AppDatabase
import com.example.weeklytotals.data.CategoryDao
import com.example.weeklytotals.data.CategoryEntity
import com.example.weeklytotals.data.FirebaseSyncManager
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ManageCategoriesActivity : AppCompatActivity() {

    private val categoryDao: CategoryDao by lazy {
        AppDatabase.getInstance(this).categoryDao()
    }

    private val syncManager: FirebaseSyncManager by lazy {
        FirebaseSyncManager.getInstance(this)
    }

    private lateinit var adapter: CategoryListAdapter

    private val presetColors = listOf(
        ColorOption("Red", "#F44336"),
        ColorOption("Pink", "#E91E63"),
        ColorOption("Purple", "#9C27B0"),
        ColorOption("Blue", "#2196F3"),
        ColorOption("Cyan", "#00BCD4"),
        ColorOption("Green", "#4CAF50"),
        ColorOption("Lime", "#8BC34A"),
        ColorOption("Orange", "#FF9800"),
        ColorOption("Brown", "#795548"),
        ColorOption("Grey", "#607D8B")
    )

    data class ColorOption(val name: String, val hex: String)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_manage_categories)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.manage_categories_title)

        val recyclerView = findViewById<RecyclerView>(R.id.recyclerViewCategories)
        val buttonAdd = findViewById<MaterialButton>(R.id.buttonAddCategory)

        adapter = CategoryListAdapter(
            onEdit = { category -> showEditDialog(category) },
            onDelete = { category -> showDeleteDialog(category) }
        )
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        categoryDao.getUserCategories().observe(this) { categories ->
            adapter.setCategories(categories)
        }

        buttonAdd.setOnClickListener { showAddDialog() }
    }

    private fun getUsedColors(exclude: String? = null): Set<String> {
        return adapter.getCategories()
            .filter { it.color != exclude }
            .map { it.color }
            .toSet()
    }

    private fun getAvailableColors(exclude: String? = null): List<ColorOption> {
        val used = getUsedColors(exclude)
        return presetColors.filter { it.hex !in used }
    }

    private fun showAddDialog() {
        val available = getAvailableColors()
        if (available.isEmpty()) {
            Toast.makeText(this, R.string.no_colors_available, Toast.LENGTH_SHORT).show()
            return
        }

        val nameInput = EditText(this).apply {
            hint = getString(R.string.category_name_hint)
        }

        val colorSpinner = Spinner(this)
        val colorAdapter = ColorSpinnerAdapter(this, available)
        colorSpinner.adapter = colorAdapter

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(64, 32, 64, 0)
            addView(nameInput)
            addView(colorSpinner)
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.add_category_title)
            .setView(container)
            .setPositiveButton(R.string.save) { _, _ ->
                val displayName = nameInput.text.toString().trim()
                if (displayName.isEmpty()) {
                    Toast.makeText(this, R.string.category_name_required, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val selectedColor = available[colorSpinner.selectedItemPosition]
                val name = displayName.uppercase().replace(" ", "_")

                CoroutineScope(Dispatchers.IO).launch {
                    val existing = categoryDao.getCategoryByName(name)
                    if (existing != null) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@ManageCategoriesActivity, R.string.category_already_exists, Toast.LENGTH_SHORT).show()
                        }
                        return@launch
                    }
                    val newCategory = CategoryEntity(
                        name = name,
                        displayName = displayName,
                        color = selectedColor.hex
                    )
                    categoryDao.insert(newCategory)
                    syncManager.pushCategory(newCategory)
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showEditDialog(category: CategoryEntity) {
        val available = getAvailableColors(exclude = category.color)
        // Include current color as first option
        val currentColorOption = presetColors.find { it.hex == category.color }
            ?: ColorOption("Current", category.color)
        val colorOptions = listOf(currentColorOption) + available

        val nameInput = EditText(this).apply {
            setText(category.displayName)
            selectAll()
        }

        val colorSpinner = Spinner(this)
        val colorAdapter = ColorSpinnerAdapter(this, colorOptions)
        colorSpinner.adapter = colorAdapter

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(64, 32, 64, 0)
            addView(nameInput)
            addView(colorSpinner)
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.edit_category_title)
            .setView(container)
            .setPositiveButton(R.string.save) { _, _ ->
                val newDisplayName = nameInput.text.toString().trim()
                if (newDisplayName.isEmpty()) {
                    Toast.makeText(this, R.string.category_name_required, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val selectedColor = colorOptions[colorSpinner.selectedItemPosition]

                CoroutineScope(Dispatchers.IO).launch {
                    val updatedCategory = category.copy(
                        displayName = newDisplayName,
                        color = selectedColor.hex
                    )
                    categoryDao.update(updatedCategory)
                    syncManager.pushCategory(updatedCategory)
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showDeleteDialog(category: CategoryEntity) {
        AlertDialog.Builder(this)
            .setTitle(R.string.delete_category_title)
            .setMessage(getString(R.string.delete_category_message, category.displayName))
            .setPositiveButton(R.string.delete) { _, _ ->
                CoroutineScope(Dispatchers.IO).launch {
                    categoryDao.delete(category)
                    syncManager.deleteCategory(category)
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    // RecyclerView adapter for the category list
    private class CategoryListAdapter(
        private val onEdit: (CategoryEntity) -> Unit,
        private val onDelete: (CategoryEntity) -> Unit
    ) : RecyclerView.Adapter<CategoryListAdapter.ViewHolder>() {

        private var categories: List<CategoryEntity> = emptyList()

        fun setCategories(list: List<CategoryEntity>) {
            categories = list
            notifyDataSetChanged()
        }

        fun getCategories(): List<CategoryEntity> = categories

        class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val colorDot: View = itemView.findViewById(R.id.viewCategoryColor)
            val nameText: TextView = itemView.findViewById(R.id.textViewCategoryName)
            val editButton: ImageButton = itemView.findViewById(R.id.buttonEditCategory)
            val deleteButton: ImageButton = itemView.findViewById(R.id.buttonDeleteCategory)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_category, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val category = categories[position]
            holder.nameText.text = category.displayName

            val dotDrawable = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor(category.color))
            }
            holder.colorDot.background = dotDrawable

            holder.editButton.setOnClickListener { onEdit(category) }
            holder.deleteButton.setOnClickListener { onDelete(category) }
        }

        override fun getItemCount(): Int = categories.size
    }

    // Custom spinner adapter that shows a colored square next to the color name
    private class ColorSpinnerAdapter(
        private val activity: AppCompatActivity,
        private val colors: List<ColorOption>
    ) : ArrayAdapter<String>(activity, android.R.layout.simple_spinner_item, colors.map { it.name }) {

        init {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            return createColorView(position, convertView, parent)
        }

        override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
            return createColorView(position, convertView, parent)
        }

        @Suppress("UNUSED_PARAMETER")
        private fun createColorView(position: Int, convertView: View?, parent: ViewGroup): View {
            val row = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(16, 16, 16, 16)
            }

            val density = activity.resources.displayMetrics.density
            val squareSize = (20 * density).toInt()

            val colorSquare = View(activity).apply {
                layoutParams = LinearLayout.LayoutParams(squareSize, squareSize).apply {
                    marginEnd = (12 * density).toInt()
                }
                setBackgroundColor(Color.parseColor(colors[position].hex))
            }

            val label = TextView(activity).apply {
                text = colors[position].name
                textSize = 16f
            }

            row.addView(colorSquare)
            row.addView(label)
            return row
        }
    }
}
