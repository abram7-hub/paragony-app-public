package pl.paragony.reading

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Synthetic receipts for the receipt validation cases: discounts, weighed products, multi-buy,
 * deposits, grosze. Built in code so each case shows exactly one thing.
 */
class ReceiptScenariosTest {

    private fun item(
        name: String, qty: String, price: String?, gross: String,
        discounts: List<String> = emptyList(), final: String? = null,
        vat: String? = "A", type: String = "PRODUCT", unit: String = "PCS",
    ) = ReadItem(
        nameRaw = name, quantityText = qty, unit = unit, unitPriceText = price, grossValueText = gross,
        discounts = discounts.map { ReadDiscount("rabat", it) }, finalValueText = final, vatRate = vat, itemType = type,
    )

    private fun doc(due: String, vararg items: ReadItem, docDiscounts: List<String> = emptyList(), totalDiscount: String? = null) =
        ReadDocument(
            purchaseDate = "2026-10-01", currency = "PLN", items = items.toList(), amountDueText = due,
            documentDiscounts = docDiscounts.map { ReadDiscount("rabat", it) }, totalDiscountText = totalDiscount,
        )

    private fun validate(d: ReadDocument) = ReadingValidator.validate(d, LocalDate.of(2026, 10, 30))

    @Test
    fun weighedProductRoundsHalfUp() {
        // 0,345 kg x 12,99 = 4,48155 -> 4,48
        val r = validate(doc("4,48", item("Jabłka", "0,345", "12,99", "4,48", unit = "KG")))
        assertEquals(SumCheck.OK, r.purchase.sumCheck)
        assertTrue(r.issues.isEmpty())
        assertEquals(345L, r.purchase.lines[0].quantity)
    }

    @Test
    fun weighedProductWrongRoundingIsWarned() {
        val r = validate(doc("4,49", item("Jabłka", "0,345", "12,99", "4,49", unit = "KG")))
        assertTrue(r.warnings.any { it.path == "items[0].grossValueText" })
        assertEquals(SumCheck.OK, r.purchase.sumCheck) // value as printed still adds up; only a warning
    }

    @Test
    fun multiBuyPromotionAsItemDiscount() {
        // "3 za 2": 3 x 4,99 = 14,97, discount 4,99 printed under the line
        val r = validate(doc("9,98", item("Jogurt", "3", "4,99", "14,97", listOf("4,99"), final = "9,98")))
        assertEquals(SumCheck.OK, r.purchase.sumCheck)
        assertEquals(499L, r.purchase.totalItemDiscount)
        assertEquals(998L, r.purchase.lines[0].finalValue)
    }

    @Test
    fun savingsAlreadyInPricesAreNotSubtractedAgain() {
        // screen shows total 80,00 and "savings 10,00" already included -> spend 80,00
        val r = validate(
            doc("80,00", item("Zakupy", "1", "80,00", "80,00"), totalDiscount = "10,00"),
        )
        assertEquals(SumCheck.OK, r.purchase.sumCheck)
        assertEquals(8000L, r.purchase.totalAmount)
        assertTrue(r.warnings.any { it.path == "totalDiscountText" }) // mismatch shown, amounts untouched
    }

    @Test
    fun discountWithMinusSignIsWarnedAndUsedAsAbsolute() {
        val r = validate(doc("8,00", item("Ser", "1", "10,00", "10,00", listOf("-2,00"))))
        assertTrue(r.warnings.any { it.path == "items[0].discounts[0].amountText" })
        assertEquals(SumCheck.OK, r.purchase.sumCheck)
    }

    @Test
    fun discountLargerThanLineIsError() {
        val r = validate(doc("-1,00", item("Ser", "1", "1,00", "1,00", listOf("2,00"))))
        assertTrue(r.errors.any { it.path == "items[0].discounts" })
    }

    @Test
    fun printedFinalValueMismatchIsError() {
        val r = validate(doc("7,00", item("Ser", "1", "10,00", "10,00", listOf("2,00"), final = "7,00")))
        assertTrue(r.errors.any { it.path == "items[0].finalValueText" })
        assertEquals(SumCheck.MISMATCH, r.purchase.sumCheck)
    }

