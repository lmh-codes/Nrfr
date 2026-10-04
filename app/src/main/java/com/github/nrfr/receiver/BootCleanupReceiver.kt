package com.github.nrfr.receiver

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import com.github.nrfr.manager.CarrierConfigManager
import com.github.nrfr.manager.ShizukuHelper
import rikka.shizuku.Shizuku
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * 开机后回写卡1/卡2覆盖。不拉起 Shizuku 界面，等用户自己连上后再写。
 */
class BootCleanupReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) {
            return
        }
        val appContext = context.applicationContext
        scheduleClearJob(appContext, delayMs = 5_000L)
        scheduleClearJob(appContext, delayMs = 60_000L, jobId = JOB_ID_RETRY)
    }

    companion object {
        const val JOB_ID = 15501
        const val JOB_ID_RETRY = 15502
        private const val TAG = "NrfrBootClear"

        private val SHIZUKU_PACKAGES = listOf(
            "moe.shizuku.privileged.api",
            "moe.shizuku.manager"
        )

        fun tryStartShizuku(context: Context) {
            if (ShizukuHelper.hasPermission() || ShizukuHelper.isBinderAvailable()) {
                return
            }
            for (pkg in SHIZUKU_PACKAGES) {
                val launch = context.packageManager.getLaunchIntentForPackage(pkg) ?: continue
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching {
                    context.startActivity(launch)
                    Log.i(TAG, "started Shizuku package $pkg")
                    return
                }
            }
        }

        fun scheduleClearJob(
            context: Context,
            delayMs: Long = 3_000L,
            jobId: Int = JOB_ID
        ) {
            val scheduler = context.getSystemService(JobScheduler::class.java) ?: return
            val component = ComponentName(context, BootRestoreJobService::class.java)
            val job = JobInfo.Builder(jobId, component)
                .setMinimumLatency(delayMs)
                .setOverrideDeadline(delayMs + 30_000L)
                .setPersisted(true)
                .setRequiresCharging(false)
                .setRequiresDeviceIdle(false)
                .build()
            scheduler.schedule(job)
            Log.i(TAG, "scheduled boot clear job id=$jobId delay=$delayMs")
        }
    }
}

class BootRestoreJobService : JobService() {
    @Volatile
    private var worker: Thread? = null

    override fun onStartJob(params: JobParameters?): Boolean {
        worker = Thread({
            try {
                if (!waitForShizuku(TIMEOUT_MS)) {
                    Log.w(TAG, "Shizuku not ready; will retry job")
                    jobFinished(params, true)
                    return@Thread
                }
                CarrierConfigManager.ensureClearedAfterReboot(applicationContext)
                Log.i(TAG, "boot restore completed")
                jobFinished(params, false)
            } catch (error: Throwable) {
                Log.w(TAG, "boot clear failed; will retry", error)
                jobFinished(params, true)
            }
        }, "NrfrBootClear").also { it.start() }
        return true
    }

    override fun onStopJob(params: JobParameters?): Boolean {
        worker?.interrupt()
        worker = null
        return true
    }

    private fun waitForShizuku(timeoutMs: Long): Boolean {
        if (ShizukuHelper.hasPermission()) {
            return true
        }
        val latch = CountDownLatch(1)
        val binderListener = Shizuku.OnBinderReceivedListener {
            if (ShizukuHelper.hasPermission()) {
                latch.countDown()
            }
        }
        return try {
            runCatching { Shizuku.addBinderReceivedListener(binderListener) }
            val deadline = System.currentTimeMillis() + timeoutMs
            while (System.currentTimeMillis() < deadline) {
                if (ShizukuHelper.hasPermission()) {
                    return true
                }
                if (Thread.currentThread().isInterrupted) {
                    return false
                }
                val remaining = deadline - System.currentTimeMillis()
                if (remaining <= 0L) {
                    break
                }
                latch.await(minOf(remaining, POLL_MS), TimeUnit.MILLISECONDS)
            }
            ShizukuHelper.hasPermission()
        } finally {
            runCatching { Shizuku.removeBinderReceivedListener(binderListener) }
        }
    }

    companion object {
        private const val TAG = "NrfrBootClear"
        private const val TIMEOUT_MS = 90_000L
        private const val POLL_MS = 2_000L
    }
}
