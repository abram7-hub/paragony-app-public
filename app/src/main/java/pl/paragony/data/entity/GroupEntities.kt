package pl.paragony.data.entity

import androidx.room.DatabaseView
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import pl.paragony.data.GroupSql
import pl.paragony.data.OverrideMode
import java.time.Instant

/** Expense group (D12). Table is `expense_group` because GROUP is an SQL keyword. */
@Entity(tableName = "expense_group")
data class ExpenseGroup(
    @PrimaryKey val id: String = newId(),
    val createdAt: Instant,
    val updatedAt: Instant,
    val deletedAt: Instant? = null,
    val name: String,
    /** ARGB, e.g. 0xFF4CAF50. */
    val color: Long? = null,
    val description: String? = null,
    /** Archived groups keep their purchases and still count when picked in a filter. */
    val archivedAt: Instant? = null,
    /** Preselected for new purchases. */
    val isDefault: Boolean = false,
)

/**
 * Purchase belongs to a group. One row per pair; removing sets [deletedAt], adding again
 * revives the same row (keeps the id stable for sync).
 */
@Entity(
    tableName = "purchase_group",
    foreignKeys = [
        ForeignKey(entity = Purchase::class, parentColumns = ["id"], childColumns = ["purchaseId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = ExpenseGroup::class, parentColumns = ["id"], childColumns = ["groupId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index(value = ["purchaseId", "groupId"], unique = true), Index("groupId")],
)
data class PurchaseGroup(
    @PrimaryKey val id: String = newId(),
    val createdAt: Instant,
    val updatedAt: Instant,
    val deletedAt: Instant? = null,
    val purchaseId: String,
    val groupId: String,
)

/** Line item exception to its purchase's groups (D12). One row per pair, like [PurchaseGroup]. */
@Entity(
    tableName = "line_item_group_override",
    foreignKeys = [
        ForeignKey(entity = LineItem::class, parentColumns = ["id"], childColumns = ["lineItemId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = ExpenseGroup::class, parentColumns = ["id"], childColumns = ["groupId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index(value = ["lineItemId", "groupId"], unique = true), Index("groupId")],
)
data class LineItemGroupOverride(
    @PrimaryKey val id: String = newId(),
    val createdAt: Instant,
    val updatedAt: Instant,
    val deletedAt: Instant? = null,
    val lineItemId: String,
    val groupId: String,
    val mode: OverrideMode,
)

/**
 * Read-only view: which spending entry belongs to which group. An entry is a live line item,
 * or a whole purchase that has no live line items (manual expense). See [GroupSql.MEMBERSHIP_VIEW].
 */
@DatabaseView(viewName = GroupSql.MEMBERSHIP_VIEW_NAME, value = GroupSql.MEMBERSHIP_VIEW)
data class SpendingGroupMembership(
    /** Line item id, or purchase id for a purchase without lines. */
    val entryId: String,
    val purchaseId: String,
    val groupId: String,
    val amount: Long,
    val isLine: Boolean,
)
