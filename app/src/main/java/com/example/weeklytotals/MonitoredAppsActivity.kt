package com.example.weeklytotals

import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.weeklytotals.data.BudgetPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MonitoredAppsActivity : AppCompatActivity() {

    private lateinit var budgetPreferences: BudgetPreferences
    private lateinit var adapter: AppListAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_monitored_apps)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.monitored_apps_title)

        budgetPreferences = BudgetPreferences(this)
        val recyclerView = findViewById<RecyclerView>(R.id.recyclerViewApps)

        adapter = AppListAdapter(
            monitoredPackages = budgetPreferences.getMonitoredApps().toMutableSet(),
            onToggle = { packages -> budgetPreferences.setMonitoredApps(packages) }
        )
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        findViewById<EditText>(R.id.editTextSearch).addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                adapter.filter(s?.toString() ?: "")
            }
        })

        loadApps()
    }

    private fun loadApps() {
        CoroutineScope(Dispatchers.IO).launch {
            val pm = packageManager
            val apps = pm.getInstalledApplications(0)
                .filter { it.flags and ApplicationInfo.FLAG_SYSTEM == 0 }
                .filter { it.packageName != packageName }
                .map { AppItem(
                    packageName = it.packageName,
                    label = pm.getApplicationLabel(it).toString(),
                    info = it
                ) }
                .sortedBy { it.label.lowercase() }

            withContext(Dispatchers.Main) {
                adapter.setApps(apps)
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    data class AppItem(
        val packageName: String,
        val label: String,
        val info: ApplicationInfo
    )

    private class AppListAdapter(
        private val monitoredPackages: MutableSet<String>,
        private val onToggle: (Set<String>) -> Unit
    ) : RecyclerView.Adapter<AppListAdapter.ViewHolder>() {

        private var allApps: List<AppItem> = emptyList()
        private var apps: List<AppItem> = emptyList()
        private var query: String = ""

        fun setApps(list: List<AppItem>) {
            allApps = list
            applyFilter()
        }

        fun filter(text: String) {
            query = text.trim().lowercase()
            applyFilter()
        }

        private fun applyFilter() {
            apps = if (query.isEmpty()) {
                allApps
            } else {
                allApps.filter { it.label.lowercase().contains(query) }
            }
            notifyDataSetChanged()
        }

        class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val icon: ImageView = itemView.findViewById(R.id.imageViewAppIcon)
            val name: TextView = itemView.findViewById(R.id.textViewAppName)
            val packageName: TextView = itemView.findViewById(R.id.textViewPackageName)
            val checkbox: CheckBox = itemView.findViewById(R.id.checkBoxMonitored)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_monitored_app, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val app = apps[position]
            val pm = holder.itemView.context.packageManager

            holder.icon.setImageDrawable(pm.getApplicationIcon(app.info))
            holder.name.text = app.label
            holder.packageName.text = app.packageName
            holder.checkbox.isChecked = app.packageName in monitoredPackages

            val toggleAction = {
                if (app.packageName in monitoredPackages) {
                    monitoredPackages.remove(app.packageName)
                } else {
                    monitoredPackages.add(app.packageName)
                }
                holder.checkbox.isChecked = app.packageName in monitoredPackages
                onToggle(monitoredPackages.toSet())
            }

            holder.checkbox.setOnClickListener { toggleAction() }
            holder.itemView.setOnClickListener { toggleAction() }
        }

        override fun getItemCount(): Int = apps.size
    }
}
