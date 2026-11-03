package pl.paragony.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import pl.paragony.data.PurchaseStatus
import pl.paragony.data.entity.Attachment
import pl.paragony.data.entity.Discount
import pl.paragony.data.entity.DiscountItem
import pl.paragony.data.entity.LineItem
import pl.paragony.data.entity.Purchase
import java.time.Instant

// Reads return live rows only (deletedAt IS NULL) unless the name says otherwise.
// Inserts abort on a duplicate id: ids are UUIDs, a clash means a bug, not something to overwrite.

@Dao
interface PurchaseDao {
    @Insert
    suspend fun insert(purchase: Purchase)

    @Update
    suspend fun update(purchase: Purchase)

    @Query("SELECT * FROM purchase WHERE id = :id AND deletedAt IS NULL")
    suspend fun getById(id: String): Purchase?

    @Query("SELECT * FROM purchase WHERE id = :id")
    suspend fun getByIdIncludingDeleted(id: String): Purchase?

    @Query(
        "SELECT * FROM purchase WHERE deletedAt IS NULL AND status = :status " +
            "ORDER BY purchasedAt IS NULL, purchasedAt DESC, createdAt DESC",
    )
    fun observeByStatus(status: PurchaseStatus): Flow<List<Purchase>>

    @Query("SELECT COUNT(*) FROM purchase WHERE deletedAt IS NULL AND status = :status")
    fun observeCountByStatus(status: PurchaseStatus): Flow<Int>

    /** Soft delete (trash). Lines stay as they are; a deleted purchase is excluded from every sum. */
    @Query("UPDATE purchase SET deletedAt = :now, updatedAt = :now WHERE id = :id AND deletedAt IS NULL")
    suspend fun softDelete(id: String, now: Instant): Int

    @Query("UPDATE purchase SET deletedAt = NULL, updatedAt = :now WHERE id = :id AND deletedAt IS NOT NULL")
    suspend fun restore(id: String, now: Instant): Int
}

@Dao
interface LineItemDao {
    @Insert
    suspend fun insertAll(lines: List<LineItem>)

    @Update
    suspend fun update(line: LineItem)

    @Query("SELECT * FROM line_item WHERE id = :id AND deletedAt IS NULL")
    suspend fun getById(id: String): LineItem?

    @Query("SELECT * FROM line_item WHERE purchaseId = :purchaseId AND deletedAt IS NULL ORDER BY position")
    suspend fun forPurchase(purchaseId: String): List<LineItem>

    @Query("SELECT * FROM line_item WHERE purchaseId = :purchaseId AND deletedAt IS NULL ORDER BY position")
    fun observeForPurchase(purchaseId: String): Flow<List<LineItem>>

    @Query("UPDATE line_item SET deletedAt = :now, updatedAt = :now WHERE id = :id AND deletedAt IS NULL")
    suspend fun softDelete(id: String, now: Instant): Int
}

@Dao
interface DiscountDao {
    @Insert
    suspend fun insertAll(discounts: List<Discount>)

    @Insert
    suspend fun insertItems(items: List<DiscountItem>)

    @Query("SELECT * FROM discount WHERE purchaseId = :purchaseId AND deletedAt IS NULL")
    suspend fun forPurchase(purchaseId: String): List<Discount>

    @Query(
        "SELECT di.* FROM discount_item di JOIN discount d ON d.id = di.discountId " +
            "WHERE d.purchaseId = :purchaseId AND d.deletedAt IS NULL AND di.deletedAt IS NULL",
    )
    suspend fun itemsForPurchase(purchaseId: String): List<DiscountItem>
}

@Dao
interface AttachmentDao {
    @Insert
    suspend fun insertAll(attachments: List<Attachment>)

    @Query("SELECT * FROM attachment WHERE purchaseId = :purchaseId AND deletedAt IS NULL ORDER BY sortOrder")
    suspend fun forPurchase(purchaseId: String): List<Attachment>

    /** Same image added again (S7 duplicates). */
    @Query("SELECT * FROM attachment WHERE sha256 = :sha256 AND deletedAt IS NULL")
    suspend fun findBySha256(sha256: String): List<Attachment>
}
