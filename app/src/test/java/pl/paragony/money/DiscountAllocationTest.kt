package pl.paragony.money

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** D6: proportional split of a document discount, leftover grosze by largest remainder. */
class DiscountAllocationTest {

    private fun allocate(discount: Long, vararg weights: Long) = DiscountAllocation.allocate(discount, weights.toList())

    @Test
    fun exactProportions() {
        assertEquals(listOf(100L, 200L, 300L), allocate(600, 1000, 2000, 3000))
    }

    @Test
    fun leftoverGroszGoesToLargestRemainder() {
        // 10,00 split over 3 equal lines: 3,33 each + 1 grosz to the first (tie -> earlier line)
        assertEquals(listOf(334L, 333L, 333L), allocate(1000, 500, 500, 500))
        // 1,00 over 1,00 / 2,00: exact 33,33 / 66,67 -> remainders 1/3 vs 2/3 -> second line gets it
        assertEquals(listOf(33L, 67L), allocate(100, 100, 200))
    }

    @Test
    fun tieOnRemainderPrefersLargerLine() {
        // 0,01 over 1,00 and 3,00: exact 0,25 and 0,75 -> second has larger remainder anyway
        assertEquals(listOf(0L, 1L), allocate(1, 100, 300))
        // 0,02 over 1,00 / 1,00 / 2,00: exact 0,5 / 0,5 / 1 -> floors 0,0,1, one grosz left,
        // equal remainders and weights -> earlier line
        assertEquals(listOf(1L, 0L, 1L), allocate(2, 100, 100, 200))
        // 0,02 over 0,01 / 0,03: exact 0,5 / 1,5 -> equal remainders -> larger line gets the grosz
        assertEquals(listOf(0L, 2L), allocate(2, 1, 3))
        // 0,01 over 1,00 / 2,00 / 1,00 where remainders 1:2:1 of 4 -> middle (largest remainder)
        assertEquals(listOf(0L, 1L, 0L), allocate(1, 100, 200, 100))
    }

    @Test
    fun wholePurchaseDiscountKeepsTotal() {
        // shop example style: lines 39,90 / 12,72 / 5,58, 10% off coupon of 5,82
        val weights = listOf(3990L, 1272L, 558L)
        val shares = DiscountAllocation.allocate(582, weights)
        assertEquals(582L, shares.sum())
        assertEquals(listOf(399L, 127L, 56L), shares)
    }

    @Test
    fun excludedLinesGetNothing() {
        // deposit passed as weight 0 by caller
        assertEquals(listOf(300L, 0L, 200L), allocate(500, 600, 0, 400))
    }

    @Test
    fun discountOnSomeProductsOnly() {
        // For a discount on selected products, the caller passes only the affected lines.
        val affected = listOf(799L, 1299L)
        val shares = DiscountAllocation.allocate(500, affected)
        assertEquals(listOf(190L, 310L), shares) // 190,42 / 309,58
        assertEquals(500L, shares.sum())
    }

    @Test
    fun zeroDiscountAndEmpty() {
        assertEquals(listOf(0L, 0L), allocate(0, 100, 200))
        assertEquals(emptyList<Long>(), allocate(0))
        assertEquals(listOf(0L), allocate(0, 0))
    }

    @Test
    fun discountEqualToTotalTakesEverything() {
        assertEquals(listOf(100L, 250L), allocate(350, 100, 250))
    }

    @Test
    fun invalidInput() {
        assertThrows(AllocationException::class.java) { allocate(301, 100, 200) }
        assertThrows(AllocationException::class.java) { allocate(1) }
        assertThrows(AllocationException::class.java) { allocate(1, 0, 0) }
        assertThrows(IllegalArgumentException::class.java) { allocate(-1, 100) }
        assertThrows(IllegalArgumentException::class.java) { allocate(10, 100, -50) }
    }

    @Test
    fun randomizedInvariants() {
        val rnd = Random(20261030)
        repeat(2000) {
            val weights = List(rnd.nextInt(1, 12)) { if (rnd.nextInt(5) == 0) 0L else rnd.nextLong(1, 50_000) }
            val total = weights.sum()
            if (total == 0L) return@repeat
            val discount = rnd.nextLong(0, total + 1)
            val shares = DiscountAllocation.allocate(discount, weights)
            assertEquals(discount, shares.sum())
            weights.indices.forEach { i ->
                assertTrue(shares[i] in 0..weights[i])
                // never more than 1 grosz away from the exact proportional share
                val exactTimesTotal = discount * weights[i]
                assertTrue(kotlin.math.abs(shares[i] * total - exactTimesTotal) < total)
            }
        }
    }
}
