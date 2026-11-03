package pl.paragony.money

/**
 * Money arithmetic. Amounts are [Long] in grosze, quantities are [Long] in thousandths,
 * percents are [Long] in hundredths of a percent (D5). Never Double/Float.
 *
 * All rounding in the app happens here: HALF_UP (half away from zero) for both signs.
 * Mirrors `prototype/reading/money.py`; test cases are shared.
 */
class MoneyParseException(message: String) : IllegalArgumentException(message)

object Money {
    private val SPACES = Regex("[\\s\u00a0\u202f]")
    private val CURRENCY = Regex("(zł|zl|pln)$", RegexOption.IGNORE_CASE)
    private val AMOUNT = Regex("^(-)?(\\d+)(?:[.,](\\d{1,2}))?$")
    private val QUANTITY = Regex("^(\\d+)(?:[.,](\\d{1,3}))?$")
    private val PERCENT = Regex("^(\\d+)(?:[.,](\\d{1,2}))?$")

    /** Largest digit count we accept before the separator; keeps every product below Long range. */
    private const val MAX_WHOLE_DIGITS = 12

    private fun clean(text: String?): String {
        if (text == null) throw MoneyParseException("empty value")
        var t = SPACES.replace(text, "")
        t = t.replace('−', '-').replace('\u2013', '-') // unicode minus, en dash
        return CURRENCY.replace(t, "")
    }

    private fun wholePart(digits: String, original: String?): Long {
        if (digits.length > MAX_WHOLE_DIGITS) throw MoneyParseException("value too large: '$original'")
        return digits.toLong()
    }

    /** "1 234,56" -> 123456, "-7,20" -> -720, "30" -> 3000, "0,5" -> 50, "167,71 zł" -> 16771. */
    fun parseAmount(text: String?): Long {
        val m = AMOUNT.matchEntire(clean(text)) ?: throw MoneyParseException("not an amount: '$text'")
        val (sign, whole, frac) = m.destructured
        val value = wholePart(whole, text) * 100 + frac.padEnd(2, '0').toLong()
        return if (sign.isNotEmpty()) -value else value
    }

    /** "2" -> 2000, "2,590" -> 2590, "0,11" -> 110 (thousandths). */
    fun parseQuantity(text: String?): Long {
        val m = QUANTITY.matchEntire(clean(text)) ?: throw MoneyParseException("not a quantity: '$text'")
        val (whole, frac) = m.destructured
        return wholePart(whole, text) * 1000 + frac.padEnd(3, '0').toLong()
    }

    /** "23%" -> 2300, "05,00%" -> 500, "8.00%" -> 800. */
    fun parsePercent(text: String?): Long {
        val m = PERCENT.matchEntire(clean(text).trimEnd('%'))
            ?: throw MoneyParseException("not a percent: '$text'")
        val (whole, frac) = m.destructured
        return wholePart(whole, text) * 100 + frac.padEnd(2, '0').toLong()
    }

    /** Math.absExact needs Java 15; not available on API 26. */
    private fun absExact(x: Long): Long {
        if (x == Long.MIN_VALUE) throw ArithmeticException("long overflow")
        return if (x < 0) -x else x
    }

    /** Integer division rounded half away from zero. [denominator] must be positive. */
    fun roundHalfUpDiv(numerator: Long, denominator: Long): Long {
        require(denominator > 0) { "denominator must be positive" }
        val abs = absExact(numerator)
        var q = abs / denominator
        val r = abs % denominator
        if (r >= denominator - r) q += 1 // 2r >= d without overflow
        return if (numerator < 0) -q else q
    }

    /** quantity (thousandths) x unit price (grosze), rounded HALF_UP to grosze. */
    fun lineValue(quantityThousandths: Long, unitPriceGrosze: Long): Long =
        roundHalfUpDiv(Math.multiplyExact(quantityThousandths, unitPriceGrosze), 1000)

    /** Tax contained in a gross amount: gross * p / (100% + p). Percent in hundredths (23% = 2300). */
    fun vatFromGross(grossGrosze: Long, percentHundredths: Long): Long {
        require(percentHundredths >= 0) { "percent must not be negative" }
        return roundHalfUpDiv(Math.multiplyExact(grossGrosze, percentHundredths), 10_000 + percentHundredths)
    }

    /** 123456 -> "1234,56", -720 -> "-7,20". Plain format for messages; UI formatting comes later. */
    fun format(grosze: Long): String {
        val sign = if (grosze < 0) "-" else ""
        val g = absExact(grosze)
        return "$sign${g / 100},${(g % 100).toString().padStart(2, '0')}"
    }
}
