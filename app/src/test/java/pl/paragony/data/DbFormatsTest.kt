package pl.paragony.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime

class DbFormatsTest {

    @Test
    fun purchasedAtIsFixedWidth() {
        assertEquals("2026-10-30T00:00:00", DbFormats.purchasedAtToText(LocalDateTime.of(2026, 10, 30, 0, 0)))
        assertEquals("2026-10-30T14:05:00", DbFormats.purchasedAtToText(LocalDateTime.of(2026, 10, 30, 14, 5)))
        assertEquals("2026-10-30T14:05:09", DbFormats.purchasedAtToText(LocalDateTime.of(2026, 10, 30, 14, 5, 9, 999_000_000)))
    }

    @Test
    fun purchasedAtRoundTrip() {
        val t = LocalDateTime.of(2026, 1, 2, 3, 4, 5)
        assertEquals(t, DbFormats.textToPurchasedAt(DbFormats.purchasedAtToText(t)))
    }

    @Test
    fun textOrderIsTimeOrder() {
        val times = listOf(
            LocalDateTime.of(2026, 9, 30, 23, 59, 59),
            LocalDateTime.of(2026, 10, 1, 0, 0),
            LocalDateTime.of(2026, 10, 1, 0, 0, 1),
            LocalDateTime.of(2026, 10, 10, 9, 0),
            LocalDateTime.of(2026, 11, 1, 0, 0),
        )
        val texts = times.map(DbFormats::purchasedAtToText)
        assertEquals(texts.sorted(), texts)
    }

    @Test
    fun instantMillis() {
        val i = Instant.ofEpochMilli(1_793_000_000_123)
        assertEquals(i, DbFormats.millisToInstant(DbFormats.instantToMillis(i)))
    }

    @Test
    fun uncertainFieldsRoundTrip() {
        val fields = listOf(UncertainField("items[3].grossValueText", "blurred"), UncertainField("purchaseDate"))
        val json = DbFormats.uncertainFieldsToJson(fields)
        assertEquals("""[{"path":"items[3].grossValueText","reason":"blurred"},{"path":"purchaseDate","reason":null}]""", json)
        assertEquals(fields, DbFormats.jsonToUncertainFields(json))
        assertEquals("[]", DbFormats.uncertainFieldsToJson(emptyList()))
    }

    @Test
    fun uncertainFieldsLenient() {
        assertTrue(DbFormats.jsonToUncertainFields(null).isEmpty())
        assertTrue(DbFormats.jsonToUncertainFields("not json").isEmpty())
        assertTrue(DbFormats.jsonToUncertainFields("{}").isEmpty())
        assertEquals(
            listOf(UncertainField("a"), UncertainField("b", "7")),
            DbFormats.jsonToUncertainFields("""[{"path":"a"}, {"reason":"no path"}, 5, {"path":"b","reason":7}]"""),
        )
    }
}
