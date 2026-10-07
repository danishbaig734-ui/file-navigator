package com.example

import android.app.job.JobScheduler
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * BroadcastReceiver listening for BOOT_COMPLETED.
 * Re-schedules the trigger jobs if they were active before reboot.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
            val prefs = context.getSharedPreferences(TriggerJobService.PREFS_NAME, Context.MODE_PRIVATE)
            val isEnabled = prefs.getBoolean(TriggerJobService.PREF_KEY_TRIGGER_ENABLED, false)

            if (isEnabled) {
                val jobScheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
                jobScheduler.schedule(TriggerJobService.buildFileJob(context))
                jobScheduler.schedule(TriggerJobService.buildPeriodicJob(context))
                LogManager.log(context, "re-armed trigger on boot")
            }
        }
    }
}
