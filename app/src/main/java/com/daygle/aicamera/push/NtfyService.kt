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
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationManagerCompat
import com.daygle.aicamera.MainActivity
import com.daygle.aicamera.R
import com.daygle.aicamera.data.NotificationConfig
import com.daygle.aicamera.data.NotificationSettingsStore
import com.daygle.aicamera.data.SessionManager
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
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
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
    lateinit var session: SessionManager

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val alertId = AtomicInteger(2000)
    private val running = AtomicBoolean(false)

    /** Set when a settings re-sync cancels the stream, so it reconnects at once. */
    private val resyncRequested = AtomicBoolean(false)

    @Volatile
    private var currentCall: okhttp3.Call? = null

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            // ntfy sends periodic keep-alives; a finite timeout lets the loop
            // recover when a mobile network silently drops the connection.
            .readTimeout(75, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            // When the Daygle server (and its ntfy endpoint) sits behind a
            // Cloudflare Tunnel protected by Cloudflare Access, the stream must
            // carry the same service-token headers as every other request.
            // The ntfy server is often a different host (e.g. public ntfy.sh),
            // so the token is only attached for the Daygle server's own host.
            // A network interceptor re-checks the host on every redirect hop,
            // so a redirect can't carry the secret to another origin either.
            .addNetworkInterceptor { chain ->
                val request = chain.request()
                val clientId = session.currentCfAccessClientId()
                val clientSecret = session.currentCfAccessClientSecret()
                if (clientId.isBlank() || clientSecret.isBlank() || !session.isServerHost(request.url.host)) {
                    chain.proceed(request)
                } else {
                    chain.proceed(
                        request.newBuilder()
                            .header("CF-Access-Client-Id", clientId)
                            .header("CF-Access-Client-Secret", clientSecret)
                            .build()
                    )
                }
            }
            .build()
    }

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannels(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundStatus()
        // onStartCommand can fire repeatedly (every time the app re-syncs the
        // listener); only ever run one stream loop.
        if (running.compareAndSet(false, true)) {
            scope.launch { runLoop() }
        } else {
            // Re-syncing after a settings change reaches the running service
            // through onStartCommand. Drop the old stream so runLoop reads the
            // new server/topic/credentials immediately instead of waiting for
            // the remote connection to time out.
            resyncRequested.set(true)
            currentCall?.cancel()
        }
        return START_STICKY
    }

    private suspend fun runLoop() {
        var backoffMs = MIN_BACKOFF_MS
        while (scope.isActive) {
            resyncRequested.set(false)
            val config = notificationSettings.current()
            if (!config.isSubscribable) {
                stopSelfSafely()
                return
            }
            // The JSON stream delivers only messages published after the
            // connection opens, so there's no history to replay on connect.
            val startedAt = SystemClock.elapsedRealtime()
            val healthy = try {
                streamOnce(config)
                true
            } catch (e: Exception) {
                if (!resyncRequested.get()) Log.w(TAG, "ntfy stream error: ${e.message}")
                false
            }
            if (!scope.isActive) return
            if (resyncRequested.get()) {
                // Settings changed: reconnect straight away with the new config.
                backoffMs = MIN_BACKOFF_MS
                continue
            }
            // A stream that ran for a while and then ended normally reconnects
            // immediately. Failures - and streams the server or a proxy closes
            // right after opening - back off exponentially so a broken setup
            // can't spin in a tight reconnect loop.
            if (healthy && SystemClock.elapsedRealtime() - startedAt >= SHORT_STREAM_THRESHOLD_MS) {
                backoffMs = MIN_BACKOFF_MS
                continue
            }
            delay(backoffMs)
            backoffMs = (backoffMs * 2).coerceAtMost(MAX_BACKOFF_MS)
        }
    }

    /** Open one streaming connection; returns when the server closes it. */
    private fun streamOnce(config: NotificationConfig) {
        val url = streamUrl(config)
            ?: throw IllegalArgumentException("Invalid ntfy server address: ${config.serverUrl}")
        val builder = Request.Builder().url(url).get()
        if (config.username.isNotBlank()) {
            builder.header("Authorization", Credentials.basic(config.username, config.password))
        }
        val call = client.newCall(builder.build())
        currentCall = call
        // A re-sync that landed after this loop read its config would have
        // cancelled the previous call; honour it for this one too.
        if (resyncRequested.get()) call.cancel()
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
                }
            }
        }
    }

    private fun postAlert(message: NtfyMessage) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) return
        val title = message.title?.takeIf { it.isNotBlank() } ?: "Camera alert"
        val text = message.message?.takeIf { it.isNotBlank() } ?: "A detection alert was triggered."

        // The Daygle server embeds the triggering event's id in the alert body
        // ("Event ID: 123"). Carry it through the tap intent so tapping the
        // notification opens that event's annotated snapshot directly.
        val eventId = eventIdFrom(message)
        // Derive the id from ntfy's unique message id so a restarted service
        // (whose counter starts over) doesn't replace alerts still on screen.
        val notificationId = message.id?.takeIf { it.isNotBlank() }
            ?.let { (it.hashCode() and Int.MAX_VALUE) or ALERT_ID_FLOOR }
            ?: alertId.incrementAndGet()
        val contentIntent = PendingIntent.getActivity(
            this,
            notificationId,
            Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                setPackage(packageName)
                eventId?.let { putExtra(EXTRA_EVENT_ID, it) }
            },
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
            NotificationManagerCompat.from(this).notify(notificationId, notification)
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
        private const val TEST_ID = 1002

        /** Streams shorter than this are treated as unhealthy for backoff purposes. */
        private const val SHORT_STREAM_THRESHOLD_MS = 5_000L
        private const val MIN_BACKOFF_MS = 2_000L
        private const val MAX_BACKOFF_MS = 60_000L

        /** Keeps hashed alert ids clear of the fixed status/test notification ids. */
        private const val ALERT_ID_FLOOR = 0x10000

        /** Intent extra on alert notifications carrying the triggering event id. */
        const val EXTRA_EVENT_ID = "com.daygle.aicamera.extra.EVENT_ID"

        /** Create the status + alert notification channels. Safe to call repeatedly. */
        fun ensureChannels(context: Context) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
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

        /**
         * Post a sample alert on the camera-alerts channel so the user can
         * confirm notifications actually appear on this device. Returns false
         * (posting nothing) when notifications are not permitted, so the caller
         * can prompt for the permission instead.
         */
        fun postTestNotification(context: Context): Boolean {
            ensureChannels(context)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) return false
            if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false

            val contentIntent = PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    setPackage(context.packageName)
                },
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val title = "Daygle AI Camera Alert: Person Detected (Test)"
            val body = "This is a test notification. If you can see this, camera alerts are working on this device."
            val notification = NotificationCompat.Builder(context, ALERTS_CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_alert)
                .setContentTitle(title)
                .setContentText("Test notification - alerts are working.")
                .setStyle(NotificationCompat.BigTextStyle().bigText("$title\n\n$body"))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setAutoCancel(true)
                .setContentIntent(contentIntent)
                .build()
            return runCatching {
                NotificationManagerCompat.from(context).notify(TEST_ID, notification)
                true
            }.getOrDefault(false)
        }
    }
}

/**
 * `{server}/{topic}/json`. The topic is added as an encoded path segment, so a
 * stray `/`, `?` or `#` in it can't point the subscription somewhere else. A
 * server address typed without a scheme defaults to https.
 */
internal fun streamUrl(config: NotificationConfig): HttpUrl? {
    val server = config.serverUrl.trim()
    val topic = config.topic.trim()
    if (server.isEmpty() || topic.isEmpty()) return null
    val withScheme = if ("://" in server) server else "https://$server"
    return withScheme.toHttpUrlOrNull()
        ?.newBuilder()
        ?.addPathSegment(topic)
        ?.addPathSegment("json")
        ?.build()
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

private val EVENT_ID_PATTERN = Regex("""Event\s*ID\s*[:=]\s*(\d+)""", RegexOption.IGNORE_CASE)

/** Extract the Daygle event id from an alert body (e.g. "Event ID: 123"). */
private fun eventIdFrom(message: NtfyMessage): Int? =
    message.message?.let { EVENT_ID_PATTERN.find(it)?.groupValues?.get(1)?.toIntOrNull() }
