package pl.paragony.money

/**
 * Splits a document-level discount across lines (D6).
 *
 * Shares are proportional to each line's value after its own item discounts. The exact share
 * `discount * weight / totalWeight` is floored, then the leftover grosze go one each to the lines
 * with the largest remainders (largest remainder method), so the parts always add up to the
 * discount exactly. Ties: larger weight first, then earlier line. The result is a calculation,
 * not something read from the document; callers store it as `discountSource = ALLOCATED`.
 */
object DiscountAllocation {

    /**
     * @param discount positive amount in grosze to split (0 gives all zeros).
     * @param weights value of each line after item discounts, in grosze. Lines that must not take
     *   part (deposits, returns) should be passed as 0 by the caller. Negative weights are rejected.
     * @return one share per weight, same order; shares sum to [discount], each share <= its weight.
     */
    fun allocate(discount: Long, weights: List<Long>): List<Long> {
        require(discount >= 0) { "discount must not be negative: $discount" }
        require(weights.all { it >= 0 }) { "weights must not be negative: $weights" }
        if (discount == 0L) return List(weights.size) { 0L }
        val total = weights.fold(0L) { acc, w -> Math.addExact(acc, w) }
        if (discount > total) {
            throw AllocationException(
                "discount ${Money.format(discount)} is larger than the lines it applies to ${Money.format(total)}",
            )
        }

        val shares = LongArray(weights.size)
        val remainders = LongArray(weights.size)
        var given = 0L
        for (i in weights.indices) {
            // discount * w can overflow Long only above ~9e18; amounts here are far below, but stay exact.
            val product = Math.multiplyExact(discount, weights[i])
            shares[i] = product / total
            remainders[i] = product % total
            given += shares[i]
        }
        val order = weights.indices
            .filter { weights[it] > 0 }
            .sortedWith(
                compareByDescending<Int> { remainders[it] }
                    .thenByDescending { weights[it] }
                    .thenBy { it },
            )
        var left = discount - given // < number of positive weights
        for (i in order) {
            if (left == 0L) break
            shares[i] += 1
            left -= 1
        }
        check(left == 0L)
        return shares.toList()
    }
}

class AllocationException(message: String) : IllegalArgumentException(message)
