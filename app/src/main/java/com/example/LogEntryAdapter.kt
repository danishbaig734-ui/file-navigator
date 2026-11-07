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

class LogEntryAdapter(
    entries: List<LogEntry>,
    private var isDark: Boolean,
    private val onEntryLongClick: ((LogEntry) -> Unit)? = null
) : RecyclerView.Adapter<LogEntryAdapter.VH>() {

    private var items: List<LogRun> = entries.map { entry ->
        LogRun(
            timestamp = entry.timestamp,
            messages = if (entry.message.isNotEmpty()) listOf(entry.message) else emptyList(),
            summary = null,
            type = entry.type
        )
    }
    private var onRunLongClick: ((LogRun) -> Unit)? = null

    constructor(
        runs: List<LogRun>,
        isDark: Boolean,
        onRunLongClick: ((LogRun) -> Unit)?,
        @Suppress("UNUSED_PARAMETER") forRuns: Unit = Unit
    ) : this(emptyList<LogEntry>(), isDark, null) {
        this.items = runs
        this.onRunLongClick = onRunLongClick
    }

    companion object {
        fun fromRuns(
            runs: List<LogRun>,
            isDark: Boolean,
            onRunLongClick: ((LogRun) -> Unit)? = null
        ): LogEntryAdapter = LogEntryAdapter(runs, isDark, onRunLongClick, Unit)
    }

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

        // 2. Messages (joined by newlines)
        if (item.messages.isNotEmpty()) {
            holder.tvMessages.visibility = View.VISIBLE
            holder.tvMessages.text = item.messages.joinToString("\n")
        } else {
            holder.tvMessages.visibility = View.GONE
        }

        // Set message text color (grey or green based on type)
        val msgColor = when (item.type) {
            LogType.SUCCESS -> if (isDark) 0xFF66BB6A.toInt() else 0xFF2E7D32.toInt()
            LogType.ERROR   -> if (isDark) 0xFFEF5350.toInt() else 0xFFC62828.toInt()
            LogType.NEUTRAL -> if (isDark) 0xFFB0B0B0.toInt() else 0xFF555555.toInt()
        }
        holder.tvMessages.setTextColor(msgColor)

        // 3. Summary (Done line)
        if (!item.summary.isNullOrEmpty()) {
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
            if (onRunLongClick != null) {
                onRunLongClick?.invoke(item)
            } else if (onEntryLongClick != null) {
                val legacyText = (item.messages + listOfNotNull(item.summary)).joinToString("\n")
                onEntryLongClick.invoke(LogEntry(item.timestamp, legacyText, item.type))
            } else {
                val clipboard = view.context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val textToCopy = buildString {
                    if (item.timestamp.isNotBlank() && item.timestamp != "—") {
                        append(item.timestamp).append(" — ")
                    }
                    append(item.messages.joinToString("\n"))
                    if (!item.summary.isNullOrEmpty()) {
                        if (isNotEmpty()) append("\n")
                        append(item.summary)
                    }
                }
                clipboard.setPrimaryClip(ClipData.newPlainText("Log Run", textToCopy))
                Toast.makeText(view.context, "Log copied to clipboard", Toast.LENGTH_SHORT).show()
            }
            true
        }
    }

    @JvmName("updateRuns")
    fun update(newItems: List<LogRun>, dark: Boolean) {
        items = newItems
        isDark = dark
        notifyDataSetChanged()
    }

    @JvmName("updateEntries")
    fun update(newEntries: List<LogEntry>, dark: Boolean) {
        items = newEntries.map { entry ->
            LogRun(
                timestamp = entry.timestamp,
                messages = if (entry.message.isNotEmpty()) listOf(entry.message) else emptyList(),
                summary = null,
                type = entry.type
            )
        }
        isDark = dark
        notifyDataSetChanged()
    }
}
