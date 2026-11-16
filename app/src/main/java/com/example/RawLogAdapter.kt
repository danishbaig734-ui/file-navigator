package com.example

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class RawLogAdapter(
    private val lines: List<String>,
    private val isDark: Boolean,
    private val isErrorTab: Boolean
) : RecyclerView.Adapter<RawLogAdapter.VH>() {

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tvLine: TextView = view.findViewById(R.id.tvRawLine)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_raw_log, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val line = lines[position]
        holder.tvLine.text = line

        val color = when {
            isErrorTab -> if (isDark) 0xFFEF5350.toInt() else 0xFFC62828.toInt()
            line.contains("[10×]") || line.contains("[1") -> if (isDark) 0xFFFFA726.toInt() else 0xFFF57C00.toInt()
            else -> if (isDark) 0xFFB0B0B0.toInt() else 0xFF555555.toInt()
        }
        holder.tvLine.setTextColor(color)
    }

    override fun getItemCount() = lines.size
}
