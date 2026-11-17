package com.daygle.aicamera.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
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

/**
 * The AI alert-verification verdict stored under `metadata.ai_verification`.
 * [UNVERIFIED] is an alert sent without a verdict: the model was unsure
 * (`unsure`), could not be asked (`error`) or was skipped (`skipped`), so a
 * model that has stopped answering shows up in the list.
 */
enum class AiVerdict(
    /** The server's `ai_check` filter value for this verdict. */
    val queryValue: String,
    val label: String,
) {
    CONFIRMED("confirmed", "AI Verified"),
    FILTERED("filtered", "AI Filtered"),
    UNVERIFIED("unverified", "AI Unverified"),
}

/**
 * The verdict with the model's reasons, for the chip's details: [summary] is
 * one plain sentence and [details] one line per checked object, e.g.
 * "person: not present - a coat on a hook".
 */
data class AiVerification(
    val verdict: AiVerdict,
    val summary: String,
    val details: List<String>,
)

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

/** The AI model's verdict on this event's alert; null when verification never ran. */
fun Event.aiVerdict(): AiVerdict? = aiVerification()?.verdict

/** The AI alert verdict with its reasons; null when verification never ran. */
fun Event.aiVerification(): AiVerification? {
    val record = metadata["ai_verification"].asObject() ?: return null
    val status = record["status"].asString()
    val delivered = (record["delivered"] as? JsonPrimitive)?.booleanOrNull
    val details = (record["labels"].asObject() ?: JsonObject(emptyMap())).mapNotNull { (label, value) ->
        value.asObject()?.let { verdictLine(label, it) }
    }
    return when (status) {
        "confirmed" -> AiVerification(AiVerdict.CONFIRMED, "The AI model confirmed this alert.", details)
        "filtered" -> AiVerification(
            AiVerdict.FILTERED,
            // A filtered event can still notify when another of its alerts passed.
            if (delivered == false) {
                "Notification not sent: the AI model judged this a false alarm."
            } else {
                "The AI model rejected part of this alert; the rest was still sent."
            },
            details,
        )
        "unsure" -> AiVerification(
            AiVerdict.UNVERIFIED, "The AI model could not tell from the snapshot, so the alert was sent.", details,
        )
        "error" -> AiVerification(
            AiVerdict.UNVERIFIED, "The AI model could not check this alert, so it was sent unverified.", details,
        )
        "skipped" -> AiVerification(
            AiVerdict.UNVERIFIED,
            record["reason"].asString()?.trimEnd('.', ' ')?.let { "Sent without AI verification: $it." }
                ?: "Sent without AI verification.",
            details,
        )
        else -> null
    }
}

private fun verdictLine(label: String, answer: JsonObject): String {
    val name = label.replace('_', ' ')
    val error = answer["error"].asString()
    val present = (answer["present"] as? JsonPrimitive)?.booleanOrNull
    val unsure = (answer["unsure"] as? JsonPrimitive)?.booleanOrNull == true
    var verdict = when {
        error != null -> "error ($error)"
        unsure -> "unsure"
        present == false -> "not present"
        present == true -> "confirmed"
        else -> "unchecked"
    }
    answer["reason"].asString()?.takeIf { error == null }?.let { verdict += " - $it" }
    answer["instead"].asString()?.let { instead ->
        val article = if (instead.first().lowercaseChar() in "aeiou") "an" else "a"
        verdict += "; looks like $article $instead"
    }
    return "$name: $verdict"
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
