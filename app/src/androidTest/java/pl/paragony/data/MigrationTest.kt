package pl.paragony.data

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Migration tests (each schema change needs an explicit migration and a test).
 * Schemas come from `app/schemas/` (exported by Room at build time, committed to git).
 *
 * When adding version N+1:
 * 1. Bump [AppDatabase.VERSION], write `Migration(N, N+1)`, add it to [Migrations.ALL].
 * 2. Build once so `app/schemas/.../N+1.json` appears; commit it.
 * 3. Add a test here: create N with sample rows via [MigrationTestHelper.createDatabase],
 *    migrate with `runMigrationsAndValidate(DB, N+1, true, *Migrations.ALL)`, check the rows.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {
    private val db = "migration-test.db"

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java)

    /** Exported schema 1 matches the entities, and a version-1 file opens with real data. */
    @Test
    fun version1OpensWithData() {
        helper.createDatabase(db, 1).apply {
            execSQL(
                "INSERT INTO purchase (id, createdAt, updatedAt, kind, status, hasTime, totalAmount, currency, " +
                    "sumCheck, uncertainFields) VALUES ('p1', 0, 0, 'MANUAL', 'APPROVED', 0, 1234, 'PLN', 'NOT_CHECKED', '[]')",
            )
            close()
        }
        helper.runMigrationsAndValidate(db, 1, true, *Migrations.ALL).close()
    }

    /** From the oldest exported version to the current one, through every migration. */
    @Test
    fun oldestToCurrent() {
        helper.createDatabase(db, 1).close()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val room = Room.databaseBuilder(context, AppDatabase::class.java, db)
            .addMigrations(*Migrations.ALL)
            .build()
        room.openHelper.writableDatabase.query("SELECT COUNT(*) FROM purchase").use {
            it.moveToFirst()
            assertEquals(0, it.getInt(0))
        }
        assertEquals(AppDatabase.VERSION, room.openHelper.readableDatabase.version)
        room.close()
    }
}
