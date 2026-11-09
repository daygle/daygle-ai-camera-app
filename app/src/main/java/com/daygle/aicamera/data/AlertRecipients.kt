package com.daygle.aicamera.data

import com.daygle.aicamera.data.model.AppUser
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull

/*
 * Alert recipients, edited on the app's Alerts screen with the same picker as
 * the server's web Alerts page. Each alert stores `email_recipients` and
 * `push_recipients`; a picked user is stored as `user:<id>` and the server
 * resolves it to that user's current email address or ntfy topic when it
 * sends. Entries that reach nobody (an address that is not a user's, an
 * inactive or deleted user) are kept and shown so they can be unticked, never
 * dropped.
 *
 * The alerts live inside the server's whole camera config (`GET/PUT
 * /api/cameras`) and face rules. The app edits that JSON in place and changes
 * nothing but the recipient lists of the alerts it touches, so settings the
 * app doesn't model survive the round trip.
 */

enum class RecipientChannel { EMAIL, PUSH }

/** Where an alert's recipients live: in the cameras config or the face rules. */
enum class AlertSource { CAMERAS, FACE_RULES }

/**
 * One alert whose recipients can be picked. [path] leads from the root array
 * of [source] to the JSON object holding `email_recipients` / `push_recipients`.
 */
data class AlertTarget(
    val key: String,
    val source: AlertSource,
    val path: List<Any>,
    val title: String,
    val scope: String,
    val emailOn: Boolean,
    val pushOn: Boolean,
    val email: List<String>,
    val push: List<String>,
)

/** One row of the picker; [value] is what is stored (`user:<id>` or an address). */
data class RecipientOption(val value: String, val name: String, val detail: String, val orphan: Boolean)

private val USER_REF = Regex("""^user:(\d+)$""", RegexOption.IGNORE_CASE)

internal fun userRecipientId(value: String): Int? = USER_REF.find(value.trim())?.groupValues?.get(1)?.toIntOrNull()

/** Recipients as stored: a list, or (face rules' email) a comma-separated string. */
internal fun recipientList(element: JsonElement?): List<String> {
    val raw = when (element) {
        is JsonArray -> element.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
        is JsonPrimitive -> element.contentOrNull?.split(',').orEmpty()
        else -> emptyList()
    }
    return raw.map { it.trim() }.filter { it.isNotEmpty() }.map { entry ->
        userRecipientId(entry)?.let { "user:$it" } ?: entry
    }.distinctBy { it.lowercase() }
}

private fun AppUser.contact(channel: RecipientChannel): String = when (channel) {
    RecipientChannel.EMAIL -> email?.trim()?.takeIf { '@' in it }.orEmpty()
    RecipientChannel.PUSH -> ntfyTopic?.trim().orEmpty()
}

/**
 * Show an old typed address as its user: an address that belongs to an active
 * user becomes that user's reference, so ticking and unticking work as for any
 * user. Only applied to what the picker shows and saves for an edited alert.
 */
internal fun toRecipientRefs(entries: List<String>, users: List<AppUser>): List<String> {
    val byEmail = users.filter { it.active && it.contact(RecipientChannel.EMAIL).isNotEmpty() }
        .associateBy({ it.contact(RecipientChannel.EMAIL).lowercase() }, { "user:${it.id}" })
    return entries.map { byEmail[it.lowercase()] ?: it }.distinctBy { it.lowercase() }
}

/**
 * The picker rows: active users who can receive on [channel] (by name), then
 * anything [selected] that isn't one of them, flagged as an orphan.
 */
internal fun recipientOptions(
    selected: List<String>,
    users: List<AppUser>,
    channel: RecipientChannel,
): List<RecipientOption> {
    val noun = if (channel == RecipientChannel.EMAIL) "email" else "push"
    val options = users.filter { it.active && it.contact(channel).isNotEmpty() }
        .map { RecipientOption("user:${it.id}", it.displayName, it.contact(channel), orphan = false) }
        .sortedBy { it.name.lowercase() }
        .toMutableList()
    val listed = options.map { it.value.lowercase() }.toMutableSet()
    for (entry in selected) {
        if (!listed.add(entry.lowercase())) continue
        val id = userRecipientId(entry)
        val user = id?.let { users.firstOrNull { u -> u.id == it } }
        val (name, detail) = when {
            id == null -> entry to "Not a user"
            user == null -> "Deleted user #$id" to "Gets no $noun"
            !user.active -> user.displayName to "Inactive, gets no $noun"
            else -> user.displayName to (if (channel == RecipientChannel.EMAIL) "No email address" else "No push topic")
        }
        options += RecipientOption(entry, name, detail, orphan = true)
    }
    return options
}

private fun JsonElement?.obj(): JsonObject? = this as? JsonObject
private fun JsonElement?.arr(): JsonArray? = this as? JsonArray
private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
private fun JsonObject.flag(key: String): Boolean =
    (this[key] as? JsonPrimitive)?.let { it.booleanOrNull ?: (it.contentOrNull == "true") } ?: false

private fun titleCase(value: String): String =
    value.split('_', ' ').filter { it.isNotEmpty() }.joinToString(" ") { it.replaceFirstChar(Char::uppercaseChar) }

private val BEHAVIOUR_ALERTS = listOf(
    "tripwire" to "Line Crossing",
    "loiter" to "Loitering",
    "time_of_day" to "Unusual Time",
    "activity_spike" to "Activity Spike",
    "ai_tags" to "AI Tags",
)

