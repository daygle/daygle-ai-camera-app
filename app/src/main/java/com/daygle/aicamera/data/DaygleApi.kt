package com.daygle.aicamera.data

import com.daygle.aicamera.data.model.ActivityStats
import com.daygle.aicamera.data.model.AppUser
import com.daygle.aicamera.data.model.AuthMe
import com.daygle.aicamera.data.model.Camera
import com.daygle.aicamera.data.model.CameraHealthResponse
import com.daygle.aicamera.data.model.CamerasResponse
import com.daygle.aicamera.data.model.Event
import com.daygle.aicamera.data.model.EventSearchResponse
import com.daygle.aicamera.data.model.LibraryFacets
import com.daygle.aicamera.data.model.Page
import com.daygle.aicamera.data.model.PushSettings
import com.daygle.aicamera.data.model.PushTestRequest
import com.daygle.aicamera.data.model.PushTestResponse
import com.daygle.aicamera.data.model.Recording
import com.daygle.aicamera.data.model.ServerResources
import com.daygle.aicamera.data.model.StorageStatus
import com.daygle.aicamera.data.model.SystemSettings
import com.daygle.aicamera.data.model.TimelineResponse
import kotlinx.serialization.json.JsonObject
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Endpoints on the Daygle AI Camera server consumed by the app. All are reads
 * except [pushTest], [saveCamerasConfig], [saveFaceRules] and
 * [saveCameraOfflineSettings], which need the
 * session's CSRF token and a same-origin `Origin` header (see
 * [CameraRepository.sendPushTest]).
 */
interface DaygleApi {

    @GET("api/cameras")
    suspend fun cameras(): CamerasResponse

    @GET("api/cameras/health")
    suspend fun cameraHealth(): CameraHealthResponse

    /**
     * A cursor page of events. The filters match the server's shared library
     * filter bar: `q` needs every word to appear in a label, zone, camera, AI
     * tag/description or face name; `face` is `any`, `unknown`, `id:<id>` or
     * `name:<name>`. Older servers ignore parameters they do not know.
     * [withRecording] keeps only events linked to a clip; off by default so
     * the feed matches the web UI's single Events feed, which also lists
     * sound and snapshot-only events.
     */
    @GET("api/events")
    suspend fun events(
        @Query("limit") limit: Int = 100,
        @Query("alerted_only") alertedOnly: Boolean = false,
        @Query("with_recording") withRecording: Boolean = false,
        @Query("since") since: String? = null,
        @Query("cursor") cursor: String? = null,
        @Query("until") until: String? = null,
        @Query("camera_id") cameraId: String? = null,
        @Query("label") label: String? = null,
        @Query("q") query: String? = null,
        @Query("face") face: String? = null,
        @Query("sort") sort: String? = null,
    ): Page<Event>

    /** One event with its detections, alert and metadata (AI description, faces). */
    @GET("api/events/{id}")
    suspend fun event(@Path("id") eventId: Int): Event

    /** Events that captured a frame, i.e. the server's Snapshots library. */
    @GET("api/snapshots")
    suspend fun snapshots(
        @Query("limit") limit: Int = 100,
        @Query("cursor") cursor: String? = null,
        @Query("since") since: String? = null,
        @Query("until") until: String? = null,
        @Query("camera_id") cameraId: String? = null,
        @Query("label") label: String? = null,
        @Query("q") query: String? = null,
        @Query("alerted_only") alertedOnly: Boolean = false,
        @Query("face") face: String? = null,
        @Query("sort") sort: String? = null,
    ): Page<Event>

    /**
     * Plain-English search over the AI event descriptions and tags. The server
     * turns the question into concepts, a camera and a time window (with a
     * keyword fallback when its vision model is unavailable).
     */
    @GET("api/event-search")
    suspend fun eventSearch(
        @Query("q") query: String,
        @Query("limit") limit: Int = 100,
    ): EventSearchResponse

