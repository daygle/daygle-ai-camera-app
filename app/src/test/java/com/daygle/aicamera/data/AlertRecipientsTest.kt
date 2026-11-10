package com.daygle.aicamera.data

import com.daygle.aicamera.data.model.AppUser
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AlertRecipientsTest {

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    private val cameras: JsonArray = json.parseToJsonElement(
        """
        [{
          "id": "front", "name": "Front", "password": "********", "status": "online",
          "detection": {
            "zones": [{
              "id": "z1", "name": "Driveway",
              "object_rules": [{
                "label": "person", "min_confidence": 0.5,
                "alert_schedules": [
                  {"id": "day", "email_enabled": true, "email_recipients": ["glen@example.com", "old@typed.com"], "push_recipients": [], "notify_start": "06:00"},
                  {"id": "night", "push_enabled": true, "push_recipients": ["user:2"]}
                ]
              }],
              "tripwire": {"name": "Gate line", "email_enabled": false, "email_recipients": []}
            }],
            "sound": {"rules": [{"class": "glass_break", "name": "Glass Break", "push_enabled": true}]}
          }
        }]
        """,
    ).jsonArray

    private val faceRules: JsonArray = json.parseToJsonElement(
        """[{"id": "_unknown:front:z1", "name": "Unknown Person", "camera_id": "front", "zone_id": "z1", "email_enabled": true, "email_recipients": "a@example.com, user:1"}]""",
    ).jsonArray

    private val users: List<AppUser> = json.decodeFromString(
        """
        [
          {"id": 1, "username": "glen", "first_name": "Glen", "email": "glen@example.com", "ntfy_topic": "glen-phone", "is_active": 1},
          {"id": 2, "username": "sam", "email": "sam@example.com", "ntfy_topic": "", "is_active": 1},
          {"id": 3, "username": "gone", "email": "gone@example.com", "ntfy_topic": "gone-phone", "is_active": 0}
        ]
        """,
    )

    @Test
    fun findsEveryAlertWithItsRecipients() {
        val targets = alertTargets(cameras, faceRules)
        assertEquals(
            listOf("Person · Schedule 1", "Person · Schedule 2", "Gate line", "Sound · Glass Break", "Person · Unknown Person"),
            targets.map { it.title },
        )
        assertEquals("Front · Driveway", targets[0].scope)
        assertEquals(listOf("glen@example.com", "old@typed.com"), targets[0].email)
        assertEquals(listOf("user:2"), targets[1].push)
        assertTrue(targets[0].emailOn)
        assertEquals("Front · Driveway", targets[4].scope)
        assertEquals(listOf("a@example.com", "user:1"), targets[4].email)
    }

    @Test
    fun typedAddressesOfUsersShowAsThoseUsers() {
        assertEquals(listOf("user:1", "old@typed.com"), toRecipientRefs(listOf("GLEN@example.com", "old@typed.com"), users))
    }

    @Test
    fun pickerListsReachableUsersThenEntriesThatReachNobody() {
        val email = recipientOptions(listOf("user:1", "old@typed.com", "user:3", "user:9"), users, RecipientChannel.EMAIL)
        assertEquals(
            listOf(
                RecipientOption("user:1", "Glen", "glen@example.com", orphan = false),
                RecipientOption("user:2", "sam", "sam@example.com", orphan = false),
                RecipientOption("old@typed.com", "old@typed.com", "Not a user", orphan = true),
                RecipientOption("user:3", "gone", "Inactive, gets no email", orphan = true),
                RecipientOption("user:9", "Deleted user #9", "Gets no email", orphan = true),
            ),
            email,
        )
        val push = recipientOptions(listOf("user:2"), users, RecipientChannel.PUSH)
        assertEquals(listOf("user:1", "user:2"), push.map { it.value })
        assertEquals("No push topic", push[1].detail)
    }

    @Test
    fun savingChangesOnlyTheRecipientLists() {
        val target = alertTargets(cameras, faceRules)[1]
        val updated = withRecipients(cameras, target.path, target.source, listOf("user:1"), listOf("user:1", "user:2"))
        val schedule = updated[0].jsonObject["detection"]!!.jsonObject["zones"]!!.jsonArray[0].jsonObject["object_rules"]!!
            .jsonArray[0].jsonObject["alert_schedules"]!!.jsonArray[1].jsonObject
        assertEquals(JsonArray(listOf(JsonPrimitive("user:1"))), schedule["email_recipients"])
        assertEquals(JsonArray(listOf(JsonPrimitive("user:1"), JsonPrimitive("user:2"))), schedule["push_recipients"])
        // Everything else is exactly as read, including fields the app doesn't model.
        fun stripSchedule(root: JsonArray): JsonArray = withRecipients(root, target.path, target.source, emptyList(), emptyList())
        assertEquals(stripSchedule(cameras), stripSchedule(updated))
        assertEquals(JsonPrimitive("********"), updated[0].jsonObject["password"])
    }

    @Test
    fun faceRuleEmailIsSavedAsAString() {
        val target = alertTargets(cameras, faceRules).last()
        val updated = withRecipients(faceRules, target.path, target.source, listOf("user:1", "a@example.com"), listOf("user:1"))
        val rule = updated[0] as JsonObject
        assertEquals(JsonPrimitive("user:1, a@example.com"), rule["email_recipients"])
        assertEquals(JsonPrimitive("Unknown Person"), rule["name"])
    }

    private val cameraOffline: JsonObject = json.parseToJsonElement(
        """{"enabled": true, "offline_delay_minutes": 5, "recipients": ["glen@example.com"], "push_recipients": ["user:1"]}""",
    ).jsonObject

    @Test
    fun cameraOfflineComesFirstWithItsOwnRecipientKeys() {
        val targets = alertTargets(cameras, faceRules, cameraOffline)
        val offline = targets.first()
        assertEquals(AlertSource.CAMERA_OFFLINE, offline.source)
        assertEquals("Camera Offline", offline.title)
        assertEquals("All cameras", offline.scope)
        assertTrue(offline.emailOn)
        assertEquals(listOf("glen@example.com"), offline.email)
        assertEquals(listOf("user:1"), offline.push)
        assertEquals(6, targets.size)
        // Without the settings (older server) the list is as before.
        assertEquals(5, alertTargets(cameras, faceRules).size)
    }

    @Test
    fun savingCameraOfflineKeepsTheSwitchAndDelay() {
        val updated = withOfflineRecipients(cameraOffline, listOf("user:1", "old@typed.com"), emptyList())
        assertEquals(JsonArray(listOf(JsonPrimitive("user:1"), JsonPrimitive("old@typed.com"))), updated["recipients"])
        assertEquals(JsonArray(emptyList()), updated["push_recipients"])
        assertEquals(JsonPrimitive(true), updated["enabled"])
        assertEquals(JsonPrimitive(5), updated["offline_delay_minutes"])
    }
}
