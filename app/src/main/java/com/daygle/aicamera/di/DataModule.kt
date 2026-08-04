package com.daygle.aicamera.di

import android.content.Context
import com.daygle.aicamera.data.AppPreferencesStore
import com.daygle.aicamera.data.CameraRepository
import com.daygle.aicamera.data.NotificationSettingsStore
import com.daygle.aicamera.data.SessionManager
import com.daygle.aicamera.data.SettingsStore
import com.daygle.aicamera.data.TunnelGate
import com.daygle.aicamera.data.WireGuardConfigStore
import com.daygle.aicamera.vpn.TunnelManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun provideSettingsStore(@ApplicationContext context: Context): SettingsStore =
        SettingsStore(context)

    @Provides
    @Singleton
    fun provideNotificationSettingsStore(@ApplicationContext context: Context): NotificationSettingsStore =
        NotificationSettingsStore(context)

    @Provides
    @Singleton
    fun provideAppPreferencesStore(@ApplicationContext context: Context): AppPreferencesStore =
        AppPreferencesStore(context)

    @Provides
    @Singleton
    fun provideWireGuardConfigStore(@ApplicationContext context: Context): WireGuardConfigStore =
        WireGuardConfigStore(context)

    // TunnelManager has an @Inject constructor; expose it as the TunnelGate the
    // network layer depends on.
    @Provides
    @Singleton
    fun provideTunnelGate(tunnelManager: TunnelManager): TunnelGate = tunnelManager

    @Provides
    @Singleton
    fun provideSessionManager(tunnelGate: TunnelGate): SessionManager = SessionManager(tunnelGate)

    @Provides
    @Singleton
    fun provideCameraRepository(
        session: SessionManager,
        settings: SettingsStore
    ): CameraRepository = CameraRepository(session, settings)
}
