package pl.paragony.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import pl.paragony.data.GroupSql
import pl.paragony.data.OverrideMode
import pl.paragony.data.entity.ExpenseGroup
import pl.paragony.data.entity.LineItemGroupOverride
import pl.paragony.data.entity.PurchaseGroup
import pl.paragony.groups.GroupMembership
import java.time.Instant
import java.time.LocalDateTime

data class GroupTotal(val groupId: String, val total: Long)

/** Groups, their assignments and sums (D12, D18). */
@Dao
abstract class GroupDao {

    // --- groups ---

    @Insert
    abstract suspend fun insert(group: ExpenseGroup)

    @Update
    abstract suspend fun update(group: ExpenseGroup)

    @Query("SELECT * FROM expense_group WHERE deletedAt IS NULL AND archivedAt IS NULL ORDER BY name COLLATE NOCASE")
    abstract fun observeActive(): Flow<List<ExpenseGroup>>

    @Query("SELECT * FROM expense_group WHERE deletedAt IS NULL AND archivedAt IS NOT NULL ORDER BY name COLLATE NOCASE")
    abstract fun observeArchived(): Flow<List<ExpenseGroup>>

    /** Preselected for a new purchase; archived groups are never preselected. */
    @Query("SELECT id FROM expense_group WHERE deletedAt IS NULL AND archivedAt IS NULL AND isDefault = 1")
    abstract suspend fun defaultGroupIds(): List<String>

    // --- purchase groups ---

    @Query(
        "SELECT pg.groupId FROM purchase_group pg JOIN expense_group g ON g.id = pg.groupId " +
            "WHERE pg.purchaseId = :purchaseId AND pg.deletedAt IS NULL AND g.deletedAt IS NULL",
    )
    abstract suspend fun groupIdsOfPurchase(purchaseId: String): List<String>

    @Query("SELECT * FROM purchase_group WHERE purchaseId = :purchaseId")
    protected abstract suspend fun purchaseGroupRowsIncludingDeleted(purchaseId: String): List<PurchaseGroup>

    @Insert
    protected abstract suspend fun insertPurchaseGroup(row: PurchaseGroup)

    @Update
    protected abstract suspend fun updatePurchaseGroup(row: PurchaseGroup)

    /**
     * Sets the purchase's groups to exactly [groupIds]. Line exceptions are kept (D12);
     * show [countLinesWithOwnSettings] to the user afterwards.
     */
    @Transaction
    open suspend fun setPurchaseGroups(purchaseId: String, groupIds: Set<String>, now: Instant) {
        val rows = purchaseGroupRowsIncludingDeleted(purchaseId)
        for (row in rows) {
            val wanted = row.groupId in groupIds
            if (wanted && row.deletedAt != null) updatePurchaseGroup(row.copy(deletedAt = null, updatedAt = now))
            if (!wanted && row.deletedAt == null) updatePurchaseGroup(row.copy(deletedAt = now, updatedAt = now))
        }
        val existing = rows.mapTo(mutableSetOf()) { it.groupId }
        for (groupId in groupIds.sorted()) {
            if (groupId !in existing) {
                insertPurchaseGroup(PurchaseGroup(createdAt = now, updatedAt = now, purchaseId = purchaseId, groupId = groupId))
            }
        }
    }

    // --- line exceptions ---

    @Query("SELECT purchaseId FROM line_item WHERE id = :lineItemId AND deletedAt IS NULL")
    protected abstract suspend fun purchaseIdOfLine(lineItemId: String): String?

    @Query("SELECT * FROM line_item_group_override WHERE lineItemId = :lineItemId")
    protected abstract suspend fun overrideRowsIncludingDeleted(lineItemId: String): List<LineItemGroupOverride>

    /** Live exceptions of all live lines of a purchase, for live groups. */
    @Query(
        "SELECT o.* FROM line_item_group_override o " +
            "JOIN line_item li ON li.id = o.lineItemId " +
            "JOIN expense_group g ON g.id = o.groupId " +
            "WHERE li.purchaseId = :purchaseId AND li.deletedAt IS NULL " +
            "AND o.deletedAt IS NULL AND g.deletedAt IS NULL",
    )
    abstract suspend fun overridesOfPurchase(purchaseId: String): List<LineItemGroupOverride>

    @Query("SELECT id FROM line_item WHERE purchaseId = :purchaseId AND deletedAt IS NULL")
    protected abstract suspend fun lineIdsOfPurchase(purchaseId: String): List<String>

    @Insert
    protected abstract suspend fun insertOverride(row: LineItemGroupOverride)

    @Update
    protected abstract suspend fun updateOverride(row: LineItemGroupOverride)

    /** Groups a line is in, from the same view the sums use. */
    @Query("SELECT groupId FROM ${GroupSql.MEMBERSHIP_VIEW_NAME} WHERE entryId = :lineItemId AND isLine = 1")
    abstract suspend fun groupIdsOfLine(lineItemId: String): List<String>

    /**
     * Gives a line exactly [groupIds] by storing the minimal exceptions against its purchase's
     * current groups. Picking the purchase's own groups clears the exceptions.
     */
    @Transaction
    open suspend fun setLineGroups(lineItemId: String, groupIds: Set<String>, now: Instant) {
        val purchaseId = purchaseIdOfLine(lineItemId) ?: error("no live line item $lineItemId")
        val target = GroupMembership.overridesFor(groupIdsOfPurchase(purchaseId).toSet(), groupIds)
        val rows = overrideRowsIncludingDeleted(lineItemId)
        for (row in rows) {
            val mode = target[row.groupId]
            when {
                mode == null && row.deletedAt == null ->
                    updateOverride(row.copy(deletedAt = now, updatedAt = now))
                mode != null && (row.deletedAt != null || row.mode != mode) ->
                    updateOverride(row.copy(mode = mode, deletedAt = null, updatedAt = now))
            }
        }
        val existing = rows.mapTo(mutableSetOf()) { it.groupId }
        for ((groupId, mode) in target) {
            if (groupId !in existing) {
                insertOverride(
                    LineItemGroupOverride(createdAt = now, updatedAt = now, lineItemId = lineItemId, groupId = groupId, mode = mode),
                )
            }
        }
    }

    /** Count items with custom group membership. Ignore redundant exceptions. */
    @Transaction
    open suspend fun countLinesWithOwnSettings(purchaseId: String): Int {
        val purchaseGroups = groupIdsOfPurchase(purchaseId).toSet()
        val byLine: Map<String, Map<String, OverrideMode>> = overridesOfPurchase(purchaseId)
            .groupBy { it.lineItemId }
            .mapValues { (_, rows) -> rows.associate { it.groupId to it.mode } }
        return lineIdsOfPurchase(purchaseId).count { line ->
            GroupMembership.hasOwnSettings(purchaseGroups, byLine[line].orEmpty())
        }
    }

    // --- sums (only APPROVED purchases, from <= purchasedAt < to, one currency) ---

    /** Total for the picked groups; an entry in several of them counts once. */
    @Query(GroupSql.SUM_FOR_GROUPS)
    abstract suspend fun sumForGroups(groupIds: List<String>, currency: String, from: LocalDateTime, to: LocalDateTime): Long

    @Query(GroupSql.SUM_PER_GROUP)
    abstract suspend fun sumPerGroup(currency: String, from: LocalDateTime, to: LocalDateTime): List<GroupTotal>

    /** Everything, regardless of groups. */
    @Query(GroupSql.SUM_ALL)
    abstract suspend fun sumAll(currency: String, from: LocalDateTime, to: LocalDateTime): Long
}
