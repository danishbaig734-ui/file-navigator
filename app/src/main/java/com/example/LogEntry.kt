package com.example

data class LogEntry(
    val timestamp: String,   // "17:10:14" or "—"
    val message: String,
    val type: LogType
)

enum class LogType { SUCCESS, ERROR, NEUTRAL }

object LogParser {
    private val TIMESTAMP_REGEX = Regex("""^(?:\d{4}-\d{2}-\d{2}\s+)?(\d{2}:\d{2}:\d{2})\s*(?:—|-|:)?\s*(.*)$""")

    fun parseLine(line: String): LogEntry {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) {
            return LogEntry("—", "", LogType.NEUTRAL)
        }

        val match = TIMESTAMP_REGEX.matchEntire(trimmed)
        val timestamp: String
        val message: String
        if (match != null) {
            timestamp = match.groupValues[1]
            val rest = match.groupValues[2].trim()
            message = if (rest.isNotEmpty()) rest else trimmed
        } else {
            timestamp = "—"
            message = trimmed
        }

        val type = determineType(message)
        return LogEntry(timestamp, message, type)
    }

    fun determineType(message: String): LogType {
        return when {
            message.contains("FAILED") ||
            message.contains("blocked") ||
            message.contains("failed") ||
            message.contains("SecurityException") ||
            message.contains("Exception") ||
            message.contains("Error") ||
            message.contains("error") ||
            message.startsWith("rules.conf save failed") -> LogType.ERROR

            (!message.contains("REMOVED") && message.contains("MOVED")) ||
            message.contains("startService") ||
            message.contains("bindService") ||
            message.startsWith("=== Done") ||
            message.startsWith("=== Run started") -> LogType.SUCCESS

            else -> LogType.NEUTRAL
        }
    }

    fun parseContent(content: String): List<LogEntry> {
        val lines = content.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.isEmpty()) {
            return listOf(LogEntry("—", "No events recorded yet.", LogType.NEUTRAL))
        }
        return lines.map { parseLine(it) }
    }
}
