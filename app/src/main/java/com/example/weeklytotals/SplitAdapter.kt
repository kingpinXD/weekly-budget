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
import com.example.weeklytotals.data.SplitEntry

class SplitAdapter(
    private val onItemClick: (SplitEntry) -> Unit,
    private val onItemLongClick: (SplitEntry) -> Unit
) : ListAdapter<SplitEntry, SplitAdapter.ViewHolder>(SplitEntryDiffCallback()) {

    private var categoryColors: Map<String, String> = emptyMap()
    private var categoryDisplayNames: Map<String, String> = emptyMap()
    private var userEmail: String = ""

    fun setMeta(colors: Map<String, String>, displayNames: Map<String, String>, email: String) {
        categoryColors = colors
        categoryDisplayNames = displayNames
        userEmail = email
        notifyDataSetChanged()
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val colorDot: View = itemView.findViewById(R.id.viewColorDot)
        val textCategory: TextView = itemView.findViewById(R.id.textViewCategory)
        val textComment: TextView = itemView.findViewById(R.id.textViewComment)
        val textAmount: TextView = itemView.findViewById(R.id.textViewAmount)
        val textBadge: TextView = itemView.findViewById(R.id.textViewBadge)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_split_entry, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val entry = getItem(position)

        // Category name
        holder.textCategory.text = categoryDisplayNames[entry.category] ?: entry.category
        holder.textCategory.setTypeface(null, Typeface.BOLD)

        // Comment
        holder.textComment.text = entry.comment

        // Color dot
        val colorHex = categoryColors[entry.category]
        val dotColor = if (colorHex != null) Color.parseColor(colorHex) else Color.GRAY
        val dotDrawable = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(dotColor)
        }
        holder.colorDot.background = dotDrawable

        // Amount with sign based on perspective
        val isMine = entry.createdByEmail == userEmail
        val displayAmount: String
        val amountColor: Int

        when (entry.splitType) {
            SplitEntry.TYPE_EQUAL -> {
                val half = entry.amount / 2.0
                if (isMine) {
                    displayAmount = String.format("+$%.2f", half)
                    amountColor = Color.parseColor("#4CAF50")
                } else {
                    displayAmount = String.format("-$%.2f", half)
                    amountColor = Color.parseColor("#F44336")
                }
            }
            SplitEntry.TYPE_I_OWE -> {
                if (isMine) {
                    displayAmount = String.format("-$%.2f", entry.amount)
                    amountColor = Color.parseColor("#F44336")
                } else {
                    displayAmount = String.format("+$%.2f", entry.amount)
                    amountColor = Color.parseColor("#4CAF50")
                }
            }
            SplitEntry.TYPE_THEY_OWE -> {
                if (isMine) {
                    displayAmount = String.format("+$%.2f", entry.amount)
                    amountColor = Color.parseColor("#4CAF50")
                } else {
                    displayAmount = String.format("-$%.2f", entry.amount)
                    amountColor = Color.parseColor("#F44336")
                }
            }
            SplitEntry.TYPE_SETTLEMENT -> {
                if (isMine) {
                    displayAmount = String.format("+$%.2f", entry.amount)
                    amountColor = Color.parseColor("#009688")
                } else {
                    displayAmount = String.format("-$%.2f", entry.amount)
                    amountColor = Color.parseColor("#009688")
                }
            }
            else -> {
                displayAmount = String.format("$%.2f", entry.amount)
                amountColor = Color.parseColor("#212121")
            }
        }
        holder.textAmount.text = displayAmount
        holder.textAmount.setTextColor(amountColor)

        // Badge
        val (badgeText, badgeColor) = when (entry.splitType) {
            SplitEntry.TYPE_EQUAL -> "EQUAL" to "#6C63FF"
            SplitEntry.TYPE_I_OWE -> "I OWE" to "#F44336"
            SplitEntry.TYPE_THEY_OWE -> "THEY OWE" to "#4CAF50"
            SplitEntry.TYPE_SETTLEMENT -> "SETTLE" to "#009688"
            else -> entry.splitType to "#607D8B"
        }
        holder.textBadge.text = badgeText
        val badgeDrawable = GradientDrawable().apply {
            cornerRadius = 8f * holder.itemView.resources.displayMetrics.density
            setColor(Color.parseColor(badgeColor))
        }
        holder.textBadge.background = badgeDrawable

        holder.itemView.setOnClickListener { onItemClick(entry) }
        holder.itemView.setOnLongClickListener {
            onItemLongClick(entry)
            true
        }
    }

    private class SplitEntryDiffCallback : DiffUtil.ItemCallback<SplitEntry>() {
        override fun areItemsTheSame(oldItem: SplitEntry, newItem: SplitEntry): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: SplitEntry, newItem: SplitEntry): Boolean {
            return oldItem == newItem
        }
    }
}
