package pl.paragony.money

/**
 * Checks for a single line. Values come from the document and are never changed here;
 * the functions only say whether they agree.
 */
object LineMath {

    /** Value after discounts: grossValue - itemDiscount - allocatedDiscount (data model, LineItem). */
    fun finalValue(grossValue: Long, itemDiscount: Long, allocatedDiscount: Long = 0): Long =
        Math.subtractExact(Math.subtractExact(grossValue, itemDiscount), allocatedDiscount)

    /**
     * Whether quantity x unit price matches the printed value before discount, after HALF_UP rounding.
     * Uses absolute value of the printed amount, so returned deposits (negative lines) pass.
     */
    fun quantityTimesPriceMatches(quantityThousandths: Long, unitPriceGrosze: Long, grossValue: Long): Boolean =
        Money.lineValue(quantityThousandths, unitPriceGrosze) == if (grossValue < 0) -grossValue else grossValue

    /** Sum of values with overflow check. */
    fun sum(values: Iterable<Long>): Long = values.fold(0L) { acc, v -> Math.addExact(acc, v) }
}
