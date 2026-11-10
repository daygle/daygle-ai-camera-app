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
import android.net.ConnectivityManager
import android.net.Network
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
import com.daygle.aicamera.data.SettingsStore
import com.daygle.aicamera.data.asHeaderValue
import com.daygle.aicamera.data.suspendRunCatching
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
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

    @Inject
    lateinit var settings: SettingsStore

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val alertId = AtomicInteger(2000)
    private val running = AtomicBoolean(false)

    /** Set when a settings re-sync cancels the stream, so it reconnects at once. */
    private val resyncRequested = AtomicBoolean(false)

    /**
     * Where the last stream got to, so a reconnect can ask ntfy to replay what
     * was published while disconnected. Only touched by the stream loop.
     */
    private var resumePoint: ResumePoint? = null

    /** Ids of recently posted alerts: a replay overlaps the last stream by design. */
    private val seenMessages = RecentIds(capacity = 200)

    @Volatile
    private var currentCall: okhttp3.Call? = null

    /**
     * The device's current default network, or null while offline. The loop
     * waits on it instead of retrying (and waking the radio) on a timer while
     * there is nothing to connect over. Unused if the callback can't be
     * registered, in which case the loop simply retries with backoff.
     */
    private val defaultNetwork = MutableStateFlow<Network?>(null)
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

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
                // Sanitised like the API client's headers: a pasted value with
                // a trailing newline would otherwise make OkHttp reject the
                // header and fail every stream attempt.
                val clientId = session.currentCfAccessClientId().asHeaderValue()
                val clientSecret = session.currentCfAccessClientSecret().asHeaderValue()
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
        watchNetwork()
    }

    /**
     * Follow the default network. When it changes (Wi-Fi to mobile, or back
     * after being offline) the open stream is bound to a socket on the old
     * network that may never see another byte; reconnect at once on the new
     * one rather than waiting for the read timeout. The resume point means
     * nothing published in between is missed.
     */
    private fun watchNetwork() {
        val connectivity = getSystemService(ConnectivityManager::class.java) ?: return
        defaultNetwork.value = connectivity.activeNetwork
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                val previous = defaultNetwork.value
                defaultNetwork.value = network
                if (previous != network) requestReconnect()
            }

            override fun onLost(network: Network) {
                defaultNetwork.compareAndSet(network, null)
            }
        }
        runCatching { connectivity.registerDefaultNetworkCallback(callback) }
            .onSuccess { networkCallback = callback }
            .onFailure { Log.w(TAG, "Could not watch network changes", it) }
    }

    private val watchingNetwork: Boolean get() = networkCallback != null

    private val hasNetwork: Boolean get() = !watchingNetwork || defaultNetwork.value != null

    /** Drop the current stream (if any) so the loop reconnects straight away. */
    private fun requestReconnect() {
        val call = currentCall ?: return
        resyncRequested.set(true)
        call.cancel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundStatus()
        // onStartCommand can fire repeatedly (app launch, the keep-alive
        // worker, a settings re-sync); only ever run one stream loop.
        if (running.compareAndSet(false, true)) {
            scope.launch { runLoop() }
        } else if (intent?.action == ACTION_RESYNC) {
            // The settings changed. Drop the old stream so runLoop reads the
            // new server/topic/credentials immediately instead of waiting for
            // the remote connection to time out. A plain start (keep-alive,
            // boot) leaves a healthy stream alone.
            resyncRequested.set(true)
            currentCall?.cancel()
        }
        return START_STICKY
    }

    /**
     * A service restarted by the system (after a reboot, or a process kill)
     * starts with a fresh [SessionManager] that knows nothing about the saved
     * server. Reload it so an Access-protected ntfy endpoint still receives the
     * Cloudflare service-token headers; signing in is not needed just to stream.
     */
    private suspend fun restoreConnection() {
        if (session.currentBaseUrl != null) return
        suspendRunCatching { session.update(settings.current()) }
            .onFailure { Log.w(TAG, "Could not restore the saved connection", it) }
    }

    private suspend fun runLoop() {
        try {
            restoreConnection()
            var backoffMs = MIN_BACKOFF_MS
            while (scope.isActive) {
                resyncRequested.set(false)
                // A storage/Keystore failure must not escape this coroutine: an
                // uncaught exception here would crash the whole process.
                val config = try {
                    notificationSettings.current()
                } catch (e: Exception) {
                    if (!resyncRequested.get()) Log.w(TAG, "Could not read the alert settings", e)
                    delay(backoffMs)
                    backoffMs = (backoffMs * 2).coerceAtMost(MAX_BACKOFF_MS)
                    continue
                }
                if (!config.isSubscribable) {
                    stopSelfSafely()
                    return
                }
                // Offline: sleep until a network appears rather than failing
                // a connect attempt every backoff interval, then connect at
                // once (re-reading the settings, which may have changed).
                if (!hasNetwork) {
                    defaultNetwork.first { it != null }
                    backoffMs = MIN_BACKOFF_MS
                    continue
                }
                // A first connect streams only what is published from now on;
                // a reconnect to the same subscription resumes from the last
                // server time seen (see [ResumePoint]).
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
                // The connection dropped because the device went offline: the
                // top of the loop waits for the network, with no timer.
                if (!hasNetwork) continue
                delay(backoffMs)
                backoffMs = (backoffMs * 2).coerceAtMost(MAX_BACKOFF_MS)
            }
        } finally {
            // The loop has finished (nothing left to subscribe to, or the scope
            // was cancelled). Clear the guard so a start that races this exit -
            // e.g. a settings re-sync or the keep-alive worker hitting the
            // stopping service - can launch a fresh stream loop instead of
            // being silently ignored while the old instance lingers.
            running.set(false)
        }
    }

    /** Open one streaming connection; returns when the server closes it. */
    private fun streamOnce(config: NotificationConfig) {
        val base = streamUrl(config)
            ?: throw IllegalArgumentException("Invalid ntfy server address: ${config.serverUrl}")
        val subscription = "${config.username.trim()}@$base"
        val since = resumePoint?.takeIf { it.subscription == subscription }?.serverTime
        val url = if (since != null) base.newBuilder().addQueryParameter("since", since.toString()).build() else base
        val builder = Request.Builder().url(url).get()
        if (config.username.isNotBlank()) {
            builder.header("Authorization", Credentials.basic(config.username, config.password))
        }
        val call = client.newCall(builder.build())
        currentCall = call
        try {
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
                    // Messages and keepalives carry the server's clock;
                    // remember the latest as the resume point. Not `open`:
                    // it arrives before a replay, which could still be cut off.
                    if (message.event != "open" && message.time > (resumePoint?.serverTime ?: 0)) {
                        resumePoint = ResumePoint(subscription, message.time)
                    }
                    if (message.event == "message" && seenMessages.add(message.id)) {
                        postAlert(message)
                    }
                }
            }
        } finally {
            // Don't keep a finished call reachable until the next reconnect.
            currentCall = null
        }
    }

    private fun postAlert(message: NtfyMessage) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) return
        val title = message.title?.takeIf { it.isNotBlank() } ?: "Camera alert"
        val text = message.message?.takeIf { it.isNotBlank() } ?: "A detection alert was triggered."

        // The Daygle server names the triggering event in an ntfy tag
        // ("daygle-event-123"; older servers only in the body text). Carry it
        // through the tap intent so tapping the notification opens that
        // event's annotated snapshot directly.
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
        networkCallback?.let { callback ->
            runCatching { getSystemService(ConnectivityManager::class.java)?.unregisterNetworkCallback(callback) }
        }
        networkCallback = null
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

        /** Start action: the settings changed, so reconnect even if streaming. */
        const val ACTION_RESYNC = "com.daygle.aicamera.action.RESYNC_ALERTS"

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
 * `{server}/{topic}/json`, or `{server}/{topic},{userTopic}/json` to listen to
 * the shared topic and the user's own topic on one connection (ntfy accepts a
 * comma-separated topic list). Each topic is percent-encoded, so a stray `/`,
 * `?`, `#` or `,` in it can't point the subscription somewhere else. A server
 * address typed without a scheme defaults to https.
 */
