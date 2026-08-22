package com.daygle.aicamera.push

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Restarts the alert listener after a device reboot if the user had push
 * enabled. Best-effort: some Android versions restrict starting a foreground
 * service from boot, so failures are swallowed inside [PushController.start]
 * and the listener simply resumes the next time the app is opened.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                PushController.sync(context.applicationContext)
            } catch (e: Exception) {
                // Never let a boot-time sync failure crash the app; the
                // keep-alive worker retries once the app is running.
                android.util.Log.w("BootReceiver", "Push sync after boot failed: ${e.message}")
            } finally {
                pending.finish()
            }
        }
    }
}
