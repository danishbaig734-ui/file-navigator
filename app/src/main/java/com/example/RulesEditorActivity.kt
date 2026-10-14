package com.example

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.provider.DocumentsContract
import android.text.Editable
import android.text.TextWatcher
import android.text.method.ArrowKeyMovementMethod
import android.text.method.KeyListener
import android.text.method.ScrollingMovementMethod
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

/**
 * Rules Editor Activity styled with the futuristic, high-contrast monochrome aesthetic
 * of the main screen. Supports dynamic light/dark theme shifting, curved editor card,
 * rounded buttons, quick insert chips, syntax pill highlighting, and seamless
 * Read-Only vs. Edit Mode interaction with tap-outside locking.
 */
class RulesEditorActivity : AppCompatActivity() {

    private val PREFS = "app_prefs"
    private val KEY_DARK_MODE = "dark_mode"

    private lateinit var rootLayout: View
    private lateinit var btnBack: ImageButton
    private lateinit var txtPill: TextView
    private lateinit var txtStatusPill: TextView
    private lateinit var btnRunScript: ImageButton
    private lateinit var editorCardContainer: View
    private lateinit var txtModePill: TextView
    private lateinit var txtRulesStats: TextView
    private lateinit var editRules: EditText
    private lateinit var buttonPickPath: Button
    private lateinit var buttonReset: Button
    private lateinit var buttonSave: Button

    private var originalContent: String = ""
    private var isModified: Boolean = false
    private var isEditMode: Boolean = false
    private var defaultKeyListener: KeyListener? = null
    private var isHighlighting: Boolean = false

