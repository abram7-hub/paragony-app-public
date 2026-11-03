package pl.paragony.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.Instant
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/** A field the reader was unsure about (`uncertainFields` in the read document). */
data class UncertainField(val path: String, val reason: String? = null)

/**
 * How values are stored in SQLite (D18). Pure JVM so it is unit tested; [Converters] only delegates.
 *
 * - Technical timestamps (`createdAt`, `updatedAt`, `deletedAt`, `archivedAt`): epoch millis.
 * - Purchase date: local date-time as fixed-width text `yyyy-MM-ddTHH:mm:ss`, no time zone,
 *   because a receipt prints local time. Fixed width keeps text order = time order, so
 *   period filters work with plain `>=` / `<`.
 */
object DbFormats {
    private val PURCHASED_AT: DateTimeFormatter = DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss")

    fun purchasedAtToText(value: LocalDateTime): String =
        value.truncatedTo(ChronoUnit.SECONDS).format(PURCHASED_AT)

    fun textToPurchasedAt(text: String): LocalDateTime = LocalDateTime.parse(text, PURCHASED_AT)

    fun instantToMillis(value: Instant): Long = value.toEpochMilli()

    fun millisToInstant(value: Long): Instant = Instant.ofEpochMilli(value)

    fun uncertainFieldsToJson(fields: List<UncertainField>): String =
        JsonArray(
            fields.map {
                JsonObject(
                    mapOf(
                        "path" to JsonPrimitive(it.path),
                        "reason" to (it.reason?.let(::JsonPrimitive) ?: JsonNull),
                    ),
                )
            },
        ).toString()

    /** Lenient: anything unreadable becomes an empty list rather than crashing a screen. */
    fun jsonToUncertainFields(text: String?): List<UncertainField> {
        if (text.isNullOrBlank()) return emptyList()
        val array = runCatching { Json.parseToJsonElement(text).jsonArray }.getOrNull() ?: return emptyList()
        return array.mapNotNull { element ->
            val obj = runCatching { element.jsonObject }.getOrNull() ?: return@mapNotNull null
            val path = runCatching { obj["path"]?.jsonPrimitive?.contentOrNull }.getOrNull()
                ?: return@mapNotNull null
            val reason = runCatching { obj["reason"]?.jsonPrimitive?.contentOrNull }.getOrNull()
            UncertainField(path, reason)
        }
    }
}
