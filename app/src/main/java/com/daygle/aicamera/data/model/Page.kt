package com.daygle.aicamera.data.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull

/**
 * One page of a cursor-paginated list endpoint (`GET /api/events`,
 * `/api/recordings`, `/api/snapshots`), which the server returns as
 * `{"items": [...], "next_cursor": "..."}`. Pass [nextCursor] back as the
 * `cursor` query parameter to fetch the following page; `null` means this
 * was the last page.
 *
 * Servers from before keyset pagination returned a bare JSON array; that
 * shape still decodes, as a single complete page.
 */
@Serializable(with = PageSerializer::class)
data class Page<T>(
    val items: List<T> = emptyList(),
    val nextCursor: String? = null,
)

class PageSerializer<T>(itemSerializer: KSerializer<T>) : KSerializer<Page<T>> {
    private val listSerializer = ListSerializer(itemSerializer)

    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("com.daygle.aicamera.data.model.Page", itemSerializer.descriptor) {
            element("items", listSerializer.descriptor)
            element("next_cursor", String.serializer().nullable.descriptor, isOptional = true)
        }

    override fun deserialize(decoder: Decoder): Page<T> {
        val input = decoder as? JsonDecoder
            ?: throw SerializationException("Page can only be decoded from JSON")
        return when (val element = input.decodeJsonElement()) {
            is JsonArray -> Page(items = input.json.decodeFromJsonElement(listSerializer, element))
            is JsonObject -> Page(
                items = element["items"]
                    ?.takeUnless { it is JsonNull }
                    ?.let { input.json.decodeFromJsonElement(listSerializer, it) }
                    ?: emptyList(),
                nextCursor = (element["next_cursor"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotEmpty() },
            )
            else -> throw SerializationException("Expected a JSON array or {items, next_cursor} object")
        }
    }

    override fun serialize(encoder: Encoder, value: Page<T>) {
        val output = encoder as? JsonEncoder
            ?: throw SerializationException("Page can only be encoded to JSON")
        output.encodeJsonElement(
            buildJsonObject {
                put("items", output.json.encodeToJsonElement(listSerializer, value.items))
                put("next_cursor", value.nextCursor?.let(::JsonPrimitive) ?: JsonNull)
            }
        )
    }
}