    @Test
    fun depositsWithoutVatAndReturnedDeposit() {
        // 2 bottles + deposit 2 x 0,50, returned 3 bottles -1,50. Fiscal total without deposits.
        val d = doc(
            "9,50",
            item("Woda", "2", "5,00", "10,00", vat = "A"),
            item("Kaucja", "2", "0,50", "1,00", vat = null, type = "DEPOSIT"),
            item("Zwrot kaucji", "3", "0,50", "-1,50", vat = null, type = "DEPOSIT"),
        ).copy(
            fiscalTotalText = "10,00",
            vatSummary = listOf(ReadVatLine("A", "23,00%", "10,00", "1,87")),
        )
        val r = validate(d)
        assertEquals(r.issues.toString(), emptyList<ValidationIssue>(), r.issues)
        assertEquals(SumCheck.OK, r.purchase.sumCheck)
    }

    @Test
    fun vatSummaryNotMatchingAmountDueIsError() {
        val d = doc("10,00", item("Woda", "2", "5,00", "10,00"))
            .copy(vatSummary = listOf(ReadVatLine("A", "23%", "11,00", "2,06")))
        val r = validate(d)
        assertTrue(r.errors.any { it.path == "vatSummary" })
        assertTrue(r.warnings.any { it.path == "vatSummary[0].taxableText" })
    }

    @Test
    fun wrongTaxIsWarned() {
        val d = doc("10,00", item("Woda", "2", "5,00", "10,00"))
            .copy(vatSummary = listOf(ReadVatLine("A", "23%", "10,00", "2,30"))) // tax added on top, wrong
        val r = validate(d)
        assertTrue(r.warnings.any { it.path == "vatSummary[0].taxText" })
    }

    @Test
    fun documentDiscountIsSplitAndMarkedAsCalculated() {
        // 10,00 off a purchase of 30,00 + 0,01 + 20,00 + deposit 0,50 -> deposit excluded
        val r = validate(
            doc(
                "40,51",
                item("A", "1", "30,00", "30,00"),
                item("B", "1", "0,01", "0,01"),
                item("C", "1", "20,00", "20,00"),
                item("Kaucja", "1", "0,50", "0,50", vat = null, type = "DEPOSIT"),
                docDiscounts = listOf("10,00"),
            ),
        )
        assertEquals(SumCheck.OK, r.purchase.sumCheck)
        val alloc = r.purchase.lines.map { it.allocatedDiscount }
        assertEquals(1000L, alloc.sum())
        assertEquals(0L, alloc[3])
        // exact 599,88 / 0,20 / 399,92 grosze -> floors 599/0/399, two grosze left -> C (.92), then A (.88)
        assertEquals(listOf(600L, 0L, 400L, 0L), alloc)
        assertEquals(r.purchase.totalAmount, r.purchase.lines.sumOf { it.finalValue!! })
        assertEquals(3000L, r.purchase.lines[0].valueAfterItemDiscounts) // read value kept separately
    }

    @Test
    fun documentDiscountLargerThanPurchaseIsError() {
        val r = validate(doc("-5,00", item("A", "1", "5,00", "5,00"), docDiscounts = listOf("10,00")))
        assertTrue(r.errors.any { it.path == "documentDiscounts" })
        assertFalse(r.purchase.sumCheck == SumCheck.OK)
    }

    @Test
    fun paymentsNotMatchingAreWarned() {
        val d = doc("10,00", item("Woda", "2", "5,00", "10,00"))
            .copy(payments = listOf(ReadPayment("Karta", "9,00")))
        assertTrue(validate(d).warnings.any { it.path == "payments" })
    }

    @Test
    fun missingQuantityMeansOne() {
        val r = validate(doc("3,65", item("Wafle", "", "3,65", "3,65")))
        assertEquals(1000L, r.purchase.lines[0].quantity)
        assertTrue(r.issues.isEmpty())
    }
}
