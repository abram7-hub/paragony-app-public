package pl.paragony.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import pl.paragony.data.AttachmentSource
import pl.paragony.data.DiscountScope
import pl.paragony.data.DiscountSource
import pl.paragony.data.LineItemType
import pl.paragony.data.PurchaseKind
import pl.paragony.data.PurchaseStatus
import pl.paragony.data.QuantityUnit
import pl.paragony.data.StoredSumCheck
import pl.paragony.data.UncertainField
import java.time.Instant
import java.time.LocalDateTime
import java.util.UUID

/** D11: every row id is a text UUID, never an autoincrement. */
fun newId(): String = UUID.randomUUID().toString()

// Every table carries id, createdAt, updatedAt, deletedAt (D11). Soft delete = deletedAt set.
// Amounts are Long grosze, quantities Long thousandths (D5).
// Hard deletes only happen when the trash is emptied; then children go with CASCADE
// and loose references become NULL.

/** A receipt or a manual expense (data model: Purchase). E4 fields come in E4. */
@Entity(
    tableName = "purchase",
    foreignKeys = [
        ForeignKey(entity = Store::class, parentColumns = ["id"], childColumns = ["storeId"], onDelete = ForeignKey.SET_NULL),
        ForeignKey(entity = Category::class, parentColumns = ["id"], childColumns = ["categoryId"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [
        Index("storeId"),
        Index("categoryId"),
        Index("status"),
        Index("purchasedAt"),
        Index("documentNumber"),
    ],
)
data class Purchase(
    @PrimaryKey val id: String = newId(),
    val createdAt: Instant,
    val updatedAt: Instant,
    val deletedAt: Instant? = null,
    val kind: PurchaseKind,
    val status: PurchaseStatus,
    val storeId: String? = null,
    val storeNameRaw: String? = null,
    /** Local time of purchase; required to approve. Midnight when [hasTime] is false. */
    val purchasedAt: LocalDateTime? = null,
    val hasTime: Boolean = false,
    val documentNumber: String? = null,
    val sellerTaxId: String? = null,
    val codeContent: String? = null,
    /** Amount printed on the document (amount due). */
    val totalAmount: Long,
    val currency: String = "PLN",
    val paymentMethod: String? = null,
    /** Description of a manual expense. */
    val title: String? = null,
    /** Default category (manual expense). */
    val categoryId: String? = null,
    val note: String? = null,
    val sumCheck: StoredSumCheck = StoredSumCheck.NOT_CHECKED,
    val uncertainFields: List<UncertainField> = emptyList(),
    val readerName: String? = null,
    val readerVersion: String? = null,
    /** Raw reader answer, kept to re-read or compare. */
    val rawReadJson: String? = null,
)

@Entity(
    tableName = "line_item",
    foreignKeys = [
        ForeignKey(entity = Purchase::class, parentColumns = ["id"], childColumns = ["purchaseId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Product::class, parentColumns = ["id"], childColumns = ["productId"], onDelete = ForeignKey.SET_NULL),
        ForeignKey(entity = Category::class, parentColumns = ["id"], childColumns = ["categoryId"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [Index("purchaseId"), Index("productId"), Index("categoryId")],
)
data class LineItem(
    @PrimaryKey val id: String = newId(),
    val createdAt: Instant,
    val updatedAt: Instant,
    val deletedAt: Instant? = null,
    val purchaseId: String,
    /** Order on the document. */
    val position: Int,
    val nameRaw: String,
    val productId: String? = null,
    /** Thousandths: 1 pc = 1000, 0.345 kg = 345. */
    val quantity: Long,
    val unit: QuantityUnit,
    /** Before discounts. */
    val unitPrice: Long? = null,
    /** Value before discounts. */
    val grossValue: Long,
    /** Discount printed under this line. */
    val itemDiscount: Long = 0,
    /** Share of the document discount, calculated (D6). */
    val allocatedDiscount: Long = 0,
    /** grossValue - itemDiscount - allocatedDiscount. Negative for deposit returns. */
    val finalValue: Long,
    val itemType: LineItemType = LineItemType.PRODUCT,
    val categoryId: String? = null,
    /** Manual fix: protected from being overwritten by a re-read. */
    val editedByUser: Boolean = false,
)

/** Document-level discount (data model: Discount). */
@Entity(
    tableName = "discount",
    foreignKeys = [
        ForeignKey(entity = Purchase::class, parentColumns = ["id"], childColumns = ["purchaseId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("purchaseId")],
)
data class Discount(
    @PrimaryKey val id: String = newId(),
    val createdAt: Instant,
    val updatedAt: Instant,
    val deletedAt: Instant? = null,
    val purchaseId: String,
    val label: String? = null,
    /** Positive grosze. */
    val amount: Long,
    val scope: DiscountScope,
    val source: DiscountSource,
)

/** How much of a [Discount] went to a line item. */
@Entity(
    tableName = "discount_item",
    foreignKeys = [
        ForeignKey(entity = Discount::class, parentColumns = ["id"], childColumns = ["discountId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = LineItem::class, parentColumns = ["id"], childColumns = ["lineItemId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("discountId"), Index("lineItemId")],
)
data class DiscountItem(
    @PrimaryKey val id: String = newId(),
    val createdAt: Instant,
    val updatedAt: Instant,
    val deletedAt: Instant? = null,
    val discountId: String,
    val lineItemId: String,
    val amount: Long,
)

/** Image or file of a document. The file lives in app storage; the database keeps path and hash (D10). */
@Entity(
    tableName = "attachment",
    foreignKeys = [
        ForeignKey(entity = Purchase::class, parentColumns = ["id"], childColumns = ["purchaseId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("purchaseId"), Index("sha256")],
)
data class Attachment(
    @PrimaryKey val id: String = newId(),
    val createdAt: Instant,
    val updatedAt: Instant,
    val deletedAt: Instant? = null,
    val purchaseId: String,
    val filePath: String,
    val sha256: String,
    val mimeType: String,
    /** data model `order`; renamed because ORDER is an SQL keyword. */
    val sortOrder: Int,
    val source: AttachmentSource,
)
