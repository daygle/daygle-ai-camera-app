package com.daygle.aicamera.push

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationManagerCompat
import com.daygle.aicamera.MainActivity
import com.daygle.aicamera.R
import com.daygle.aicamera.data.NotificationConfig
import com.daygle.aicamera.data.NotificationSettingsStore
import com.daygle.aicamera.data.VpnRequiredException
import com.daygle.aicamera.vpn.TunnelManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject

/**
 * A foreground service that keeps a live connection to the ntfy topic the
 * Daygle server publishes detection alerts to, and raises an Android
 * notification for each incoming alert.
 *
 * This is the same "instant delivery" model the official ntfy app uses for
 * self-hosted servers without Firebase: a persistent streaming HTTP connection
 * (`GET {server}/{topic}/json`) held open by a foreground service, with
 * automatic reconnect. It delivers while the app is backgrounded; it does not
 * require Google Play Services or a cloud push project.
 */
@AndroidEntryPoint
class NtfyService : Service() {

    @Inject
    lateinit var notificationSettings: NotificationSettingsStore

    @Inject
    lateinit var tunnelManager: TunnelManager

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val alertId = AtomicInteger(2000)
    private val running = java.util.concurrent.atomic.AtomicBoolean(false)

    @Volatile
    private var currentCall: okhttp3.Call? = null

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            // ntfy sends periodic keep-alives; a finite timeout lets the loop
            // recover when a mobile network silently drops the connection.
            .readTimeout(75, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            // Fail closed: never open the alert stream outside the tunnel when
            // VPN-only mode is on.
            .addInterceptor { chain ->
                if (tunnelManager.vpnOnlyEnabled && !tunnelManager.tunnelUp) {
                    throw VpnRequiredException()
                }
                chain.proceed(chain.request())
            }
            .build()
    }

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundStatus()
        // onStartCommand can fire repeatedly (every time the app re-syncs the
        // listener); only ever run one stream loop.
        if (running.compareAndSet(false, true)) {
            scope.launch { runLoop() }
        }
        return START_STICKY
    }

    private suspend fun runLoop() {
        var backoffMs = 2_000L
        while (scope.isActive) {
            val config = notificationSettings.current()
            if (!config.isSubscribable) {
                stopSelfSafely()
                return
            }
            // Raise the WireGuard tunnel (no-op unless VPN-only mode is on) so
            // alerts keep flowing while the app is backgrounded.
            tunnelManager.ensureUp()
            try {
                // The JSON stream delivers only messages published after the
                // connection opens, so there's no history to replay on connect.
                streamOnce(config)
                backoffMs = 2_000L
            } catch (e: VpnRequiredException) {
                // Tunnel not up yet: poll briefly instead of backing off, so the
                // stream reconnects promptly once WireGuard reconnects.
                Log.i(TAG, "Waiting for VPN tunnel before opening alert stream")
                if (!scope.isActive) return
                delay(3_000L)
                continue
            } catch (e: Exception) {
                Log.w(TAG, "ntfy stream error: ${e.message}")
            }
            if (!scope.isActive) return
            delay(backoffMs)
            backoffMs = (backoffMs * 2).coerceAtMost(60_000L)
        }
    }

    /** Open one streaming connection; returns when the server closes it. */
    private fun streamOnce(config: NotificationConfig): Int {
        val base = config.serverUrl.trim().trimEnd('/')
        val url = "$base/${config.topic.trim()}/json"
        val builder = Request.Builder().url(url).get()
        if (config.username.isNotBlank()) {
            builder.header("Authorization", Credentials.basic(config.username, config.password))
        }
        val call = client.newCall(builder.build())
        currentCall = call
        var count = 0
        call.execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("ntfy returned HTTP ${response.code}")
            }
            val source = response.body.source()
            while (scope.isActive && !source.exhausted()) {
                val line = source.readUtf8Line() ?: break
                if (line.isBlank()) continue
                val message = runCatching { json.decodeFromString<NtfyMessage>(line) }.getOrNull() ?: continue
                if (message.event == "message") {
                    postAlert(message)
                    count++
                }
            }
        }
        return count
    }

    private fun postAlert(message: NtfyMessage) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) return
        val title = message.title?.takeIf { it.isNotBlank() } ?: "Camera alert"
        val text = message.message?.takeIf { it.isNotBlank() } ?: "A detection alert was triggered."

        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val notification = NotificationCompat.Builder(this, ALERTS_CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_alert)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(if (message.priority >= 4) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()

        runCatching {
            NotificationManagerCompat.from(this).notify(alertId.incrementAndGet(), notification)
        }.onFailure { error ->
            Log.w(TAG, "Could not post camera alert notification", error)
        }
    }

    private fun startForegroundStatus() {
        val notification: Notification = NotificationCompat.Builder(this, STATUS_CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_alert)
            .setContentTitle("Watching for camera alerts")
            .setContentText("Connected to your Daygle AI Camera server.")
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .build()
        // The `specialUse` foreground-service type only exists on API 34+.
        // On older versions, start foreground without a declared type.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(STATUS_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(STATUS_ID, notification)
        }
    }

    private fun stopSelfSafely() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createChannels() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(STATUS_CHANNEL, "Alert listener", NotificationManager.IMPORTANCE_MIN).apply {
                description = "Ongoing status while listening for camera alerts."
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(ALERTS_CHANNEL, "Camera alerts", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Object and sound detection alerts from your cameras."
                enableVibration(true)
            },
        )
    }

    override fun onDestroy() {
        currentCall?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "NtfyService"
        private const val STATUS_CHANNEL = "alert_listener"
        private const val ALERTS_CHANNEL = "camera_alerts"
        private const val STATUS_ID = 1001
    }
}

@Serializable
private data class NtfyMessage(
    val id: String? = null,
    val time: Long = 0,
    val event: String? = null,
    val topic: String? = null,
    val message: String? = null,
    val title: String? = null,
    val priority: Int = 3,
)
