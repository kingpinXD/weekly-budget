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
        val textDetails: TextView = itemView.findViewById(R.id.textViewDetails)
        val textAmount: TextView = itemView.findViewById(R.id.textViewAmount)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_transaction, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val transaction = getItem(position)
        val isRefund = transaction.category == "REFUND"

        if (transaction.isAdjustment) {
            holder.textCategory.text = holder.itemView.context.getString(R.string.adjustment_label)
            holder.textCategory.setTypeface(null, Typeface.ITALIC)
            holder.textCategory.setTextColor(Color.parseColor("#FF6600"))
            holder.textAmount.setTextColor(Color.parseColor("#FF6600"))
        } else if (isRefund) {
            holder.textCategory.text = categoryDisplayNames[transaction.category] ?: "Refund"
            holder.textCategory.setTypeface(null, Typeface.BOLD)
            holder.textCategory.setTextColor(Color.parseColor("#009688"))
            holder.textAmount.setTextColor(Color.parseColor("#009688"))
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

        // Format amount: refunds show as "+$X.XX" with absolute value
        if (isRefund) {
            holder.textAmount.text = String.format("+$%.2f CAD", Math.abs(transaction.amount))
        } else {
            holder.textAmount.text = String.format("$%.2f CAD", transaction.amount)
        }

        // Details text (smaller font below category)
        if (!transaction.details.isNullOrBlank()) {
            holder.textDetails.text = transaction.details
            holder.textDetails.visibility = View.VISIBLE
        } else {
            holder.textDetails.visibility = View.GONE
        }

        holder.itemView.setOnClickListener {
            onItemClick(transaction)
        }

        holder.itemView.setOnLongClickListener {
            onItemLongClick(transaction)
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
