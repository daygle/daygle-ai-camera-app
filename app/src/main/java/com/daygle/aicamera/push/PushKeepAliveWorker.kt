package com.daygle.aicamera.push

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * Periodically syncs the push-alert listener so it restarts automatically if
 * Android kills the foreground service under memory pressure. Runs every
 * ~15 minutes; the minimum interval for periodic work.
 *
 * Scheduled when the user enables alerts. Canceled when they disable them.
 * Uses [ExistingPeriodicWorkPolicy.KEEP] to avoid duplicate work across
 * app restarts and [BootReceiver] invocations.
 */
class PushKeepAliveWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            PushController.sync(applicationContext)
            Result.success()
        } catch (e: Exception) {
            Log.w(TAG, "Keep-alive sync failed, will retry: ${e.message}")
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "PushKeepAliveWorker"
        private const val NAME = "push-keep-alive"
        private const val INTERVAL_MINUTES = 15L

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<PushKeepAliveWorker>(
                INTERVAL_MINUTES, TimeUnit.MINUTES,
            ).build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(NAME)
        }
    }
}
