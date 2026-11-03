package pl.paragony.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import pl.paragony.data.dao.AttachmentDao
import pl.paragony.data.dao.CategoryDao
import pl.paragony.data.dao.DiscountDao
import pl.paragony.data.dao.GroupDao
import pl.paragony.data.dao.LineItemDao
import pl.paragony.data.dao.ProductDao
import pl.paragony.data.dao.PurchaseDao
import pl.paragony.data.dao.StoreDao
import pl.paragony.data.entity.Attachment
import pl.paragony.data.entity.Category
import pl.paragony.data.entity.Discount
import pl.paragony.data.entity.DiscountItem
import pl.paragony.data.entity.ExpenseGroup
import pl.paragony.data.entity.LineItem
import pl.paragony.data.entity.LineItemGroupOverride
import pl.paragony.data.entity.Product
import pl.paragony.data.entity.ProductAlias
import pl.paragony.data.entity.Purchase
import pl.paragony.data.entity.PurchaseGroup
import pl.paragony.data.entity.SpendingGroupMembership
import pl.paragony.data.entity.Store

/**
 * Schema version history (each step = explicit [Migration] in [Migrations] + a migration test):
 * - 1 (S3): first schema, data model without E4/E5.
 */
@Database(
    version = AppDatabase.VERSION,
    entities = [
        Purchase::class, LineItem::class, Discount::class, DiscountItem::class, Attachment::class,
        Store::class, Category::class, Product::class, ProductAlias::class,
        ExpenseGroup::class, PurchaseGroup::class, LineItemGroupOverride::class,
    ],
    views = [SpendingGroupMembership::class],
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun purchaseDao(): PurchaseDao
    abstract fun lineItemDao(): LineItemDao
    abstract fun discountDao(): DiscountDao
    abstract fun attachmentDao(): AttachmentDao
    abstract fun storeDao(): StoreDao
    abstract fun categoryDao(): CategoryDao
    abstract fun productDao(): ProductDao
    abstract fun groupDao(): GroupDao

    companion object {
        const val VERSION = 1
        const val FILE_NAME = "paragony.db"

        /**
         * Never `fallbackToDestructiveMigration`: a missing migration must crash in testing,
         * not wipe the user's purchases.
         */
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, FILE_NAME)
                .addMigrations(*Migrations.ALL)
                .build()
    }
}

object Migrations {
    /** Add each new `Migration(n, n + 1)` here and a case to `MigrationTest`. */
    val ALL: Array<Migration> = arrayOf()
}
