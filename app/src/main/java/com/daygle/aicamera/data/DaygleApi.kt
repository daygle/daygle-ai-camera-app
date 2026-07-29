package com.daygle.aicamera.data

import com.daygle.aicamera.data.model.Camera
import com.daygle.aicamera.data.model.CameraHealthResponse
import com.daygle.aicamera.data.model.CamerasResponse
import com.daygle.aicamera.data.model.Event
import com.daygle.aicamera.data.model.PushSettings
import com.daygle.aicamera.data.model.Recording
import com.daygle.aicamera.data.model.StatusResponse
import retrofit2.http.GET
import retrofit2.http.Query

/** Read-only endpoints on the Daygle AI Camera server consumed by the app. */
interface DaygleApi {

    @GET("api/cameras")
    suspend fun cameras(): CamerasResponse

    @GET("api/cameras/health")
    suspend fun cameraHealth(): CameraHealthResponse

    @GET("api/status")
    suspend fun status(@Query("camera_id") cameraId: String? = null): StatusResponse

    @GET("api/events")
    suspend fun events(
        @Query("limit") limit: Int = 100,
        @Query("alerted_only") alertedOnly: Boolean = false,
    ): List<Event>

    @GET("api/recordings")
    suspend fun recordings(
        @Query("camera_id") cameraId: String? = null,
        @Query("limit") limit: Int = 100,
    ): List<Recording>

    @GET("api/settings/alert-push")
    suspend fun pushSettings(): PushSettings
}
