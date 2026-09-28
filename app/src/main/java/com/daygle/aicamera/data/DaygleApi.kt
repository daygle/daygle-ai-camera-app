package com.daygle.aicamera.data

import com.daygle.aicamera.data.model.Camera
import com.daygle.aicamera.data.model.CameraHealthResponse
import com.daygle.aicamera.data.model.CamerasResponse
import com.daygle.aicamera.data.model.Event
import com.daygle.aicamera.data.model.Page
import com.daygle.aicamera.data.model.PushSettings
import com.daygle.aicamera.data.model.Recording
import com.daygle.aicamera.data.model.TimelineResponse
import retrofit2.http.GET
import retrofit2.http.Query

/** Read-only endpoints on the Daygle AI Camera server consumed by the app. */
interface DaygleApi {

    @GET("api/cameras")
    suspend fun cameras(): CamerasResponse

    @GET("api/cameras/health")
    suspend fun cameraHealth(): CameraHealthResponse

    @GET("api/events")
    suspend fun events(
        @Query("limit") limit: Int = 100,
        @Query("alerted_only") alertedOnly: Boolean = false,
        @Query("with_recording") withRecording: Boolean = true,
        // Supported by newer servers for incremental refresh; older servers
        // simply ignore the unknown parameter.
        @Query("since") since: String? = null,
        @Query("cursor") cursor: String? = null,
    ): Page<Event>

    /** Events that captured a frame, i.e. the server's Snapshots library. */
    @GET("api/snapshots")
    suspend fun snapshots(
        @Query("limit") limit: Int = 100,
        @Query("cursor") cursor: String? = null,
    ): Page<Event>

    @GET("api/recordings")
    suspend fun recordings(
        @Query("camera_id") cameraId: String? = null,
        @Query("limit") limit: Int = 100,
        @Query("cursor") cursor: String? = null,
    ): Page<Recording>

    /** Pre-grouped timeline segments for one camera-day in the given timezone. */
    @GET("api/recordings/timeline")
    suspend fun recordingsTimeline(
        @Query("camera_id") cameraId: String? = null,
        @Query("day") day: String? = null,
        @Query("tz_offset_minutes") tzOffsetMinutes: Int? = null,
    ): TimelineResponse

    @GET("api/settings/alert-push")
    suspend fun pushSettings(): PushSettings
}
