package com.daygle.aicamera.data

import android.util.Log
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.JavaNetCookieJar
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.io.IOException
import java.net.CookieManager
import java.net.CookiePolicy
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * Thrown when Cloudflare Access intercepts a request because the service token
 * is missing or invalid. Extends [IOException] so OkHttp/Retrofit surface it as
 * an ordinary network failure.
 */
class CloudflareAccessRequiredException : IOException("Cloudflare Access rejected the request")

/**
 * Owns the connection to a single Daygle AI Camera server: base URL,
 * credentials, the shared cookie jar, and the authenticated OkHttp/Retrofit
 * stack.
 */
class SessionManager {

    @Volatile
    private var connection: Connection = Connection()

    @Volatile
    private var baseUrl: HttpUrl? = null

    @Volatile
    private var cfAccessClientId: String = ""

    @Volatile
    private var cfAccessClientSecret: String = ""

    @Volatile
    private var customHeaderName: String = ""

    @Volatile
    private var customHeaderValue: String = ""

    private val cookieManager = CookieManager(null, CookiePolicy.ACCEPT_ALL)
    private val cookieJar = JavaNetCookieJar(cookieManager)
    private val loginLock = Any()

    private val sessionGeneration = AtomicLong()

    private val logging = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BASIC
        redactHeader("Cookie")
        redactHeader("Set-Cookie")
        redactHeader("CF-Access-Client-Id")
        redactHeader("CF-Access-Client-Secret")
    }

    private val cloudflareAccessInterceptor = CloudflareAccessInterceptor(
        clientIdProvider = { cfAccessClientId },
        clientSecretProvider = { cfAccessClientSecret }
    )

    private val customHeaderInterceptor = CustomHeaderInterceptor(
        nameProvider = { customHeaderName },
        valueProvider = { customHeaderValue }
    )

    /** Bare client used only for the login handshake. */
    private val authClient: OkHttpClient = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .addInterceptor(cloudflareAccessInterceptor)
        .addInterceptor(customHeaderInterceptor)
        .addInterceptor(logging)
        .build()

    /**
     * The authenticated client, shared by Retrofit, Coil (snapshots) and
     * Media3 (recording playback).
     */
    val httpClient: OkHttpClient = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .addInterceptor(cloudflareAccessInterceptor)
        .addInterceptor(customHeaderInterceptor)
        .addInterceptor(logging)
        .addInterceptor(
            AuthInterceptor(
                sessionGeneration = sessionGeneration,
                loginLock = loginLock,
                isConfigured = { connection.isConfigured },
                performLogin = ::performLogin,
                isCloudflareAccessRejection = ::isCloudflareAccessRejection
            )
        )
        .build()

    @Volatile
    var api: DaygleApi = buildApi(null)
        private set

    val currentBaseUrl: String? get() = baseUrl?.toString()?.trimEnd('/')

    fun currentCfAccessClientId(): String = cfAccessClientId
    fun currentCfAccessClientSecret(): String = cfAccessClientSecret

    fun update(connection: Connection) {
        val normalized = normalizeBaseUrl(connection.baseUrl)
        val changed = normalized?.toString() != baseUrl?.toString() || connection != this.connection
        
        this.connection = connection
        this.baseUrl = normalized
        this.cfAccessClientId = connection.cfAccessClientId.trim()
        this.cfAccessClientSecret = connection.cfAccessClientSecret
        this.customHeaderName = connection.customHeaderName.trim()
        this.customHeaderValue = connection.customHeaderValue
        
        if (customHeaderName.isNotBlank()) {
            logging.redactHeader(customHeaderName)
        }
        
        if (changed) {
            cookieManager.cookieStore.removeAll()
            sessionGeneration.incrementAndGet()
            api = buildApi(normalized)
        }
    }

    private fun buildApi(url: HttpUrl?): DaygleApi {
        val effective = url ?: "http://localhost/".toHttpUrlOrNull()!!
        return try {
            Retrofit.Builder()
                .baseUrl(effective)
                .client(httpClient)
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                .build()
                .create(DaygleApi::class.java)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to build API for $effective", e)
            // If even the fallback fails, we must avoid recursion.
            if (url == null) throw e
            buildApi(null)
        }
    }

    fun snapshotUrl(cameraId: String?, cacheBuster: Long): String? {
        val base = baseUrl ?: return null
        return base.newBuilder()
            .addPathSegments("api/live/snapshot")
            .apply {
                if (!cameraId.isNullOrBlank()) addQueryParameter("camera_id", cameraId)
                addQueryParameter("width", "1280")
                addQueryParameter("height", "720")
            }
            .addQueryParameter("t", cacheBuster.toString())
            .build()
            .toString()
    }

    fun recordingStreamUrl(recordingId: Int): String? {
        val base = baseUrl ?: return null
        return base.newBuilder()
            .addPathSegments("api/recordings/$recordingId/stream")
            .addQueryParameter("width", "1280")
            .addQueryParameter("height", "720")
            .build()
            .toString()
    }

    fun eventSnapshotUrl(eventId: Int): String? {
        val base = baseUrl ?: return null
        return base.newBuilder()
            .addPathSegments("api/events/$eventId/snapshot")
            .build()
            .toString()
    }

    suspend fun login(): LoginResult = withContext(Dispatchers.IO) {
        synchronized(loginLock) { performLogin() }
    }

    private fun isCloudflareAccessRejection(response: Response): Boolean {
        val finalUrl = response.request.url.toString().lowercase()
        if (finalUrl.contains("cloudflareaccess") || finalUrl.contains("cf-access")) return true
        val location = response.header("Location").orEmpty().lowercase()
        if (location.contains("cloudflareaccess") || location.contains("cf-access")) return true
        return response.header("Cf-Access-Error") != null
    }

    private fun performLogin(): LoginResult {
        val base = baseUrl ?: return LoginResult.NotConfigured
        val conn = connection
        if (!conn.isConfigured) return LoginResult.NotConfigured
        val loginUrl = base.newBuilder().addPathSegment("login").build()
        
        return try {
            val token = authClient.newCall(Request.Builder().url(loginUrl).get().build())
                .execute().use { pageResponse ->
                    if (isCloudflareAccessRejection(pageResponse)) {
                        return LoginResult.Error(CLOUDFLARE_ACCESS_MESSAGE)
                    }
                    if (!pageResponse.isSuccessful) {
                        return LoginResult.Error(mapHttpError(pageResponse.code, "/login"))
                    }
                    val pageHtml = pageResponse.body.string()
                    extractCsrfToken(pageHtml)
                        ?: return LoginResult.Error(
                            "Could not read the login security token from the server."
                        )
                }

            val form = FormBody.Builder()
                .add("username", conn.username)
                .add("password", conn.password)
                .add("csrf_token", token)
                .build()
                
            val postRequest = Request.Builder()
                .url(loginUrl)
                .header("Origin", base.toString().trimEnd('/'))
                .post(form)
                .build()
                
            authClient.newCall(postRequest).execute().use { response ->
                if (isCloudflareAccessRejection(response)) {
                    return LoginResult.Error(CLOUDFLARE_ACCESS_MESSAGE)
                }
                if (response.code == 429) {
                    return LoginResult.Error(rateLimitedMessage(response.header("Retry-After")?.toIntOrNull()))
                }
                if (response.code in 500..599) {
                    return LoginResult.Error(mapHttpError(response.code, "/login"))
                }
            }

            val verifyUrl = base.newBuilder().addPathSegments("api/cameras").build()
            authClient.newCall(Request.Builder().url(verifyUrl).get().build()).execute().use { verify ->
                when {
                    verify.isSuccessful -> {
                        sessionGeneration.incrementAndGet()
                        LoginResult.Success
                    }
                    isCloudflareAccessRejection(verify) -> LoginResult.Error(CLOUDFLARE_ACCESS_MESSAGE)
                    verify.code == 401 -> LoginResult.InvalidCredentials
                    else -> LoginResult.Error(mapHttpError(verify.code, "/api/cameras"))
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Login failed", e)
            LoginResult.Error("${e.toUserFriendlyMessage()} (${e::class.java.simpleName})")
        }
    }

    private fun rateLimitedMessage(retryAfterSeconds: Int?): String =
        if (retryAfterSeconds != null && retryAfterSeconds > 0) {
            "Too many login attempts. Wait $retryAfterSeconds seconds and try again."
        } else {
            "Too many login attempts. Wait a moment and try again."
        }

    private fun mapHttpError(code: Int, path: String): String = when (code) {
        in 520..527, 530 ->
            "Cloudflare couldn't reach your server (edge error $code). Check that the tunnel is running."
        in 500..599 -> "Server error ($code) on $path. Check the server logs."
        404 -> "Endpoint not found (404) on $path. Check the server address."
        403 -> "Access forbidden (403). Check your account permissions."
        401 -> "Authentication failed. Check your username and password."
        else -> "Unexpected server response: $code on $path."
    }

    companion object {
        private const val TAG = "SessionManager"
        const val CLOUDFLARE_ACCESS_MESSAGE =
            "This server is protected by Cloudflare Access. Add your service token in settings to sign in."

        val json: Json = Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
            explicitNulls = false
        }

        private val CSRF_INPUT = Regex(
            """<input\b[^>]*\bname\s*=\s*(?:"csrf_token"|'csrf_token'|csrf_token)[^>]*>""",
            RegexOption.IGNORE_CASE
        )
        private val VALUE_ATTR = Regex(
            """\bvalue\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s>]*))"""
        )

        fun extractCsrfToken(html: String): String? {
            val inputTag = CSRF_INPUT.find(html)?.value ?: return null
            val match = VALUE_ATTR.find(inputTag) ?: return null
            val (doubleQuoted, singleQuoted, unquoted) = match.destructured
            return doubleQuoted.ifEmpty { singleQuoted }.ifEmpty { unquoted }.ifEmpty { null }
        }

        fun normalizeBaseUrl(raw: String): HttpUrl? {
            val trimmed = raw.trim()
            if (trimmed.isEmpty()) return null
            val hasScheme = trimmed.startsWith("http://", ignoreCase = true) ||
                trimmed.startsWith("https://", ignoreCase = true)
            val withScheme = if (hasScheme) trimmed else "https://$trimmed"
            val url = withScheme.toHttpUrlOrNull() ?: return null
            return if (url.pathSegments.last().isNotEmpty()) {
                url.newBuilder().addPathSegment("").build()
            } else {
                url
            }
        }
    }
}

sealed interface LoginResult {
    data object Success : LoginResult
    data object InvalidCredentials : LoginResult
    data object NotConfigured : LoginResult
    data class Error(val message: String) : LoginResult
}
