package com.daygle.aicamera.data

import android.util.Log
import com.daygle.aicamera.data.model.ActivityStats
import com.daygle.aicamera.data.model.AuthUser
import com.daygle.aicamera.data.model.Camera
import com.daygle.aicamera.data.model.CameraHealthResponse
import com.daygle.aicamera.data.model.Event
import com.daygle.aicamera.data.model.EventSearchResponse
import com.daygle.aicamera.data.model.LibraryFacets
import com.daygle.aicamera.data.model.LibraryQuery
import com.daygle.aicamera.data.model.Page
import com.daygle.aicamera.data.model.PushSettings
import com.daygle.aicamera.data.model.PushTestRequest
import com.daygle.aicamera.data.model.Recording
import com.daygle.aicamera.data.model.ServerResources
import com.daygle.aicamera.data.model.StorageStatus
import com.daygle.aicamera.data.model.SystemSettings
import com.daygle.aicamera.data.model.TimelineResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import retrofit2.HttpException

/**
 * Thin domain layer over [SessionManager]/[DaygleApi]. Each call returns a
 * [Result] so screens can render a friendly error instead of crashing when the
 * server is unreachable or the session cannot be established.
 */
class CameraRepository(
    private val session: SessionManager,
    private val settings: SettingsStore,
) {

    private val _account = MutableStateFlow<AuthUser?>(null)

    /** Work that outlives the screen that started it (a background sign-in). */
    private val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * The signed-in user (id and role), or null while unknown: before the
     * first successful sign-in, after signing out, or while the server is
     * unreachable. Screens hide admin-only items unless this says admin.
     */
    val account: StateFlow<AuthUser?> = _account.asStateFlow()

    /**
     * Re-read who is signed in (`GET /api/auth/me`), e.g. when an admin may
     * have changed this account's role. A failure keeps what was known.
     */
    suspend fun refreshAccount(): AuthUser? {
        suspendRunCatching { session.api.me().user }
            .onSuccess { user -> _account.value = user }
            .onFailure { Log.w(TAG, "Could not read the signed-in account", it) }
        return _account.value
    }

    /** Whether the signed-in user is an admin, asking the server if not yet known. */
    suspend fun isAdmin(): Boolean = (_account.value ?: refreshAccount())?.isAdmin == true

    /** Verify connection details and persist them only after a successful login. */
    suspend fun connect(connection: Connection): LoginResult {
        session.update(connection)
        val result = session.login()
        if (result is LoginResult.Success) {
            // A storage/Keystore failure must not crash a successful sign-in;
            // the session still works for this launch.
            suspendRunCatching { settings.save(connection) }
                .onFailure { Log.w(TAG, "Could not save the connection", it) }
            // A different server or account: forget the old role first.
            _account.value = null
            refreshAccount()
        } else {
            // The attempt pointed the live session at the server that just
            // rejected it. Put the saved connection back so the rest of the
            // app keeps using the server it was working with; a failed save
            // must not silently switch the app to a bad address.
            val persisted = suspendRunCatching { settings.current() }.getOrNull()
            if (persisted != null && persisted != connection) session.update(persisted)
        }
        return result
    }

    /**
     * Re-apply the persisted connection to the session (e.g. on app launch).
     *
     * The session cookie lives only in memory, so after a process restart it
     * is always gone. Signing in here - before any screen fires its first
     * API call - avoids a guaranteed 401 in the server log on every launch.
     *
     * The launch waits at most [RESTORE_SIGN_IN_WAIT_MS] for it: an
     * unreachable server would otherwise hold the app on its loading screen
     * through every connect and read timeout. A slower sign-in carries on in
     * the background, and API calls made meanwhile wait for it (they re-use
     * its session through [AuthInterceptor]). Failures (offline, tunnel down)
     * are ignored: screens recover via the usual lazy re-auth path.
     */
    suspend fun restore(): Boolean {
        settings.migrateLegacyCredentials()
        val connection = settings.current()
        session.update(connection)
        if (connection.isConfigured) {
            val signIn = backgroundScope.async {
                if (session.login() is LoginResult.Success) refreshAccount()
            }
            withTimeoutOrNull(RESTORE_SIGN_IN_WAIT_MS) { signIn.await() }
        }
        return connection.isConfigured
    }

    /** Forget the server and end the session. */
    suspend fun disconnect() {
        // Revoke the session server-side too; otherwise it stays valid until it
        // expires. Best effort - signing out must work offline.
        session.logout()
        _account.value = null
        settings.clear()
        session.update(Connection())
    }

    /** The persisted connection, so the connect screen can pre-fill it. */
    suspend fun currentConnection(): Connection = settings.current()

    suspend fun cameras(): Result<List<Camera>> = suspendRunCatching { session.api.cameras().cameras }

    suspend fun cameraHealth(): Result<CameraHealthResponse> = suspendRunCatching { session.api.cameraHealth() }

    /** One server-filtered cursor page of events (`GET /api/events`). */
    suspend fun eventsPage(query: LibraryQuery, cursor: String? = null): Result<Page<Event>> =
        suspendRunCatching {
            session.api.events(
                limit = PAGE_SIZE,
                cursor = cursor,
                alertedOnly = query.alertedOnly,
                since = query.since,
                until = query.until,
                cameraId = query.cameraId,
                label = query.label,
                query = query.q,
                face = query.face,
                sort = query.sort,
                aiCheck = query.aiCheck?.queryValue,
                falsePositive = query.falsePositive?.queryValue,
            )
        }

    /**
     * One server-filtered cursor page of events with a stored snapshot
     * (`GET /api/snapshots`), including ones not linked to a recording.
     * Servers without that endpoint fall back to the events feed filtered to
     * entries that advertise a snapshot.
     */
    suspend fun snapshotsPage(query: LibraryQuery, cursor: String? = null): Result<Page<Event>> =
        suspendRunCatching {
            try {
                session.api.snapshots(
                    limit = PAGE_SIZE,
                    cursor = cursor,
                    since = query.since,
                    until = query.until,
                    cameraId = query.cameraId,
                    label = query.label,
                    query = query.q,
                    alertedOnly = query.alertedOnly,
                    face = query.face,
                    sort = query.sort,
                    falsePositive = query.falsePositive?.queryValue,
                )
            } catch (e: HttpException) {
                if (e.code() != 404) throw e
                val page = session.api.events(
                    limit = PAGE_SIZE,
                    cursor = cursor,
                    withRecording = false,
                    alertedOnly = query.alertedOnly,
                    since = query.since,
                    until = query.until,
                    cameraId = query.cameraId,
                    label = query.label,
                    query = query.q,
                    face = query.face,
                    sort = query.sort,
                    falsePositive = query.falsePositive?.queryValue,
                )
                page.copy(items = page.items.filter { it.hasSnapshot || !it.snapshotPath.isNullOrBlank() })
            }
        }

    /** One server-filtered cursor page of recordings (`GET /api/recordings`). */
    suspend fun recordingsPage(query: LibraryQuery, cursor: String? = null): Result<Page<Recording>> =
        suspendRunCatching {
            session.api.recordings(
                limit = PAGE_SIZE,
                cursor = cursor,
                cameraId = query.cameraId,
                startedAfter = query.since,
                startedBefore = query.until,
                label = query.label,
                query = query.q,
                alertedOnly = query.alertedOnly,
                face = query.face,
                sort = query.sort,
                falsePositive = query.falsePositive?.queryValue,
            )
        }

    /**
     * Label and face filter options for a library list ([kind] is `events`,
     * `snapshots` or `recordings`) over the given window.
     */
    suspend fun libraryFacets(kind: String, since: String?, until: String?): Result<LibraryFacets> =
        suspendRunCatching { session.api.libraryFacets(kind, since, until) }

    /**
     * Recordings started between [since] and [until] (ISO-8601), up to one
     * full server page. For servers without the timeline endpoint, which then
     * build the day's timeline from these.
     */
    suspend fun recordingsBetween(since: String?, until: String?): Result<List<Recording>> =
        suspendRunCatching {
            session.api.recordings(limit = MAX_PAGE_SIZE, startedAfter = since, startedBefore = until).items
        }

    suspend fun event(eventId: Int): Result<Event> = suspendRunCatching { session.api.event(eventId) }

    /** Plain-English AI search over described events (`GET /api/event-search`). */
    suspend fun searchEvents(query: String): Result<EventSearchResponse> =
        suspendRunCatching { session.api.eventSearch(query) }

    suspend fun recording(recordingId: Int): Result<Recording> =
        suspendRunCatching { session.api.recording(recordingId) }

    /** Pre-grouped timeline for a camera-day; server clamps to the day and localizes via [tzOffsetMinutes]. */
    suspend fun recordingsTimeline(
        cameraId: String? = null,
        day: String? = null,
        tzOffsetMinutes: Int? = null,
    ): Result<TimelineResponse> =
        suspendRunCatching { session.api.recordingsTimeline(cameraId, day, tzOffsetMinutes) }

    suspend fun pushSettings(): Result<PushSettings> = suspendRunCatching { session.api.pushSettings() }

    /**
     * Who a server-sent test push can go to: the signed-in user's id and the
     * users list, or null for a non-admin account (the server only lets
     * admins send a test or list users).
     */
    suspend fun pushTestTargets(): Result<PushTestTargets?> = suspendRunCatching {
        val me = session.api.me().user?.also { _account.value = it }
        if (me == null || !me.isAdmin) null else PushTestTargets(me.id, session.api.users())
    }

    /**
     * The alerts config for the Alerts screen, or null for a non-admin account
     * (the server only lets admins read or change it).
     */
    suspend fun alertConfig(): Result<AlertConfig?> = suspendRunCatching {
        val me = session.api.me().user?.also { _account.value = it }
        if (me == null || !me.isAdmin) {
            null
        } else {
            AlertConfig(
                users = session.api.users(),
                cameras = session.api.camerasConfig().arrayAt("cameras"),
                faceRules = session.api.faceRules().arrayAt("rules"),
                // Optional: without it the screen just leaves camera offline out.
                cameraOffline = suspendRunCatching { session.api.cameraOfflineSettings() }
                    .onFailure { Log.w(TAG, "Could not read camera offline alert settings", it) }
                    .getOrNull(),
            )
        }
    }

    /**
     * Save the edited alert config. Only the parts that changed are sent: a
     * camera save makes the server re-apply the camera settings.
     */
    suspend fun saveAlertConfig(
        cameras: JsonArray?,
        faceRules: JsonArray?,
        cameraOffline: JsonObject? = null,
    ): Result<Unit> = suspendRunCatching {
        val csrfToken = session.api.me().csrfToken.orEmpty()
        val origin = checkNotNull(session.currentBaseUrl) { "Not connected to a server." }
        if (cameras != null) session.api.saveCamerasConfig(csrfToken, origin, JsonObject(mapOf("cameras" to cameras)))
        if (faceRules != null) session.api.saveFaceRules(csrfToken, origin, JsonObject(mapOf("rules" to faceRules)))
        if (cameraOffline != null) session.api.saveCameraOfflineSettings(csrfToken, origin, cameraOffline)
    }

    /**
     * Ask the server to push a test notification to [recipients] (`user:<id>`
     * references; empty means the shared topic). Returns the topics it went to.
     */
    suspend fun sendPushTest(recipients: List<String>): Result<List<String>> = suspendRunCatching {
        val csrfToken = session.api.me().csrfToken.orEmpty()
        val origin = checkNotNull(session.currentBaseUrl) { "Not connected to a server." }
        session.api.pushTest(csrfToken, origin, PushTestRequest(recipients)).topics
    }

    /**
     * Admins only: mark an event (or the snapshot it saved) a false positive
     * ([falsePositive] true) or a real detection (false), then re-read it so
     * the caller shows what the server stored.
     */
    suspend fun markEventFalsePositive(eventId: Int, falsePositive: Boolean): Result<Event> = suspendRunCatching {
        val csrfToken = session.api.me().csrfToken.orEmpty()
        val origin = checkNotNull(session.currentBaseUrl) { "Not connected to a server." }
        session.api.markEventFalsePositive(csrfToken, origin, eventId, falsePositiveBody(falsePositive))
        session.api.event(eventId)
    }

    /**
     * Admins only: apply one decision to every event in a recording, then
     * re-read the recording (its false-positive status is worked out by the
     * server from those events).
     */
    suspend fun markRecordingFalsePositive(recordingId: Int, falsePositive: Boolean): Result<Recording> =
        suspendRunCatching {
            val csrfToken = session.api.me().csrfToken.orEmpty()
            val origin = checkNotNull(session.currentBaseUrl) { "Not connected to a server." }
            session.api.markRecordingFalsePositive(csrfToken, origin, recordingId, falsePositiveBody(falsePositive))
            session.api.recording(recordingId)
        }

    suspend fun systemSettings(): Result<SystemSettings> = suspendRunCatching { session.api.systemSettings() }

    /** Host CPU, load, RAM, GPU and video decoding (admin only). */
    suspend fun serverResources(): Result<ServerResources> = suspendRunCatching { session.api.systemResources() }

    /** Disk space, media usage and retention limits (admin only; 404 on older servers). */
    suspend fun serverStorage(): Result<StorageStatus> = suspendRunCatching { session.api.systemStorage() }

    /** Event and alert counts since [since] (ISO-8601), or all time when null (admin only). */
    suspend fun activityStats(since: String? = null): Result<ActivityStats> =
        suspendRunCatching { session.api.stats(since) }

    fun snapshotUrl(cameraId: String?, cacheBuster: Long): String? =
        session.snapshotUrl(cameraId, cacheBuster)

    fun recordingStreamUrl(recordingId: Int): String? = session.recordingStreamUrl(recordingId)

    fun recordingDownloadUrl(recordingId: Int): String? = session.recordingDownloadUrl(recordingId)

    fun eventSnapshotUrl(eventId: Int, thumbnail: Boolean = false): String? =
        session.eventSnapshotUrl(eventId, thumbnail)

    fun httpClient() = session.httpClient

    fun currentSettingsStore(): SettingsStore = settings

    companion object {
        private const val TAG = "CameraRepository"

        /** Rows per list page; more load as the user scrolls. */
        const val PAGE_SIZE = 50

        /** Longest the launch waits for [restore]'s sign-in before showing the app. */
        const val RESTORE_SIGN_IN_WAIT_MS = 4_000L

        /** The server's largest `/api/recordings` page. */
        private const val MAX_PAGE_SIZE = 500
    }
}

/** Body of the false-positive mark endpoints. */
internal fun falsePositiveBody(falsePositive: Boolean): JsonObject =
    JsonObject(mapOf("false_positive" to JsonPrimitive(falsePositive)))
