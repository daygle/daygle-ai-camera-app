package com.daygle.aicamera

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import com.daygle.aicamera.data.SessionManager
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Application-scoped container. Hilt provides the dependency graph. 
 * Coil is wired to the same authenticated [okhttp3.OkHttpClient] as the 
 * API layer so snapshot requests carry the session cookie.
 */
@HiltAndroidApp
class DaygleApp : Application(), SingletonImageLoader.Factory {

    @Inject
    lateinit var session: SessionManager

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components {
                add(OkHttpNetworkFetcherFactory(callFactory = { session.httpClient }))
            }
            .build()
}

// AppContainer removed in favor of Hilt
