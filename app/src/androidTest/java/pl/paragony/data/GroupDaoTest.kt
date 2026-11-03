package pl.paragony.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import pl.paragony.data.entity.ExpenseGroup
import pl.paragony.data.entity.LineItem
import pl.paragony.data.entity.Purchase
import java.time.Instant
import java.time.LocalDateTime

/**
 * DAO tests on a real SQLite (in memory). Same cases as the JVM tests in `groups`, now through
 * the queries, plus the transactions that write assignments. Run in Android Studio on a phone
 * or emulator: right click `app/src/androidTest` → Run, or `gradlew connectedAndroidTest`.
 */
@RunWith(AndroidJUnit4::class)
class GroupDaoTest {
    private lateinit var db: AppDatabase
    private val now = Instant.parse("2026-10-30T10:00:00Z")
    private val from = LocalDateTime.of(2026, 10, 1, 0, 0)
    private val to = LocalDateTime.of(2026, 11, 1, 0, 0)

    @Before
    fun open() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
    }

    @After
    fun close() = db.close()

    private suspend fun group(name: String, archived: Boolean = false): String {
        val g = ExpenseGroup(createdAt = now, updatedAt = now, name = name, archivedAt = if (archived) now else null)
        db.groupDao().insert(g)
        return g.id
    }

    private suspend fun purchase(
        total: Long,
        lines: List<Long>,
        at: LocalDateTime = LocalDateTime.of(2026, 10, 15, 12, 0),
        status: PurchaseStatus = PurchaseStatus.APPROVED,
    ): Pair<String, List<String>> {
        val p = Purchase(
            createdAt = now, updatedAt = now, kind = if (lines.isEmpty()) PurchaseKind.MANUAL else PurchaseKind.RECEIPT,
            status = status, purchasedAt = at, hasTime = true, totalAmount = total,
        )
        db.purchaseDao().insert(p)
        val items = lines.mapIndexed { i, v ->
            LineItem(
                createdAt = now, updatedAt = now, purchaseId = p.id, position = i + 1, nameRaw = "POS $i",
                quantity = 1000, unit = QuantityUnit.PCS, grossValue = v, finalValue = v,
            )
        }
        db.lineItemDao().insertAll(items)
        return p.id to items.map { it.id }
    }

    private suspend fun sum(vararg groups: String) = db.groupDao().sumForGroups(groups.toList(), "PLN", from, to)

    @Test
    fun requirementsExample() = runTest {
        val mine = group("Moje")
        val home = group("Dom")
        val (p, lines) = purchase(15000, listOf(13000, 2000))
        db.groupDao().setPurchaseGroups(p, setOf(mine, home), now)
        db.groupDao().setLineGroups(lines[1], setOf(mine), now) // cosmetic out of "Dom"

        assertEquals(15000L, sum(mine))
        assertEquals(13000L, sum(home))
        assertEquals(15000L, sum(mine, home))
        assertEquals(
            mapOf(mine to 15000L, home to 13000L),
            db.groupDao().sumPerGroup("PLN", from, to).associate { it.groupId to it.total },
        )
        assertEquals(15000L, db.groupDao().sumAll("PLN", from, to))
        assertEquals(1, db.groupDao().countLinesWithOwnSettings(p))
    }

    @Test
    fun changingPurchaseGroupsKeepsLineExceptions() = runTest {
        val mine = group("Moje")
        val home = group("Dom")
        val trip = group("Wyjazd")
        val (p, lines) = purchase(1000, listOf(600, 400))
        db.groupDao().setPurchaseGroups(p, setOf(mine, home), now)
        db.groupDao().setLineGroups(lines[1], setOf(mine), now)

        db.groupDao().setPurchaseGroups(p, setOf(home, trip), now)
        assertEquals(setOf(home, trip), db.groupDao().groupIdsOfLine(lines[0]).toSet())
        assertEquals(setOf(trip), db.groupDao().groupIdsOfLine(lines[1]).toSet()) // still out of "Dom"

        // Back to the original groups: the same rows are revived, no duplicates.
        db.groupDao().setPurchaseGroups(p, setOf(mine, home), now)
        assertEquals(setOf(mine, home), db.groupDao().groupIdsOfPurchase(p).toSet())
        assertEquals(setOf(mine), db.groupDao().groupIdsOfLine(lines[1]).toSet())
    }

    @Test
    fun lineGroupsBackToPurchaseClearsExceptions() = runTest {
        val mine = group("Moje")
        val home = group("Dom")
        val (p, lines) = purchase(1000, listOf(1000))
        db.groupDao().setPurchaseGroups(p, setOf(mine), now)
        db.groupDao().setLineGroups(lines[0], setOf(home), now)
        assertEquals(1, db.groupDao().countLinesWithOwnSettings(p))
        assertEquals(setOf(home), db.groupDao().groupIdsOfLine(lines[0]).toSet())

        db.groupDao().setLineGroups(lines[0], setOf(mine), now)
        assertEquals(0, db.groupDao().countLinesWithOwnSettings(p))
        assertEquals(emptyList<Any>(), db.groupDao().overridesOfPurchase(p))
    }

    @Test
    fun manualExpenseAndDeletedAndOutOfPeriodAndNotApproved() = runTest {
        val home = group("Dom")
        val (manual, _) = purchase(4999, emptyList())
        val (deleted, _) = purchase(700, listOf(700))
        val (before, _) = purchase(300, listOf(300), at = LocalDateTime.of(2026, 9, 30, 23, 59, 59))
        val (onBound, _) = purchase(200, listOf(200), at = LocalDateTime.of(2026, 11, 1, 0, 0))
        val (toReview, _) = purchase(100, listOf(100), status = PurchaseStatus.TO_REVIEW)
        val (firstSecond, _) = purchase(50, listOf(50), at = from)
        for (p in listOf(manual, deleted, before, onBound, toReview, firstSecond)) {
            db.groupDao().setPurchaseGroups(p, setOf(home), now)
        }
        db.purchaseDao().softDelete(deleted, now)
        assertEquals(4999L + 50L, sum(home))

        db.purchaseDao().restore(deleted, now)
        assertEquals(4999L + 50L + 700L, sum(home))
    }

    @Test
    fun archivedGroupStillCountsDeletedGroupDoesNot() = runTest {
        val old = group("Wakacje 2025", archived = true)
        val gone = group("Usunięta")
        val (p, _) = purchase(1200, listOf(1200))
        db.groupDao().setPurchaseGroups(p, setOf(old, gone), now)
        assertEquals(1200L, sum(old))

        db.openHelper.writableDatabase.execSQL("UPDATE expense_group SET deletedAt = 1 WHERE id = ?", arrayOf(gone))
        assertEquals(0L, sum(gone))
        assertEquals(setOf(old), db.groupDao().groupIdsOfPurchase(p).toSet())
    }

    @Test
    fun convertersRoundTripThroughDatabase() = runTest {
        val fields = listOf(UncertainField("items[0].grossValueText", "blurred"))
        val p = Purchase(
            createdAt = now, updatedAt = now, kind = PurchaseKind.RECEIPT, status = PurchaseStatus.TO_REVIEW,
            purchasedAt = LocalDateTime.of(2026, 10, 30, 14, 5), hasTime = true, totalAmount = 1459,
            uncertainFields = fields,
        )
        db.purchaseDao().insert(p)
        val back = db.purchaseDao().getById(p.id)!!
        assertEquals(p, back)

        db.purchaseDao().softDelete(p.id, now)
        assertNull(db.purchaseDao().getById(p.id))
        assertEquals(now, db.purchaseDao().getByIdIncludingDeleted(p.id)!!.deletedAt)
    }
}
