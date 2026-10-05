package com.example

import android.content.Context
import android.os.Handler
import android.os.Looper
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Thread-safe log manager persisting rolling event logs to internal storage.
 *
 * Rules:
 * - On write: appends the new timestamped line, and if lines > 100, drops the oldest lines.
 * - On read: reads the entire file without truncation.
 * - Supports an optional in-process listener for live UI updates on the main thread.
 */
object LogManager {

    private const val LOG_FILE_NAME = "trigger_log.txt"
    private const val MAX_LOG_LINES = 100
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var uiListener: ((String) -> Unit)? = null

    fun setListener(listener: ((String) -> Unit)?) {
        uiListener = listener
    }

    private fun getLogFile(context: Context): File {
        return File(context.filesDir, LOG_FILE_NAME)
    }

    @Synchronized
    fun log(context: Context, message: String) {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val entry = "$timestamp — $message"

        val file = getLogFile(context)
        try {
            val existingLines = if (file.exists()) {
                file.readLines()
            } else {
                emptyList()
            }

            val updatedLines = (existingLines + entry).let { allLines ->
                if (allLines.size > MAX_LOG_LINES) {
                    allLines.takeLast(MAX_LOG_LINES)
                } else {
                    allLines
                }
            }

            file.writeText(updatedLines.joinToString("\n") + "\n")

            val fullText = updatedLines.joinToString("\n")
            notifyListener(fullText)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @Synchronized
    fun readLog(context: Context): String {
        val file = getLogFile(context)
        return try {
            if (file.exists()) {
                val content = file.readText().trimEnd()
                if (content.isEmpty()) "No events recorded yet." else content
            } else {
                "No events recorded yet."
            }
        } catch (e: Exception) {
            "Error reading log: ${e.message}"
        }
    }

    @Synchronized
    fun clearLog(context: Context) {
        val file = getLogFile(context)
        try {
            if (file.exists()) {
                file.delete()
            }
            notifyListener("No events recorded yet.")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun notifyListener(text: String) {
        val listener = uiListener ?: return
        mainHandler.post {
            listener.invoke(text)
        }
    }
}