    /** One recording with its trigger event, member events and AI tags. */
    @GET("api/recordings/{id}")
    suspend fun recording(@Path("id") recordingId: Int): Recording

    @GET("api/recordings")
    suspend fun recordings(
        @Query("camera_id") cameraId: String? = null,
        @Query("limit") limit: Int = 100,
        @Query("cursor") cursor: String? = null,
        @Query("started_after") startedAfter: String? = null,
        @Query("started_before") startedBefore: String? = null,
        @Query("label") label: String? = null,
        @Query("q") query: String? = null,
        @Query("alerted_only") alertedOnly: Boolean = false,
        @Query("face") face: String? = null,
        @Query("sort") sort: String? = null,
    ): Page<Recording>

    /** Label and face options, with counts, for one library list's time window. */
    @GET("api/library/facets")
    suspend fun libraryFacets(
        @Query("kind") kind: String,
        @Query("since") since: String? = null,
        @Query("until") until: String? = null,
    ): LibraryFacets

    /** Pre-grouped timeline segments for one camera-day in the given timezone. */
    @GET("api/recordings/timeline")
    suspend fun recordingsTimeline(
        @Query("camera_id") cameraId: String? = null,
        @Query("day") day: String? = null,
        @Query("tz_offset_minutes") tzOffsetMinutes: Int? = null,
    ): TimelineResponse

    @GET("api/settings/alert-push")
    suspend fun pushSettings(): PushSettings

    @GET("api/auth/me")
    suspend fun me(): AuthMe

    /** Admins only. */
    @GET("api/users")
    suspend fun users(): List<AppUser>

    /**
     * Admins only. The whole camera config as raw JSON, so the Alerts screen
     * can change recipient lists and send everything else back untouched.
     */
    @GET("api/cameras")
    suspend fun camerasConfig(): JsonObject

    /** Admins only. Body: `{"cameras": [...]}` as read by [camerasConfig]. */
    @PUT("api/cameras")
    suspend fun saveCamerasConfig(
        @Header("X-CSRF-Token") csrfToken: String,
        @Header("Origin") origin: String,
        @Body body: JsonObject,
    ): JsonObject

    /** Admins only. `{"rules": [...]}`: per-person and unknown-face alerts. */
    @GET("api/settings/face-detection-rules")
    suspend fun faceRules(): JsonObject

    @PUT("api/settings/face-detection-rules")
    suspend fun saveFaceRules(
        @Header("X-CSRF-Token") csrfToken: String,
        @Header("Origin") origin: String,
        @Body body: JsonObject,
    ): JsonObject

    /** Camera offline alert settings: on/off, delay, email and push recipients. */
    @GET("api/settings/camera-offline")
    suspend fun cameraOfflineSettings(): JsonObject

    /** Admins only. The server rebuilds the settings from this whole body. */
    @PUT("api/settings/camera-offline")
    suspend fun saveCameraOfflineSettings(
        @Header("X-CSRF-Token") csrfToken: String,
        @Header("Origin") origin: String,
        @Body body: JsonObject,
    ): JsonObject

    /** Admins only. Sends a test push to the chosen users' own ntfy topics. */
    @POST("api/settings/alert-push/test")
    suspend fun pushTest(
        @Header("X-CSRF-Token") csrfToken: String,
        @Header("Origin") origin: String,
        @Body body: PushTestRequest,
    ): PushTestResponse

    /** Admin only: other roles get 403. */
    @GET("api/settings/system")
    suspend fun systemSettings(): SystemSettings

    /** Admin only. Host CPU, load, RAM, GPU and video-decode snapshot (the web System page). */
    @GET("api/system/resources")
    suspend fun systemResources(): ServerResources

    /** Admin only. Disk space, media usage and retention limits (newer servers). */
    @GET("api/system/storage")
    suspend fun systemStorage(): StorageStatus

    /** Admin only. Event and alert counts, since [since] (ISO-8601) or all time. */
    @GET("api/stats")
    suspend fun stats(@Query("since") since: String? = null): ActivityStats
}
