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

    @Test
    fun listensToTheSharedAndTheUsersOwnTopic() {
        val both = NotificationConfig(enabled = true, serverUrl = "https://ntfy.sh", topic = "alerts", userTopic = " alice-phone ")
        assertEquals("https://ntfy.sh/alerts,alice-phone/json", streamUrl(both).toString())
        // The same topic twice is one subscription; a blank user topic is ignored.
        assertEquals("https://ntfy.sh/alerts/json", streamUrl(both.copy(userTopic = "alerts")).toString())
        assertEquals("https://ntfy.sh/alerts/json", streamUrl(both.copy(userTopic = " ")).toString())
        // A comma inside a topic stays part of that topic.
        assertEquals("https://ntfy.sh/alerts,a%2Cb/json", streamUrl(both.copy(userTopic = "a,b")).toString())
    }
}
