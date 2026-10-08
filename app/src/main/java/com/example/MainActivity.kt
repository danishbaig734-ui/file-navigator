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
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.PopupWindow
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

        val header = findViewById<View>(R.id.headerRow)
        header.setBackgroundColor(if (dark) 0xFF222222.toInt() else 0xFFFFFFFF.toInt())

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
        val gearBgDrawable = (gear.background.mutate() as? android.graphics.drawable.RippleDrawable)?.getDrawable(0) as? GradientDrawable
            ?: gear.background.mutate() as? GradientDrawable
        gearBgDrawable?.setColor(gearBg)
        gear.setColorFilter(gearIcon)

        // 6. Circular action button
        val runBtn = findViewById<ImageButton>(R.id.btnRunScript)
        val runBtnBg = if (dark) 0xFFFFFFFF.toInt() else 0xFF000000.toInt()
        val runBtnIcon = if (dark) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
        val runBgDrawable = (runBtn.background.mutate() as? android.graphics.drawable.RippleDrawable)?.getDrawable(0) as? GradientDrawable
            ?: runBtn.background.mutate() as? GradientDrawable
        runBgDrawable?.setColor(runBtnBg)
        runBtn.setColorFilter(runBtnIcon)

        // 7. Secondary buttons (and refresh/clear log buttons)
        val editRulesBtn = findViewById<Button>(R.id.btnEditRules)
        val viewLogsBtn = findViewById<Button>(R.id.btnViewLogs)
        val secBtnTextColor = if (dark) 0xFFFFFFFF.toInt() else 0xFF000000.toInt()

        if (dark) {
            editRulesBtn.setBackgroundResource(R.drawable.rounded_button_ripple)
            viewLogsBtn.setBackgroundResource(R.drawable.rounded_button_ripple)
        } else {
            editRulesBtn.setBackgroundResource(R.drawable.rounded_button_ripple_light)
            viewLogsBtn.setBackgroundResource(R.drawable.rounded_button_ripple_light)
        }
        editRulesBtn.setTextColor(secBtnTextColor)
        viewLogsBtn.setTextColor(secBtnTextColor)

        val secBtnBgColor = if (dark) 0xFF2A2A2A.toInt() else 0xFFE5E5E5.toInt()
        listOf(
            findViewById<Button>(R.id.btnRefreshLog),
            findViewById<Button>(R.id.btnClearLog)
        ).forEach { btn ->
            (btn.background.mutate() as? GradientDrawable)?.setColor(secBtnBgColor)
            btn.setTextColor(secBtnTextColor)
        }

        // 8. Labels
        val labelColor = if (dark) 0xFFCCCCCC.toInt() else 0xFF555555.toInt()
        findViewById<TextView>(R.id.lblEventLog).setTextColor(labelColor)

        // 9. Log fields
        val fieldBgColor = if (dark) 0xFF0F0F0F.toInt() else 0xFFFFFFFF.toInt()
        val fieldTextColor = if (dark) 0xFFF5F5F5.toInt() else 0xFF1A1A1A.toInt()

        val logScroll = findViewById<View>(R.id.scrollLog)
        (logScroll.background.mutate() as? GradientDrawable)?.setColor(fieldBgColor)
        findViewById<TextView>(R.id.tvLog).setTextColor(fieldTextColor)
    }

    private lateinit var buttonRun: ImageButton
    private lateinit var buttonEditRules: Button
    private lateinit var buttonViewLogs: Button
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
        textLog = findViewById(R.id.tvLog)
        scrollLog = findViewById(R.id.scrollLog)
        buttonRefreshLog = findViewById(R.id.btnRefreshLog)
        buttonClearLog = findViewById(R.id.btnClearLog)
        mainScrollView = findViewById(R.id.scrollContent)
    }

    private fun setupListeners() {
        findViewById<ImageButton>(R.id.btnSettings).setOnClickListener {
            showGearMenu()
        }

        buttonRun.setOnClickListener {
            animateSpring(it)
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

    private fun animateSpring(view: View) {
        view.animate()
            .scaleX(0.9f)
            .scaleY(0.9f)
            .setDuration(80)
            .withEndAction {
                view.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setInterpolator(android.view.animation.OvershootInterpolator(3f))
                    .setDuration(250)
                    .start()
            }
            .start()
    }

    private fun regrantFolderAccess() {
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

    private fun showGearMenu() {
        val anchor = findViewById<ImageButton>(R.id.btnSettings)
        val popupView = layoutInflater.inflate(R.layout.dialog_gear_menu, null)

        val dark = isDarkMode()
        val cardRoot = popupView.findViewById<LinearLayout>(R.id.dialogCardRoot)
        val rowRegrant = popupView.findViewById<LinearLayout>(R.id.rowRegrant)
        val rowSettings = popupView.findViewById<LinearLayout>(R.id.rowSettings)
        val divider = popupView.findViewById<View>(R.id.dialogDivider)
        val titles = listOf(
            popupView.findViewById<TextView>(R.id.txtRegrantTitle),
            popupView.findViewById<TextView>(R.id.txtSettingsTitle)
        )
        val subtitles = listOf(
            popupView.findViewById<TextView>(R.id.txtRegrantSubtitle),
            popupView.findViewById<TextView>(R.id.txtSettingsSubtitle)
        )

        val cardColor = if (dark) 0xFF1E1E1E.toInt() else 0xFFFFFFFF.toInt()
        val titleColor = if (dark) 0xFFFFFFFF.toInt() else 0xFF000000.toInt()
        val subtitleColor = if (dark) 0xFFAAAAAA.toInt() else 0xFF666666.toInt()
        val dividerColor = if (dark) 0xFF2A2A2A.toInt() else 0xFFE5E5E5.toInt()

        // Apply compact rounded card background
        val bg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 14f * resources.displayMetrics.density
            setColor(cardColor)
        }
        cardRoot?.background = bg
        divider?.setBackgroundColor(dividerColor)

        titles.forEach { it?.setTextColor(titleColor) }
        subtitles.forEach { it?.setTextColor(subtitleColor) }

        val popupWidth = (240 * resources.displayMetrics.density).toInt()
        val popupWindow = PopupWindow(
            popupView,
            popupWidth,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        ).apply {
            elevation = 12f * resources.displayMetrics.density
            isOutsideTouchable = true
            isFocusable = true
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        }

        rowRegrant?.setOnClickListener {
            popupWindow.dismiss()
            regrantFolderAccess()
        }
        rowSettings?.setOnClickListener {
            popupWindow.dismiss()
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        // Align right edge of popup with right edge of gear button, right underneath it
        val xOffset = anchor.width - popupWidth
        val yOffset = (6 * resources.displayMetrics.density).toInt()
        popupWindow.showAsDropDown(anchor, xOffset, yOffset)

        popupView.scaleX = 0.85f
        popupView.scaleY = 0.85f
        popupView.alpha = 0f
        popupView.pivotX = popupWidth.toFloat()
        popupView.pivotY = 0f
        popupView.animate()
            .scaleX(1f)
            .scaleY(1f)
            .alpha(1f)
            .setInterpolator(android.view.animation.OvershootInterpolator(2.5f))
            .setDuration(220)
            .start()
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