/** Every alert in the camera config and face rules, in config order. */
internal fun alertTargets(cameras: JsonArray, faceRules: JsonArray): List<AlertTarget> {
    val targets = mutableListOf<AlertTarget>()
    val cameraNames = mutableMapOf<String, String>()
    val zoneNames = mutableMapOf<String, String>()
    fun add(source: AlertSource, path: List<Any>, holder: JsonObject, title: String, scope: String, emailOn: Boolean, pushOn: Boolean) {
        targets += AlertTarget(
            key = source.name + path.joinToString("/", prefix = ":"),
            source = source, path = path, title = title, scope = scope,
            emailOn = emailOn, pushOn = pushOn,
            email = recipientList(holder["email_recipients"]),
            push = recipientList(holder["push_recipients"]),
        )
    }
    cameras.forEachIndexed { ci, cameraElement ->
        val camera = cameraElement.obj() ?: return@forEachIndexed
        val cameraName = camera.str("name") ?: camera.str("id") ?: "Camera ${ci + 1}"
        camera.str("id")?.let { cameraNames[it] = cameraName }
        val detection = camera["detection"].obj() ?: return@forEachIndexed
        detection["zones"].arr()?.forEachIndexed zones@{ zi, zoneElement ->
            val zone = zoneElement.obj() ?: return@zones
            val zoneName = zone.str("name") ?: "Zone ${zi + 1}"
            zone.str("id")?.let { zoneNames[it] = zoneName }
            val scope = "$cameraName · $zoneName"
            zone["object_rules"].arr()?.forEachIndexed rules@{ ri, ruleElement ->
                val rule = ruleElement.obj() ?: return@rules
                val label = titleCase(rule.str("label") ?: "Object")
                val schedules = rule["alert_schedules"].arr().orEmpty()
                schedules.forEachIndexed sched@{ si, scheduleElement ->
                    val schedule = scheduleElement.obj() ?: return@sched
                    val title = if (schedules.size > 1) "$label · Schedule ${si + 1}" else label
                    add(
                        AlertSource.CAMERAS,
                        listOf(ci, "detection", "zones", zi, "object_rules", ri, "alert_schedules", si),
                        schedule, title, scope, schedule.flag("email_enabled"), schedule.flag("push_enabled"),
                    )
                }
            }
            for ((key, name) in BEHAVIOUR_ALERTS) {
                val rule = zone[key].obj() ?: continue
                add(
                    AlertSource.CAMERAS, listOf(ci, "detection", "zones", zi, key),
                    rule, rule.str("name")?.takeIf { key == "tripwire" } ?: name, scope,
                    rule.flag("email_enabled"), rule.flag("push_enabled"),
                )
            }
        }
        detection["sound"].obj()?.get("rules").arr()?.forEachIndexed sounds@{ ri, ruleElement ->
            val rule = ruleElement.obj() ?: return@sounds
            add(
                AlertSource.CAMERAS, listOf(ci, "detection", "sound", "rules", ri),
                rule, "Sound · " + (rule.str("name") ?: titleCase(rule.str("class") ?: "sound")), cameraName,
                rule.flag("email_enabled"), rule.flag("push_enabled"),
            )
        }
    }
    faceRules.forEachIndexed { fi, ruleElement ->
        val rule = ruleElement.obj() ?: return@forEachIndexed
        val camera = rule.str("camera_id")?.let { cameraNames[it] ?: it } ?: "All cameras"
        val zone = rule.str("zone_id")?.let { zoneNames[it] ?: it }
        add(
            AlertSource.FACE_RULES, listOf(fi),
            rule, "Person · " + (rule.str("name") ?: "Unknown"), listOfNotNull(camera, zone).joinToString(" · "),
            rule.flag("email_enabled"), rule.flag("push_enabled"),
        )
    }
    return targets
}

/**
 * [root] with the object at [path] given new recipient lists. Face rules
 * store email recipients as a comma-separated string, as the server expects.
 */
internal fun withRecipients(
    root: JsonArray,
    path: List<Any>,
    source: AlertSource,
    email: List<String>,
    push: List<String>,
): JsonArray {
    fun update(element: JsonElement, rest: List<Any>): JsonElement {
        if (rest.isEmpty()) {
            val holder = element as JsonObject
            val emailValue: JsonElement = if (source == AlertSource.FACE_RULES) {
                JsonPrimitive(email.joinToString(", "))
            } else {
                JsonArray(email.map { JsonPrimitive(it) })
            }
            return JsonObject(holder + mapOf("email_recipients" to emailValue, "push_recipients" to JsonArray(push.map { JsonPrimitive(it) })))
        }
        return when (val step = rest.first()) {
            is Int -> JsonArray((element as JsonArray).mapIndexed { i, child -> if (i == step) update(child, rest.drop(1)) else child })
            is String -> JsonObject((element as JsonObject).mapValues { (key, child) -> if (key == step) update(child, rest.drop(1)) else child })
            else -> error("Bad path step $step")
        }
    }
    return update(root, path) as JsonArray
}

/** The `cameras` array of `GET /api/cameras`, or the `rules` array of the face rules. */
internal fun JsonObject.arrayAt(key: String): JsonArray = (this[key] as? JsonArray) ?: JsonArray(emptyList())

/** What the Alerts screen edits: the users to pick from, and the raw configs. */
data class AlertConfig(val users: List<AppUser>, val cameras: JsonArray, val faceRules: JsonArray)
