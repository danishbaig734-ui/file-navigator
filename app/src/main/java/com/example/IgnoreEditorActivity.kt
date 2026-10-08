package com.example

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity

/**
 * Activity for viewing and editing ignore.txt in the FileNavigator root directory.
 */
class IgnoreEditorActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    fun readIgnoreFile(treeUri: Uri): String? {
        val root = DocumentFile.fromTreeUri(this, treeUri) ?: return null
        val file = root.findFile("ignore.txt") ?: return null
        return try {
            contentResolver.openInputStream(file.uri)?.use { stream ->
                stream.bufferedReader(Charsets.UTF_8).readText()
            }
        } catch (e: Exception) {
            Log.e(TAG, "readIgnoreFile failed: ${e.message}", e)
            null
        }
    }

    fun saveIgnoreFile(treeUri: Uri, content: String): Boolean {
        return try {
            val root = DocumentFile.fromTreeUri(this, treeUri) ?: return false
            val file = root.findFile("ignore.txt") ?: root.createFile("text/plain", "ignore.txt") ?: return false
            val outputStream = contentResolver.openOutputStream(file.uri, "wt") ?: return false
            outputStream.use { stream ->
                stream.write(content.toByteArray(Charsets.UTF_8))
                stream.flush()
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "saveIgnoreFile failed: ${e.message}", e)
            false
        }
    }

    companion object {
        private const val TAG = "IgnoreEditorActivity"
    }
}
