package com.example

import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ErrorLogAdapter(
    private var items: List<ErrorEntry>,
    private var isDark: Boolean,
    private val onItemClick: ((ErrorEntry) -> Unit)? = null
) : RecyclerView.Adapter<ErrorLogAdapter.VH>() {

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tvTimestamp: TextView = view.findViewById(R.id.tvTimestamp)
        val tvErrorMessage: TextView = view.findViewById(R.id.tvErrorMessage)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_error_log, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        val density = holder.itemView.context.resources.displayMetrics.density

        // 1. Card Background (#1E1E1E dark / #FFFFFF light)
        val cardColor = if (isDark) 0xFF1E1E1E.toInt() else 0xFFFFFFFF.toInt()
        val strokeColor = if (isDark) 0xFF4A2024.toInt() else 0xFFFFCDD2.toInt()
        holder.itemView.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 16f * density
            setColor(cardColor)
            setStroke((1f * density).toInt(), strokeColor)
        }

        // 2. Timestamp Pill
        holder.tvTimestamp.text = item.timestamp
        holder.tvTimestamp.setTextColor(if (isDark) 0xFFE57373.toInt() else 0xFF721C24.toInt())
        holder.tvTimestamp.setBackgroundResource(
            if (isDark) R.drawable.log_pill_error_dark else R.drawable.log_pill_error_light
        )

        // 3. Error Message
        holder.tvErrorMessage.text = item.message
        holder.tvErrorMessage.setTextColor(if (isDark) 0xFFEF5350.toInt() else 0xFFC62828.toInt())

        holder.itemView.setOnClickListener {
            onItemClick?.invoke(item)
        }
    }

    override fun getItemCount() = items.size

    fun update(newItems: List<ErrorEntry>, dark: Boolean = isDark) {
        items = newItems
        isDark = dark
        notifyDataSetChanged()
    }
}
