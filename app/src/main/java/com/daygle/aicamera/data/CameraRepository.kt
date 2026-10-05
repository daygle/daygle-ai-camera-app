package com.daygle.aicamera.data

import com.daygle.aicamera.data.model.Camera
import com.daygle.aicamera.data.model.CameraHealthResponse
import com.daygle.aicamera.data.model.Event
import com.daygle.aicamera.data.model.EventSearchResponse
import com.daygle.aicamera.data.model.PushSettings
import com.daygle.aicamera.data.model.Recording
import com.daygle.aicamera.data.model.TimelineResponse
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

    /** Verify connection details and persist them only after a successful login. */
    suspend fun connect(connection: Connection): LoginResult {
        session.update(connection)
        val result = session.login()
        if (result is LoginResult.Success) {
            settings.save(connection)
        }
        return result
    }

    /**
     * Re-apply the persisted connection to the session (e.g. on app launch).
     *
     * The session cookie lives only in memory, so after a process restart it
     * is always gone. Signing in here - before any screen fires its first
     * API call - avoids a guaranteed 401 in the server log on every launch.
     * Failures (offline, tunnel down, etc.) are ignored: navigation proceeds
     * as before and screens recover via the usual lazy re-auth path.
     */
    suspend fun restore(): Boolean {
        settings.migrateLegacyCredentials()
        val connection = settings.current()
        session.update(connection)
        if (connection.isConfigured) {
            session.login()
        }
        return connection.isConfigured
    }

    /** Forget the server and end the session. */
    suspend fun disconnect() {
        // Revoke the session server-side too; otherwise it stays valid until it
        // expires. Best effort - signing out must work offline.
        session.logout()
        settings.clear()
        session.update(Connection())
    }

    /** The persisted connection, so the connect screen can pre-fill it. */
    suspend fun currentConnection(): Connection = settings.current()

    suspend fun cameras(): Result<List<Camera>> = suspendRunCatching { session.api.cameras().cameras }

    suspend fun cameraHealth(): Result<CameraHealthResponse> = suspendRunCatching { session.api.cameraHealth() }

    suspend fun events(alertedOnly: Boolean = false, since: String? = null): Result<List<Event>> =
        suspendRunCatching { session.api.events(alertedOnly = alertedOnly, since = since).items }

    /**
     * Events with a stored snapshot (`GET /api/snapshots`), including ones not
     * linked to a recording. Servers without that endpoint fall back to the
     * events feed filtered to entries that advertise a snapshot.
     */
    suspend fun snapshots(): Result<List<Event>> = suspendRunCatching {
        try {
            session.api.snapshots().items
        } catch (e: HttpException) {
            if (e.code() != 404) throw e
            session.api.events(withRecording = false).items
                .filter { it.hasSnapshot || !it.snapshotPath.isNullOrBlank() }
        }
    }

    suspend fun recordings(cameraId: String? = null): Result<List<Recording>> =
        suspendRunCatching { session.api.recordings(cameraId = cameraId).items }

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

    fun snapshotUrl(cameraId: String?, cacheBuster: Long): String? =
        session.snapshotUrl(cameraId, cacheBuster)

    fun recordingStreamUrl(recordingId: Int): String? = session.recordingStreamUrl(recordingId)

    fun recordingDownloadUrl(recordingId: Int): String? = session.recordingDownloadUrl(recordingId)

    fun eventSnapshotUrl(eventId: Int, thumbnail: Boolean = false): String? =
        session.eventSnapshotUrl(eventId, thumbnail)

    fun httpClient() = session.httpClient

    fun currentSettingsStore(): SettingsStore = settings
}
