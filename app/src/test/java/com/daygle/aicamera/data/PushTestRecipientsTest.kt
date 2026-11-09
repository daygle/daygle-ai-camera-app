package com.daygle.aicamera.data

import com.daygle.aicamera.data.model.AppUser
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PushTestRecipientsTest {

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    private val users: List<AppUser> = json.decodeFromString(
        """
        [
          {"id": 1, "username": "zed", "first_name": "Zed", "last_name": "Smith", "ntfy_topic": "zed-phone", "is_active": 1},
          {"id": 2, "username": "amy", "ntfy_topic": " amy-phone ", "is_active": 1},
          {"id": 3, "username": "notopic", "ntfy_topic": "", "is_active": 1},
          {"id": 4, "username": "gone", "ntfy_topic": "gone-phone", "is_active": 0},
          {"id": 5, "username": "flag", "ntfy_topic": "flag-phone", "is_active": false}
        ]
        """,
    )

    @Test
    fun readsIsActiveAsNumberOrBoolean() {
        assertTrue(users[0].active)
        assertFalse(users[3].active)
        assertFalse(users[4].active)
        assertTrue(AppUser(id = 9).active)
    }

    @Test
    fun listsActiveUsersWithATopicByName() {
        assertEquals(
            listOf(
                PushRecipientOption("user:2", "amy", "amy-phone"),
                PushRecipientOption("user:1", "Zed Smith", "zed-phone"),
            ),
            pushRecipientOptions(users),
        )
    }

    @Test
    fun ticksTheSignedInUserOnlyWhenTheyCanReceive() {
        val options = pushRecipientOptions(users)
        assertEquals(setOf("user:1"), defaultPushRecipients(options, meId = 1))
        assertEquals(emptySet<String>(), defaultPushRecipients(options, meId = 3))
    }
}
