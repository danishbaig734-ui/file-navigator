package com.example

import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val prefs = getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE)
        val isDark = prefs.getBoolean(KEY_DARK_MODE, true)

        val switchDarkMode = findViewById<SwitchCompat>(R.id.switchDarkMode)
        switchDarkMode.isChecked = isDark

        switchDarkMode.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(KEY_DARK_MODE, isChecked).apply()
        }

        val etScriptPath = findViewById<EditText>(R.id.etScriptPath)
        val savedPath = prefs.getString(MainActivity.PREF_KEY_SCRIPT_PATH, MainActivity.DEFAULT_SCRIPT_PATH)
            ?: MainActivity.DEFAULT_SCRIPT_PATH
        etScriptPath.setText(savedPath)

        etScriptPath.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val path = s?.toString()?.trim() ?: ""
                if (path.isNotEmpty()) {
                    prefs.edit().putString(MainActivity.PREF_KEY_SCRIPT_PATH, path).apply()
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    companion object {
        private const val KEY_DARK_MODE = "dark_mode"
    }
}
