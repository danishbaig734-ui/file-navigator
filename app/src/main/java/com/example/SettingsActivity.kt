package com.example

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat

/**
 * SettingsActivity managing app theme, Termux script dispatch path,
 * Scoped Storage folder access, and diagnostic environment checks.
 * Styled to perfectly match the sleek aesthetic of File Navigator.
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var rootLayout: View
    private lateinit var headerRow: View
    private lateinit var btnBack: ImageButton
    private lateinit var txtPill: TextView
    private lateinit var txtStatusPill: TextView
    private lateinit var txtHeaderTitle: TextView
    private lateinit var txtSubtitle: TextView
    private lateinit var btnResetAll: ImageButton

    // Card 1: Appearance
    private lateinit var cardTheme: LinearLayout
    private lateinit var badgeTheme: TextView
    private lateinit var imgThemeIcon: ImageView
    private lateinit var txtDarkModeTitle: TextView
    private lateinit var txtDarkModeSubtitle: TextView
    private lateinit var switchDarkMode: SwitchCompat
    private lateinit var pillDarkTheme: TextView
    private lateinit var pillLightTheme: TextView

    // Card 2: Termux Script Path
    private lateinit var cardScriptPath: LinearLayout
    private lateinit var badgeEngine: TextView
    private lateinit var txtEngineStatusBadge: TextView
    private lateinit var lblScriptPath: TextView
    private lateinit var lblScriptPathSub: TextView
    private lateinit var inputScriptContainer: LinearLayout
    private lateinit var txtShellPrompt: TextView
    private lateinit var etScriptPath: EditText
    private lateinit var btnResetDefaultPath: Button
    private lateinit var btnPastePath: Button
    private lateinit var btnTestScript: Button
    private lateinit var txtScriptStatusHint: TextView

    // Card 3: Storage Access
    private lateinit var cardStorage: LinearLayout
    private lateinit var badgeStorage: TextView
    private lateinit var txtStorageStatusPill: TextView
    private lateinit var txtStorageTitle: TextView
    private lateinit var txtStorageSubtitle: TextView
    private lateinit var txtStorageUriDisplay: TextView
    private lateinit var btnChangeFolder: Button

    // Card 4: System Diagnostics & About
    private lateinit var cardAbout: LinearLayout
    private lateinit var txtAboutTitle: TextView
    private lateinit var txtAboutSubtitle: TextView
    private lateinit var chipBadge1: TextView
    private lateinit var chipBadge2: TextView
    private lateinit var chipBadge3: TextView

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
                // Ignore if security manager restricts persistable take
            }
            val prefs = getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putString(RulesEditorActivity.PREF_KEY_RULES_TREE_URI, uri.toString()).apply()
            updateStorageDisplay()
            flashStatusPill("Folder linked ✓")
            LogManager.log(this, "Settings: Storage folder updated to $uri")
        }
    }

    private fun isDarkMode(): Boolean {
        val prefs = getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_DARK_MODE, true)
    }

    private fun setDarkMode(enabled: Boolean) {
        val prefs = getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_DARK_MODE, enabled).apply()
        applyTheme()
        flashStatusPill(if (enabled) "Dark theme ✓" else "Light theme ✓")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        initViews()
        setupListeners()
        loadPreferences()
        applyTheme()
    }

    override fun onResume() {
        super.onResume()
        updateStorageDisplay()
    }

    private fun initViews() {
        rootLayout = findViewById(R.id.rootLayout)
        headerRow = findViewById(R.id.headerRow)
        btnBack = findViewById(R.id.btnBack)
        txtPill = findViewById(R.id.txtPill)
        txtStatusPill = findViewById(R.id.txtStatusPill)
        txtHeaderTitle = findViewById(R.id.txtHeaderTitle)
        txtSubtitle = findViewById(R.id.txtSubtitle)
        btnResetAll = findViewById(R.id.btnResetAll)

        cardTheme = findViewById(R.id.cardTheme)
        badgeTheme = findViewById(R.id.badgeTheme)
        imgThemeIcon = findViewById(R.id.imgThemeIcon)
        txtDarkModeTitle = findViewById(R.id.txtDarkModeTitle)
        txtDarkModeSubtitle = findViewById(R.id.txtDarkModeSubtitle)
        switchDarkMode = findViewById(R.id.switchDarkMode)
        pillDarkTheme = findViewById(R.id.pillDarkTheme)
        pillLightTheme = findViewById(R.id.pillLightTheme)

        cardScriptPath = findViewById(R.id.cardScriptPath)
        badgeEngine = findViewById(R.id.badgeEngine)
        txtEngineStatusBadge = findViewById(R.id.txtEngineStatusBadge)
        lblScriptPath = findViewById(R.id.lblScriptPath)
        lblScriptPathSub = findViewById(R.id.lblScriptPathSub)
        inputScriptContainer = findViewById(R.id.inputScriptContainer)
        txtShellPrompt = findViewById(R.id.txtShellPrompt)
        etScriptPath = findViewById(R.id.etScriptPath)
        btnResetDefaultPath = findViewById(R.id.btnResetDefaultPath)
        btnPastePath = findViewById(R.id.btnPastePath)
        btnTestScript = findViewById(R.id.btnTestScript)
        txtScriptStatusHint = findViewById(R.id.txtScriptStatusHint)

        cardStorage = findViewById(R.id.cardStorage)
        badgeStorage = findViewById(R.id.badgeStorage)
        txtStorageStatusPill = findViewById(R.id.txtStorageStatusPill)
        txtStorageTitle = findViewById(R.id.txtStorageTitle)
        txtStorageSubtitle = findViewById(R.id.txtStorageSubtitle)
        txtStorageUriDisplay = findViewById(R.id.txtStorageUriDisplay)
        btnChangeFolder = findViewById(R.id.btnChangeFolder)

        cardAbout = findViewById(R.id.cardAbout)
        txtAboutTitle = findViewById(R.id.txtAboutTitle)
        txtAboutSubtitle = findViewById(R.id.txtAboutSubtitle)
        chipBadge1 = findViewById(R.id.chipBadge1)
        chipBadge2 = findViewById(R.id.chipBadge2)
        chipBadge3 = findViewById(R.id.chipBadge3)
    }

    private fun loadPreferences() {
        val prefs = getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE)
        val isDark = prefs.getBoolean(KEY_DARK_MODE, true)
        switchDarkMode.isChecked = isDark

        val savedPath = prefs.getString(MainActivity.PREF_KEY_SCRIPT_PATH, MainActivity.DEFAULT_SCRIPT_PATH)
            ?: MainActivity.DEFAULT_SCRIPT_PATH
        etScriptPath.setText(savedPath)

        updateStorageDisplay()
    }

    private fun updateStorageDisplay() {
        val prefs = getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE)
        val uriString = prefs.getString(RulesEditorActivity.PREF_KEY_RULES_TREE_URI, null)

        val dark = isDarkMode()
        if (!uriString.isNullOrEmpty()) {
            val decoded = Uri.decode(uriString)
            val friendlyPath = if (decoded.contains(":")) {
                decoded.substringAfterLast(":")
            } else {
                decoded
            }
            txtStorageUriDisplay.text = "Linked: $friendlyPath"
            txtStorageStatusPill.text = "Granted ✓"

            val grantedBg = if (dark) 0xFF14301E.toInt() else 0xFFD1FAE5.toInt()
            val grantedText = if (dark) 0xFFA7F3D0.toInt() else 0xFF059669.toInt()
            (txtStorageStatusPill.background.mutate() as? GradientDrawable)?.setColor(grantedBg)
            txtStorageStatusPill.setTextColor(grantedText)
        } else {
            txtStorageUriDisplay.text = "No storage folder linked"
            txtStorageStatusPill.text = "Not Linked"

            val unlinkedBg = if (dark) 0xFF332010.toInt() else 0xFFFEF3C7.toInt()
            val unlinkedText = if (dark) 0xFFFFD54F.toInt() else 0xFFD97706.toInt()
            (txtStorageStatusPill.background.mutate() as? GradientDrawable)?.setColor(unlinkedBg)
            txtStorageStatusPill.setTextColor(unlinkedText)
        }
    }

    private fun setupListeners() {
        btnBack.setOnClickListener {
            finish()
        }

        attachSpringPress(btnBack, pressScale = 0.88f, overshoot = 4f)
        attachSpringPress(btnResetAll, pressScale = 0.88f, overshoot = 4f)
        attachSpringPress(btnResetDefaultPath, pressScale = 0.94f, overshoot = 3.5f)
        attachSpringPress(btnPastePath, pressScale = 0.94f, overshoot = 3.5f)
        attachSpringPress(btnTestScript, pressScale = 0.94f, overshoot = 3.5f)
        attachSpringPress(btnChangeFolder, pressScale = 0.96f, overshoot = 3.5f)
        attachSpringPress(pillDarkTheme, pressScale = 0.94f, overshoot = 3.5f)
        attachSpringPress(pillLightTheme, pressScale = 0.94f, overshoot = 3.5f)

        switchDarkMode.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked != isDarkMode()) {
                setDarkMode(isChecked)
            }
        }

        pillDarkTheme.setOnClickListener {
            if (!isDarkMode()) {
                springPulse(pillDarkTheme)
                switchDarkMode.isChecked = true
            }
        }

        pillLightTheme.setOnClickListener {
            if (isDarkMode()) {
                springPulse(pillLightTheme)
                switchDarkMode.isChecked = false
            }
        }

        etScriptPath.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val path = s?.toString()?.trim() ?: ""
                if (path.isNotEmpty()) {
                    val prefs = getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE)
                    prefs.edit().putString(MainActivity.PREF_KEY_SCRIPT_PATH, path).apply()
                    txtStatusPill.text = "Saved ✓"
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        btnResetDefaultPath.setOnClickListener {
            springPulse(btnResetDefaultPath)
            etScriptPath.setText(MainActivity.DEFAULT_SCRIPT_PATH)
            val prefs = getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putString(MainActivity.PREF_KEY_SCRIPT_PATH, MainActivity.DEFAULT_SCRIPT_PATH).apply()
            flashStatusPill("Reset default path ✓")
            Toast.makeText(this, "Script path reset to default", Toast.LENGTH_SHORT).show()
        }

        btnPastePath.setOnClickListener {
            springPulse(btnPastePath)
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            val clip = clipboard?.primaryClip
            if (clip != null && clip.itemCount > 0) {
                val text = clip.getItemAt(0)?.coerceToText(this)?.toString()?.trim() ?: ""
                if (text.isNotEmpty()) {
                    etScriptPath.setText(text)
                    flashStatusPill("Pasted ✓")
                    Toast.makeText(this, "Pasted script path", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Clipboard is empty", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "Clipboard is empty", Toast.LENGTH_SHORT).show()
            }
        }

        btnTestScript.setOnClickListener {
            springPulse(btnTestScript)
            handleTestRun()
        }

        btnChangeFolder.setOnClickListener {
            springPulse(btnChangeFolder)
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                addFlags(
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                )
            }
            try {
                folderPermissionLauncher.launch(intent)
            } catch (e: Exception) {
                Toast.makeText(this, "Could not open document tree: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }

        btnResetAll.setOnClickListener {
            springPulse(btnResetAll)
            showResetConfirmationDialog()
        }
    }

    private fun handleTestRun() {
        val permission = "com.termux.permission.RUN_COMMAND"
        val hasPermission = ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
        if (!hasPermission) {
            AlertDialog.Builder(this)
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
            return
        }

        val path = etScriptPath.text.toString().trim().ifEmpty {
            MainActivity.DEFAULT_SCRIPT_PATH
        }
        MainActivity.dispatchTermux(this, path)
        flashStatusPill("Dispatched ⚡")
        Toast.makeText(this, "Dispatched Termux test command", Toast.LENGTH_SHORT).show()
    }

    private fun showResetConfirmationDialog() {
        val dark = isDarkMode()
        val cardBg = if (dark) 0xFF1E1E1E.toInt() else 0xFFFFFFFF.toInt()
        val textColor = if (dark) 0xFFFFFFFF.toInt() else 0xFF000000.toInt()
        val messageColor = if (dark) 0xFFAAAAAA.toInt() else 0xFF555555.toInt()

        val dialogView = layoutInflater.inflate(R.layout.dialog_clear_log, null)
        val cardRoot = dialogView.findViewById<LinearLayout>(R.id.dialogCardRoot)
        val txtTitle = dialogView.findViewById<TextView>(R.id.txtDialogTitle)
        val txtMessage = dialogView.findViewById<TextView>(R.id.txtDialogMessage)
        val btnCancel = dialogView.findViewById<Button>(R.id.btnDialogCancel)
        val btnConfirm = dialogView.findViewById<Button>(R.id.btnDialogConfirm)

        cardRoot?.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 16f * resources.displayMetrics.density
            setColor(cardBg)
        }

        txtTitle?.text = "Reset Settings?"
        txtTitle?.setTextColor(textColor)
        txtMessage?.text = "This will restore Dark Mode and reset the Termux script path to defaults."
        txtMessage?.setTextColor(messageColor)

        btnConfirm?.text = "Reset"
        btnCancel?.setTextColor(if (dark) 0xFFFFFFFF.toInt() else 0xFF000000.toInt())

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        btnCancel?.setOnClickListener {
            dialog.dismiss()
        }
        btnConfirm?.setOnClickListener {
            dialog.dismiss()
            val prefs = getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit()
                .putBoolean(KEY_DARK_MODE, true)
                .putString(MainActivity.PREF_KEY_SCRIPT_PATH, MainActivity.DEFAULT_SCRIPT_PATH)
                .apply()

            switchDarkMode.isChecked = true
            etScriptPath.setText(MainActivity.DEFAULT_SCRIPT_PATH)
            applyTheme()
            flashStatusPill("Settings Reset ✓")
            Toast.makeText(this, "All settings restored to default", Toast.LENGTH_SHORT).show()
        }

        dialog.show()
    }

    private fun flashStatusPill(message: String) {
        txtStatusPill.text = message
        txtStatusPill.scaleX = 0.85f
        txtStatusPill.scaleY = 0.85f
        txtStatusPill.animate()
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(280)
            .setInterpolator(OvershootInterpolator(3.5f))
            .start()
    }

    private fun springPulse(view: View) {
        view.scaleX = 0.88f
        view.scaleY = 0.88f
        view.animate()
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(300)
            .setInterpolator(OvershootInterpolator(3.8f))
            .start()
    }

    private fun attachSpringPress(view: View, pressScale: Float = 0.94f, overshoot: Float = 3.5f) {
        view.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    v.animate()
                        .scaleX(pressScale)
                        .scaleY(pressScale)
                        .setDuration(90)
                        .setInterpolator(DecelerateInterpolator())
                        .start()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(240)
                        .setInterpolator(OvershootInterpolator(overshoot))
                        .start()
                }
            }
            false
        }
    }

    private fun applyTheme() {
        val dark = isDarkMode()

        // 1. Root & Header Backgrounds
        val rootBg = if (dark) 0xFF1A1A1A.toInt() else 0xFFF5F5F5.toInt()
        val headerBg = if (dark) 0xFF222222.toInt() else 0xFFFFFFFF.toInt()
        rootLayout.setBackgroundColor(rootBg)
        headerRow.setBackgroundColor(headerBg)

        // 2. Back Button
        val backBtnBg = if (dark) 0xFFFFFFFF.toInt() else 0xFF000000.toInt()
        val backBtnIcon = if (dark) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
        val backBgDrawable = (btnBack.background.mutate() as? RippleDrawable)?.getDrawable(0) as? GradientDrawable
            ?: btnBack.background.mutate() as? GradientDrawable
        backBgDrawable?.setColor(backBtnBg)
        btnBack.setColorFilter(backBtnIcon)

        // 3. Header Pills
        val pillBgColor = if (dark) 0xFF1A1A1A.toInt() else 0xFFE5E5E5.toInt()
        val pillTextColor = if (dark) 0xFFFFFFFF.toInt() else 0xFF000000.toInt()

        (txtPill.background.mutate() as? GradientDrawable)?.setColor(pillBgColor)
        txtPill.setTextColor(pillTextColor)

        (txtStatusPill.background.mutate() as? GradientDrawable)?.setColor(pillBgColor)
        txtStatusPill.setTextColor(pillTextColor)

        // 4. Hero Header Text
        val titleColor = if (dark) 0xFFFFFFFF.toInt() else 0xFF000000.toInt()
        txtHeaderTitle.setTextColor(titleColor)
        txtSubtitle.setTextColor(titleColor)

        // 5. Hero Action Button (Reset All)
        val resetBtnBg = if (dark) 0xFFFFFFFF.toInt() else 0xFF000000.toInt()
        val resetBtnIcon = if (dark) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
        val resetBgDrawable = (btnResetAll.background.mutate() as? RippleDrawable)?.getDrawable(0) as? GradientDrawable
            ?: btnResetAll.background.mutate() as? GradientDrawable
        resetBgDrawable?.setColor(resetBtnBg)
        btnResetAll.setColorFilter(resetBtnIcon)

        // 6. Cards Styling (24dp rounded corners, dark elevation #0F0F0F vs light #FFFFFF)
        val cardBg = if (dark) 0xFF0F0F0F.toInt() else 0xFFFFFFFF.toInt()
        val cardBorder = if (dark) 0xFF282828.toInt() else 0xFFE5E5E5.toInt()
        val cardRadius = 24f * resources.displayMetrics.density

        fun styleCard(card: LinearLayout) {
            card.background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = cardRadius
                setColor(cardBg)
                setStroke((1f * resources.displayMetrics.density).toInt(), cardBorder)
            }
        }
        styleCard(cardTheme)
        styleCard(cardScriptPath)
        styleCard(cardStorage)
        styleCard(cardAbout)

        // 7. Category Badges
        val badgeBg = if (dark) 0xFF222222.toInt() else 0xFFEAEAEA.toInt()
        val badgeText = if (dark) 0xFFCCCCCC.toInt() else 0xFF444444.toInt()

        fun styleBadge(badge: TextView) {
            (badge.background.mutate() as? GradientDrawable)?.apply {
                cornerRadius = 12f * resources.displayMetrics.density
                setColor(badgeBg)
            }
            badge.setTextColor(badgeText)
        }
        styleBadge(badgeTheme)
        styleBadge(badgeEngine)
        styleBadge(txtEngineStatusBadge)
        styleBadge(badgeStorage)
        styleBadge(chipBadge1)
        styleBadge(chipBadge2)
        styleBadge(chipBadge3)

        // 8. Card 1 Details (Theme)
        txtDarkModeTitle.setTextColor(titleColor)
        txtDarkModeSubtitle.setTextColor(if (dark) 0xFFAAAAAA.toInt() else 0xFF666666.toInt())

        val iconBg = if (dark) 0xFF222222.toInt() else 0xFFEAEAEA.toInt()
        val iconTint = if (dark) 0xFFFFFFFF.toInt() else 0xFF000000.toInt()
        (imgThemeIcon.background.mutate() as? GradientDrawable)?.apply {
            cornerRadius = 14f * resources.displayMetrics.density
            setColor(iconBg)
        }
        imgThemeIcon.setColorFilter(iconTint)

        // Switch Colors
        val thumbColor = if (dark) 0xFFFFFFFF.toInt() else 0xFF000000.toInt()
        val trackColor = if (dark) 0xFF444444.toInt() else 0xFFCCCCCC.toInt()
        switchDarkMode.thumbTintList = ColorStateList.valueOf(thumbColor)
        switchDarkMode.trackTintList = ColorStateList.valueOf(trackColor)

        // Theme Dual Pill Selector
        val activePillBg = if (dark) 0xFFFFFFFF.toInt() else 0xFF000000.toInt()
        val activePillText = if (dark) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
        val inactivePillBg = if (dark) 0xFF202020.toInt() else 0xFFEDEDED.toInt()
        val inactivePillText = if (dark) 0xFF888888.toInt() else 0xFF777777.toInt()

        if (dark) {
            (pillDarkTheme.background.mutate() as? GradientDrawable)?.apply {
                cornerRadius = 19f * resources.displayMetrics.density
                setColor(activePillBg)
            }
            pillDarkTheme.setTextColor(activePillText)

            (pillLightTheme.background.mutate() as? GradientDrawable)?.apply {
                cornerRadius = 19f * resources.displayMetrics.density
                setColor(inactivePillBg)
            }
            pillLightTheme.setTextColor(inactivePillText)
        } else {
            (pillDarkTheme.background.mutate() as? GradientDrawable)?.apply {
                cornerRadius = 19f * resources.displayMetrics.density
                setColor(inactivePillBg)
            }
            pillDarkTheme.setTextColor(inactivePillText)

            (pillLightTheme.background.mutate() as? GradientDrawable)?.apply {
                cornerRadius = 19f * resources.displayMetrics.density
                setColor(activePillBg)
            }
            pillLightTheme.setTextColor(activePillText)
        }

        // 9. Card 2 Details (Execution Engine)
        lblScriptPath.setTextColor(titleColor)
        lblScriptPathSub.setTextColor(if (dark) 0xFFAAAAAA.toInt() else 0xFF666666.toInt())
        txtScriptStatusHint.setTextColor(if (dark) 0xFF888888.toInt() else 0xFF777777.toInt())

        val inputContainerBg = if (dark) 0xFF141414.toInt() else 0xFFF2F2F2.toInt()
        val inputBorder = if (dark) 0xFF2C2C2C.toInt() else 0xFFD8D8D8.toInt()
        inputScriptContainer.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 18f * resources.displayMetrics.density
            setColor(inputContainerBg)
            setStroke((1f * resources.displayMetrics.density).toInt(), inputBorder)
        }

        val promptColor = if (dark) 0xFF80D8FF.toInt() else 0xFF0288D1.toInt()
        txtShellPrompt.setTextColor(promptColor)
        etScriptPath.setTextColor(promptColor)
        etScriptPath.setHintTextColor(if (dark) 0xFF616161.toInt() else 0xFF9E9E9E.toInt())

        // Quick action buttons under path
        val actionBtnBg = if (dark) 0xFF242424.toInt() else 0xFFE0E0E0.toInt()
        val actionBtnText = if (dark) 0xFFFFFFFF.toInt() else 0xFF000000.toInt()

        fun styleActionBtn(btn: Button) {
            (btn.background.mutate() as? GradientDrawable)?.apply {
                cornerRadius = 18f * resources.displayMetrics.density
                setColor(actionBtnBg)
            }
            btn.setTextColor(actionBtnText)
        }
        styleActionBtn(btnResetDefaultPath)
        styleActionBtn(btnPastePath)
        styleActionBtn(btnTestScript)

        // 10. Card 3 Details (Storage)
        txtStorageTitle.setTextColor(titleColor)
        txtStorageSubtitle.setTextColor(if (dark) 0xFFAAAAAA.toInt() else 0xFF666666.toInt())

        val uriBg = if (dark) 0xFF141414.toInt() else 0xFFF2F2F2.toInt()
        val uriText = if (dark) 0xFFCCCCCC.toInt() else 0xFF333333.toInt()
        (txtStorageUriDisplay.background.mutate() as? GradientDrawable)?.apply {
            cornerRadius = 12f * resources.displayMetrics.density
            setColor(uriBg)
        }
        txtStorageUriDisplay.setTextColor(uriText)

        if (dark) {
            btnChangeFolder.setBackgroundResource(R.drawable.rounded_button_ripple_white)
            btnChangeFolder.setTextColor(0xFF000000.toInt())
        } else {
            btnChangeFolder.setBackgroundResource(R.drawable.rounded_button_ripple_black)
            btnChangeFolder.setTextColor(0xFFFFFFFF.toInt())
        }

        // 11. Card 4 Details (About)
        txtAboutTitle.setTextColor(titleColor)
        txtAboutSubtitle.setTextColor(if (dark) 0xFFAAAAAA.toInt() else 0xFF666666.toInt())

        updateStorageDisplay()
    }

    companion object {
        const val KEY_DARK_MODE = "dark_mode"
    }
}
