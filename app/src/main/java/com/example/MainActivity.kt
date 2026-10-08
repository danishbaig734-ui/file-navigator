package com.example

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.provider.DocumentsContract
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

/**
 * Main Activity of the application.
 * Manages manual Termux execution, script path configuration, rolling execution log, and theme.
 */
class MainActivity : AppCompatActivity() {

    private val PREFS = "app_prefs"
    private val KEY_DARK_MODE = "dark_mode"

    private fun isDarkMode(): Boolean =
        getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(KEY_DARK_MODE, true)

    private fun applyTheme() {
        val dark = isDarkMode()

        // 1. Root background
        val root = findViewById<View>(R.id.rootLayout)
        val bgColor = if (dark) 0xFF1A1A1A.toInt() else 0xFFF5F5F5.toInt()
        root.setBackgroundColor(bgColor)

        // 2. Heading text
        val headerTitle = findViewById<TextView>(R.id.txtHeaderTitle)
        headerTitle.setTextColor(if (dark) 0xFFFFFFFF.toInt() else 0xFF000000.toInt())

        // 3. Subtitle
        val subtitle = findViewById<TextView>(R.id.txtSubtitle)
        subtitle.setTextColor(if (dark) 0xFFFFFFFF.toInt() else 0xFF000000.toInt())

        // 4. Pill badge
        val pill = findViewById<TextView>(R.id.txtPill)
        val pillBgColor = if (dark) 0xFF1A1A1A.toInt() else 0xFFE5E5E5.toInt()
        (pill.background.mutate() as? GradientDrawable)?.setColor(pillBgColor)
        pill.setTextColor(if (dark) 0xFFFFFFFF.toInt() else 0xFF000000.toInt())

        // 5. Circular settings button
        val gear = findViewById<ImageButton>(R.id.btnSettings)
        val gearBg = if (dark) 0xFFFFFFFF.toInt() else 0xFF000000.toInt()
        val gearIcon = if (dark) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
        (gear.background.mutate() as? GradientDrawable)?.setColor(gearBg)
        gear.setColorFilter(gearIcon)

        // 6. Circular action button
        val runBtn = findViewById<ImageButton>(R.id.btnRunScript)
        (runBtn.background.mutate() as? GradientDrawable)?.setColor(0xFF6C63FF.toInt())
        runBtn.setColorFilter(0xFFFFFFFF.toInt())

        // 7. Secondary buttons (and refresh/clear log buttons)
        val secBtnBgColor = if (dark) 0xFF2A2A2A.toInt() else 0xFFE5E5E5.toInt()
        val secBtnTextColor = if (dark) 0xFFFFFFFF.toInt() else 0xFF000000.toInt()
        listOf(
            findViewById<Button>(R.id.btnEditRules),
            findViewById<Button>(R.id.btnViewLogs),
            findViewById<Button>(R.id.btnRegrantAccess),
            findViewById<Button>(R.id.btnRefreshLog),
            findViewById<Button>(R.id.btnClearLog)
        ).forEach { btn ->
            (btn.background.mutate() as? GradientDrawable)?.setColor(secBtnBgColor)
            btn.setTextColor(secBtnTextColor)
        }

        // 8. Labels
        val labelColor = if (dark) 0xFFCCCCCC.toInt() else 0xFF555555.toInt()
        findViewById<TextView>(R.id.lblScriptPath).setTextColor(labelColor)
        findViewById<TextView>(R.id.lblEventLog).setTextColor(labelColor)

        // 9. Path & Log fields
        val fieldBgColor = if (dark) 0xFF0F0F0F.toInt() else 0xFFFFFFFF.toInt()
        val fieldTextColor = if (dark) 0xFFF5F5F5.toInt() else 0xFF1A1A1A.toInt()

        val pathField = findViewById<EditText>(R.id.etScriptPath)
        (pathField.background.mutate() as? GradientDrawable)?.setColor(fieldBgColor)
        pathField.setTextColor(fieldTextColor)
        pathField.setHintTextColor(if (dark) 0xFF616161.toInt() else 0xFF9E9E9E.toInt())

        val logScroll = findViewById<View>(R.id.scrollLog)
        (logScroll.background.mutate() as? GradientDrawable)?.setColor(fieldBgColor)
        findViewById<TextView>(R.id.tvLog).setTextColor(fieldTextColor)
    }

    private lateinit var buttonRun: ImageButton
    private lateinit var buttonEditRules: Button
    private lateinit var buttonViewLogs: Button
    private lateinit var buttonRegrantAccess: Button
    private lateinit var editScriptPath: EditText
    private lateinit var textLog: TextView
    private lateinit var scrollLog: ScrollView
    private lateinit var buttonRefreshLog: Button
    private lateinit var buttonClearLog: Button
    private lateinit var mainScrollView: ScrollView

