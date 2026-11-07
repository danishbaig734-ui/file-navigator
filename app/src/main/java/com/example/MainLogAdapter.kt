package com.example

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

data class MainLogEntry(
    val timestamp: String,
    val message: String,
    val type: LogType
)

class MainLogAdapter(
    private var items: List<MainLogEntry>,
    private var isDark: Boolean,
    private val onItemClick: ((MainLogEntry) -> Unit)? = null
) : RecyclerView.Adapter<MainLogAdapter.VH>() {

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tvTimestamp: TextView = view.findViewById(R.id.tvTimestamp)
        val tvMessage: TextView = view.findViewById(R.id.tvMessage)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_main_log_entry, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]

        // 1. Time pill
        holder.tvTimestamp.text = item.timestamp
        val pillBg = when (item.type) {
            LogType.SUCCESS -> if (isDark) R.drawable.log_pill_success_dark else R.drawable.log_pill_success_light
            LogType.ERROR -> if (isDark) R.drawable.log_pill_error_dark else R.drawable.log_pill_error_light
            LogType.NEUTRAL -> if (isDark) R.drawable.log_pill_neutral_dark else R.drawable.log_pill_neutral_light
        }
        holder.tvTimestamp.setBackgroundResource(pillBg)

        val pillTextColor = when (item.type) {
            LogType.SUCCESS -> if (isDark) 0xFF81C784.toInt() else 0xFF155724.toInt()
            LogType.ERROR -> if (isDark) 0xFFE57373.toInt() else 0xFF721C24.toInt()
            LogType.NEUTRAL -> if (isDark) 0xFFE0E0E0.toInt() else 0xFF383D41.toInt()
        }
        holder.tvTimestamp.setTextColor(pillTextColor)

        // 2. Message
        holder.tvMessage.text = item.message
        val msgColor = when (item.type) {
            LogType.SUCCESS -> if (isDark) 0xFF72B08B.toInt() else 0xFF2E7D32.toInt()
            LogType.ERROR -> if (isDark) 0xFFEF5350.toInt() else 0xFFC62828.toInt()
            LogType.NEUTRAL -> if (isDark) 0xFFB0B0B0.toInt() else 0xFF555555.toInt()
        }
        holder.tvMessage.setTextColor(msgColor)

        holder.itemView.setOnClickListener {
            onItemClick?.invoke(item)
        }
    }

    override fun getItemCount() = items.size

    fun update(newItems: List<MainLogEntry>, dark: Boolean = isDark) {
        items = newItems
        isDark = dark
        notifyDataSetChanged()
    }
}
