package com.example

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class LogEntryAdapter(
    private var items: List<LogEntry>,
    private var isDark: Boolean
) : RecyclerView.Adapter<LogEntryAdapter.VH>() {

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tvTimestamp: TextView = view.findViewById(R.id.tvTimestamp)
        val tvMessage: TextView = view.findViewById(R.id.tvMessage)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_log_entry, parent, false)
        return VH(v)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val entry = items[position]
        holder.tvTimestamp.text = entry.timestamp

        val pillBg = when (entry.type) {
            LogType.SUCCESS -> if (isDark) R.drawable.log_pill_success_dark else R.drawable.log_pill_success_light
            LogType.ERROR -> if (isDark) R.drawable.log_pill_error_dark else R.drawable.log_pill_error_light
            LogType.NEUTRAL -> if (isDark) R.drawable.log_pill_neutral_dark else R.drawable.log_pill_neutral_light
        }
        holder.tvTimestamp.setBackgroundResource(pillBg)

        val pillTextColor = if (isDark) {
            0xFFFFFFFF.toInt()
        } else {
            when (entry.type) {
                LogType.SUCCESS -> 0xFF1B5E20.toInt()
                LogType.ERROR -> 0xFFB71C1C.toInt()
                LogType.NEUTRAL -> 0xFF222222.toInt()
            }
        }
        holder.tvTimestamp.setTextColor(pillTextColor)

        holder.tvMessage.text = entry.message
        val messageColor = when (entry.type) {
            LogType.SUCCESS -> if (isDark) 0xFF4CAF50.toInt() else 0xFF2E7D32.toInt()
            LogType.ERROR -> if (isDark) 0xFFEF5350.toInt() else 0xFFC62828.toInt()
            LogType.NEUTRAL -> if (isDark) 0xFFA0A0A0.toInt() else 0xFF666666.toInt()
        }
        holder.tvMessage.setTextColor(messageColor)
    }

    fun update(newItems: List<LogEntry>, dark: Boolean) {
        items = newItems
        isDark = dark
        notifyDataSetChanged()
    }
}
