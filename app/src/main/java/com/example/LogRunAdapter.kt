package com.example

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class LogRunAdapter(
    private var items: List<LogRun>,
    private val isDark: Boolean,
    private val onItemClick: ((LogRun) -> Unit)? = null
) : RecyclerView.Adapter<LogRunAdapter.VH>() {

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tvTimestamp: TextView = view.findViewById(R.id.tvTimestamp)
        val tvMessages: TextView = view.findViewById(R.id.tvMessages)
        val tvSummary: TextView = view.findViewById(R.id.tvSummary)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_log_entry, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]

        // 1. Timestamp
        holder.tvTimestamp.text = item.timestamp
        holder.tvTimestamp.setTextColor(if (isDark) 0xFFFFFFFF.toInt() else 0xFF000000.toInt())
        holder.tvTimestamp.setBackgroundResource(
            if (isDark) R.drawable.log_pill_neutral_dark else R.drawable.log_pill_neutral_light
        )

        // 2. Messages (Join with newlines)
        if (item.messages.isEmpty()) {
            holder.tvMessages.visibility = View.GONE
        } else {
            holder.tvMessages.visibility = View.VISIBLE
            holder.tvMessages.text = item.messages.joinToString("\n")
            holder.tvMessages.setTextColor(if (isDark) 0xFFB0B0B0.toInt() else 0xFF555555.toInt())
        }

        // 3. Summary
        holder.tvSummary.text = item.summary
        val summaryColor = when (item.type) {
            LogType.SUCCESS -> if (isDark) 0xFF66BB6A.toInt() else 0xFF2E7D32.toInt()
            LogType.ERROR -> if (isDark) 0xFFEF5350.toInt() else 0xFFC62828.toInt()
            LogType.NEUTRAL -> if (isDark) 0xFFB0B0B0.toInt() else 0xFF555555.toInt()
        }
        holder.tvSummary.setTextColor(summaryColor)

        holder.itemView.setOnClickListener {
            onItemClick?.invoke(item)
        }
    }

    override fun getItemCount() = items.size

    fun update(newItems: List<LogRun>, dark: Boolean = isDark) {
        items = newItems
        notifyDataSetChanged()
    }
}