    private val folderPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uri = result.data?.data
        if (result.resultCode == RESULT_OK && uri != null) {
            val flags = result.data?.flags ?: 0
            val takeFlags = flags and (
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            ).let { granted ->
                if (granted != 0) granted else (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            }
            try {
                contentResolver.takePersistableUriPermission(uri, takeFlags)
            } catch (e: Exception) {
                // ignore
            }
            val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putString(RulesEditorActivity.PREF_KEY_RULES_TREE_URI, uri.toString()).apply()
            Toast.makeText(this, "Folder access updated", Toast.LENGTH_SHORT).show()
            LogManager.append(this, "tree URI re-granted: $uri")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        applyTheme()

        initViews()
        setupScriptPath()
        setupListeners()
        refreshLogDisplay()
    }

    override fun onResume() {
        super.onResume()
        applyTheme()
        LogManager.setListener { updatedText ->
            runOnUiThread {
                textLog.text = updatedText
                scrollLog.post {
                    scrollLog.fullScroll(View.FOCUS_DOWN)
                }
            }
        }
        refreshLogDisplay()
    }

    override fun onPause() {
        super.onPause()
        LogManager.setListener(null)
    }

    private fun initViews() {
        buttonRun = findViewById(R.id.btnRunScript)
        buttonEditRules = findViewById(R.id.btnEditRules)
        buttonViewLogs = findViewById(R.id.btnViewLogs)
        buttonRegrantAccess = findViewById(R.id.btnRegrantAccess)
        editScriptPath = findViewById(R.id.etScriptPath)
        textLog = findViewById(R.id.tvLog)
        scrollLog = findViewById(R.id.scrollLog)
        buttonRefreshLog = findViewById(R.id.btnRefreshLog)
        buttonClearLog = findViewById(R.id.btnClearLog)
        mainScrollView = findViewById(R.id.rootLayout)
    }

    private fun setupScriptPath() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedPath = prefs.getString(PREF_KEY_SCRIPT_PATH, DEFAULT_SCRIPT_PATH) ?: DEFAULT_SCRIPT_PATH

        editScriptPath.setText(savedPath)

        editScriptPath.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val path = s?.toString()?.trim() ?: ""
                if (path.isNotEmpty()) {
                    prefs.edit().putString(PREF_KEY_SCRIPT_PATH, path).apply()
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun setupListeners() {
        findViewById<ImageButton>(R.id.btnSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        buttonRun.setOnClickListener {
            val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val currentPath = prefs.getString(PREF_KEY_SCRIPT_PATH, DEFAULT_SCRIPT_PATH)?.ifEmpty {
                DEFAULT_SCRIPT_PATH
            } ?: DEFAULT_SCRIPT_PATH
            dispatchTermux(this, currentPath)
        }

        buttonEditRules.setOnClickListener {
            val intent = Intent(this, RulesEditorActivity::class.java)
            startActivity(intent)
        }

        buttonViewLogs.setOnClickListener {
            val intent = Intent(this, LogViewerActivity::class.java)
            startActivity(intent)
        }

        buttonRegrantAccess.setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                addFlags(
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val hintUri = DocumentsContract.buildDocumentUri(
                        "com.android.externalstorage.documents",
                        "primary:FileNavigator"
                    )
                    putExtra(DocumentsContract.EXTRA_INITIAL_URI, hintUri)
                }
            }
            folderPermissionLauncher.launch(intent)
        }

        buttonRefreshLog.setOnClickListener {
            refreshLogDisplay()
        }

        buttonClearLog.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(R.string.dialog_clear_title)
                .setMessage(R.string.dialog_clear_message)
                .setPositiveButton(R.string.dialog_clear_confirm) { _, _ ->
                    LogManager.clearLog(this)
                    textLog.text = "No events recorded yet."
                }
                .setNegativeButton(R.string.dialog_cancel, null)
                .show()
        }
    }

    private fun refreshLogDisplay() {
        val content = LogManager.readLog(this)
        textLog.text = content
        scrollLog.post {
            scrollLog.fullScroll(View.FOCUS_DOWN)
        }
    }

    companion object {
        const val PREFS_NAME = "app_prefs"
        const val PREF_KEY_SCRIPT_PATH = "script_path"
        const val DEFAULT_SCRIPT_PATH = "/data/data/com.termux/files/home/file-bus.sh"

        /**
         * Dispatches command intent to Termux's RunCommandService with Android 12 fallback chain.
         * Tries startService first; if blocked by background restrictions, falls back to bindService.
         * Never uses startForegroundService or notifications.
         */
        fun dispatchTermux(context: Context, scriptPath: String) {
            val termuxIntent = Intent().apply {
                setClassName("com.termux", "com.termux.app.RunCommandService")
                action = "com.termux.RUN_COMMAND"
                putExtra("com.termux.RUN_COMMAND_PATH", scriptPath)
                putExtra("com.termux.RUN_COMMAND_BACKGROUND", true)
            }

            try {
                context.startService(termuxIntent)
                LogManager.log(context, "Termux started via startService")
            } catch (e: Exception) {
                if (e is IllegalStateException || e is SecurityException) {
                    LogManager.log(context, "startService blocked: ${e.javaClass.simpleName} - ${e.message}")
                    try {
                        val connection = object : ServiceConnection {
                            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                                try {
                                    context.unbindService(this)
                                } catch (_: Exception) {}
                            }

                            override fun onServiceDisconnected(name: ComponentName?) {}
                        }
                        val bound = context.bindService(termuxIntent, connection, Context.BIND_AUTO_CREATE)
                        if (bound) {
                            LogManager.log(context, "Termux dispatched via bindService")
                        } else {
                            LogManager.log(context, "bindService returned false")
                        }
                    } catch (bindEx: Exception) {
                        LogManager.log(context, "Termux intent failed: ${bindEx.javaClass.simpleName} - ${bindEx.message}")
                    }
                } else {
                    LogManager.log(context, "Termux intent failed: ${e.javaClass.simpleName} - ${e.message}")
                }
            }
        }
    }
}
