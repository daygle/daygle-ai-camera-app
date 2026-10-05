package com.daygle.aicamera.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

/**
 * What the server's local vision model wrote about an event
 * (`metadata.ai_description`): one factual sentence plus up to eight short
 * object tags the detector does not cover (e.g. "ladder", "hi-vis vest").
 * Tags that repeat a real detection label are already removed server-side.
 */
data class AiDescription(
    val text: String?,
    val tags: List<String>,
) {
    val isEmpty: Boolean get() = text.isNullOrBlank() && tags.isEmpty()
}

/** The AI alert-verification verdict stored under `metadata.ai_verification`. */
enum class AiVerdict { CONFIRMED, FILTERED }

/** One recognised person from `metadata.face_identities.people`. */
data class FacePerson(
    /** Stable key: `id:<person_id>`, or `name:<lower>` for legacy rows. */
    val key: String,
    val name: String,
)

/** Recognised and unrecognised faces on an event (`metadata.face_identities`). */
data class FaceIdentities(
    val people: List<FacePerson> = emptyList(),
    val unknown: Int = 0,
) {
    val isEmpty: Boolean get() = people.isEmpty() && unknown <= 0

    operator fun plus(other: FaceIdentities): FaceIdentities = FaceIdentities(
        people = (people + other.people).distinctBy { it.key },
        unknown = unknown + other.unknown,
    )
}

private fun JsonElement?.asObject(): JsonObject? = this as? JsonObject

private fun JsonElement?.asString(): String? = (this as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }

/** The AI write-up for this event, or null when the model has not described it. */
fun Event.aiDescription(): AiDescription? {
    val record = metadata["ai_description"].asObject() ?: return null
    val tags = (record["tags"] as? JsonArray).orEmpty()
        .mapNotNull { it.asString()?.lowercase() }
        .distinct()
    return AiDescription(text = record["text"].asString(), tags = tags).takeUnless { it.isEmpty }
}

/** Whether the AI model confirmed or filtered this event's alert; null when it never ruled. */
fun Event.aiVerdict(): AiVerdict? =
    when (metadata["ai_verification"].asObject()?.get("status").asString()) {
        "confirmed" -> AiVerdict.CONFIRMED
        "filtered" -> AiVerdict.FILTERED
        else -> null
    }

/** Faces recognition saw on this event; empty for events that ran no face match. */
fun Event.faceIdentities(): FaceIdentities {
    val record = metadata["face_identities"].asObject() ?: return FaceIdentities()
    val people = (record["people"] as? JsonArray).orEmpty().mapNotNull { element ->
        val person = element.asObject() ?: return@mapNotNull null
        val name = person["name"].asString()
        val personId = person["person_id"].asString()
        val key = when {
            personId != null -> "id:$personId"
            name != null -> "name:${name.lowercase()}"
            else -> return@mapNotNull null
        }
        FacePerson(key = key, name = name ?: "Unknown person")
    }.distinctBy { it.key }
    val unknown = ((record["unknown"] as? JsonPrimitive)?.intOrNull ?: 0).coerceAtLeast(0)
    return FaceIdentities(people, unknown)
}

/**
 * Largest share (0-1) of a zone's pixels that changed among this list's motion
 * detections, or null when none recorded one (objects, and motion saved before
 * the server kept the share).
 */
fun List<Detection>.motionFraction(): Double? =
    filter { it.label.trim().equals("motion", ignoreCase = true) }
        .mapNotNull { it.motionFraction }
        .maxOrNull()

/** Every event linked to a recording: its trigger plus the clip's members, deduplicated. */
fun Recording.linkedEvents(): List<Event> =
    (listOfNotNull(event) + events).distinctBy { it.id }

/** The first AI write-up among the recording's linked events. */
fun Recording.aiDescription(): AiDescription? =
    linkedEvents().firstNotNullOfOrNull { it.aiDescription()?.takeIf { d -> !d.text.isNullOrBlank() } }

/**
 * AI tags for the clip: the server's `ai_labels` (tags attached to the
 * recording), falling back to the linked events' tags for older servers.
 */
fun Recording.aiTags(): List<String> =
    aiLabels.ifEmpty { linkedEvents().flatMap { it.aiDescription()?.tags.orEmpty() } }
        .map { it.lowercase() }
        .distinct()

/** Faces seen across every event linked to the recording. */
fun Recording.faceIdentities(): FaceIdentities =
    linkedEvents().fold(FaceIdentities()) { acc, event -> acc + event.faceIdentities() }

/**
 * Lower-cased text a keyword search may match on an event, mirroring the
 * server's library filter: labels, zones, source/camera, AI description and
 * tags, and recognised face names.
 */
fun Event.searchableText(): String = buildList {
    addAll(detections.map { it.label })
    addAll(detections.mapNotNull { it.zoneName })
    add(source)
    add(triggerLabel)
    add(triggerType)
    listOf("camera_name", "camera_id", "label", "class_label", "zone_name").forEach { add(metadataString(it)) }
    aiDescription()?.let { description ->
        add(description.text)
        addAll(description.tags)
    }
    addAll(faceIdentities().people.map { it.name })
}.filterNotNull().joinToString(" ").lowercase()

/** Searchable text for a recording: its own labels and AI tags plus every linked event. */
fun Recording.searchableText(): String = buildList {
    add(cameraId)
    add(source)
    add(triggerLabel)
    add(triggerType)
    addAll(labels)
    addAll(aiLabels)
    addAll(linkedEvents().map { it.searchableText() })
}.filterNotNull().joinToString(" ").lowercase()

/**
 * True when every whitespace-separated word of [query] appears in [haystack]
 * (AND semantics, as the server's keyword filter: "red car" narrows rather
 * than widens). A blank query matches everything.
 */
fun matchesAllWords(haystack: String, query: String): Boolean =
    query.lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }.all { haystack.contains(it) }

/**
 * How the server understood a plain-English search (`GET /api/event-search`):
 * concept groups (each a list of synonyms), an optional camera and time
 * window, and whether the model or the keyword fallback interpreted it.
 */
@Serializable
data class SearchInterpretation(
    val terms: List<List<String>> = emptyList(),
    val camera: String? = null,
    val since: String? = null,
    val until: String? = null,
    @SerialName("interpreted_by") val interpretedBy: String? = null,
    /** True when nothing matched every concept, so results match any of them. */
    val relaxed: Boolean = false,
)

/** Response of `GET /api/event-search`. */
@Serializable
data class EventSearchResponse(
    val items: List<Event> = emptyList(),
    val interpretation: SearchInterpretation? = null,
)
