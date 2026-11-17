package com.daygle.aicamera.data.model

import kotlinx.serialization.Serializable

/**
 * The server-side filters shared by the Events, Snapshots and Recordings
 * lists, mirroring the web UI's library filter bar. Null or blank fields are
 * not sent. Timestamps are ISO-8601 with an offset.
 */
data class LibraryQuery(
    val query: String? = null,
    val since: String? = null,
    val until: String? = null,
    val cameraId: String? = null,
    val label: String? = null,
    /** `any`, `unknown`, `id:<person_id>` or `name:<name>`. */
    val face: String? = null,
    val alertedOnly: Boolean = false,
    val oldestFirst: Boolean = false,
    /** Events only: the AI verification verdict to match (`ai_check`). */
    val aiCheck: AiVerdict? = null,
    /** Hide false positives, or list only them (`false_positive`); null = all. */
    val falsePositive: FalsePositiveFilter? = null,
    /**
     * Events only: the AI's per-detection verdict to match. Sent as the same
     * `ai_check` parameter as [aiCheck], so only one of the two is set.
     */
    val objectCheck: DetectionVerdictFilter? = null,
) {
    val sort: String get() = if (oldestFirst) "oldest" else "newest"
    val q: String? get() = query?.trim()?.takeIf { it.isNotEmpty() }
}

/** The Events AI Check filter's per-detection options (the server's `ai_check`). */
enum class DetectionVerdictFilter(val queryValue: String, val label: String) {
    CONFIRMED("object_confirmed", "AI Confirmed Object"),
    REJECTED("object_rejected", "AI Rejected Object"),
}

/** The library lists' False Positives filter (the server's `false_positive` parameter). */
enum class FalsePositiveFilter(val queryValue: String, val label: String) {
    HIDE("hide", "Hide False Positives"),
    ONLY("only", "Only False Positives"),
}

/** `GET /api/library/facets`: Label and Face options for the selected time window. */
@Serializable
data class LibraryFacets(
    val labels: List<LabelFacet> = emptyList(),
    val faces: FaceFacets = FaceFacets(),
)

@Serializable
data class LabelFacet(
    val value: String = "",
    val count: Int = 0,
    /** True when every occurrence is an AI tag rather than a detection. */
    val ai: Boolean = false,
)

@Serializable
data class FaceFacets(
    val people: List<PersonFacet> = emptyList(),
    val unknown: Int = 0,
)

@Serializable
data class PersonFacet(
    /** The filter token: `id:<person_id>` or `name:<name>`. */
    val value: String = "",
    val name: String = "",
    val count: Int = 0,
)
