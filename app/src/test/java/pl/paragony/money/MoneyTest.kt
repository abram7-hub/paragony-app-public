package pl.paragony.money

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Cases from prototype/reading/test_reading.py (MoneyTest) plus Kotlin-only edge cases. */
class MoneyTest {

    @Test
    fun parseAmount_prototypeCases() {
        val cases = mapOf(
            "14,59" to 1459L, "-7,20" to -720L, "1 234,56" to 123456L, "0,5" to 50L, "30" to 3000L,
            "167,71 zł" to 16771L, "7.20" to 720L, "−6,30" to -630L,
        )
        cases.forEach { (text, want) -> assertEquals(text, want, Money.parseAmount(text)) }
        assertThrows(MoneyParseException::class.java) { Money.parseAmount("0,79A") }
    }

    @Test
    fun parseAmount_separatorsSpacesAndCurrency() {
        assertEquals(123456L, Money.parseAmount("1\u00a0234,56"))   // no-break space
        assertEquals(123456L, Money.parseAmount("1\u202f234,56"))   // narrow no-break space
        assertEquals(-720L, Money.parseAmount("\u20137,20"))        // en dash as minus
        assertEquals(1999L, Money.parseAmount("19,99 PLN"))
        assertEquals(1999L, Money.parseAmount("19,99zl"))
        assertEquals(0L, Money.parseAmount("0,00"))
        assertEquals(5L, Money.parseAmount("0,05"))
    }

    @Test
    fun parseAmount_rejectsGarbage() {
        listOf(null, "", " ", "1,234", "12,3,4", "abc", "1.234,56", "--1", "1-", "+5", ",50")
            .forEach { text -> assertThrows(text.toString(), MoneyParseException::class.java) { Money.parseAmount(text) } }
    }

    @Test
    fun parseAmount_rejectsHugeValues() {
        assertThrows(MoneyParseException::class.java) { Money.parseAmount("9999999999999,99") }
    }

    @Test
    fun parseQuantity_prototypeCases() {
        assertEquals(2000L, Money.parseQuantity("2"))
        assertEquals(2590L, Money.parseQuantity("2,590"))
        assertEquals(110L, Money.parseQuantity("0,11"))
        assertEquals(3000L, Money.parseQuantity("3,000"))
        assertThrows(MoneyParseException::class.java) { Money.parseQuantity("1,2345") }
    }

    @Test
    fun parseQuantity_rejectsNegative() {
        assertThrows(MoneyParseException::class.java) { Money.parseQuantity("-1") }
    }

    @Test
    fun lineValue_halfUp_prototypeCases() {
        assertEquals(515L, Money.lineValue(2590, 199))   // 5,1541 -> 5,15
        assertEquals(126L, Money.lineValue(105, 1199))   // 1,25895 -> 1,26
        assertEquals(2066L, Money.lineValue(560, 3690))  // 20,664 -> 20,66
        assertEquals(425L, Money.lineValue(74, 5740))    // 4,2476 -> 4,25
        assertEquals(274L, Money.lineValue(110, 2490))   // 2,739 -> 2,74
        assertEquals(1L, Money.lineValue(500, 1))        // 0,005 -> 0,01 (half up)
        assertEquals(-1L, Money.roundHalfUpDiv(-5, 10))  // symmetric for negatives
    }

    @Test
    fun roundHalfUpDiv_edges() {
        assertEquals(0L, Money.roundHalfUpDiv(4, 10))
        assertEquals(1L, Money.roundHalfUpDiv(5, 10))
        assertEquals(0L, Money.roundHalfUpDiv(-4, 10))
        assertEquals(-2L, Money.roundHalfUpDiv(-15, 10))
        assertEquals(3L, Money.roundHalfUpDiv(3, 1))
        assertEquals(0L, Money.roundHalfUpDiv(1, 3))     // 0,333 -> 0
        assertEquals(1L, Money.roundHalfUpDiv(2, 3))
        assertThrows(IllegalArgumentException::class.java) { Money.roundHalfUpDiv(1, 0) }
        assertThrows(IllegalArgumentException::class.java) { Money.roundHalfUpDiv(1, -2) }
        assertThrows(ArithmeticException::class.java) { Money.roundHalfUpDiv(Long.MIN_VALUE, 2) }
        // no overflow in the remainder test for a denominator close to Long.MAX_VALUE
        assertEquals(1L, Money.roundHalfUpDiv(Long.MAX_VALUE / 2 + 1, Long.MAX_VALUE))
    }

    @Test
    fun lineValue_weighedProducts() {
        // 0,345 kg x 12,99 zł/kg = 4,48155 -> 4,48
        assertEquals(448L, Money.lineValue(Money.parseQuantity("0,345"), Money.parseAmount("12,99")))
        // 1,005 kg x 9,99 = 10,03995 -> 10,04
        assertEquals(1004L, Money.lineValue(1005, 999))
        // 0,002 kg x 2,50 = 0,005 -> 0,01
        assertEquals(1L, Money.lineValue(2, 250))
    }

    @Test
    fun lineValue_overflowIsAnError() {
        assertThrows(ArithmeticException::class.java) { Money.lineValue(Long.MAX_VALUE / 10, 1000) }
    }

    @Test
    fun vat_prototypeCases() {
        assertEquals(356L, Money.vatFromGross(7483, 500))
        assertEquals(78L, Money.vatFromGross(1047, 800))
        assertEquals(500L, Money.parsePercent("05,00%"))
        assertEquals(2300L, Money.parsePercent("23.00%"))
        assertEquals(2300L, Money.parsePercent("23%"))
        assertEquals(0L, Money.vatFromGross(1000, 0))
        // Lidl testdata: 19,90 at 23% -> 3,72; 12,72 at 5% -> 0,61
        assertEquals(372L, Money.vatFromGross(1990, 2300))
        assertEquals(61L, Money.vatFromGross(1272, 500))
        assertThrows(MoneyParseException::class.java) { Money.parsePercent("abc%") }
    }

    @Test
    fun format() {
        assertEquals("1234,56", Money.format(123456))
        assertEquals("-7,20", Money.format(-720))
        assertEquals("0,05", Money.format(5))
        assertEquals("0,00", Money.format(0))
        assertEquals("-0,01", Money.format(-1))
    }

    @Test
    fun parseThenFormat_roundTrip() {
        listOf("0,01", "9,99", "-12,30", "100000,00").forEach { assertEquals(it, Money.format(Money.parseAmount(it))) }
    }

    @Test
    fun lineMath() {
        assertEquals(1500L, LineMath.finalValue(2000, 300, 200))
        assertTrue(LineMath.quantityTimesPriceMatches(2590, 199, 515))
        assertFalse(LineMath.quantityTimesPriceMatches(2590, 199, 516))
        assertTrue(LineMath.quantityTimesPriceMatches(1000, 50, -50)) // returned deposit
        assertEquals(6L, LineMath.sum(listOf(1, 2, 3)))
        assertThrows(ArithmeticException::class.java) { LineMath.sum(listOf(Long.MAX_VALUE, 1)) }
    }
}
