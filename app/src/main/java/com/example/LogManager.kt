package com.example

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Thread-safe log manager persisting rolling event logs to internal storage.
 * Uses context.applicationContext directly for each call to avoid reliance on Activity lifecycle.
 */
object LogManager {

    private const val TAG = "LogManager"
    private const val LOG_FILE_NAME = "trigger_log.txt"
    private const val MAX_LOG_LINES = 100
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var uiListener: ((String) -> Unit)? = null

    fun setListener(listener: ((String) -> Unit)?) {
        uiListener = listener
    }

    private fun getLogFile(context: Context): File {
        return File(context.applicationContext.filesDir, LOG_FILE_NAME)
    }

    @Synchronized
    fun log(context: Context, message: String) {
        val appContext = context.applicationContext
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val entry = "$timestamp — $message"

        try {
            val file = getLogFile(appContext)
            val parent = file.parentFile
            if (parent != null && !parent.exists()) {
                parent.mkdirs()
            }
            if (!file.exists()) {
                file.createNewFile()
            }

            val existingLines = if (file.length() > 0) {
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
            Log.e(TAG, "Failed to write log: ${e.javaClass.name}: ${e.message}", e)
        }
    }

    fun append(context: Context, message: String) {
        log(context, message)
    }

    @Synchronized
    fun readLog(context: Context): String {
        val appContext = context.applicationContext
        val file = getLogFile(appContext)
        return try {
            if (file.exists() && file.length() > 0) {
                val content = file.readText().trimEnd()
                if (content.isEmpty()) "No events recorded yet." else content
            } else {
                "No events recorded yet."
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read log: ${e.javaClass.name}: ${e.message}", e)
            "Error reading log: ${e.message}"
        }
    }

    @Synchronized
    fun clearLog(context: Context) {
        val appContext = context.applicationContext
        val file = getLogFile(appContext)
        try {
            if (file.exists()) {
                file.delete()
            }
            notifyListener("No events recorded yet.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear log: ${e.javaClass.name}: ${e.message}", e)
        }
    }

    private fun notifyListener(text: String) {
        val listener = uiListener ?: return
        mainHandler.post {
            listener.invoke(text)
        }
    }
}
