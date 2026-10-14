package com.example

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.view.MotionEvent
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

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

/**
 * LogViewerActivity redesigned with the minimalist monochrome aesthetic,
 * spring touch physics, pill tabs, structured log entry list, and long-press clipboard copy.
 */
class LogViewerActivity : AppCompatActivity() {

    enum class LogTab(val title: String, val fileName: String) {
        ACTIONS("Actions", "actions.log"),
        UNMATCHED("Unmatched", "unmatched.log"),
        ERRORS("Errors", "errors.log")
    }

    private val PREFS = "app_prefs"
    private val KEY_DARK_MODE = "dark_mode"

    private lateinit var rootLayout: View
    private lateinit var btnBack: ImageButton
    private lateinit var txtPill: TextView
    private lateinit var btnClearLog: Button
    private lateinit var txtHeaderTitle: TextView
    private lateinit var txtSubtitle: TextView
    private lateinit var btnRefreshLogs: ImageButton
    private lateinit var tabActions: Button
    private lateinit var tabUnmatched: Button
    private lateinit var tabErrors: Button
    private lateinit var logCardContainer: FrameLayout
    private lateinit var rvLogViewer: RecyclerView
    private lateinit var txtEmptyLogs: TextView

    private var logAdapter: LogEntryAdapter? = null
    private var currentTab: LogTab = LogTab.ACTIONS

