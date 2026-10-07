package com.example

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

/**
 * Main and only Activity of the application.
 * Manages manual Termux execution, script path configuration, and rolling execution log.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var textStatus: TextView
    private lateinit var buttonRun: Button
    private lateinit var editScriptPath: EditText
    private lateinit var textLog: TextView
    private lateinit var buttonRefreshLog: Button
    private lateinit var buttonClearLog: Button
    private lateinit var mainScrollView: ScrollView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        setupScriptPath()
        setupListeners()
        refreshLogDisplay()
    }

    override fun onResume() {
        super.onResume()
        LogManager.setListener { updatedText ->
            runOnUiThread {
                textLog.text = updatedText
                mainScrollView.post {
                    mainScrollView.fullScroll(View.FOCUS_DOWN)
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
        textStatus = findViewById(R.id.text_status)
        buttonRun = findViewById(R.id.button_run)
        editScriptPath = findViewById(R.id.edit_script_path)
        textLog = findViewById(R.id.text_log)
        buttonRefreshLog = findViewById(R.id.button_refresh_log)
        buttonClearLog = findViewById(R.id.button_clear_log)
        mainScrollView = findViewById(R.id.main_scroll_view)
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
        buttonRun.setOnClickListener {
            val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val currentPath = prefs.getString(PREF_KEY_SCRIPT_PATH, DEFAULT_SCRIPT_PATH)?.ifEmpty {
                DEFAULT_SCRIPT_PATH
            } ?: DEFAULT_SCRIPT_PATH
            dispatchTermux(this, currentPath)
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
        mainScrollView.post {
            mainScrollView.fullScroll(View.FOCUS_DOWN)
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
