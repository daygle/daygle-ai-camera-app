package com.daygle.aicamera.push

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import com.daygle.aicamera.DaygleApp

/**
 * Starts/stops the [NtfyService] to match the persisted notification config.
 * Starting a foreground service is only reliable from a foreground context
 * (an Activity) or an allowed background entry point (boot), so callers should
 * invoke [sync] from those places — e.g. after sign-in and on app launch.
 */
object PushController {

    fun start(context: Context) {
        val intent = Intent(context, NtfyService::class.java)
        runCatching {
            ContextCompat.startForegroundService(context, intent)
        }.onFailure { Log.w(TAG, "Could not start alert listener: ${it.message}") }
    }

    fun stop(context: Context) {
        context.stopService(Intent(context, NtfyService::class.java))
    }

    /** Start or stop the listener to match the saved config. */
    suspend fun sync(context: Context) {
        val app = context.applicationContext as DaygleApp
        val config = app.container.notificationSettings.current()
        if (config.isSubscribable) start(context) else stop(context)
    }

    private const val TAG = "PushController"
}
