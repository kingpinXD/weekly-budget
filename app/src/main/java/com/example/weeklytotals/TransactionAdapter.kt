package com.example.weeklytotals

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.weeklytotals.data.Transaction

class TransactionAdapter(
    private val onItemClick: (Transaction) -> Unit,
    private val onItemLongClick: (Transaction) -> Unit
) : ListAdapter<Transaction, TransactionAdapter.ViewHolder>(TransactionDiffCallback()) {

    private var categoryColors: Map<String, String> = emptyMap()
    private var categoryDisplayNames: Map<String, String> = emptyMap()

    fun setCategoryColors(colors: Map<String, String>, displayNames: Map<String, String>) {
        categoryColors = colors
        categoryDisplayNames = displayNames
        notifyDataSetChanged()
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val colorDot: View = itemView.findViewById(R.id.viewColorDot)
        val textCategory: TextView = itemView.findViewById(R.id.textViewCategory)
        val textAmount: TextView = itemView.findViewById(R.id.textViewAmount)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_transaction, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val transaction = getItem(position)

        if (transaction.isAdjustment) {
            holder.textCategory.text = holder.itemView.context.getString(R.string.adjustment_label)
            holder.textCategory.setTypeface(null, Typeface.ITALIC)
            holder.textCategory.setTextColor(Color.parseColor("#FF6600"))
            holder.textAmount.setTextColor(Color.parseColor("#FF6600"))
        } else {
            holder.textCategory.text = categoryDisplayNames[transaction.category] ?: transaction.category
            holder.textCategory.setTypeface(null, Typeface.BOLD)
            holder.textCategory.setTextColor(Color.parseColor("#212121"))
            holder.textAmount.setTextColor(Color.parseColor("#212121"))
        }

        // Set color dot
        val colorHex = categoryColors[transaction.category]
        val dotColor = if (colorHex != null) Color.parseColor(colorHex) else Color.GRAY
        val dotDrawable = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(dotColor)
        }
        holder.colorDot.background = dotDrawable

        holder.textAmount.text = String.format("$%.2f CAD", transaction.amount)

        holder.itemView.setOnClickListener {
            if (!transaction.isAdjustment) {
                onItemClick(transaction)
            }
        }

        holder.itemView.setOnLongClickListener {
            if (!transaction.isAdjustment) {
                onItemLongClick(transaction)
            }
            true
        }
    }

    private class TransactionDiffCallback : DiffUtil.ItemCallback<Transaction>() {
        override fun areItemsTheSame(oldItem: Transaction, newItem: Transaction): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Transaction, newItem: Transaction): Boolean {
            return oldItem == newItem
        }
    }
}
