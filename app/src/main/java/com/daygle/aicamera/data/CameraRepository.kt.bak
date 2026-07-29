package com.daygle.aicamera.data

import com.daygle.aicamera.data.model.Camera
import com.daygle.aicamera.data.model.CameraHealthResponse
import com.daygle.aicamera.data.model.Event
import com.daygle.aicamera.data.model.PushSettings
import com.daygle.aicamera.data.model.Recording
import com.daygle.aicamera.data.model.StatusResponse

/**
 * Thin domain layer over [SessionManager]/[DaygleApi]. Each call returns a
 * [Result] so screens can render a friendly error instead of crashing when the
 * server is unreachable or the session cannot be established.
 */
class CameraRepository(
    private val session: SessionManager,
    private val settings: SettingsStore,
) {

    /** Save connection details and verify them by logging in. */
    suspend fun connect(connection: Connection): LoginResult {
        settings.save(connection)
        session.update(connection)
        return session.login()
    }

    /** Re-apply the persisted connection to the session (e.g. on app launch). */
    suspend fun restore(): Boolean {
        val connection = settings.current()
        session.update(connection)
        return connection.isConfigured
    }

    /** Forget the server and end the session. */
    suspend fun disconnect() {
        settings.clear()
        session.update(Connection())
    }

    /** The persisted connection, so the connect screen can pre-fill it. */
    suspend fun currentConnection(): Connection = settings.current()

    suspend fun cameras(): Result<List<Camera>> = runCatching { session.api.cameras().cameras }

    suspend fun cameraHealth(): Result<CameraHealthResponse> = runCatching { session.api.cameraHealth() }

    suspend fun status(cameraId: String?): Result<StatusResponse> =
        runCatching { session.api.status(cameraId) }

    suspend fun events(alertedOnly: Boolean = false): Result<List<Event>> =
        runCatching { session.api.events(alertedOnly = alertedOnly) }

    suspend fun recordings(cameraId: String? = null): Result<List<Recording>> =
        runCatching { session.api.recordings(cameraId = cameraId) }

    suspend fun pushSettings(): Result<PushSettings> = runCatching { session.api.pushSettings() }

    fun snapshotUrl(cameraId: String?, cacheBuster: Long): String? =
        session.snapshotUrl(cameraId, cacheBuster)

    fun recordingStreamUrl(recordingId: Int): String? = session.recordingStreamUrl(recordingId)

    fun httpClient() = session.httpClient
}
