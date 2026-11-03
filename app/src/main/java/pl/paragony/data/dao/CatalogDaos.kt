package pl.paragony.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import pl.paragony.data.entity.Category
import pl.paragony.data.entity.Product
import pl.paragony.data.entity.ProductAlias
import pl.paragony.data.entity.Store

@Dao
interface StoreDao {
    @Insert
    suspend fun insert(store: Store)

    @Update
    suspend fun update(store: Store)

    @Query("SELECT * FROM store WHERE id = :id AND deletedAt IS NULL")
    suspend fun getById(id: String): Store?

    @Query("SELECT * FROM store WHERE taxId = :taxId AND deletedAt IS NULL ORDER BY createdAt")
    suspend fun findByTaxId(taxId: String): List<Store>

    @Query("SELECT * FROM store WHERE deletedAt IS NULL ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<Store>>
}

@Dao
interface CategoryDao {
    @Insert
    suspend fun insert(category: Category)

    @Update
    suspend fun update(category: Category)

    @Query("SELECT * FROM category WHERE deletedAt IS NULL ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<Category>>
}

@Dao
interface ProductDao {
    @Insert
    suspend fun insert(product: Product)

    @Update
    suspend fun update(product: Product)

    @Query("SELECT * FROM product WHERE id = :id AND deletedAt IS NULL")
    suspend fun getById(id: String): Product?

    @Insert
    suspend fun insertAlias(alias: ProductAlias)

    /**
     * Remembered product for a receipt name: an alias for this chain wins over a chain-less one.
     * Exact text match; normalising names is for the product sessions (E3).
     */
    @Query(
        "SELECT * FROM product_alias WHERE nameRaw = :nameRaw AND deletedAt IS NULL " +
            "AND (storeChain = :storeChain OR storeChain IS NULL) " +
            "ORDER BY storeChain IS NULL, updatedAt DESC LIMIT 1",
    )
    suspend fun findAlias(nameRaw: String, storeChain: String?): ProductAlias?
}