internal fun streamUrl(config: NotificationConfig): HttpUrl? {
    val server = config.serverUrl.trim()
    val topic = config.topic.trim()
    if (server.isEmpty() || topic.isEmpty()) return null
    val withScheme = if ("://" in server) server else "https://$server"
    val topics = listOf(topic, config.userTopic.trim())
        .filter { it.isNotEmpty() }
        .distinct()
        .joinToString(",") { it.encodeTopic() }
    return withScheme.toHttpUrlOrNull()
        ?.newBuilder()
        ?.addEncodedPathSegment(topics)
        ?.addPathSegment("json")
        ?.build()
}

/**
 * The server time (unix seconds) a stream last reported, for the subscription
 * (ntfy user and stream URL) it belongs to. ntfy's `since=<time>` replays
 * cached messages from that second on, so alerts published during a dropped
 * connection still arrive; a changed subscription starts fresh.
 */
private data class ResumePoint(val subscription: String, val serverTime: Long)

/**
 * A bounded set of the most recent message ids. [add] returns false for an id
 * already seen, so a replay that overlaps the previous stream doesn't post the
 * same alert twice. Messages without an id are always treated as new.
 */
internal class RecentIds(private val capacity: Int) {
    private val ids = LinkedHashSet<String>()

    fun add(id: String?): Boolean {
        if (id.isNullOrBlank()) return true
        if (!ids.add(id)) return false
        if (ids.size > capacity) ids.remove(ids.first())
        return true
    }
}

/** Percent-encode one topic for a path segment, commas included. */
private fun String.encodeTopic(): String =
    java.net.URLEncoder.encode(this, "UTF-8").replace("+", "%20")

@Serializable
internal data class NtfyMessage(
    val id: String? = null,
    val time: Long = 0,
    val event: String? = null,
    val topic: String? = null,
    val message: String? = null,
    val title: String? = null,
    val priority: Int = 3,
    val tags: List<String> = emptyList(),
)

/** ntfy tag the Daygle server puts on detection alerts, e.g. `daygle-event-123`. */
private const val EVENT_TAG_PREFIX = "daygle-event-"

private val EVENT_ID_PATTERN = Regex("""Event\s*ID\s*[:=]\s*(\d+)""", RegexOption.IGNORE_CASE)

/**
 * The Daygle event an alert is about: from its `daygle-event-<id>` tag, or,
 * for servers that predate the tag, the "Event ID: 123" line in its body.
 */
internal fun eventIdFrom(message: NtfyMessage): Int? =
    message.tags.firstNotNullOfOrNull { tag ->
        tag.trim().takeIf { it.startsWith(EVENT_TAG_PREFIX) }?.removePrefix(EVENT_TAG_PREFIX)?.toIntOrNull()
    } ?: message.message?.let { EVENT_ID_PATTERN.find(it)?.groupValues?.get(1)?.toIntOrNull() }
