package pl.paragony.groups

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.paragony.data.OverrideMode.ADD
import pl.paragony.data.OverrideMode.REMOVE
import kotlin.random.Random

class GroupMembershipTest {
    private val mine = "MOJE"
    private val home = "DOM"
    private val trip = "WYJAZD"

    @Test
    fun lineWithoutOverridesFollowsPurchase() {
        assertEquals(setOf(mine, home), GroupMembership.groupsOfLine(setOf(mine, home), emptyMap()))
    }

    @Test
    fun removeAndAdd() {
        val groups = GroupMembership.groupsOfLine(setOf(mine, home), mapOf(home to REMOVE, trip to ADD))
        assertEquals(setOf(mine, trip), groups)
    }

    @Test
    fun removeForGroupThePurchaseIsNotInDoesNothing() {
        assertEquals(setOf(mine), GroupMembership.groupsOfLine(setOf(mine), mapOf(home to REMOVE)))
    }

    @Test
    fun lineCanLeaveEveryGroup() {
        assertEquals(emptySet<String>(), GroupMembership.groupsOfLine(setOf(mine), mapOf(mine to REMOVE)))
    }

    @Test
    fun overridesForIsMinimal() {
        assertEquals(emptyMap<String, Any>(), GroupMembership.overridesFor(setOf(mine, home), setOf(mine, home)))
        assertEquals(mapOf(home to REMOVE), GroupMembership.overridesFor(setOf(mine, home), setOf(mine)))
        assertEquals(mapOf(trip to ADD, home to REMOVE), GroupMembership.overridesFor(setOf(mine, home), setOf(mine, trip)))
    }

    @Test
    fun overridesForRoundTripsOnRandomSets() {
        val all = listOf("A", "B", "C", "D", "E")
        val random = Random(12)
        repeat(500) {
            val purchase = all.filter { random.nextBoolean() }.toSet()
            val desired = all.filter { random.nextBoolean() }.toSet()
            val overrides = GroupMembership.overridesFor(purchase, desired)
            assertEquals(desired, GroupMembership.groupsOfLine(purchase, overrides))
        }
    }

    @Test
    fun exceptionsKeptAfterPurchaseGroupsChange() {
        // D12: line left DOM; purchase then also joins WYJAZD. The line is still out of DOM.
        val overrides = mapOf(home to REMOVE)
        assertEquals(setOf(mine, trip), GroupMembership.groupsOfLine(setOf(mine, home, trip), overrides))
    }

    @Test
    fun redundantExceptionIsNotOwnSetting() {
        // Line was added to DOM, later the whole purchase joined DOM: ADD is now redundant.
        assertFalse(GroupMembership.hasOwnSettings(setOf(mine, home), mapOf(home to ADD)))
        assertTrue(GroupMembership.hasOwnSettings(setOf(mine), mapOf(home to ADD)))
        assertFalse(GroupMembership.hasOwnSettings(setOf(mine), mapOf(home to REMOVE)))
    }

    @Test
    fun countLinesWithOwnSettings() {
        val lines = listOf(emptyMap(), mapOf(home to REMOVE), mapOf(mine to ADD), mapOf(trip to ADD))
        assertEquals(2, GroupMembership.countLinesWithOwnSettings(setOf(mine, home), lines))
    }
}

class GroupSumsTest {
    private val mine = "MOJE"
    private val home = "DOM"

    /** receipt 150 zł in Moje + Dom, cosmetic 20 zł removed from Dom. */
    private val example = PurchaseForSum(
        totalAmount = 15000,
        groups = setOf(mine, home),
        lines = listOf(
            LineForSum(13000),
            LineForSum(2000, mapOf(home to REMOVE)),
        ),
    )

    @Test
    fun requirementsExample() {
        val purchases = listOf(example)
        assertEquals(15000L, GroupSums.sumForGroups(purchases, setOf(mine)))
        assertEquals(13000L, GroupSums.sumForGroups(purchases, setOf(home)))
        assertEquals(15000L, GroupSums.sumForGroups(purchases, setOf(mine, home)))
        assertEquals(mapOf(home to 13000L, mine to 15000L), GroupSums.sumPerGroup(purchases))
        assertEquals(15000L, GroupSums.sumAll(purchases))
    }

    @Test
    fun manualExpenseCountsWithTotal() {
        val manual = PurchaseForSum(totalAmount = 4999, groups = setOf(home))
        val purchases = listOf(example, manual)
        assertEquals(17999L, GroupSums.sumForGroups(purchases, setOf(home)))
        assertEquals(19999L, GroupSums.sumForGroups(purchases, setOf(mine, home)))
        assertEquals(19999L, GroupSums.sumAll(purchases))
    }

    @Test
    fun lineAddedToOtherGroupOnly() {
        val p = PurchaseForSum(
            totalAmount = 1000,
            groups = setOf(mine),
            lines = listOf(LineForSum(700), LineForSum(300, mapOf(mine to REMOVE, home to ADD))),
        )
        assertEquals(700L, GroupSums.sumForGroups(listOf(p), setOf(mine)))
        assertEquals(300L, GroupSums.sumForGroups(listOf(p), setOf(home)))
        assertEquals(1000L, GroupSums.sumForGroups(listOf(p), setOf(mine, home)))
    }

    @Test
    fun depositReturnLowersTheSum() {
        val p = PurchaseForSum(
            totalAmount = 850,
            groups = setOf(home),
            lines = listOf(LineForSum(1000), LineForSum(-150)),
        )
        assertEquals(850L, GroupSums.sumForGroups(listOf(p), setOf(home)))
    }

    @Test
    fun noGroupsPickedIsZero() {
        assertEquals(0L, GroupSums.sumForGroups(listOf(example), emptySet()))
    }

    @Test
    fun unionNeverExceedsSumOfPartsAndCoversEachPart() {
        val groups = listOf("A", "B", "C")
        val random = Random(7)
        repeat(300) {
            val purchases = List(random.nextInt(1, 6)) {
                val pg = groups.filter { random.nextBoolean() }.toSet()
                val lines = List(random.nextInt(0, 5)) {
                    val overrides = groups.filter { random.nextInt(3) == 0 }
                        .associateWith { if (random.nextBoolean()) ADD else REMOVE }
                    LineForSum(random.nextLong(0, 10_000), overrides)
                }
                PurchaseForSum(random.nextLong(0, 10_000), pg, lines)
            }
            val perGroup = GroupSums.sumPerGroup(purchases)
            val union = GroupSums.sumForGroups(purchases, groups.toSet())
            assertTrue(union <= perGroup.values.sum())
            perGroup.values.forEach { assertTrue(it <= union) }
            assertTrue(union <= GroupSums.sumAll(purchases))
        }
    }
}
