package com.example

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView

open class LogRunAdapter(
    protected var items: List<LogRun>,
    protected var isDark: Boolean,
    private val onItemLongClick: ((LogRun) -> Unit)? = null
) : RecyclerView.Adapter<LogRunAdapter.VH>() {

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tvTimestamp: TextView = view.findViewById(R.id.tvTimestamp)
        val tvMessages: TextView = view.findViewById(R.id.tvMessages)
        val tvSummary: TextView = view.findViewById(R.id.tvSummary)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_log_entry, parent, false)
        return VH(v)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]

        // 1. Timestamp Pill
        holder.tvTimestamp.text = item.timestamp
        val pillRes = when (item.type) {
            LogType.SUCCESS -> if (isDark) R.drawable.log_pill_success_dark else R.drawable.log_pill_success_light
            LogType.ERROR   -> if (isDark) R.drawable.log_pill_error_dark   else R.drawable.log_pill_error_light
            LogType.NEUTRAL -> if (isDark) R.drawable.log_pill_neutral_dark else R.drawable.log_pill_neutral_light
        }
        holder.tvTimestamp.setBackgroundResource(pillRes)
        holder.tvTimestamp.setTextColor(if (isDark) 0xFFFFFFFF.toInt() else 0xFF000000.toInt())

        // 2. Messages (joined by newlines). If empty, hide tvMessages
        if (item.messages.isNotEmpty()) {
            holder.tvMessages.visibility = View.VISIBLE
            holder.tvMessages.text = item.messages.joinToString("\n")
        } else {
            holder.tvMessages.visibility = View.GONE
        }

        val msgColor = when (item.type) {
            LogType.SUCCESS -> if (isDark) 0xFF66BB6A.toInt() else 0xFF2E7D32.toInt()
            LogType.ERROR   -> if (isDark) 0xFFEF5350.toInt() else 0xFFC62828.toInt()
            LogType.NEUTRAL -> if (isDark) 0xFFB0B0B0.toInt() else 0xFF555555.toInt()
        }
        holder.tvMessages.setTextColor(msgColor)

        // 3. Summary (Done line)
        if (item.summary.isNotBlank()) {
            holder.tvSummary.visibility = View.VISIBLE
            holder.tvSummary.text = item.summary
            holder.tvSummary.setTextColor(msgColor)
        } else {
            holder.tvSummary.visibility = View.GONE
        }

        // Rounded card background styling
        val cardBg = if (isDark) 0xFF1C1C1C.toInt() else 0xFFF0F0F0.toInt()
        (holder.itemView.background?.mutate() as? GradientDrawable)?.setColor(cardBg)

        // Long click copy logic
        holder.itemView.setOnLongClickListener { view ->
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            if (onItemLongClick != null) {
                onItemLongClick.invoke(item)
            } else {
                val clipboard = view.context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val textToCopy = buildString {
                    if (item.timestamp.isNotBlank() && item.timestamp != "—") {
                        append(item.timestamp).append("\n")
                    }
                    if (item.messages.isNotEmpty()) {
                        append(item.messages.joinToString("\n")).append("\n")
                    }
                    if (item.summary.isNotBlank()) {
                        append(item.summary)
                    }
                }.trim()
                clipboard.setPrimaryClip(ClipData.newPlainText("Log Run", textToCopy))
                Toast.makeText(view.context, "Log copied to clipboard", Toast.LENGTH_SHORT).show()
            }
            true
        }
    }

    open fun update(newItems: List<LogRun>, dark: Boolean) {
        items = newItems
        isDark = dark
        notifyDataSetChanged()
    }
}

/**
 * Backward compatibility adapter for LogViewerActivity and TechnicalLogActivity
 */
class LogEntryAdapter(
    entries: List<LogEntry>,
    isDark: Boolean,
    private val onEntryLongClick: ((LogEntry) -> Unit)? = null
) : LogRunAdapter(
    items = entries.map { entry ->
        LogRun(
            timestamp = entry.timestamp,
            messages = if (entry.message.isNotEmpty()) listOf(entry.message) else emptyList(),
            summary = "",
            type = entry.type
        )
    },
    isDark = isDark,
    onItemLongClick = null
) {
    @JvmName("updateEntries")
    fun update(newEntries: List<LogEntry>, dark: Boolean) {
        val runs = newEntries.map { entry ->
            LogRun(
                timestamp = entry.timestamp,
                messages = if (entry.message.isNotEmpty()) listOf(entry.message) else emptyList(),
                summary = "",
                type = entry.type
            )
        }
        super.update(runs, dark)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        super.onBindViewHolder(holder, position)
        if (onEntryLongClick != null) {
            val item = items[position]
            holder.itemView.setOnLongClickListener { view ->
                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                val legacyText = (item.messages + listOf(item.summary)).filter { it.isNotBlank() }.joinToString("\n")
                onEntryLongClick.invoke(LogEntry(item.timestamp, legacyText, item.type))
                true
            }
        }
    }
}
