package com.example

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.provider.DocumentsContract
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar

class RulesEditorActivity : AppCompatActivity() {

    private lateinit var toolbar: Toolbar
    private lateinit var editRules: EditText
    private lateinit var buttonPickPath: Button
    private lateinit var buttonSave: Button
    private lateinit var buttonRunScript: Button

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
                editRules.setText(DEFAULT_STARTER_TEMPLATE)
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
        initRulesFile()
    }

    private fun initViews() {
        toolbar = findViewById(R.id.toolbar)
        editRules = findViewById(R.id.edit_rules)
        buttonPickPath = findViewById(R.id.button_pick_path)
        buttonSave = findViewById(R.id.button_save)
        buttonRunScript = findViewById(R.id.button_run_script)

        toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun setupListeners() {
        buttonPickPath.setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
            pickPathLauncher.launch(intent)
        }

        buttonSave.setOnClickListener {
            saveRulesToFile(showSuccessToast = true)
        }

        buttonRunScript.setOnClickListener {
            val saved = saveRulesToFile(showSuccessToast = false)
            if (saved) {
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
            editRules.setText(content)
        } else {
            editRules.setText(DEFAULT_STARTER_TEMPLATE)
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

    private fun insertPathAtCursor(path: String) {
        val cursorPosition = editRules.selectionStart.coerceAtLeast(0)
        val text = editRules.text
        if (text != null) {
            text.insert(cursorPosition, path)
            editRules.setSelection(cursorPosition + path.length)
        } else {
            editRules.setText(path)
            editRules.setSelection(path.length)
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
