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
 * an ordinary network failure that [toUserFriendlyMessage] can translate.
 */
class CloudflareAccessRequiredException : IOException("Cloudflare Access rejected the request")

/**
 * Owns the connection to a single Daygle AI Camera server: base URL,
 * credentials, the shared cookie jar, and the authenticated OkHttp/Retrofit
 * stack.
 *
 * The server uses browser-style session-cookie auth with a CSRF token (there
 * is no bearer/API-token endpoint), so [performLogin] reproduces the browser
 * login handshake:
 *  1. `GET /login` - the server sets a CSRF cookie and embeds a matching
 *     `csrf_token` in the returned HTML form.
 *  2. `POST /login` (form-encoded `username`, `password`, `csrf_token`) - on
 *     success the server sets the session cookie, which the [cookieJar] keeps.
 *
 * All read (GET /api/ endpoints) calls only need the session cookie. If it expires the
 * [authInterceptor] transparently re-logs in and retries once.
 *
 * When the server is exposed through a Cloudflare Tunnel protected by
 * Cloudflare Access, every request (login handshake included) must carry the
 * `CF-Access-Client-Id` / `CF-Access-Client-Secret` service-token headers, or
 * Access redirects/denies the traffic before it ever reaches the server. If the
 * user configured a service token in [Connection], [cloudflareAccessInterceptor]
 * adds those headers to every request on both clients.
 */
class SessionManager {

