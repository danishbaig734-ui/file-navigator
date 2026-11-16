package com.example

import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class UnmatchedLogAdapter(
    private var items: List<UnmatchedEntry>,
    private var isDark: Boolean,
    private val onItemClick: ((UnmatchedEntry) -> Unit)? = null
) : RecyclerView.Adapter<UnmatchedLogAdapter.VH>() {

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tvTimestamp: TextView = view.findViewById(R.id.tvTimestamp)
        val tvCountBadge: TextView = view.findViewById(R.id.tvCountBadge)
        val tvFilePath: TextView = view.findViewById(R.id.tvFilePath)
        val tvReason: TextView = view.findViewById(R.id.tvReason)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_unmatched_log, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        val density = holder.itemView.context.resources.displayMetrics.density

        // 1. Card Background (#1E1E1E dark / #FFFFFF light)
        val cardColor = if (isDark) 0xFF1E1E1E.toInt() else 0xFFFFFFFF.toInt()
        val strokeColor = if (isDark) 0xFF2C2C2C.toInt() else 0xFFE5E5E5.toInt()
        holder.itemView.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 16f * density
            setColor(cardColor)
            setStroke((1f * density).toInt(), strokeColor)
        }

        // 2. Timestamp Pill
        holder.tvTimestamp.text = item.timestamp
        holder.tvTimestamp.setTextColor(if (isDark) 0xFFFFFFFF.toInt() else 0xFF000000.toInt())
        holder.tvTimestamp.setBackgroundResource(
            if (isDark) R.drawable.log_pill_neutral_dark else R.drawable.log_pill_neutral_light
        )

        // 3. Count Badge (orange pill)
        holder.tvCountBadge.text = "${item.count}×"
        holder.tvCountBadge.setTextColor(0xFFFFFFFF.toInt())
        holder.tvCountBadge.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 100f * density
            setColor(if (isDark) 0xFFFB8C00.toInt() else 0xFFF57C00.toInt())
        }

        // 4. File Path
        holder.tvFilePath.text = item.filePath
        holder.tvFilePath.setTextColor(if (isDark) 0xFFFFFFFF.toInt() else 0xFF000000.toInt())

        // 5. Reason
        if (item.reason.isBlank()) {
            holder.tvReason.visibility = View.GONE
        } else {
            holder.tvReason.visibility = View.VISIBLE
            holder.tvReason.text = item.reason
            holder.tvReason.setTextColor(if (isDark) 0xFF9E9E9E.toInt() else 0xFF666666.toInt())
        }

        holder.itemView.setOnClickListener {
            onItemClick?.invoke(item)
        }
    }

    override fun getItemCount() = items.size

    fun update(newItems: List<UnmatchedEntry>, dark: Boolean = isDark) {
        items = newItems
        isDark = dark
        notifyDataSetChanged()
    }
}
