package com.daygle.aicamera.data

import android.util.Log
import okhttp3.Interceptor
import okhttp3.Response
import java.util.concurrent.atomic.AtomicLong

private const val TAG = "Interceptors"

/**
 * Token pasted from a terminal or password manager often carries a trailing
 * newline. OkHttp rejects CR/LF in header values with an IllegalArgumentException
 * thrown before the request is sent, which would break every request.
 */
internal fun String.asHeaderValue(): String = replace("\r", "").replace("\n", "").trim()

/**
 * Attaches the `CF-Access-Client-Id` / `CF-Access-Client-Secret` headers to the request
 * when a Cloudflare Access service token is configured.
 */
internal class CloudflareAccessInterceptor(
    private val clientIdProvider: () -> String,
    private val clientSecretProvider: () -> String,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val clientId = clientIdProvider().asHeaderValue()
        val clientSecret = clientSecretProvider().asHeaderValue()
        if (clientId.isEmpty() || clientSecret.isEmpty()) {
            return chain.proceed(chain.request())
        }
        return chain.proceed(
            chain.request().newBuilder()
                .header("CF-Access-Client-Id", clientId)
                .header("CF-Access-Client-Secret", clientSecret)
                .build()
        )
    }
}

/**
 * Attaches a custom header to every request when configured.
 */
internal class CustomHeaderInterceptor(
    private val nameProvider: () -> String,
    private val valueProvider: () -> String
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val name = nameProvider().trim()
        val value = valueProvider().asHeaderValue()
        if (name.isEmpty() || value.isEmpty()) {
            return chain.proceed(chain.request())
        }
        // A saved header with an illegal name is a configuration mistake, not a
        // reason to fail every request: skip it and keep the client usable.
        val request = try {
            chain.request().newBuilder().header(name, value).build()
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Ignoring invalid custom header '$name': ${e.message}")
            chain.request()
        }
        return chain.proceed(request)
    }
}

/**
 * Transparently re-authenticates 401 API responses if possible.
 */
internal class AuthInterceptor(
    private val sessionGeneration: AtomicLong,
    private val loginLock: Any,
    private val isConfigured: () -> Boolean,
    private val performLogin: () -> LoginResult,
    private val isCloudflareAccessRejection: (Response) -> Boolean
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        // Requests already re-sent after a re-login are never retried again;
        // without this a server that keeps returning 401 (stale credentials,
        // clock skew) would trap the app in an unbounded login/retry loop.
        val alreadyRetried = request.header(RETRY_HEADER) != null
        val generationAtRequest = sessionGeneration.get()
        val response = chain.proceed(request)

        if (isCloudflareAccessRejection(response)) {
            Log.w("AuthInterceptor", "Cloudflare Access intercepted ${request.url.encodedPath}")
            response.close()
            throw CloudflareAccessRequiredException()
        }

        val isApiCall = request.url.pathSegments.contains("api")
        if (!alreadyRetried && response.code == 401 && isApiCall && isConfigured()) {
            Log.w("AuthInterceptor", "401 on ${request.url.encodedPath}; attempting re-login")
            val result = synchronized(loginLock) {
                if (sessionGeneration.get() != generationAtRequest) {
                    LoginResult.Success
                } else {
                    performLogin()
                }
            }
            if (result is LoginResult.Success) {
                response.close()
                Log.i("AuthInterceptor", "Re-login succeeded; retrying ${request.url.encodedPath}")
                return chain.proceed(request.newBuilder().header(RETRY_HEADER, "1").build())
            }
            Log.w("AuthInterceptor", "Re-login failed: $result")
        }
        return response
    }

    private companion object {
        /** Marks a request that has already been retried after a re-login. */
        const val RETRY_HEADER = "X-Daygle-Auth-Retry"
    }
}
