package com.daygle.aicamera

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.daygle.aicamera.data.SessionManager
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Application-scoped container. Hilt provides the dependency graph. 
 * Coil is wired to the same authenticated [okhttp3.OkHttpClient] as the 
 * API layer so snapshot requests carry the session cookie.
 */
@HiltAndroidApp
class DaygleApp : Application(), ImageLoaderFactory {

    @Inject
    lateinit var session: SessionManager

    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .okHttpClient { session.httpClient }
            .respectCacheHeaders(false)
            .build()
}

// AppContainer removed in favor of Hilt
