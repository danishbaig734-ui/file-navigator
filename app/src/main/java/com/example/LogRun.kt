package com.example

enum class LogType { SUCCESS, ERROR, NEUTRAL }

data class LogRun(
    val timestamp: String,
    val messages: List<String>,
    val summary: String,
    val type: LogType
)

object LogParser {
    fun parseLogLines(rawLog: String): List<LogRun> {
        val runs = mutableListOf<LogRun>()
        val normalized = rawLog.replace("\r\n", "\n").trimEnd()
        val blocks = normalized.split(Regex("\n---\n|\n---$|(?m)^---$"))
        
        for (block in blocks) {
            val lines = block.trim().lines().map { it.trim() }.filter { it.isNotBlank() }
            if (lines.isEmpty()) continue
            
            if (lines.size == 1) {
                val single = lines[0]
                val type = when {
                    single.contains("Failed", ignoreCase = true) || single.contains("error", ignoreCase = true) -> LogType.ERROR
                    single.contains("Paused", ignoreCase = true) -> LogType.NEUTRAL
                    else -> LogType.SUCCESS
                }
                runs.add(LogRun("—", emptyList(), single, type))
                continue
            }
            
            // 1. Extract timestamp from first line (remove brackets)
            val timestamp = lines[0].replace("[", "").replace("]", "")
            
            // 2. Extract summary from last line
            val summary = lines.last()
            
            // 3. Everything in between is a message
            val messages = if (lines.size > 2) {
                lines.subList(1, lines.size - 1)
            } else {
                emptyList()
            }
            
            // 4. Determine Type
            val type = when {
                summary.contains("Failed", ignoreCase = true) || summary.contains("error", ignoreCase = true) -> LogType.ERROR
                summary.contains("Paused", ignoreCase = true) -> LogType.NEUTRAL
                else -> LogType.SUCCESS
            }
            
            runs.add(LogRun(timestamp, messages, summary, type))
        }
        return runs
    }

    fun parseContent(rawLog: String): List<LogRun> = parseLogLines(rawLog)
}