    private fun isDarkMode(): Boolean =
        getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_DARK_MODE, true)

    private fun applyTheme() {
        val dark = isDarkMode()

        // 1. Root & Header backgrounds
        val bgColor = if (dark) 0xFF1A1A1A.toInt() else 0xFFF5F5F5.toInt()
        rootLayout.setBackgroundColor(bgColor)

        val header = findViewById<View>(R.id.headerRow)
        header.setBackgroundColor(if (dark) 0xFF222222.toInt() else 0xFFFFFFFF.toInt())

        // 2. Headings
        val headerTitle = findViewById<TextView>(R.id.txtHeaderTitle)
        headerTitle.setTextColor(if (dark) 0xFFFFFFFF.toInt() else 0xFF000000.toInt())

        val subtitle = findViewById<TextView>(R.id.txtSubtitle)
        subtitle.setTextColor(if (dark) 0xFFCCCCCC.toInt() else 0xFF555555.toInt())

        // 3. Header Pills
        val pillBgColor = if (dark) 0xFF1A1A1A.toInt() else 0xFFE5E5E5.toInt()
        (txtPill.background.mutate() as? GradientDrawable)?.setColor(pillBgColor)
        txtPill.setTextColor(if (dark) 0xFFFFFFFF.toInt() else 0xFF000000.toInt())

        updateStatusPill(isModified, dark)

        // 4. Circular Back Button
        val backBg = if (dark) 0xFFFFFFFF.toInt() else 0xFF000000.toInt()
        val backIcon = if (dark) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
        val backBgDrawable = (btnBack.background.mutate() as? android.graphics.drawable.RippleDrawable)?.getDrawable(0) as? GradientDrawable
            ?: btnBack.background.mutate() as? GradientDrawable
        backBgDrawable?.setColor(backBg)
        btnBack.setColorFilter(backIcon)

        // 5. Circular Action/Run Button
        val runBtnBg = if (dark) 0xFFFFFFFF.toInt() else 0xFF000000.toInt()
        val runBtnIcon = if (dark) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
        val runBgDrawable = (btnRunScript.background.mutate() as? android.graphics.drawable.RippleDrawable)?.getDrawable(0) as? GradientDrawable
            ?: btnRunScript.background.mutate() as? GradientDrawable
        runBgDrawable?.setColor(runBtnBg)
        btnRunScript.setColorFilter(runBtnIcon)

        // 6. Curved editor container
        val fieldBgColor = if (dark) 0xFF0F0F0F.toInt() else 0xFFFFFFFF.toInt()
        (editorCardContainer.background.mutate() as? GradientDrawable)?.apply {
            cornerRadius = 24f * resources.displayMetrics.density
            setColor(fieldBgColor)
        }

        // 7. Divider inside editor card
        val dividerColor = if (dark) 0x22FFFFFF else 0x15000000
        findViewById<View>(R.id.editorDivider).setBackgroundColor(dividerColor)

        // 8. Editor base text and hint
        val hintColor = if (dark) 0xFF616161.toInt() else 0xFF9E9E9E.toInt()
        editRules.setHintTextColor(hintColor)

        // 9. Quick snippet chips
        val chipBgColor = if (dark) 0xFF1C1C1C.toInt() else 0xFFE8E8E8.toInt()
        val chipTextColor = if (dark) 0xFF80D8FF.toInt() else 0xFF005B94.toInt()
        val labelColor = if (dark) 0xFFAAAAAA.toInt() else 0xFF777777.toInt()
        findViewById<TextView>(R.id.lblQuickInsert).setTextColor(labelColor)

        listOf(
            findViewById<TextView>(R.id.chipRule),
            findViewById<TextView>(R.id.chipDest),
            findViewById<TextView>(R.id.chipExt),
            findViewById<TextView>(R.id.chipScanDirs)
        ).forEach { chip ->
            (chip.background.mutate() as? GradientDrawable)?.apply {
                cornerRadius = 12f * resources.displayMetrics.density
                setColor(chipBgColor)
            }
            chip.setTextColor(chipTextColor)
        }

        // 10. Secondary rounded buttons (Pick Path, Reset)
        val secBtnTextColor = if (dark) 0xFFFFFFFF.toInt() else 0xFF000000.toInt()
        val secBtnBgColor = if (dark) 0xFF2A2A2A.toInt() else 0xFFE5E5E5.toInt()
        listOf(buttonPickPath, buttonReset).forEach { btn ->
            (btn.background.mutate() as? GradientDrawable)?.apply {
                cornerRadius = 24f * resources.displayMetrics.density
                setColor(secBtnBgColor)
            }
            btn.setTextColor(secBtnTextColor)
        }

        // 11. Primary Save button
        if (dark) {
            buttonSave.setBackgroundResource(R.drawable.rounded_button_ripple_white)
            buttonSave.setTextColor(0xFF000000.toInt())
        } else {
            buttonSave.setBackgroundResource(R.drawable.rounded_button_ripple_black)
            buttonSave.setTextColor(0xFFFFFFFF.toInt())
        }

        // Update mode styling and syntax highlighting
        updateModePillUI()
        triggerSyntaxHighlighting()
    }

    private fun updateStatusPill(modified: Boolean, dark: Boolean = isDarkMode()) {
        isModified = modified
        val statusBg = if (dark) 0xFF222222.toInt() else 0xFFEAEAEA.toInt()
        (txtStatusPill.background.mutate() as? GradientDrawable)?.setColor(statusBg)
        if (modified) {
            txtStatusPill.text = "Modified •"
            txtStatusPill.setTextColor(if (dark) 0xFFFFB74D.toInt() else 0xFFE65100.toInt())
        } else {
            txtStatusPill.text = "Saved ✓"
            txtStatusPill.setTextColor(if (dark) 0xFF81C784.toInt() else 0xFF2E7D32.toInt())
        }
    }

    private fun updateModePillUI() {
        val dark = isDarkMode()
        if (isEditMode) {
            txtModePill.text = "✏️ EDITING • Tap outside to lock"
            val bg = if (dark) 0xFF2D1F10.toInt() else 0xFFFFF3E0.toInt()
            val text = if (dark) 0xFFFFB74D.toInt() else 0xFFE65100.toInt()
            (txtModePill.background.mutate() as? GradientDrawable)?.apply {
                cornerRadius = 12f * resources.displayMetrics.density
                setColor(bg)
            }
            txtModePill.setTextColor(text)
        } else {
            txtModePill.text = "📖 READ MODE • Tap to edit"
            val bg = if (dark) 0xFF142426.toInt() else 0xFFE1F5FE.toInt()
            val text = if (dark) 0xFF80D8FF.toInt() else 0xFF0277BD.toInt()
            (txtModePill.background.mutate() as? GradientDrawable)?.apply {
                cornerRadius = 12f * resources.displayMetrics.density
                setColor(bg)
            }
            txtModePill.setTextColor(text)
        }
    }

    private fun setEditMode(enabled: Boolean, animateSpring: Boolean = true) {
        if (isEditMode == enabled) return
        isEditMode = enabled

        updateModePillUI()

        if (animateSpring) {
            // Spring bounce on the mode pill
            txtModePill.scaleX = 0.86f
            txtModePill.scaleY = 0.86f
            txtModePill.animate()
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(350)
                .setInterpolator(android.view.animation.OvershootInterpolator(4f))
                .start()

            // Subtle spring bounce on editor card container
            editorCardContainer.scaleX = 0.98f
            editorCardContainer.scaleY = 0.98f
            editorCardContainer.animate()
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(320)
                .setInterpolator(android.view.animation.OvershootInterpolator(3f))
                .start()
        }

        if (isEditMode) {
            editRules.keyListener = defaultKeyListener
            editRules.movementMethod = ArrowKeyMovementMethod.getInstance()
            editRules.isFocusable = true
            editRules.isFocusableInTouchMode = true
            editRules.isCursorVisible = true
            editRules.requestFocus()

            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.showSoftInput(editRules, InputMethodManager.SHOW_IMPLICIT)
        } else {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.hideSoftInputFromWindow(editRules.windowToken, 0)

            editRules.clearFocus()
            editRules.isCursorVisible = false
            editRules.keyListener = null
            editRules.movementMethod = ScrollingMovementMethod.getInstance()
        }

        triggerSyntaxHighlighting()
    }

    private fun triggerSyntaxHighlighting() {
        if (isHighlighting) return
        isHighlighting = true
        try {
            val dark = isDarkMode()
            val density = resources.displayMetrics.density
            val cursorPosition = editRules.selectionStart

            RulesSyntaxHighlighter.highlight(
                text = editRules.text,
                isDark = dark,
                isReadMode = !isEditMode,
                density = density
            )

            if (isEditMode && cursorPosition >= 0 && cursorPosition <= editRules.length()) {
                editRules.setSelection(cursorPosition)
            }

            updateStats()
        } catch (e: Exception) {
            Log.e(TAG, "highlighting failed: ${e.message}", e)
        } finally {
            isHighlighting = false
        }
    }

    private fun updateStats() {
        val text = editRules.text?.toString() ?: ""
        val sectionMatches = Regex("""(?m)^\s*\[[^\]\n]+\]""").findAll(text).count()
        val linesCount = text.lines().size
        txtRulesStats.text = "$sectionMatches sections • $linesCount lines"
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.action == MotionEvent.ACTION_DOWN && isEditMode) {
            val rect = Rect()
            editorCardContainer.getGlobalVisibleRect(rect)
            val x = ev.rawX.toInt()
            val y = ev.rawY.toInt()

            // If user taps outside the editorCardContainer, lock into Read Mode immediately
            if (!rect.contains(x, y)) {
                setEditMode(false)
            }
        }
        return super.dispatchTouchEvent(ev)
    }

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
                Log.e(TAG, "takePersistableUriPermission failed: ${e.message}", e)
            }

            val prefs = getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putString(PREF_KEY_RULES_TREE_URI, uri.toString()).apply()
            loadRulesFile(uri)
        } else {
            if (editRules.text.isNullOrEmpty()) {
                setEditorContent(DEFAULT_STARTER_TEMPLATE)
            }
        }
    }

    private val pickPathLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uri = result.data?.data
        if (result.resultCode == RESULT_OK && uri != null) {
            val path = treeUriToPath(uri)
            if (path != null) {
                insertPathAtCursor(path)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_rules_editor)

        initViews()
        setupListeners()
        applyTheme()

        // Start in Read Mode by default so the keyboard doesn't pop up automatically
        setEditMode(false)

        initRulesFile()
    }

    override fun onResume() {
        super.onResume()
        applyTheme()
    }

    private fun initViews() {
        rootLayout = findViewById(R.id.rootLayout)
        btnBack = findViewById(R.id.btnBack)
        txtPill = findViewById(R.id.txtPill)
        txtStatusPill = findViewById(R.id.txtStatusPill)
        btnRunScript = findViewById(R.id.btnRunScript)
        editorCardContainer = findViewById(R.id.editorCardContainer)
        txtModePill = findViewById(R.id.txtModePill)
        txtRulesStats = findViewById(R.id.txtRulesStats)
        editRules = findViewById(R.id.edit_rules)
        buttonPickPath = findViewById(R.id.button_pick_path)
        buttonReset = findViewById(R.id.button_reset)
        buttonSave = findViewById(R.id.button_save)

        defaultKeyListener = editRules.keyListener
    }

    private fun setupListeners() {
        btnBack.setOnClickListener {
            finish()
        }

        attachSpringPress(btnBack, pressScale = 0.88f, overshoot = 4f)
        attachSpringPress(btnRunScript, pressScale = 0.90f, overshoot = 4f)
        attachSpringPress(buttonPickPath, pressScale = 0.95f, overshoot = 3.5f)
        attachSpringPress(buttonReset, pressScale = 0.95f, overshoot = 3.5f)
        attachSpringPress(buttonSave, pressScale = 0.95f, overshoot = 3.5f)
        attachSpringPress(txtModePill, pressScale = 0.92f, overshoot = 4f)

        // Toggle Read/Edit mode via the mode pill
        txtModePill.setOnClickListener {
            setEditMode(!isEditMode)
        }

        // Tap inside the editor to unlock Edit Mode if in Read Mode
        editRules.setOnTouchListener { _, event ->
            if (!isEditMode && event.action == MotionEvent.ACTION_UP) {
                setEditMode(true)
                true
            } else {
                false
            }
        }

        editorCardContainer.setOnClickListener {
            if (!isEditMode) {
                setEditMode(true)
            }
        }

        // Helper chips click handlers
        val chipRule = findViewById<TextView>(R.id.chipRule)
        val chipDest = findViewById<TextView>(R.id.chipDest)
        val chipExt = findViewById<TextView>(R.id.chipExt)
        val chipScanDirs = findViewById<TextView>(R.id.chipScanDirs)

        attachSpringPress(chipRule, pressScale = 0.90f, overshoot = 4f)
        attachSpringPress(chipDest, pressScale = 0.90f, overshoot = 4f)
        attachSpringPress(chipExt, pressScale = 0.90f, overshoot = 4f)
        attachSpringPress(chipScanDirs, pressScale = 0.90f, overshoot = 4f)

        fun springPulseView(v: View) {
            v.scaleX = 0.85f
            v.scaleY = 0.85f
            v.animate()
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(320)
                .setInterpolator(android.view.animation.OvershootInterpolator(4f))
                .start()
        }

        chipRule.setOnClickListener {
            springPulseView(chipRule)
            if (!isEditMode) setEditMode(true)
            insertTextAtCursor("\n\n[custom]\ndest = /sdcard/Custom\next = txt, md")
        }

        chipDest.setOnClickListener {
            springPulseView(chipDest)
            if (!isEditMode) setEditMode(true)
            insertTextAtCursor("dest = /sdcard/Downloads/Sorted")
        }

        chipExt.setOnClickListener {
            springPulseView(chipExt)
            if (!isEditMode) setEditMode(true)
            insertTextAtCursor("ext = ")
        }

        chipScanDirs.setOnClickListener {
            springPulseView(chipScanDirs)
            if (!isEditMode) setEditMode(true)
            insertTextAtCursor("scan_dirs = /sdcard/Download")
        }

        editRules.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val currentText = s?.toString() ?: ""
                val modified = (currentText != originalContent)
                if (modified != isModified) {
                    updateStatusPill(modified)
                }
            }
            override fun afterTextChanged(s: Editable?) {
                if (!isHighlighting) {
                    triggerSyntaxHighlighting()
                }
            }
        })

        buttonPickPath.setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
            pickPathLauncher.launch(intent)
        }

        buttonReset.setOnClickListener {
            showResetConfirmDialog()
        }

        buttonSave.setOnClickListener {
            val saved = saveRulesToFile(showSuccessToast = true)
            if (saved) {
                // Return to clean Read Mode after saving
                setEditMode(false)
            }
        }

        btnRunScript.setOnClickListener {
            val saved = saveRulesToFile(showSuccessToast = false)
            if (saved) {
                setEditMode(false)
                val prefs = getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE)
                val scriptPath = prefs.getString(
                    MainActivity.PREF_KEY_SCRIPT_PATH,
                    MainActivity.DEFAULT_SCRIPT_PATH
                )?.ifEmpty {
                    MainActivity.DEFAULT_SCRIPT_PATH
                } ?: MainActivity.DEFAULT_SCRIPT_PATH

                dispatchTermux(scriptPath)
            }
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

    private fun setEditorContent(content: String) {
        originalContent = content
        editRules.setText(content)
        updateStatusPill(false)
        triggerSyntaxHighlighting()
    }

    private fun initRulesFile() {
        val prefs = getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE)
        val savedUriString = prefs.getString(PREF_KEY_RULES_TREE_URI, null)

        if (savedUriString != null) {
            val treeUri = Uri.parse(savedUriString)
            val hasPermission = contentResolver.persistedUriPermissions.any {
                it.uri == treeUri && it.isReadPermission && it.isWritePermission
            }
            if (hasPermission) {
                loadRulesFile(treeUri)
                return
            }
        }

        requestFolderPermission()
    }

    private fun requestFolderPermission() {
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

    private fun loadRulesFile(treeUri: Uri) {
        val content = readRulesFile(treeUri)
        if (content != null) {
            setEditorContent(content)
        } else {
            setEditorContent(DEFAULT_STARTER_TEMPLATE)
        }
    }

    private fun readRulesFile(treeUri: Uri): String? {
        val root = DocumentFile.fromTreeUri(this, treeUri) ?: return null
        val file = root.findFile("rules.conf") ?: return null
        return try {
            contentResolver.openInputStream(file.uri)?.use { stream ->
                stream.bufferedReader(Charsets.UTF_8).readText()
            }
        } catch (e: Exception) {
            Log.e(TAG, "readRulesFile failed: ${e.message}", e)
            null
        }
    }

    private fun saveRulesToFile(showSuccessToast: Boolean = true): Boolean {
        val prefs = getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE)
        val uriString = prefs.getString(PREF_KEY_RULES_TREE_URI, null)
        if (uriString == null) {
            val reason = "no folder permission granted"
            LogManager.log(this, "rules.conf save failed: $reason")
            Toast.makeText(this, "Save failed: $reason", Toast.LENGTH_LONG).show()
            requestFolderPermission()
            return false
        }

        val treeUri = Uri.parse(uriString)
        val content = editRules.text?.toString() ?: ""

        return try {
            val root = DocumentFile.fromTreeUri(this, treeUri)
            if (root == null) {
                val reason = "invalid tree URI"
                LogManager.log(this, "rules.conf save failed: $reason")
                Toast.makeText(this, "Save failed: $reason", Toast.LENGTH_LONG).show()
                return false
            }

            val file = root.findFile("rules.conf") ?: root.createFile("text/plain", "rules.conf")
            if (file == null) {
                val reason = "unable to create or locate rules.conf"
                LogManager.log(this, "rules.conf save failed: $reason")
                Toast.makeText(this, "Save failed: $reason", Toast.LENGTH_LONG).show()
                return false
            }

            val outputStream = contentResolver.openOutputStream(file.uri, "wt")
                ?: throw IllegalStateException("Output stream is null")

            outputStream.use { stream ->
                stream.write(content.toByteArray(Charsets.UTF_8))
                stream.flush()
            }

            originalContent = content
            updateStatusPill(false)
            LogManager.log(this, "rules.conf saved")
            if (showSuccessToast) {
                Toast.makeText(this, "Rules saved", Toast.LENGTH_SHORT).show()
            }
            true
        } catch (e: Exception) {
            val reason = e.message ?: "unknown error"
            LogManager.log(this, "rules.conf save failed: $reason")
            Toast.makeText(this, "Save failed: $reason", Toast.LENGTH_LONG).show()
            false
        }
    }

    private fun showResetConfirmDialog() {
        val dark = isDarkMode()
        val dialogView = layoutInflater.inflate(R.layout.dialog_clear_log, null)
        val cardRoot = dialogView.findViewById<LinearLayout>(R.id.dialogCardRoot)
        val txtTitle = dialogView.findViewById<TextView>(R.id.txtDialogTitle)
        val txtMessage = dialogView.findViewById<TextView>(R.id.txtDialogMessage)
        val btnCancel = dialogView.findViewById<Button>(R.id.btnDialogCancel)
        val btnConfirm = dialogView.findViewById<Button>(R.id.btnDialogConfirm)

        txtTitle?.text = "Reset Rules?"
        txtMessage?.text = "Restore default rules template? Unsaved changes will be discarded."
        btnConfirm?.text = "Reset"

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
            setEditorContent(DEFAULT_STARTER_TEMPLATE)
            updateStatusPill(true)
            Toast.makeText(this, "Reset to default template", Toast.LENGTH_SHORT).show()
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

    private fun insertTextAtCursor(textToInsert: String) {
        val cursorPosition = editRules.selectionStart.coerceAtLeast(0)
        val text = editRules.text
        if (text != null) {
            text.insert(cursorPosition, textToInsert)
            editRules.setSelection(cursorPosition + textToInsert.length)
        } else {
            editRules.setText(textToInsert)
            editRules.setSelection(textToInsert.length)
        }
        editRules.requestFocus()
    }

    private fun insertPathAtCursor(path: String) {
        if (!isEditMode) setEditMode(true)
        insertTextAtCursor(path)
    }

    private fun treeUriToPath(uri: Uri): String? {
        val lastSegment = uri.lastPathSegment ?: return null
        val decoded = Uri.decode(lastSegment)
        return when {
            decoded == "primary:" -> "/sdcard"
            decoded.startsWith("primary:") -> {
                val relative = decoded.removePrefix("primary:").trimStart('/')
                if (relative.isEmpty()) {
                    "/sdcard"
                } else {
                    "/sdcard/$relative"
                }
            }
            decoded.contains(":") -> {
                val afterColon = decoded.substringAfter(":").trimStart('/')
                if (afterColon.isEmpty()) "/sdcard" else "/sdcard/$afterColon"
            }
            else -> {
                val relative = decoded.trimStart('/')
                if (relative.isEmpty()) "/sdcard" else "/sdcard/$relative"
            }
        }
    }

    private fun dispatchTermux(scriptPath: String) {
        val termuxIntent = Intent().apply {
            setClassName("com.termux", "com.termux.app.RunCommandService")
            action = "com.termux.RUN_COMMAND"
            putExtra("com.termux.RUN_COMMAND_PATH", scriptPath)
            putExtra("com.termux.RUN_COMMAND_BACKGROUND", true)
        }

        try {
            startService(termuxIntent)
            LogManager.log(this, "editor: Termux intent sent")
            Toast.makeText(this, "Script triggered", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            if (e is IllegalStateException || e is SecurityException) {
                try {
                    val connection = object : ServiceConnection {
                        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                            try {
                                unbindService(this)
                            } catch (_: Exception) {}
                        }

                        override fun onServiceDisconnected(name: ComponentName?) {}
                    }
                    val bound = bindService(termuxIntent, connection, Context.BIND_AUTO_CREATE)
                    if (bound) {
                        LogManager.log(this, "editor: Termux intent sent")
                        Toast.makeText(this, "Script triggered", Toast.LENGTH_SHORT).show()
                    } else {
                        val reason = "bindService returned false"
                        LogManager.log(this, "editor: Termux intent failed: $reason")
                        Toast.makeText(this, "Trigger failed: $reason", Toast.LENGTH_LONG).show()
                    }
                } catch (bindEx: Exception) {
                    val reason = bindEx.message ?: "bindService exception"
                    LogManager.log(this, "editor: Termux intent failed: $reason")
                    Toast.makeText(this, "Trigger failed: $reason", Toast.LENGTH_LONG).show()
                }
            } else {
                val reason = e.message ?: "startService exception"
                LogManager.log(this, "editor: Termux intent failed: $reason")
                Toast.makeText(this, "Trigger failed: $reason", Toast.LENGTH_LONG).show()
            }
        }
    }

    companion object {
        private const val TAG = "RulesEditorActivity"
        const val PREF_KEY_RULES_TREE_URI = "rules_tree_uri"

        private val DEFAULT_STARTER_TEMPLATE = """
            |[settings]
            |scan_dirs = /sdcard/Download
            |
            |[audio]
            |dest = /sdcard/Music
            |ext  = mp3, m4a, aac, flac, ogg, wav
            |
            |[document]
            |dest = /sdcard/Documents
            |ext  = pdf, doc, docx
            |
            |[apk]
            |dest = /sdcard/APKs
            |ext  = apk, xapk
        """.trimMargin()
    }
}
