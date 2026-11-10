package com.daygle.aicamera.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.util.Log
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Whether the device has a default network, updated as it comes and goes.
 * Emits the current state first. If connectivity can't be watched it reports
 * online, so callers fall back to trying (and handling the failure).
 */
fun Context.onlineStatus(): Flow<Boolean> = callbackFlow {
    val connectivity = getSystemService(ConnectivityManager::class.java)
    if (connectivity == null) {
        trySend(true)
        awaitClose { }
        return@callbackFlow
    }
    trySend(connectivity.activeNetwork != null)
    var current: Network? = connectivity.activeNetwork
    val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            current = network
            trySend(true)
        }

        override fun onLost(network: Network) {
            if (current == network || current == null) {
                current = null
                trySend(false)
            }
        }
    }
    val registered = runCatching { connectivity.registerDefaultNetworkCallback(callback) }
        .onFailure {
            Log.w("NetworkStatus", "Could not watch network changes", it)
            trySend(true)
        }
        .isSuccess
    awaitClose { if (registered) runCatching { connectivity.unregisterNetworkCallback(callback) } }
}.distinctUntilChanged()
