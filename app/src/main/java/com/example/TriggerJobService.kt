package com.example

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.IBinder

/**
 * Background JobService triggered by file modifications in Android media storage.
 *
 * Note: Returns false to indicate the work completed synchronously within this method call.
 * Re-arming is done inline before return.
 */
class TriggerJobService : JobService() {

    override fun onStartJob(params: JobParameters?): Boolean {
        LogManager.log(this, "job fired")

        // Read script path fresh on every fire from SharedPreferences (never cached)
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val scriptPath = prefs.getString(PREF_KEY_SCRIPT_PATH, DEFAULT_SCRIPT_PATH) ?: DEFAULT_SCRIPT_PATH

        // Execute dispatch via fallback chain (startService -> bindService)
        dispatchTermux(this, scriptPath)

        // ContentUri triggers are one-shot; re-arm inline
        scheduleJob(this)
        LogManager.log(this, "job re-scheduled")

        // Returns false to indicate the work completed synchronously within this method call. Re-arming is done inline before return.
        return false
    }

    override fun onStopJob(params: JobParameters?): Boolean {
        // No ongoing async work to cancel
        return false
    }

    companion object {
        const val JOB_ID = 4040
        const val PREFS_NAME = "app_prefs"
        const val PREF_KEY_TRIGGER_ENABLED = "trigger_enabled"
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

        /**
         * Configures and schedules the ContentUri trigger job in JobScheduler.
         */
        fun scheduleJob(context: Context) {
            val jobScheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
            val component = ComponentName(context, TriggerJobService::class.java)
            val uri = Uri.parse("content://media/external/file")
            val triggerUri = JobInfo.TriggerContentUri(uri, JobInfo.TriggerContentUri.FLAG_NOTIFY_FOR_DESCENDANTS)

            val builder = JobInfo.Builder(JOB_ID, component)
                .addTriggerContentUri(triggerUri)
                .setTriggerContentUpdateDelay(0)
                .setTriggerContentMaxDelay(1000)

            jobScheduler.schedule(builder.build())
        }

        /**
         * Cancels the trigger job in JobScheduler.
         */
        fun cancelJob(context: Context) {
            val jobScheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
            jobScheduler.cancel(JOB_ID)
        }

        /**
         * Checks whether the job is currently scheduled in JobScheduler.
         */
        fun isJobScheduled(context: Context): Boolean {
            val jobScheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
            return jobScheduler.getPendingJob(JOB_ID) != null
        }
    }
}
