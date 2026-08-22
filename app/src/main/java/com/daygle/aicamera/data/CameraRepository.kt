package com.daygle.aicamera.data

import com.daygle.aicamera.data.model.Camera
import com.daygle.aicamera.data.model.CameraHealthResponse
import com.daygle.aicamera.data.model.Event
import com.daygle.aicamera.data.model.PushSettings
import com.daygle.aicamera.data.model.Recording

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
     * is always gone. Signing in here — before any screen fires its first
     * API call — avoids a guaranteed 401 in the server log on every launch.
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
        settings.clear()
        session.update(Connection())
    }

    /** The persisted connection, so the connect screen can pre-fill it. */
    suspend fun currentConnection(): Connection = settings.current()

    suspend fun cameras(): Result<List<Camera>> = suspendRunCatching { session.api.cameras().cameras }

    suspend fun cameraHealth(): Result<CameraHealthResponse> = suspendRunCatching { session.api.cameraHealth() }

    suspend fun events(alertedOnly: Boolean = false): Result<List<Event>> =
        suspendRunCatching { session.api.events(alertedOnly = alertedOnly) }

    suspend fun recordings(cameraId: String? = null): Result<List<Recording>> =
        suspendRunCatching { session.api.recordings(cameraId = cameraId) }

    suspend fun pushSettings(): Result<PushSettings> = suspendRunCatching { session.api.pushSettings() }

    fun snapshotUrl(cameraId: String?, cacheBuster: Long): String? =
        session.snapshotUrl(cameraId, cacheBuster)

    fun recordingStreamUrl(recordingId: Int): String? = session.recordingStreamUrl(recordingId)

    fun eventSnapshotUrl(eventId: Int): String? = session.eventSnapshotUrl(eventId)

    fun httpClient() = session.httpClient

    fun currentSettingsStore(): SettingsStore = settings

    fun appPrefs(): AppPreferencesStore = settings.appPrefs()
}
