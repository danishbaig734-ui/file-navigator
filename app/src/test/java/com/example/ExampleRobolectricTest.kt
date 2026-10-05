package com.example

import android.app.job.JobScheduler
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun testScheduleAndCancelJob() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        // Schedule job without throwing IllegalArgumentException
        TriggerJobService.scheduleJob(context)
        val jobScheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
        val pendingJob = jobScheduler.getPendingJob(TriggerJobService.JOB_ID)
        assertTrue("Job should be scheduled in JobScheduler", pendingJob != null)

        // Cancel job
        TriggerJobService.cancelJob(context)
        val afterCancel = jobScheduler.getPendingJob(TriggerJobService.JOB_ID)
        assertFalse("Job should be cancelled in JobScheduler", afterCancel != null)
    }
}