    // NOTE: declaration order matters - Kotlin initializes properties top to
    // bottom, so every field that [api]/the clients depend on must be declared
    // above them (the clients reference [cookieJar]/[logging]; [api] references
    // [httpClient]).

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
        // Service-token and custom headers are credentials; never let logging print them.
        redactHeader("CF-Access-Client-Id")
        redactHeader("CF-Access-Client-Secret")
    }

    /**
     * When a Cloudflare Access service token is configured, attach the
     * `CF-Access-Client-Id` / `CF-Access-Client-Secret` headers to the request.
     * No-op (request passes through untouched) when unset, so behaviour is
     * unchanged for servers not behind Access.
     */
    private fun cloudflareAccessInterceptor() = Interceptor { chain ->
        val clientId = cfAccessClientId
        val clientSecret = cfAccessClientSecret
        if (clientId.isBlank() || clientSecret.isBlank()) {
            return@Interceptor chain.proceed(chain.request())
        }
        chain.proceed(
            chain.request().newBuilder()
                .header("CF-Access-Client-Id", clientId)
                .header("CF-Access-Client-Secret", clientSecret)
                .build()
        )
    }

    /**
     * When a custom header (name + value) is configured, attach it to every
     * request - the login handshake included - so servers that require an
     * extra header (e.g. an API key or reverse-proxy token) accept the app's
     * traffic. No-op when unset, so behaviour is unchanged otherwise.
     */
    private fun customHeaderInterceptor() = Interceptor { chain ->
        val name = customHeaderName
        val value = customHeaderValue
        if (name.isBlank() || value.isBlank()) {
            return@Interceptor chain.proceed(chain.request())
        }
        chain.proceed(
            chain.request().newBuilder()
                .header(name, value)
                .build()
        )
    }

    /**
     * True when a response was intercepted by a Cloudflare
     * Access login page: either the request ended up on a *.cloudflareaccess.com
     * address, or the server answered with an Access rejection header.
     */
    private fun isCloudflareAccessRejection(response: Response): Boolean {
        val finalUrl = response.request.url.toString().lowercase()
        if (finalUrl.contains("cloudflareaccess") || finalUrl.contains("cf-access")) return true
        val location = response.header("Location").orEmpty().lowercase()
        if (location.contains("cloudflareaccess") || location.contains("cf-access")) return true
        return response.header("Cf-Access-Error") != null
    }

    /** Bare client used only for the login handshake (no auth interceptor, to avoid recursion). */
    private val authClient: OkHttpClient = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        // Do not forward session or service-token credentials to an arbitrary
        // host via a server-side redirect. Same-origin redirects are handled
        // explicitly by the login flow where required.
        .followRedirects(false)
        .followSslRedirects(false)
        .addInterceptor(cloudflareAccessInterceptor())
        .addInterceptor(customHeaderInterceptor())
        .addInterceptor(logging)
        .build()

    /**
     * The authenticated client, shared by Retrofit, Coil (snapshots) and
     * Media3 (recording playback) so they all ride the same session cookie and
     * benefit from transparent re-login.
     */
    val httpClient: OkHttpClient = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        // Do not forward session or service-token credentials to an arbitrary
        // host via a server-side redirect. Same-origin redirects are handled
        // explicitly by the login flow where required.
        .followRedirects(false)
        .followSslRedirects(false)
        .addInterceptor(cloudflareAccessInterceptor())
        .addInterceptor(customHeaderInterceptor())
        .addInterceptor(logging)
        .addInterceptor(authInterceptor())
        .build()

    @Volatile
    var api: DaygleApi = buildApi(null)
        private set

    val currentBaseUrl: String? get() = baseUrl?.toString()?.trimEnd('/')

    /** Current Cloudflare Access service-token Client ID, or "" when unset. */
    fun currentCfAccessClientId(): String = cfAccessClientId

    /** Current Cloudflare Access service-token Client Secret, or "" when unset. */
    fun currentCfAccessClientSecret(): String = cfAccessClientSecret

    /** Point the manager at a (possibly new) server. Clears cookies if the target changed. */
    fun update(connection: Connection) {
        val normalized = normalizeBaseUrl(connection.baseUrl)
        val changed = normalized?.toString() != baseUrl?.toString() || connection != this.connection
        this.connection = connection
        this.baseUrl = normalized
        this.cfAccessClientId = connection.cfAccessClientId.trim()
        this.cfAccessClientSecret = connection.cfAccessClientSecret
        this.customHeaderName = connection.customHeaderName.trim()
        this.customHeaderValue = connection.customHeaderValue
        // Keep the configured header out of the HTTP logs. update() runs on
        // the caller's thread before any network traffic is issued, so this
        // redaction is in place before requests are logged.
        if (customHeaderName.isNotBlank()) {
            logging.redactHeader(customHeaderName)
        }
        if (changed) {
            cookieManager.cookieStore.removeAll()
            sessionGeneration.incrementAndGet()
        }
        api = buildApi(normalized)
    }

    private fun buildApi(url: HttpUrl?): DaygleApi {
        // Retrofit requires a base URL even before the user configures one; a
        // harmless placeholder keeps [api] non-null until [update] is called.
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
            // Fallback to localhost if the provided URL is still somehow invalid for Retrofit
            buildApi(null)
        }
    }

    /** Absolute URL for a live snapshot; [cacheBuster] forces a fresh frame each poll. */
    fun snapshotUrl(cameraId: String?, cacheBuster: Long): String? {
        val base = baseUrl ?: return null
        return base.newBuilder()
            .addPathSegments("api/live/snapshot")
            .apply {
                if (!cameraId.isNullOrBlank()) addQueryParameter("camera_id", cameraId)
                // Request a standard resolution to prevent decoder/network strain
                addQueryParameter("width", "1280")
                addQueryParameter("height", "720")
            }
            .addQueryParameter("t", cacheBuster.toString())
            .build()
            .toString()
    }

    /** Absolute URL for streaming a recording's MP4. */
    fun recordingStreamUrl(recordingId: Int): String? {
        val base = baseUrl ?: return null
        return base.newBuilder()
            .addPathSegments("api/recordings/$recordingId/stream")
            // Request a standard resolution for recordings too, to avoid decoder crashes
            .addQueryParameter("width", "1280")
            .addQueryParameter("height", "720")
            .build()
            .toString()
    }

    /**
     * Absolute URL for an event's saved snapshot, annotated with green
     * detection boxes (the same overlay used in alert emails). Loaded by Coil,
     * which rides the shared session-cookie client.
     */
    fun eventSnapshotUrl(eventId: Int): String? {
        val base = baseUrl ?: return null
        return base.newBuilder()
            .addPathSegments("api/events/$eventId/snapshot")
            .build()
            .toString()
    }

    /**
     * Establish a session against the current server. Safe to call from a
     * coroutine; the blocking network work runs on [Dispatchers.IO].
     */
    suspend fun login(): LoginResult = withContext(Dispatchers.IO) {
        synchronized(loginLock) { performLogin() }
    }

    private fun authInterceptor() = Interceptor { chain ->
        val request = chain.request()
        val generationAtRequest = sessionGeneration.get()
        val response = chain.proceed(request)
        // Cloudflare Access owns this request before the server ever saw it.
        // Surface a clear message instead of a redirect/HTML page, which would
        // otherwise fail confusingly (login page parsed as JSON, etc.).
        if (isCloudflareAccessRejection(response)) {
            Log.w(TAG, "Cloudflare Access intercepted ${request.url.encodedPath}")
            response.close()
            throw CloudflareAccessRequiredException()
        }
        val isApiCall = request.url.pathSegments.contains("api")
        if (response.code == 401 && isApiCall && connection.isConfigured) {
            Log.w(TAG, "401 on ${request.url.encodedPath}; attempting re-login")
            val result = synchronized(loginLock) {
                if (sessionGeneration.get() != generationAtRequest) {
                    LoginResult.Success
                } else {
                    performLogin()
                }
            }
            if (result is LoginResult.Success) {
                response.close()
                Log.i(TAG, "Re-login succeeded; retrying ${request.url.encodedPath}")
                return@Interceptor chain.proceed(request.newBuilder().build())
            }
            Log.w(TAG, "Re-login failed: $result")
        }
        response
    }

    /** Blocking login handshake. Callers must hold [loginLock]. */
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
                            "Could not read the login security token from the server. " +
                                "The server returned ${pageHtml.length} bytes of HTML without a csrf_token input."
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
                // The server's per-IP login rate limiter answers 429 *before* it
                // ever checks the credentials. Surface it directly - otherwise the
                // verify call below sees no session cookie and misreports the
                // throttle as "invalid username or password".
                if (response.code == 429) {
                    return LoginResult.Error(rateLimitedMessage(response.header("Retry-After")?.toIntOrNull()))
                }
                // A 5xx at this point is a server-side fault, not a credential
                // problem; report it as such instead of a bogus 401 downstream.
                if (response.code in 500..599) {
                    return LoginResult.Error(mapHttpError(response.code, "/login"))
                }
                // On success the server answers 303 (redirect not followed) and
                // sets the session cookie, captured by the jar. On bad credentials
                // it re-renders the login page (200). The verify call below tells
                // those two apart.
            }

            // The login POST redirects to a page regardless of outcome, so verify
            // by hitting an authenticated endpoint.
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

    /** Message for a throttled login, including the server-supplied wait when present. */
    private fun rateLimitedMessage(retryAfterSeconds: Int?): String =
        if (retryAfterSeconds != null && retryAfterSeconds > 0) {
            "Too many login attempts. Wait $retryAfterSeconds seconds and try again."
        } else {
            "Too many login attempts. Wait a moment and try again."
        }

    private fun mapHttpError(code: Int, path: String): String = when (code) {
        // Cloudflare edge errors (5xx in the 52x band, plus 530). These are
        // returned by Cloudflare's edge - not the origin - when it cannot reach
        // the server behind the tunnel, so "check the server logs" is
        // misleading: the request never arrived there. Ordered before the
        // generic 500..599 branch so it wins.
        in 520..527, 530 ->
            "Cloudflare couldn't reach your server (edge error $code). The Cloudflare " +
                "Tunnel connector may be down or the server is offline - check that the " +
                "tunnel is running, then try again."
        in 500..599 -> "Server error ($code) on $path. Check the server logs."
        404 -> "Endpoint not found (404) on $path. Check the server address or update the server."
        403 -> "Access forbidden (403). Check your account permissions."
        401 -> "Authentication failed. Check your username and password."
        else -> "Unexpected server response: $code on $path."
    }

    companion object {
        private const val TAG = "SessionManager"

        /** Shown when Cloudflare Access blocks a request because the service token is missing or rejected. */
        const val CLOUDFLARE_ACCESS_MESSAGE =
            "This server is protected by Cloudflare Access. Add your Cloudflare Access service token " +
                "(Client ID and Client Secret) in the connection settings to sign in."

        val json: Json = Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
            explicitNulls = false
        }

        /**
         * Two-step CSRF token extraction:
         *  1. Locate the `<input ... name="csrf_token" ...>` tag (any attribute order).
         *  2. Within that tag, find the `value="..."` attribute.
         *
         * This is order-independent and works with double-quoted, single-quoted,
         * or unquoted attribute values.
         */
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

        /** Normalize user input into an `http(s)://host[:port]` URL, ensuring a trailing slash for Retrofit. */
        fun normalizeBaseUrl(raw: String): HttpUrl? {
            val trimmed = raw.trim()
            if (trimmed.isEmpty()) return null
            val hasScheme = trimmed.startsWith("http://", ignoreCase = true) ||
                trimmed.startsWith("https://", ignoreCase = true)
            // Default a bare host to HTTPS. Release builds forbid cleartext
            // (see res/xml/network_security_config.xml), so defaulting to
            // http:// guaranteed a blocked-connection failure whenever the user
            // typed only a hostname - which is exactly the common case for a
            // Cloudflare-tunnel https host. Users who really want plain-HTTP LAN
            // access (debug builds) type the explicit http:// prefix shown in
            // the field placeholder.
            val withScheme = if (hasScheme) trimmed else "https://$trimmed"

            val url = withScheme.toHttpUrlOrNull() ?: return null

            // Retrofit strictly requires base URLs to end in '/'.
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
