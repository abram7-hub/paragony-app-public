package pl.paragony.reading

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Ports of ValidateTest from prototype/reading/test_reading.py, on the same testdata. */
class ReadingValidatorTest {

    private val today = LocalDate.of(2026, 10, 30)
    private fun validate(doc: ReadDocument) = ReadingValidator.validate(doc, today)

    @Test
    fun allExpectedFilesAddUp() {
        val files = TestData.expectedFiles
        assertTrue("expected at least 9 samples, got ${files.size}", files.size >= 9)
        for (f in files) {
            val r = validate(ReadDocumentJson.parse(f.readText()))
            assertEquals("${f.name}: ${r.issues}", SumCheck.OK, r.purchase.sumCheck)
            assertEquals(f.name, emptyList<ValidationIssue>(), r.errors)
            // every line's final value is known and they add up to the amount paid
            assertEquals(f.name, r.purchase.totalAmount, r.purchase.lines.sumOf { it.finalValue!! })
        }
    }

    @Test
    fun depositAfterSum() {
        val p = validate(TestData.load("biedronka_2000-01-03")).purchase
        assertEquals(1060L, p.totalAmount)
        assertEquals(960L, p.fiscalTotal)
    }

    @Test
    fun wrongAmountIsReportedNotFixed() {
        val doc = TestData.load("biedronka_2000-01-01")
        val items = doc.items.toMutableList()
        items[1] = items[1].copy(grossValueText = "29,78") // misread digit
        val r = validate(doc.copy(items = items))
        assertEquals(SumCheck.MISMATCH, r.purchase.sumCheck)
        assertEquals(2978L, r.purchase.lines[1].grossValue) // kept as read
        val paths = r.issues.map { it.path }
        assertTrue(paths.contains("items[1].grossValueText"))  // qty x price
        assertTrue(paths.contains("items[1].finalValueText"))  // inconsistent printed total
        assertTrue(paths.contains("amountDueText"))
    }

    @Test
    fun missingItemBreaksSum() {
        val doc = TestData.load("carrefour_2000-01-04")
        val r = validate(doc.copy(items = doc.items.filterIndexed { i, _ -> i != 8 })) // merged a real repeated line
        assertEquals(SumCheck.MISMATCH, r.purchase.sumCheck)
        assertTrue(r.errors.any { "difference 5,00" in it.message })
    }

    @Test
    fun voucherTakenAsDiscountBreaksSum() {
        val doc = TestData.load("biedronka_2000-01-03")
        val r = validate(doc.copy(documentDiscounts = listOf(ReadDiscount("VOUCHER", "30,00"))))
        assertEquals(SumCheck.MISMATCH, r.purchase.sumCheck)
    }

    @Test
    fun uncertainFieldsAreSurfaced() {
        val doc = TestData.load("biedronka_2000-01-02")
        val r = validate(doc.copy(uncertainFields = listOf(ReadUncertainField("items[2].unitPriceText", "smudged"))))
        assertTrue(r.issues.any { it.path == "items[2].unitPriceText" })
    }

    @Test
    fun missingDateIsError() {
        val r = validate(TestData.load("lidl_2000-01-09").copy(purchaseDate = null))
        assertTrue(r.errors.any { it.path == "purchaseDate" })
    }

    // --- Kotlin additions ---

    @Test
    fun storeChainComesFromCodeNotModel() {
        val expected = mapOf("biedronka" to "Biedronka", "carrefour" to "Carrefour", "lidl" to "Lidl")
        for (f in TestData.expectedFiles) {
            val doc = ReadDocumentJson.parse(f.readText())
            val p = validate(doc.copy(store = doc.store.copy(chain = null))).purchase // model left it empty
            assertEquals(f.name, expected[f.name.substringBefore('_')], p.storeChain)
        }
    }

    @Test
    fun futureDateAndBadTimeAreWarnings() {
        val r = validate(TestData.load("lidl_2000-01-09").copy(purchaseDate = "2026-11-02", purchaseTime = "25:10"))
        assertTrue(r.warnings.any { it.path == "purchaseDate" })
        assertTrue(r.warnings.any { it.path == "purchaseTime" })
        assertEquals(SumCheck.OK, r.purchase.sumCheck)
    }

    @Test
    fun invalidDateIsError() {
        val r = validate(TestData.load("lidl_2000-01-09").copy(purchaseDate = "31.01.2000"))
        assertTrue(r.errors.any { it.path == "purchaseDate" })
        assertEquals(SumCheck.MISMATCH, r.purchase.sumCheck)
    }

    @Test
    fun unreadableValueMakesTotalUnknown() {
        val doc = TestData.load("lidl_2000-01-09")
        val items = doc.items.toMutableList()
        items[0] = items[0].copy(grossValueText = "3,6S")
        val r = validate(doc.copy(items = items))
        assertNull(r.purchase.itemsTotal)
        assertNull(r.purchase.lines[0].finalValue)
        assertEquals(SumCheck.MISMATCH, r.purchase.sumCheck)
        assertTrue(r.errors.any { it.path == "items[0].grossValueText" })
    }

    @Test
    fun noItemsIsError() {
        val r = validate(ReadDocument(purchaseDate = "2026-10-01", amountDueText = "0,00"))
        assertTrue(r.errors.any { it.path == "items" })
        assertEquals(SumCheck.MISMATCH, r.purchase.sumCheck)
    }
}
