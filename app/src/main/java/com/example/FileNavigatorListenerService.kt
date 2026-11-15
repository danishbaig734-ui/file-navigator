package com.example

import android.app.Notification
import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class FileNavigatorListenerService : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        // Only listen to Termux notifications with ID 999
        if (sbn.packageName == "com.termux" && sbn.id == 999) {
            val extras = sbn.notification.extras
            val content = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
                ?: extras.getString(Notification.EXTRA_TEXT)
                ?: ""

            // Send the content to the Activity
            val intent = Intent(ACTION_PROGRESS).apply {
                setPackage(packageName)
                putExtra(EXTRA_MESSAGE, content)
            }
            sendBroadcast(intent)
        }
    }

    companion object {
        const val ACTION_PROGRESS = "FILE_NAVIGATOR_PROGRESS"
        const val EXTRA_MESSAGE = "message"
    }
}
