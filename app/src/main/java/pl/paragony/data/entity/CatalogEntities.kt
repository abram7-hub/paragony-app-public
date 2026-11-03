package pl.paragony.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import pl.paragony.data.QuantityUnit
import java.time.Instant

@Entity(tableName = "store", indices = [Index("taxId"), Index("chain")])
data class Store(
    @PrimaryKey val id: String = newId(),
    val createdAt: Instant,
    val updatedAt: Instant,
    val deletedAt: Instant? = null,
    val name: String,
    /** Code-decided chain (D15/D17), e.g. "BIEDRONKA". */
    val chain: String? = null,
    val address: String? = null,
    val branchNo: String? = null,
    val taxId: String? = null,
)

@Entity(tableName = "category")
data class Category(
    @PrimaryKey val id: String = newId(),
    val createdAt: Instant,
    val updatedAt: Instant,
    val deletedAt: Instant? = null,
    val name: String,
    /** ARGB, e.g. 0xFF4CAF50. */
    val color: Long? = null,
    val isSystem: Boolean = false,
)

@Entity(
    tableName = "product",
    foreignKeys = [
        ForeignKey(entity = Category::class, parentColumns = ["id"], childColumns = ["categoryId"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [Index("categoryId"), Index("ean")],
)
data class Product(
    @PrimaryKey val id: String = newId(),
    val createdAt: Instant,
    val updatedAt: Instant,
    val deletedAt: Instant? = null,
    val name: String,
    val brand: String? = null,
    val variant: String? = null,
    /** Thousandths of [packageUnit]: 500 g = 500 with KG. */
    val packageSize: Long? = null,
    val packageUnit: QuantityUnit? = null,
    val ean: String? = null,
    val categoryId: String? = null,
)

/** Remembered mapping of a receipt abbreviation to a product, optionally per chain. */
@Entity(
    tableName = "product_alias",
    foreignKeys = [
        ForeignKey(entity = Product::class, parentColumns = ["id"], childColumns = ["productId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("productId"), Index("nameRaw", "storeChain")],
)
data class ProductAlias(
    @PrimaryKey val id: String = newId(),
    val createdAt: Instant,
    val updatedAt: Instant,
    val deletedAt: Instant? = null,
    val nameRaw: String,
    val storeChain: String? = null,
    val productId: String,
)