    private fun isDarkMode(): Boolean =
        getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_DARK_MODE, true)

    private fun applyTheme() {
        val dark = isDarkMode()

        // 1. Root and Header backgrounds
        val bgColor = if (dark) 0xFF1A1A1A.toInt() else 0xFFF5F5F5.toInt()
        rootLayout.setBackgroundColor(bgColor)

        val header = findViewById<View>(R.id.headerRow)
        header.setBackgroundColor(if (dark) 0xFF222222.toInt() else 0xFFFFFFFF.toInt())

        // 2. Titles
        txtHeaderTitle.setTextColor(if (dark) 0xFFFFFFFF.toInt() else 0xFF000000.toInt())
        txtSubtitle.setTextColor(if (dark) 0xFFCCCCCC.toInt() else 0xFF555555.toInt())

        // 3. Top pill
        val pillBgColor = if (dark) 0xFF1A1A1A.toInt() else 0xFFE5E5E5.toInt()
        (txtPill.background.mutate() as? GradientDrawable)?.setColor(pillBgColor)
        txtPill.setTextColor(if (dark) 0xFFFFFFFF.toInt() else 0xFF000000.toInt())

        // 4. Back button & Refresh button
        val iconBg = if (dark) 0xFFFFFFFF.toInt() else 0xFF000000.toInt()
        val iconColor = if (dark) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()

        val backBgDrawable = (btnBack.background.mutate() as? android.graphics.drawable.RippleDrawable)?.getDrawable(0) as? GradientDrawable
            ?: btnBack.background.mutate() as? GradientDrawable
        backBgDrawable?.setColor(iconBg)
        btnBack.setColorFilter(iconColor)

        val refreshBgDrawable = (btnRefreshLogs.background.mutate() as? android.graphics.drawable.RippleDrawable)?.getDrawable(0) as? GradientDrawable
            ?: btnRefreshLogs.background.mutate() as? GradientDrawable
        refreshBgDrawable?.setColor(iconBg)
        btnRefreshLogs.setColorFilter(iconColor)

        // 5. Clear button
        val secBtnTextColor = if (dark) 0xFFFFFFFF.toInt() else 0xFF000000.toInt()
        val secBtnBgColor = if (dark) 0xFF2A2A2A.toInt() else 0xFFE5E5E5.toInt()
        (btnClearLog.background.mutate() as? GradientDrawable)?.apply {
            cornerRadius = 18f * resources.displayMetrics.density
            setColor(secBtnBgColor)
        }
        btnClearLog.setTextColor(secBtnTextColor)

        // 6. Log card container
        val cardBg = if (dark) 0xFF0F0F0F.toInt() else 0xFFFFFFFF.toInt()
        (logCardContainer.background.mutate() as? GradientDrawable)?.apply {
            cornerRadius = 24f * resources.displayMetrics.density
            setColor(cardBg)
        }

        // 7. Update Tabs and Adapter
        updateTabButtons()
        loadCurrentLog()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_log_viewer)

        initViews()
        setupListeners()
        applyTheme()
        selectTab(LogTab.ACTIONS, animate = false)
    }

    override fun onResume() {
        super.onResume()
        applyTheme()
    }

    private fun initViews() {
        rootLayout = findViewById(R.id.rootLayout)
        btnBack = findViewById(R.id.btnBack)
        txtPill = findViewById(R.id.txtPill)
        btnClearLog = findViewById(R.id.btnClearLog)
        txtHeaderTitle = findViewById(R.id.txtHeaderTitle)
        txtSubtitle = findViewById(R.id.txtSubtitle)
        btnRefreshLogs = findViewById(R.id.btnRefreshLogs)
        tabActions = findViewById(R.id.tab_actions)
        tabUnmatched = findViewById(R.id.tab_unmatched)
        tabErrors = findViewById(R.id.tab_errors)
        logCardContainer = findViewById(R.id.logCardContainer)
        rvLogViewer = findViewById(R.id.rvLogViewer)
        txtEmptyLogs = findViewById(R.id.txtEmptyLogs)

        rvLogViewer.layoutManager = LinearLayoutManager(this)
    }

    private fun setupListeners() {
        btnBack.setOnClickListener {
            finish()
        }

        attachSpringPress(btnBack, pressScale = 0.88f, overshoot = 4f)
        attachSpringPress(btnRefreshLogs, pressScale = 0.90f, overshoot = 4f)
        attachSpringPress(btnClearLog, pressScale = 0.94f, overshoot = 3.5f)
        attachSpringPress(tabActions, pressScale = 0.94f, overshoot = 3.5f)
        attachSpringPress(tabUnmatched, pressScale = 0.94f, overshoot = 3.5f)
        attachSpringPress(tabErrors, pressScale = 0.94f, overshoot = 3.5f)

        btnRefreshLogs.setOnClickListener {
            loadCurrentLog()
            Toast.makeText(this, "Logs refreshed", Toast.LENGTH_SHORT).show()
        }

        tabActions.setOnClickListener { selectTab(LogTab.ACTIONS) }
        tabUnmatched.setOnClickListener { selectTab(LogTab.UNMATCHED) }
        tabErrors.setOnClickListener { selectTab(LogTab.ERRORS) }

        btnClearLog.setOnClickListener {
            showCustomClearDialog()
        }
    }

    private fun attachSpringPress(view: View, pressScale: Float = 0.94f, overshoot: Float = 3.5f) {
        view.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    v.animate()
                        .scaleX(pressScale)
                        .scaleY(pressScale)
                        .setDuration(100)
                        .setInterpolator(DecelerateInterpolator())
                        .start()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(350)
                        .setInterpolator(android.view.animation.OvershootInterpolator(overshoot))
                        .start()
                }
            }
            false
        }
    }

    private fun selectTab(tab: LogTab, animate: Boolean = true) {
        currentTab = tab
        txtPill.text = tab.fileName

        if (animate) {
            val selectedBtn = when (tab) {
                LogTab.ACTIONS -> tabActions
                LogTab.UNMATCHED -> tabUnmatched
                LogTab.ERRORS -> tabErrors
            }
            selectedBtn.scaleX = 0.90f
            selectedBtn.scaleY = 0.90f
            selectedBtn.animate()
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(300)
                .setInterpolator(android.view.animation.OvershootInterpolator(4f))
                .start()
        }

        updateTabButtons()
        loadCurrentLog()
    }

    private fun updateTabButtons() {
        val dark = isDarkMode()
        val tabs = listOf(
            tabActions to (currentTab == LogTab.ACTIONS),
            tabUnmatched to (currentTab == LogTab.UNMATCHED),
            tabErrors to (currentTab == LogTab.ERRORS)
        )

        for ((btn, isSelected) in tabs) {
            if (isSelected) {
                if (dark) {
                    btn.setBackgroundResource(R.drawable.rounded_button_ripple_white)
                    btn.setTextColor(0xFF000000.toInt())
                } else {
                    btn.setBackgroundResource(R.drawable.rounded_button_ripple_black)
                    btn.setTextColor(0xFFFFFFFF.toInt())
                }
                btn.paint.isFakeBoldText = true
            } else {
                val secBg = if (dark) 0xFF262626.toInt() else 0xFFE5E5E5.toInt()
                val secText = if (dark) 0xFFAAAAAA.toInt() else 0xFF666666.toInt()
                (btn.background.mutate() as? GradientDrawable)?.apply {
                    cornerRadius = 22f * resources.displayMetrics.density
                    setColor(secBg)
                }
                btn.setTextColor(secText)
                btn.paint.isFakeBoldText = false
            }
            btn.invalidate()
        }
    }

    private fun loadCurrentLog() {
        val prefs = getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE)
        val uriString = prefs.getString(RulesEditorActivity.PREF_KEY_RULES_TREE_URI, null)
        val dark = isDarkMode()

        if (uriString == null) {
            displayEntries(listOf(LogEntry("—", "(tree URI invalid — re-grant folder access)", LogType.ERROR)), dark)
            return
        }

        val treeUri = Uri.parse(uriString)
        val root = DocumentFile.fromTreeUri(this, treeUri)
        if (root == null || !root.canRead()) {
            displayEntries(listOf(LogEntry("—", "(tree URI invalid — re-grant folder access)", LogType.ERROR)), dark)
            return
        }

        val logsDir = root.findFile("logs")
        if (logsDir == null || !logsDir.isDirectory) {
            displayEntries(listOf(LogEntry("—", "(logs folder not found)", LogType.NEUTRAL)), dark)
            return
        }

        val fileName = currentTab.fileName
        val logFile = logsDir.findFile(fileName)
        if (logFile == null) {
            displayEntries(emptyList(), dark)
            return
        }

        try {
            val lines = contentResolver.openInputStream(logFile.uri)?.bufferedReader()?.use { reader ->
                reader.readLines()
            } ?: emptyList()

            val rawContent = lines.takeLast(200).joinToString("\n").trim()
            val entries = if (rawContent.isEmpty()) {
                emptyList()
            } else {
                LogParser.parseContent(rawContent)
            }
            displayEntries(entries, dark)
        } catch (_: Exception) {
            displayEntries(listOf(LogEntry("—", "(error reading log file)", LogType.ERROR)), dark)
        }
    }

    private fun displayEntries(entries: List<LogEntry>, dark: Boolean) {
        if (entries.isEmpty()) {
            txtEmptyLogs.visibility = View.VISIBLE
            rvLogViewer.visibility = View.GONE
        } else {
            txtEmptyLogs.visibility = View.GONE
            rvLogViewer.visibility = View.VISIBLE

            if (logAdapter == null) {
                logAdapter = LogEntryAdapter(entries, dark) { entry ->
                    copyLogToClipboard(entry)
                }
                rvLogViewer.adapter = logAdapter
            } else {
                logAdapter?.update(entries, dark)
            }
            rvLogViewer.post {
                rvLogViewer.scrollToPosition(entries.size - 1)
            }
        }
    }

    private fun copyLogToClipboard(entry: LogEntry) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val textToCopy = if (entry.timestamp.isNotBlank() && entry.timestamp != "—") {
            "${entry.timestamp} — ${entry.message}"
        } else {
            entry.message
        }
        val clip = ClipData.newPlainText("Log Entry", textToCopy)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, "Log copied to clipboard", Toast.LENGTH_SHORT).show()
    }

    private fun showCustomClearDialog() {
        val dark = isDarkMode()
        val dialogView = layoutInflater.inflate(R.layout.dialog_clear_log, null)
        val cardRoot = dialogView.findViewById<LinearLayout>(R.id.dialogCardRoot)
        val txtTitle = dialogView.findViewById<TextView>(R.id.txtDialogTitle)
        val txtMessage = dialogView.findViewById<TextView>(R.id.txtDialogMessage)
        val btnCancel = dialogView.findViewById<Button>(R.id.btnDialogCancel)
        val btnConfirm = dialogView.findViewById<Button>(R.id.btnDialogConfirm)

        txtTitle?.text = "Clear ${currentTab.title} Log?"
        txtMessage?.text = "This will erase all recorded events from ${currentTab.fileName}."
        btnConfirm?.text = "Clear"

        val cardBgColor = if (dark) 0xFF1E1E1E.toInt() else 0xFFFFFFFF.toInt()
        val titleColor = if (dark) 0xFFFFFFFF.toInt() else 0xFF000000.toInt()
        val messageColor = if (dark) 0xFFAAAAAA.toInt() else 0xFF555555.toInt()
        val cancelTextColor = if (dark) 0xFFFFFFFF.toInt() else 0xFF000000.toInt()

        cardRoot?.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 16f * resources.displayMetrics.density
            setColor(cardBgColor)
        }

        txtTitle?.setTextColor(titleColor)
        txtMessage?.setTextColor(messageColor)
        btnCancel?.setTextColor(cancelTextColor)

        if (dark) {
            btnCancel?.setBackgroundResource(R.drawable.dialog_button_neutral_dark)
            btnConfirm?.setBackgroundResource(R.drawable.dialog_button_danger_dark)
        } else {
            btnCancel?.setBackgroundResource(R.drawable.dialog_button_neutral_light)
            btnConfirm?.setBackgroundResource(R.drawable.dialog_button_danger_light)
        }

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        btnCancel?.let { attachSpringPress(it, pressScale = 0.94f, overshoot = 3.5f) }
        btnConfirm?.let { attachSpringPress(it, pressScale = 0.94f, overshoot = 3.5f) }

        fun dismissWithAnimation() {
            cardRoot.animate()
                .scaleX(0.90f)
                .scaleY(0.90f)
                .alpha(0f)
                .setDuration(120)
                .setInterpolator(AccelerateDecelerateInterpolator())
                .withEndAction {
                    try {
                        dialog.dismiss()
                    } catch (_: Exception) {}
                }
                .start()
        }

        btnCancel?.setOnClickListener {
            dismissWithAnimation()
        }

        btnConfirm?.setOnClickListener {
            clearCurrentLog()
            dismissWithAnimation()
        }

        dialog.show()

        cardRoot.scaleX = 0.85f
        cardRoot.scaleY = 0.85f
        cardRoot.alpha = 0f
        cardRoot.animate()
            .scaleX(1f)
            .scaleY(1f)
            .alpha(1f)
            .setDuration(260)
            .setInterpolator(android.view.animation.OvershootInterpolator(3.5f))
            .start()
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
            Toast.makeText(this, "${currentTab.title} log cleared", Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {}
        loadCurrentLog()
    }
}
