package com.example

data class UnmatchedEntry(
    val timestamp: String,
    val count: Int,
    val filePath: String,
    val reason: String
)

data class ErrorEntry(
    val timestamp: String,
    val message: String
)

fun parseUnmatched(raw: String): List<UnmatchedEntry> {
    val countRegex = Regex("""^\[(\d+)[×x]]\s*(.*)$""")
    return raw.lines()
        .map { it.trim() }
        .filter { it.isNotBlank() && it != "---" }
        .mapNotNull { line ->
            val parts = line.split(" — ")
            when {
                parts.size >= 3 -> {
                    val rawTime = parts[0].trim()
                    val time = if (rawTime.contains(" ")) rawTime.substringAfter(" ").take(8) else rawTime.take(8)
                    val middle = parts[1].trim()
                    val match = countRegex.find(middle)
                    val count = match?.groupValues?.get(1)?.toIntOrNull() ?: 1
                    val path = match?.groupValues?.get(2)?.trim() ?: middle
                    val reason = parts.drop(2).joinToString(" — ").trim()
                    UnmatchedEntry(time, count, path, reason)
                }
                parts.size == 2 -> {
                    val rawTime = parts[0].trim()
                    val time = if (rawTime.contains(" ")) rawTime.substringAfter(" ").take(8) else rawTime.take(8)
                    val middle = parts[1].trim()
                    val match = countRegex.find(middle)
                    val count = match?.groupValues?.get(1)?.toIntOrNull() ?: 1
                    val path = match?.groupValues?.get(2)?.trim() ?: middle
                    UnmatchedEntry(time, count, path, "")
                }
                else -> UnmatchedEntry("—", 1, line, "")
            }
        }
}

fun parseErrors(raw: String): List<ErrorEntry> {
    return raw.lines()
        .map { it.trim() }
        .filter { it.isNotBlank() && it != "---" }
        .mapNotNull { line ->
            val parts = line.split(" — ", limit = 2)
            if (parts.size == 2) {
                val rawTime = parts[0].trim()
                val time = if (rawTime.contains(" ")) rawTime.substringAfter(" ").take(8) else rawTime.take(8)
                ErrorEntry(time, parts[1].trim())
            } else {
                ErrorEntry("—", line)
            }
        }
}
