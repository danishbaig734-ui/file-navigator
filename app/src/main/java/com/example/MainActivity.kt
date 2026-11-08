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
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.*
import java.io.File
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
        findViewById<TextView>(R.id.tvButtonLabel)?.setTextColor(runBtnIcon)

        // 7. Secondary buttons (and refresh/clear log buttons)
        val editRulesBtn = findViewById<Button>(R.id.btnEditRules)
        val viewLogsBtn = findViewById<Button>(R.id.btnViewLogs)

        if (dark) {
            editRulesBtn.setBackgroundResource(R.drawable.rounded_button_ripple_white)
            editRulesBtn.setTextColor(0xFF000000.toInt())
            viewLogsBtn.setBackgroundResource(R.drawable.rounded_button_ripple)
            viewLogsBtn.setTextColor(0xFFFFFFFF.toInt())
        } else {
            editRulesBtn.setBackgroundResource(R.drawable.rounded_button_ripple_black)
            editRulesBtn.setTextColor(0xFFFFFFFF.toInt())
            viewLogsBtn.setBackgroundResource(R.drawable.rounded_button_ripple_light)
            viewLogsBtn.setTextColor(0xFF000000.toInt())
        }

        val secBtnTextColor = if (dark) 0xFFFFFFFF.toInt() else 0xFF000000.toInt()
        val secBtnBgColor = if (dark) 0xFF2A2A2A.toInt() else 0xFFE5E5E5.toInt()
        findViewById<Button>(R.id.btnClearLog)?.let { btn ->
            (btn.background.mutate() as? GradientDrawable)?.apply {
                cornerRadius = 18f * resources.displayMetrics.density
                setColor(secBtnBgColor)
            }
            btn.setTextColor(secBtnTextColor)
        }

        // 8. Labels
        val labelColor = if (dark) 0xFFCCCCCC.toInt() else 0xFF555555.toInt()
        findViewById<TextView>(R.id.lblEventLog).setTextColor(labelColor)

        // 9. Log fields (styled RecyclerView)
        val fieldBgColor = if (dark) 0xFF0F0F0F.toInt() else 0xFFFFFFFF.toInt()

        val logRecycler = findViewById<RecyclerView>(R.id.rvLog)
        (logRecycler.background.mutate() as? GradientDrawable)?.apply {
            cornerRadius = 28f * resources.displayMetrics.density
            setColor(fieldBgColor)
        }
        logAdapter?.let { adapter ->
            val content = LogManager.readLog(this)
            adapter.update(parseLogLines(content), dark)
        }
    }

    private lateinit var btnRunScript: ImageButton
    private lateinit var buttonRun: ImageButton
    private lateinit var tvButtonLabel: TextView
    private var isRunning = false
    private var thinkingJob: Job? = null
    private lateinit var buttonEditRules: Button
    private lateinit var buttonViewLogs: Button
    private lateinit var rvLog: RecyclerView
    private var logAdapter: MainLogAdapter? = null
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

    private val RUN_COMMAND_PERMISSION = "com.termux.permission.RUN_COMMAND"
    private var permissionDialog: AlertDialog? = null

    private fun hasRunCommandPermission(): Boolean =
        androidx.core.content.ContextCompat.checkSelfPermission(
            this,
            RUN_COMMAND_PERMISSION
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

    private fun checkRunCommandPermission() {
        if (!hasRunCommandPermission()) {
            showPermissionDialog()
        }
    }

    private fun showPermissionDialog() {
        if (isFinishing || isDestroyed || permissionDialog?.isShowing == true) return

        permissionDialog = AlertDialog.Builder(this)
            .setTitle(R.string.permission_dialog_title)
            .setMessage(R.string.permission_dialog_message)
            .setPositiveButton(R.string.permission_dialog_open_settings) { _, _ ->
                val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", packageName, null)
                }
                startActivity(intent)
            }
            .setNegativeButton(R.string.permission_dialog_dismiss, null)
            .show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        checkRunCommandPermission()

        applyTheme()

        initViews()
        setupListeners()
        refreshLogDisplay()
    }

    override fun onResume() {
        super.onResume()
        applyTheme()
        if (hasRunCommandPermission()) {
            permissionDialog?.dismiss()
            permissionDialog = null
        }
        LogManager.setListener { _ ->
            runOnUiThread {
                refreshLogDisplay()
            }
        }
        refreshLogDisplay()
    }

    override fun onPause() {
        super.onPause()
        LogManager.setListener(null)
        permissionDialog?.dismiss()
        permissionDialog = null
        if (isRunning) {
            thinkingJob?.cancel()
            thinkingJob = null
            stopAndResetButton()
        }
    }

    private fun initViews() {
        btnRunScript = findViewById(R.id.btnRunScript)
        buttonRun = btnRunScript
        tvButtonLabel = findViewById(R.id.tvButtonLabel)
        buttonEditRules = findViewById(R.id.btnEditRules)
        buttonViewLogs = findViewById(R.id.btnViewLogs)
        rvLog = findViewById(R.id.rvLog)
        rvLog.layoutManager = LinearLayoutManager(this)
        buttonClearLog = findViewById(R.id.btnClearLog)
        mainScrollView = findViewById(R.id.scrollContent)
    }

    private fun setupListeners() {
        findViewById<ImageButton>(R.id.btnSettings).setOnClickListener {
            showGearMenu()
        }

        attachPressScale(buttonRun, grow = 1.08f)
        attachPressScale(buttonEditRules, grow = 1.04f)
        attachPressScale(buttonViewLogs, grow = 1.04f)
        attachPressScale(buttonClearLog, grow = 1.05f)

        buttonRun.setOnClickListener {
            if (!hasRunCommandPermission()) {
                showPermissionDialog()
                return@setOnClickListener
            }
            if (isRunning) return@setOnClickListener

            val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val currentPath = prefs.getString(PREF_KEY_SCRIPT_PATH, DEFAULT_SCRIPT_PATH)?.ifEmpty {
                DEFAULT_SCRIPT_PATH
            } ?: DEFAULT_SCRIPT_PATH
            dispatchTermux(this, currentPath)
            startTermuxAnimation()
        }

        buttonEditRules.setOnClickListener {
            val intent = Intent(this, RulesEditorActivity::class.java)
            startActivity(intent)
        }

        buttonViewLogs.setOnClickListener {
            val intent = Intent(this, LogViewerActivity::class.java)
            startActivity(intent)
        }

        buttonClearLog.setOnClickListener {
            showCustomClearLogDialog()
        }

        val logTouchListener = View.OnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                    view.parent?.requestDisallowInterceptTouchEvent(true)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    view.parent?.requestDisallowInterceptTouchEvent(false)
                }
            }
            false
        }
        rvLog.setOnTouchListener(logTouchListener)
    }

    private fun attachPressScale(view: View, grow: Float = 1.06f) {
        view.setOnTouchListener { v, event ->
            if (v == btnRunScript && isRunning) {
                return@setOnTouchListener false
            }
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    v.animate()
                        .scaleX(grow)
                        .scaleY(grow)
                        .setDuration(90)
                        .setInterpolator(DecelerateInterpolator())
                        .start()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(120)
                        .setInterpolator(AccelerateDecelerateInterpolator())
                        .start()
                }
            }
            false // allow click listener to receive the event
        }
    }

    private fun showCustomClearLogDialog() {
        val dark = isDarkMode()
        val dialogView = layoutInflater.inflate(R.layout.dialog_clear_log, null)
        val cardRoot = dialogView.findViewById<LinearLayout>(R.id.dialogCardRoot)
        val txtTitle = dialogView.findViewById<TextView>(R.id.txtDialogTitle)
        val txtMessage = dialogView.findViewById<TextView>(R.id.txtDialogMessage)
        val btnCancel = dialogView.findViewById<Button>(R.id.btnDialogCancel)
        val btnConfirm = dialogView.findViewById<Button>(R.id.btnDialogConfirm)

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

        btnCancel?.let { attachPressScale(it, grow = 1.05f) }
        btnConfirm?.let { attachPressScale(it, grow = 1.05f) }

        fun dismissWithAnimation() {
            cardRoot.animate()
                .scaleX(0.92f)
                .scaleY(0.92f)
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
            LogManager.clearLog(this)
            refreshLogDisplay()
            dismissWithAnimation()
        }

        dialog.show()

        // Smooth open animation
        cardRoot.scaleX = 0.90f
        cardRoot.scaleY = 0.90f
        cardRoot.alpha = 0f
        cardRoot.animate()
            .scaleX(1f)
            .scaleY(1f)
            .alpha(1f)
            .setDuration(180)
            .setInterpolator(DecelerateInterpolator())
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

        popupView.scaleX = 0.70f
        popupView.scaleY = 0.70f
        popupView.alpha = 0f
        popupView.pivotX = popupWidth.toFloat()
        popupView.pivotY = 0f
        popupView.animate()
            .scaleX(1f)
            .scaleY(1f)
            .alpha(1f)
            .setInterpolator(android.view.animation.OvershootInterpolator(4f))
            .setDuration(280)
            .start()
    }

    private fun refreshLogDisplay() {
        val content = LogManager.readLog(this)
        val entries = parseLogLines(content)
        val dark = isDarkMode()
        if (logAdapter == null) {
            logAdapter = MainLogAdapter(entries, dark) { entry ->
                copyLogToClipboard(entry)
            }
            rvLog.adapter = logAdapter
        } else {
            logAdapter?.update(entries, dark)
        }
        if (entries.isNotEmpty()) {
            rvLog.post {
                rvLog.scrollToPosition(entries.size - 1)
            }
        }
    }

    private fun copyLogToClipboard(entry: MainLogEntry) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val textToCopy = if (entry.timestamp.isNotBlank() && entry.timestamp != "—") {
            "${entry.timestamp} — ${entry.message}"
        } else {
            entry.message
        }
        val clip = android.content.ClipData.newPlainText("Log Entry", textToCopy)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, "Log copied to clipboard", Toast.LENGTH_SHORT).show()
    }

    private fun parseLogLines(rawLog: String): List<MainLogEntry> {
        val clean = rawLog.replace("\r\n", "\n").trim()
        if (clean.isEmpty() || clean == "No events recorded yet.") {
            return listOf(MainLogEntry("—", "No events recorded yet.", LogType.NEUTRAL))
        }

        val lines = clean.lines().map { it.trim() }.filter { it.isNotBlank() }
        if (lines.isEmpty()) {
            return listOf(MainLogEntry("—", "No events recorded yet.", LogType.NEUTRAL))
        }

        return lines.map { parseSingleLine(it) }
    }

    private fun parseSingleLine(line: String): MainLogEntry {
        val parts = line.split(" — ", limit = 2)
        val (rawTs, rawMsg) = if (parts.size == 2) {
            Pair(parts[0].trim(), parts[1].trim())
        } else {
            val bracketMatch = Regex("^\\[(.*?)\\]\\s*(.*)$").find(line)
            if (bracketMatch != null) {
                Pair(bracketMatch.groupValues[1].trim(), bracketMatch.groupValues[2].trim())
            } else {
                Pair("—", line.trim())
            }
        }

        val timePill = extractTimePill(rawTs)

        val type = when {
            rawMsg.contains("failed", ignoreCase = true) ||
            rawMsg.contains("error", ignoreCase = true) ||
            rawMsg.contains("blocked", ignoreCase = true) ||
            rawMsg.contains("SecurityException", ignoreCase = true) -> LogType.ERROR

            rawMsg.contains("Termux started", ignoreCase = true) ||
            rawMsg.contains("Termux bound", ignoreCase = true) ||
            rawMsg.contains("Done", ignoreCase = true) ||
            rawMsg.contains("success", ignoreCase = true) ||
            rawMsg.contains("MOVED", ignoreCase = true) -> LogType.SUCCESS

            else -> LogType.NEUTRAL
        }

        return MainLogEntry(timePill, rawMsg, type)
    }

    private fun extractTimePill(rawTs: String): String {
        val clean = rawTs.replace("[", "").replace("]", "").trim()
        if (clean == "—" || clean.isEmpty()) return "—"
        val timeMatch = Regex("\\b(\\d{2}:\\d{2}:\\d{2})\\b").find(clean)
        if (timeMatch != null) {
            return timeMatch.groupValues[1]
        }
        val timeMatchShort = Regex("\\b(\\d{2}:\\d{2})\\b").find(clean)
        if (timeMatchShort != null) {
            return timeMatchShort.groupValues[1]
        }
        return if (clean.contains(" ")) clean.substringAfterLast(" ") else clean
    }

    private fun startTermuxAnimation() {
        if (isRunning) return
        isRunning = true
        btnRunScript.setImageDrawable(null)

        morph(toSquare = true) {
            tvButtonLabel.visibility = View.VISIBLE
            thinkingJob = lifecycleScope.launch {
                val statusFile = File("/sdcard/FileNavigator/status.txt")

                withContext(Dispatchers.Main) { typeText("Waking Termux...") }

                // PHASE 1: Wait for Termux to actually wake up (write "starting" or "working")
                var isAwake = false
                var wakeAttempts = 0
                while (isActive && !isAwake) {
                    delay(100)
                    val status = withContext(Dispatchers.IO) {
                        if (statusFile.exists()) statusFile.readText().trim() else "idle"
                    }
                    if (status == "starting" || status == "working") {
                        isAwake = true
                    }
                    wakeAttempts++
                    if (wakeAttempts > 80) { // Safety timeout ~8s
                        isAwake = true
                    }
                }

                // PHASE 2 & 3: Dramatic Loop + Check for "done"
                val fakeMessages = listOf(
                    "Scanning extensions...",
                    "Reading rules...",
                    "Moving files...",
                    "Cleaning folders..."
                )
                var i = 0
                var isDone = false

                while (isActive && !isDone) {
                    // 1. Type one fake message
                    val msg = fakeMessages[i % fakeMessages.size]
                    withContext(Dispatchers.Main) { typeText(msg) }

                    // 2. Check the real status file
                    val status = withContext(Dispatchers.IO) {
                        if (statusFile.exists()) statusFile.readText().trim() else "idle"
                    }

                    // 3. If Termux finished, break the loop
                    if (status == "done" || status == "idle") {
                        isDone = true
                        break
                    }

                    i++
                    delay(300) // Small pause so the text is readable
                    if (i > 30) {
                        break
                    }
                }

                // PHASE 4: Finish
                withContext(Dispatchers.Main) {
                    typeText("Done!")
                    delay(1000)
                    stopAndResetButton()
                }
            }
        }
    }

    // Helper for fast typing
    private suspend fun typeText(text: String, delayMs: Long = 20) {
        tvButtonLabel.text = ""
        for (char in text) {
            tvButtonLabel.append(char.toString())
            delay(delayMs)
        }
    }

    private fun stopAndResetButton() {
        tvButtonLabel.text = ""
        tvButtonLabel.visibility = View.INVISIBLE
        morph(toSquare = false) {
            btnRunScript.setImageResource(R.drawable.ic_play)
            isRunning = false
        }
    }

    private fun morph(toSquare: Boolean, onEnd: () -> Unit) {
        val density = resources.displayMetrics.density
        val startWidth = if (btnRunScript.width > 0) btnRunScript.width else (72 * density).toInt()
        val targetWidth = if (toSquare) (160 * density).toInt() else (72 * density).toInt()
        val startRadius = if (toSquare) 36f * density else 16f * density
        val targetRadius = if (toSquare) 16f * density else 36f * density

        val bgDrawable = (btnRunScript.background.mutate() as? android.graphics.drawable.RippleDrawable)?.getDrawable(0) as? GradientDrawable
            ?: btnRunScript.background.mutate() as? GradientDrawable

        val anim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 250
            interpolator = DecelerateInterpolator()
            addUpdateListener { animator ->
                val fraction = animator.animatedFraction
                val currentWidth = (startWidth + (targetWidth - startWidth) * fraction).toInt()
                val currentRadius = startRadius + (targetRadius - startRadius) * fraction

                val params = btnRunScript.layoutParams
                params.width = currentWidth
                btnRunScript.layoutParams = params

                bgDrawable?.cornerRadius = currentRadius
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    onEnd()
                }
            })
        }
        anim.start()
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
