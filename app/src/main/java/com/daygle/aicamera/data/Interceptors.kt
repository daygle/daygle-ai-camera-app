package com.daygle.aicamera.data

import android.util.Log
import okhttp3.Interceptor
import okhttp3.Response
import java.util.concurrent.atomic.AtomicLong

/**
 * Attaches the `CF-Access-Client-Id` / `CF-Access-Client-Secret` headers to the request
 * when a Cloudflare Access service token is configured.
 */
internal class CloudflareAccessInterceptor(
    private val clientIdProvider: () -> String,
    private val clientSecretProvider: () -> String,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val clientId = clientIdProvider()
        val clientSecret = clientSecretProvider()
        if (clientId.isBlank() || clientSecret.isBlank()) {
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
        val name = nameProvider()
        val value = valueProvider()
        if (name.isBlank() || value.isBlank()) {
            return chain.proceed(chain.request())
        }
        return chain.proceed(
            chain.request().newBuilder()
                .header(name, value)
                .build()
        )
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
        val generationAtRequest = sessionGeneration.get()
        val response = chain.proceed(request)

        if (isCloudflareAccessRejection(response)) {
            Log.w("AuthInterceptor", "Cloudflare Access intercepted ${request.url.encodedPath}")
            response.close()
            throw CloudflareAccessRequiredException()
        }

        val isApiCall = request.url.pathSegments.contains("api")
        if ((response.code == 401) && isApiCall && isConfigured()) {
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
                return chain.proceed(request.newBuilder().build())
            }
            Log.w("AuthInterceptor", "Re-login failed: $result")
        }
        return response
    }
}
