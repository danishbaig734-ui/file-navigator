package com.example

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/**
 * Quick Settings tile service that triggers the Termux script.
 */
class TriggerTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        qsTile?.let { tile ->
            tile.label = "Run File Bus"
            tile.state = Tile.STATE_INACTIVE
            tile.updateTile()
        }
    }

    override fun onClick() {
        super.onClick()
        val prefs = getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE)
        val scriptPath = prefs.getString(
            MainActivity.PREF_KEY_SCRIPT_PATH,
            MainActivity.DEFAULT_SCRIPT_PATH
        ) ?: MainActivity.DEFAULT_SCRIPT_PATH

        val termuxIntent = Intent().apply {
            setClassName("com.termux", "com.termux.app.RunCommandService")
            action = "com.termux.RUN_COMMAND"
            putExtra("com.termux.RUN_COMMAND_PATH", scriptPath)
            putExtra("com.termux.RUN_COMMAND_BACKGROUND", true)
        }

        try {
            startService(termuxIntent)
            LogManager.log(this, "tile tapped — Termux intent sent")
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
                        LogManager.log(this, "tile tapped — Termux intent sent")
                    } else {
                        LogManager.log(this, "tile tap failed: bindService returned false")
                    }
                } catch (bindEx: Exception) {
                    LogManager.log(this, "tile tap failed: ${bindEx.message}")
                }
            } else {
                LogManager.log(this, "tile tap failed: ${e.message}")
            }
        }
    }
}
