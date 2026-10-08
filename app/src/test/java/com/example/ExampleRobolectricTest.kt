package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun testAppNameString() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Termux Trigger", appName)
    }

    @Test
    fun testLogManagerWriteAndTruncate() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        LogManager.clearLog(context)

        // Write 105 entries
        for (i in 1..105) {
            LogManager.log(context, "Entry #$i")
        }

        val logContent = LogManager.readLog(context)
        val lines = logContent.lines().filter { it.isNotBlank() }

        // Must cap at 100 entries
        assertEquals(100, lines.size)
        // Oldest entry should be Entry #6
        assertTrue(lines.first().contains("Entry #6"))
        // Newest entry should be Entry #105
        assertTrue(lines.last().contains("Entry #105"))
    }

    @Test
    fun testPreferencesDefaultScriptPath() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE)
        val path = prefs.getString(MainActivity.PREF_KEY_SCRIPT_PATH, MainActivity.DEFAULT_SCRIPT_PATH)
        assertEquals("/data/data/com.termux/files/home/file-bus.sh", path)
    }
}
