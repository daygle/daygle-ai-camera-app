package com.daygle.aicamera.data

import com.daygle.aicamera.data.model.PushSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PushSettingsSyncTest {

    private val server = PushSettings(
        enabled = true,
        serverUrl = "https://ntfy.example.com",
        topic = "daygle-alerts",
        username = "alerts",
        password = null, // redacted for a viewer account
    )

    @Test
    fun followingInstallTakesTheServersNewTopic() {
        val local = NotificationConfig(
            enabled = true, serverUrl = "https://ntfy.example.com", topic = "old-topic",
            username = "alerts", password = "secret", followServer = true,
        )
        val updated = followServerSettings(local, server)!!
        assertEquals("daygle-alerts", updated.topic)
        // The server redacts the password for viewers: the device's one stays.
        assertEquals("secret", updated.password)
        assertEquals(true, updated.enabled)
    }

    @Test
    fun nothingToSaveWhenAlreadyInStep() {
        val local = NotificationConfig(
            enabled = true, serverUrl = "https://ntfy.example.com", topic = "daygle-alerts",
            username = "alerts", password = "secret", followServer = true,
        )
        assertNull(followServerSettings(local, server))
    }

    @Test
    fun manualSubscriptionIsLeftAlone() {
        val local = NotificationConfig(serverUrl = "https://public.example.com", topic = "mine", followServer = false)
        assertNull(followServerSettings(local, server))
    }

    @Test
    fun existingInstallWithHandEnteredValuesStopsFollowing() {
        val local = NotificationConfig(enabled = true, serverUrl = "https://public.example.com", topic = "daygle-alerts")
        val updated = followServerSettings(local, server)!!
        assertEquals(false, updated.followServer)
        assertEquals("https://public.example.com", updated.serverUrl)
    }

    @Test
    fun existingInstallMatchingTheServerStartsFollowing() {
        val local = NotificationConfig(enabled = true, serverUrl = "https://ntfy.example.com/", topic = "daygle-alerts")
        assertEquals(true, followServerSettings(local, server)!!.followServer)
    }

    @Test
    fun freshInstallFollowsTheServer() {
        val updated = followServerSettings(NotificationConfig(), server)!!
        assertEquals(true, updated.followServer)
        assertEquals("https://ntfy.example.com", updated.serverUrl)
        assertEquals("alerts", updated.username)
        // Following never switches alerts on by itself.
        assertEquals(false, updated.enabled)
    }

    @Test
    fun serverWithoutPushSetUpChangesNothing() {
        val local = NotificationConfig(serverUrl = "https://ntfy.example.com", topic = "t", followServer = true)
        assertNull(followServerSettings(local, PushSettings(serverUrl = "", topic = null)))
    }
}
