package pl.paragony.data

import androidx.room.TypeConverter
import java.time.Instant
import java.time.LocalDateTime

/** Room type converters; the formats live in [DbFormats]. Enums use Room's built-in TEXT mapping. */
class Converters {
    @TypeConverter
    fun instantToMillis(value: Instant?): Long? = value?.let(DbFormats::instantToMillis)

    @TypeConverter
    fun millisToInstant(value: Long?): Instant? = value?.let(DbFormats::millisToInstant)

    @TypeConverter
    fun purchasedAtToText(value: LocalDateTime?): String? = value?.let(DbFormats::purchasedAtToText)

    @TypeConverter
    fun textToPurchasedAt(value: String?): LocalDateTime? = value?.let(DbFormats::textToPurchasedAt)

    @TypeConverter
    fun uncertainFieldsToJson(value: List<UncertainField>): String = DbFormats.uncertainFieldsToJson(value)

    @TypeConverter
    fun jsonToUncertainFields(value: String): List<UncertainField> = DbFormats.jsonToUncertainFields(value)
}
