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

/**
 * Lightweight SAF DocumentFile helper operating over tree URIs without external dependencies.
 */
class DocumentFile private constructor(
    private val context: Context,
    val treeUri: Uri,
    val uri: Uri,
    val isDirectory: Boolean
) {
    fun canRead(): Boolean = true
    fun canWrite(): Boolean = true

    fun findFile(name: String): DocumentFile? {
        val docId = DocumentsContract.getDocumentId(uri)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE
        )
        return try {
            context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                val idCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                if (idCol != -1 && nameCol != -1) {
                    while (cursor.moveToNext()) {
                        val displayName = cursor.getString(nameCol)
                        if (displayName.equals(name, ignoreCase = true)) {
                            val childId = cursor.getString(idCol)
                            val mimeType = if (mimeCol != -1) cursor.getString(mimeCol) else ""
                            val isDir = mimeType == DocumentsContract.Document.MIME_TYPE_DIR
                            val childUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, childId)
                            return DocumentFile(context, treeUri, childUri, isDir)
                        }
                    }
                }
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    fun createFile(mimeType: String, displayName: String): DocumentFile? {
        return try {
            val docId = DocumentsContract.getDocumentId(uri)
            val parentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
            val newDocUri = DocumentsContract.createDocument(context.contentResolver, parentUri, mimeType, displayName)
            if (newDocUri != null) {
                DocumentFile(context, treeUri, newDocUri, isDirectory = false)
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        fun fromTreeUri(context: Context, treeUri: Uri): DocumentFile? {
            return try {
                val docId = DocumentsContract.getTreeDocumentId(treeUri)
                val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                DocumentFile(context, treeUri, docUri, isDirectory = true)
            } catch (_: Exception) {
                null
            }
        }
    }
}

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
                    clearCurrentLog()
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
        val prefs = getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE)
        val uriString = prefs.getString(RulesEditorActivity.PREF_KEY_RULES_TREE_URI, null)
        if (uriString == null) {
            textLogContent.text = "(tree URI invalid — re-grant folder access)"
            return
        }

        val treeUri = Uri.parse(uriString)
        val root = DocumentFile.fromTreeUri(this, treeUri)
        if (root == null || !root.canRead()) {
            textLogContent.text = "(tree URI invalid — re-grant folder access)"
            return
        }

        val logsDir = root.findFile("logs")
        if (logsDir == null || !logsDir.isDirectory) {
            textLogContent.text = "(logs folder not found)"
            return
        }

        val fileName = currentTab.fileName
        val logFile = logsDir.findFile(fileName)
        if (logFile == null) {
            textLogContent.text = "(no entries)"
            return
        }

        try {
            val lines = contentResolver.openInputStream(logFile.uri)?.bufferedReader()?.use { reader ->
                reader.readLines()
            } ?: emptyList()

            if (lines.isEmpty()) {
                textLogContent.text = "(no entries)"
            } else {
                val text = lines.takeLast(200).joinToString("\n")
                textLogContent.text = if (text.isBlank()) "(no entries)" else text
            }
        } catch (_: Exception) {
            textLogContent.text = "(no entries)"
        }

        scrollLog.post {
            scrollLog.fullScroll(View.FOCUS_DOWN)
        }
    }

    private fun clearCurrentLog() {
        val prefs = getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE)
        val uriString = prefs.getString(RulesEditorActivity.PREF_KEY_RULES_TREE_URI, null) ?: return
        val treeUri = Uri.parse(uriString)
        val root = DocumentFile.fromTreeUri(this, treeUri) ?: return
        val logsDir = root.findFile("logs") ?: return
        val logFile = logsDir.findFile(currentTab.fileName) ?: return
        try {
            contentResolver.openOutputStream(logFile.uri, "wt")?.use { stream ->
                stream.write(ByteArray(0))
                stream.flush()
            }
        } catch (_: Exception) {}
        textLogContent.text = "(no entries)"
    }
}
