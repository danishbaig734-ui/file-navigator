package com.example

import android.content.Context
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.view.View
import android.widget.Button
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import java.io.File

class LogViewerActivity : AppCompatActivity() {

    enum class LogTab(val title: String, val fileName: String) {
        ACTIONS("Actions", "actions.log"),
        UNMATCHED("Unmatched", "unmatched.log"),
        ERRORS("Errors", "errors.log")
    }

    private lateinit var toolbar: Toolbar
    private lateinit var tabActions: Button
    private lateinit var tabUnmatched: Button
    private lateinit var tabErrors: Button
    private lateinit var scrollLog: ScrollView
    private lateinit var textLogContent: TextView
    private lateinit var buttonClearLog: Button

    private var currentTab: LogTab = LogTab.ACTIONS

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_log_viewer)

        initViews()
        setupListeners()
        selectTab(LogTab.ACTIONS)
    }

    override fun onResume() {
        super.onResume()
        loadCurrentLog()
    }

    private fun initViews() {
        toolbar = findViewById(R.id.toolbar)
        tabActions = findViewById(R.id.tab_actions)
        tabUnmatched = findViewById(R.id.tab_unmatched)
        tabErrors = findViewById(R.id.tab_errors)
        scrollLog = findViewById(R.id.scroll_log)
        textLogContent = findViewById(R.id.text_log_content)
        buttonClearLog = findViewById(R.id.button_clear_log)

        toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun setupListeners() {
        tabActions.setOnClickListener { selectTab(LogTab.ACTIONS) }
        tabUnmatched.setOnClickListener { selectTab(LogTab.UNMATCHED) }
        tabErrors.setOnClickListener { selectTab(LogTab.ERRORS) }

        buttonClearLog.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Clear Log")
                .setMessage("Clear ${currentTab.title} log?")
                .setPositiveButton("Clear") { _, _ ->
                    clearLogFile(currentTab.fileName)
                    textLogContent.text = "(no entries)"
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun selectTab(tab: LogTab) {
        currentTab = tab
        updateTabButtons()
        loadCurrentLog()
    }

    private fun updateTabButtons() {
        tabActions.paint.isFakeBoldText = (currentTab == LogTab.ACTIONS)
        tabUnmatched.paint.isFakeBoldText = (currentTab == LogTab.UNMATCHED)
        tabErrors.paint.isFakeBoldText = (currentTab == LogTab.ERRORS)

        tabActions.setTextColor(if (currentTab == LogTab.ACTIONS) Color.WHITE else Color.parseColor("#9E9E9E"))
        tabUnmatched.setTextColor(if (currentTab == LogTab.UNMATCHED) Color.WHITE else Color.parseColor("#9E9E9E"))
        tabErrors.setTextColor(if (currentTab == LogTab.ERRORS) Color.WHITE else Color.parseColor("#9E9E9E"))

        tabActions.invalidate()
        tabUnmatched.invalidate()
        tabErrors.invalidate()
    }

    private fun loadCurrentLog() {
        val lines = readLogFile(currentTab.fileName)
        if (lines.isNullOrEmpty()) {
            textLogContent.text = "(no entries)"
        } else {
            val lastLines = if (lines.size > 200) lines.takeLast(200) else lines
            val joined = lastLines.joinToString("\n")
            textLogContent.text = if (joined.isBlank()) "(no entries)" else joined
        }
        scrollLog.post {
            scrollLog.fullScroll(View.FOCUS_DOWN)
        }
    }

    private fun getLogDocumentUri(fileName: String): Uri? {
        val prefs = getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE)
        val uriString = prefs.getString(RulesEditorActivity.PREF_KEY_RULES_TREE_URI, null) ?: return null
        return try {
            val treeUri = Uri.parse(uriString)
            val treeDocId = DocumentsContract.getTreeDocumentId(treeUri)

            var logsDocId: String? = null
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, treeDocId)
            val projection = arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME
            )
            contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                val idCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                while (cursor.moveToNext()) {
                    if (cursor.getString(nameCol).equals("logs", ignoreCase = true)) {
                        logsDocId = cursor.getString(idCol)
                        break
                    }
                }
            } ?: return null

            if (logsDocId == null) return null

            var fileDocId: String? = null
            val logsChildrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, logsDocId)
            contentResolver.query(logsChildrenUri, projection, null, null, null)?.use { cursor ->
                val idCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                while (cursor.moveToNext()) {
                    if (cursor.getString(nameCol).equals(fileName, ignoreCase = true)) {
                        fileDocId = cursor.getString(idCol)
                        break
                    }
                }
            }

            if (fileDocId != null) {
                DocumentsContract.buildDocumentUriUsingTree(treeUri, fileDocId)
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun readLogFile(fileName: String): List<String>? {
        val docUri = getLogDocumentUri(fileName)
        if (docUri != null) {
            try {
                contentResolver.openInputStream(docUri)?.use { stream ->
                    return stream.bufferedReader(Charsets.UTF_8).readLines()
                }
            } catch (_: Exception) {}
        }

        val file = File("/sdcard/FileNavigator/logs", fileName)
        if (file.exists() && file.canRead()) {
            try {
                return file.readLines(Charsets.UTF_8)
            } catch (_: Exception) {}
        }

        return null
    }

    private fun clearLogFile(fileName: String): Boolean {
        var cleared = false
        val docUri = getLogDocumentUri(fileName)
        if (docUri != null) {
            try {
                contentResolver.openOutputStream(docUri, "wt")?.use { stream ->
                    stream.write(ByteArray(0))
                    stream.flush()
                }
                cleared = true
            } catch (_: Exception) {}
        }

        val file = File("/sdcard/FileNavigator/logs", fileName)
        if (file.exists() && file.canWrite()) {
            try {
                file.writeText("")
                cleared = true
            } catch (_: Exception) {}
        }

        return cleared
    }
}
