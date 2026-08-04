package com.daygle.aicamera.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.wireGuardDataStore: DataStore<Preferences> by preferencesDataStore(name = "wireguard")

/**
 * The user's WireGuard tunnel configuration and VPN-only preference.
 *
 * [config] is the raw `.conf` text the user pastes in (keys, peer endpoint,
 * AllowedIPs). It is stored in the app's private DataStore and excluded from
 * cloud backup alongside the server credentials (see `res/xml/backup_rules.xml`
 * and `data_extraction_rules.xml`).
 */
data class WireGuardConfig(
    val vpnOnly: Boolean = false,
    val config: String = "",
) {
    /** True when there is a config to bring a tunnel up with. */
    val hasConfig: Boolean get() = config.isNotBlank()
}

class WireGuardConfigStore(private val context: Context) {

    private object Keys {
        val VPN_ONLY = booleanPreferencesKey("vpn_only")
        val CONFIG = stringPreferencesKey("wg_config")
    }

    val config: Flow<WireGuardConfig> = context.wireGuardDataStore.data.map { prefs ->
        WireGuardConfig(
            vpnOnly = prefs[Keys.VPN_ONLY] ?: false,
            config = prefs[Keys.CONFIG].orEmpty(),
        )
    }

    suspend fun current(): WireGuardConfig = config.first()

    suspend fun saveConfig(config: String) {
        context.wireGuardDataStore.edit { it[Keys.CONFIG] = config.trim() }
    }

    suspend fun setVpnOnly(enabled: Boolean) {
        context.wireGuardDataStore.edit { it[Keys.VPN_ONLY] = enabled }
    }

    suspend fun clear() {
        context.wireGuardDataStore.edit { it.clear() }
    }
}
