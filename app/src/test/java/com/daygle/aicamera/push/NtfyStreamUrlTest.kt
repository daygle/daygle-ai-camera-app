package com.daygle.aicamera.push

import com.daygle.aicamera.data.NotificationConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NtfyStreamUrlTest {

    private fun config(server: String, topic: String) =
        NotificationConfig(enabled = true, serverUrl = server, topic = topic)

    @Test
    fun buildsJsonStreamUrl() {
        assertEquals("https://ntfy.sh/alerts/json", streamUrl(config("https://ntfy.sh", "alerts")).toString())
        assertEquals("https://ntfy.sh/alerts/json", streamUrl(config("https://ntfy.sh/", " alerts ")).toString())
        assertEquals(
            "https://example.com/ntfy/alerts/json",
            streamUrl(config("https://example.com/ntfy", "alerts")).toString(),
        )
    }

    @Test
    fun defaultsToHttpsWhenSchemeMissing() {
        assertEquals("https://ntfy.example.com/alerts/json", streamUrl(config("ntfy.example.com", "alerts")).toString())
    }

    @Test
    fun encodesTopicSoItCannotChangeThePath() {
        assertEquals(
            "https://ntfy.sh/a%2Fb%3Fc/json",
            streamUrl(config("https://ntfy.sh", "a/b?c")).toString(),
        )
    }

    @Test
    fun rejectsMissingOrInvalidValues() {
        assertNull(streamUrl(config("", "alerts")))
        assertNull(streamUrl(config("https://ntfy.sh", " ")))
        assertNull(streamUrl(config("https://", "alerts")))
    }
}
