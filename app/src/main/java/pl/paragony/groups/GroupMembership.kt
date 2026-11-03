package pl.paragony.groups

import pl.paragony.data.OverrideMode

/**
 * Group rules from D12, without Android. The SQL in `data.GroupSql` must give the same
 * answers; `tools/check_group_sql.py` compares them on random data.
 *
 * Group ids are plain strings. Callers pass only live (not deleted) groups and overrides.
 */
object GroupMembership {

    /** Groups of a line: purchase groups minus REMOVE, plus ADD. */
    fun groupsOfLine(purchaseGroups: Set<String>, overrides: Map<String, OverrideMode>): Set<String> {
        val result = purchaseGroups.filterTo(mutableSetOf()) { overrides[it] != OverrideMode.REMOVE }
        overrides.forEach { (group, mode) -> if (mode == OverrideMode.ADD) result += group }
        return result
    }

    /**
     * Minimal overrides that give a line exactly [desired] groups: ADD for groups the purchase
     * lacks, REMOVE for purchase groups the line should leave. Empty map = line follows its purchase.
     */
    fun overridesFor(purchaseGroups: Set<String>, desired: Set<String>): Map<String, OverrideMode> {
        val result = linkedMapOf<String, OverrideMode>()
        (desired - purchaseGroups).sorted().forEach { result[it] = OverrideMode.ADD }
        (purchaseGroups - desired).sorted().forEach { result[it] = OverrideMode.REMOVE }
        return result
    }

    /**
     * True when the line really differs from its purchase. Overrides kept after the purchase
     * groups changed (D12) may have become redundant, e.g. ADD G after the purchase joined G;
     * those do not count.
     */
    fun hasOwnSettings(purchaseGroups: Set<String>, overrides: Map<String, OverrideMode>): Boolean =
        groupsOfLine(purchaseGroups, overrides) != purchaseGroups

    /** Count items with custom group membership for the UI. */
    fun countLinesWithOwnSettings(purchaseGroups: Set<String>, lineOverrides: Collection<Map<String, OverrideMode>>): Int =
        lineOverrides.count { hasOwnSettings(purchaseGroups, it) }
}

/** One line of a purchase for group sums. */
data class LineForSum(val finalValue: Long, val overrides: Map<String, OverrideMode> = emptyMap())

/** A purchase for group sums: lines empty = counted with [totalAmount] (manual expense). */
data class PurchaseForSum(
    val totalAmount: Long,
    val groups: Set<String>,
    val lines: List<LineForSum> = emptyList(),
)

/** Reference sums (D12, D18): what the database queries must return. */
object GroupSums {

    /** Spending entries as (amount, groups): each line, or the whole purchase when it has no lines. */
    fun entries(purchase: PurchaseForSum): List<Pair<Long, Set<String>>> =
        if (purchase.lines.isEmpty()) {
            listOf(purchase.totalAmount to purchase.groups)
        } else {
            purchase.lines.map { it.finalValue to GroupMembership.groupsOfLine(purchase.groups, it.overrides) }
        }

    /** Total for the picked groups; an entry in several picked groups counts once. */
    fun sumForGroups(purchases: List<PurchaseForSum>, picked: Set<String>): Long =
        purchases.flatMap(::entries).filter { (_, groups) -> groups.any(picked::contains) }.sumOf { it.first }

    /** Total per group; an entry counts in each of its groups. */
    fun sumPerGroup(purchases: List<PurchaseForSum>): Map<String, Long> {
        val result = sortedMapOf<String, Long>()
        purchases.flatMap(::entries).forEach { (amount, groups) ->
            groups.forEach { result[it] = (result[it] ?: 0L) + amount }
        }
        return result
    }

    fun sumAll(purchases: List<PurchaseForSum>): Long = purchases.flatMap(::entries).sumOf { it.first }
}
