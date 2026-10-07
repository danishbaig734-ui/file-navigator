package com.example

import android.Manifest
import android.app.job.JobScheduler
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

/**
 * Main and only Activity of the application.
 * Manages the trigger toggle, manual test trigger, script path configuration,
 * media permissions request, and rolling execution log.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var textTriggerState: TextView
    private lateinit var buttonToggle: Button
    private lateinit var buttonTest: Button
    private lateinit var editScriptPath: EditText
    private lateinit var textLog: TextView
    private lateinit var buttonClearLog: Button
    private lateinit var mainScrollView: ScrollView

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val allGranted = results.values.all { it }
        if (!allGranted) {
            LogManager.log(this, "Permissions denied: trigger will not fire until granted")
        } else {
            LogManager.log(this, "Storage permissions granted")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        setupScriptPath()
        setupListeners()
        updateUiState()
        loadInitialLog()
        requestStoragePermissions()
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
        updateUiState()
    }

    override fun onPause() {
        super.onPause()
        LogManager.setListener(null)
    }

    private fun initViews() {
        textTriggerState = findViewById(R.id.text_trigger_state)
        buttonToggle = findViewById(R.id.button_toggle)
        buttonTest = findViewById(R.id.button_test)
        editScriptPath = findViewById(R.id.edit_script_path)
        textLog = findViewById(R.id.text_log)
        buttonClearLog = findViewById(R.id.button_clear_log)
        mainScrollView = findViewById(R.id.main_scroll_view)
    }

    private fun setupScriptPath() {
        val prefs = getSharedPreferences(TriggerJobService.PREFS_NAME, Context.MODE_PRIVATE)
        val savedPath = prefs.getString(
            TriggerJobService.PREF_KEY_SCRIPT_PATH,
            TriggerJobService.DEFAULT_SCRIPT_PATH
        ) ?: TriggerJobService.DEFAULT_SCRIPT_PATH

        editScriptPath.setText(savedPath)

        editScriptPath.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val path = s?.toString()?.trim() ?: ""
                if (path.isNotEmpty()) {
                    prefs.edit().putString(TriggerJobService.PREF_KEY_SCRIPT_PATH, path).apply()
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun setupListeners() {
        val prefs = getSharedPreferences(TriggerJobService.PREFS_NAME, Context.MODE_PRIVATE)

        buttonToggle.setOnClickListener {
            val isCurrentlyOn = isTriggerActive()
            val jobScheduler = getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
            if (isCurrentlyOn) {
                jobScheduler.cancel(TriggerJobService.JOB_ID_FILE)
                jobScheduler.cancel(TriggerJobService.JOB_ID_PERIODIC)
                prefs.edit().putBoolean(TriggerJobService.PREF_KEY_TRIGGER_ENABLED, false).apply()
                updateUiState(isActive = false)
                LogManager.log(this, "job cancelled")
            } else {
                jobScheduler.schedule(TriggerJobService.buildFileJob(this))
                jobScheduler.schedule(TriggerJobService.buildPeriodicJob(this))
                prefs.edit().putBoolean(TriggerJobService.PREF_KEY_TRIGGER_ENABLED, true).apply()
                updateUiState(isActive = true)
                LogManager.log(this, "job scheduled")
            }
        }

        buttonTest.setOnClickListener {
            LogManager.log(this, "manual test fired")
            val currentPath = editScriptPath.text.toString().trim().ifEmpty {
                TriggerJobService.DEFAULT_SCRIPT_PATH
            }
            TriggerJobService.dispatchTermux(this, currentPath)
        }

        buttonClearLog.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(R.string.dialog_clear_title)
                .setMessage(R.string.dialog_clear_message)
                .setPositiveButton(R.string.dialog_clear_confirm) { _, _ ->
                    LogManager.clearLog(this)
                    textLog.text = getString(R.string.log_section_title)
                }
                .setNegativeButton(R.string.dialog_cancel, null)
                .show()
        }
    }

    private fun isTriggerActive(): Boolean {
        val prefs = getSharedPreferences(TriggerJobService.PREFS_NAME, Context.MODE_PRIVATE)
        val jobScheduler = getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
        val isFileJobScheduled = jobScheduler.getPendingJob(TriggerJobService.JOB_ID_FILE) != null
        return isFileJobScheduled || prefs.getBoolean(TriggerJobService.PREF_KEY_TRIGGER_ENABLED, false)
    }

    private fun updateUiState(isActive: Boolean = isTriggerActive()) {
        if (isActive) {
            textTriggerState.text = getString(R.string.trigger_state_on)
            textTriggerState.setTextColor(Color.parseColor("#4CAF50")) // Green
            buttonToggle.text = getString(R.string.button_turn_off)
        } else {
            textTriggerState.text = getString(R.string.trigger_state_off)
            textTriggerState.setTextColor(Color.parseColor("#B0BEC5")) // Muted Gray
            buttonToggle.text = getString(R.string.button_turn_on)
        }
    }

    private fun loadInitialLog() {
        val content = LogManager.readLog(this)
        textLog.text = content
    }

    private fun requestStoragePermissions() {
        val permissionsToRequest = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ granular media permissions
            val permissions = arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_AUDIO
            )
            for (permission in permissions) {
                if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                    permissionsToRequest.add(permission)
                }
            }
        } else {
            // Android 12 and below (API <= 32)
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }

        if (permissionsToRequest.isNotEmpty()) {
            permissionLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }
}
