package com.daygle.aicamera.push

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import com.daygle.aicamera.DaygleApp

/**
 * Starts/stops the [NtfyService] to match the persisted notification config.
 * Also schedules a periodic [PushKeepAliveWorker] so the listener restarts
 * automatically if Android kills the foreground service under memory pressure
 * — the user does not need to reopen the app to restore push delivery.
 */
object PushController {

    fun start(context: Context) {
        val intent = Intent(context, NtfyService::class.java)
        runCatching {
            ContextCompat.startForegroundService(context, intent)
        }.onFailure { Log.w(TAG, "Could not start alert listener: ${it.message}") }
        PushKeepAliveWorker.schedule(context)
    }

    fun stop(context: Context) {
        context.stopService(Intent(context, NtfyService::class.java))
        PushKeepAliveWorker.cancel(context)
    }

    /** Start or stop the listener to match the saved config. */
    suspend fun sync(context: Context) {
        val app = context.applicationContext as DaygleApp
        val config = app.container.notificationSettings.current()
        if (config.isSubscribable) start(context) else stop(context)
    }

    private const val TAG = "PushController"
}
