package com.daygle.aicamera

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.daygle.aicamera.data.CameraRepository
import com.daygle.aicamera.data.NotificationSettingsStore
import com.daygle.aicamera.data.SessionManager
import com.daygle.aicamera.data.SettingsStore

/**
 * Application-scoped container. A hand-rolled service locator keeps the
 * dependency graph tiny and free of an annotation processor. Coil is wired to
 * the same authenticated [okhttp3.OkHttpClient] as the API layer so snapshot
 * requests carry the session cookie.
 */
class DaygleApp : Application(), ImageLoaderFactory {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }

    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .okHttpClient { container.session.httpClient }
            .respectCacheHeaders(false)
            .build()
}

class AppContainer(app: Application) {
    val settings = SettingsStore(app)
    val notificationSettings = NotificationSettingsStore(app)
    val session = SessionManager()
    val repository = CameraRepository(session, settings)
}
