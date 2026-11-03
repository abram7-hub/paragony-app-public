package pl.paragony.reading

import pl.paragony.money.AllocationException
import pl.paragony.money.DiscountAllocation
import pl.paragony.money.LineMath
import pl.paragony.money.Money
import pl.paragony.money.MoneyParseException
import java.time.LocalDate
import java.time.format.DateTimeParseException

enum class IssueLevel { ERROR, WARN }

/** [path] points into the read document, e.g. `items[3].unitPriceText`, so the UI can mark the field. */
data class ValidationIssue(val level: IssueLevel, val path: String, val message: String)

enum class SumCheck { OK, MISMATCH }

data class NormalizedLine(
    val position: Int,
    val nameRaw: String?,
    val quantity: Long?,
    val unit: String?,
    val unitPrice: Long?,
    val grossValue: Long?,
    /** Discounts printed under this line. */
    val itemDiscount: Long,
    /** grossValue - itemDiscount; null when grossValue is unreadable. */
    val valueAfterItemDiscounts: Long?,
    /** Share of document discounts, calculated (D6, `discountSource = ALLOCATED`), not read. */
    val allocatedDiscount: Long,
    /** grossValue - itemDiscount - allocatedDiscount (data model, LineItem.finalValue). */
    val finalValue: Long?,
    val vatRate: String?,
    val itemType: String?,
)

data class NormalizedPurchase(
    /** Decided by code from NIP / store name (D15). */
    val storeChain: String?,
    /** What the model put in `store.chain`; informational only. */
    val storeChainRead: String?,
    val storeNameRaw: String?,
    val sellerTaxId: String?,
    val purchaseDate: String?,
    val purchaseTime: String?,
    val totalAmount: Long?,
    val fiscalTotal: Long?,
    val itemsTotal: Long?,
    val totalItemDiscount: Long,
    val documentDiscount: Long,
    val currency: String,
    val sumCheck: SumCheck,
    val lines: List<NormalizedLine>,
)

data class ValidationResult(val purchase: NormalizedPurchase, val issues: List<ValidationIssue>) {
    val errors: List<ValidationIssue> get() = issues.filter { it.level == IssueLevel.ERROR }
    val warnings: List<ValidationIssue> get() = issues.filter { it.level == IssueLevel.WARN }
}

/**
 * Checks a read document with integer arithmetic. Port of `prototype/reading/validate.py`.
 *
 * The model only proposes data. This parses every amount, recomputes line values and totals and
 * reports what does not add up. It never changes amounts to make totals match.
 * Messages are for developers and logs; the review screen (S5) will map paths to Polish texts.
 */
object ReadingValidator {

    private val TIME = Regex("([01]\\d|2[0-3]):[0-5]\\d")

    fun validate(doc: ReadDocument, today: LocalDate = LocalDate.now()): ValidationResult {
        val issues = mutableListOf<ValidationIssue>()
        fun error(path: String, msg: String) = issues.add(ValidationIssue(IssueLevel.ERROR, path, msg))
        fun warn(path: String, msg: String) = issues.add(ValidationIssue(IssueLevel.WARN, path, msg))
        fun fmt(v: Long) = Money.format(v)

        fun amount(text: String?, path: String, required: Boolean = true): Long? {
            if (text.isNullOrBlank()) {
                if (required) error(path, "missing amount")
                return null
            }
            return try {
                Money.parseAmount(text)
            } catch (e: MoneyParseException) {
                error(path, e.message ?: "not an amount")
                null
            }
        }

        // --- header ---
        val date = doc.purchaseDate
        if (date.isNullOrEmpty()) {
            error("purchaseDate", "missing date (required to approve)")
        } else {
            try {
                if (LocalDate.parse(date).isAfter(today)) warn("purchaseDate", "date $date is in the future")
            } catch (e: DateTimeParseException) {
                error("purchaseDate", "not YYYY-MM-DD: '$date'")
            }
        }
        val time = doc.purchaseTime
        if (!time.isNullOrEmpty() && !TIME.matches(time)) warn("purchaseTime", "not HH:MM: '$time'")
        val taxIdRaw = doc.store.taxId
        val taxId = StoreChains.normalizeTaxId(taxIdRaw)
        if (!taxIdRaw.isNullOrEmpty() && taxId == null) warn("store.taxId", "NIP should have 10 digits: '$taxIdRaw'")

        // --- items ---
        data class Partial(val item: ReadItem, val index: Int, val qty: Long?, val price: Long?, val gross: Long?,
                           val discount: Long, val afterItem: Long?)

        val partials = doc.items.mapIndexed { i, item ->
            val p = "items[$i]"
            val qty = try {
                Money.parseQuantity(item.quantityText?.takeIf { it.isNotEmpty() } ?: "1")
            } catch (e: MoneyParseException) {
                error("$p.quantityText", e.message ?: "not a quantity")
                null
            }
            val price = amount(item.unitPriceText, "$p.unitPriceText", required = false)
            val gross = amount(item.grossValueText, "$p.grossValueText")
            var discount = 0L
            item.discounts.forEachIndexed { j, d ->
                val path = "$p.discounts[$j].amountText"
                amount(d.amountText, path)?.let { a ->
                    if (a < 0) warn(path, "discount given with minus sign; using absolute value")
                    discount += if (a < 0) -a else a
                }
            }
            var afterItem: Long? = null
            if (gross != null) {
                afterItem = gross - discount
                if (qty != null && price != null && !LineMath.quantityTimesPriceMatches(qty, price, gross)) {
                    warn("$p.grossValueText", "quantity x price = ${fmt(Money.lineValue(qty, price))}, printed ${fmt(gross)}")
                }
                if (gross > 0 && discount > gross) {
                    error("$p.discounts", "discount ${fmt(discount)} is larger than value ${fmt(gross)}")
                }
                if (!item.finalValueText.isNullOrEmpty()) {
                    val printed = amount(item.finalValueText, "$p.finalValueText")
                    if (printed != null && printed != afterItem) {
                        error("$p.finalValueText",
                            "value ${fmt(gross)} - discount ${fmt(discount)} = ${fmt(afterItem)}, printed ${fmt(printed)}")
                    }
                }
            }
            Partial(item, i, qty, price, gross, discount, afterItem)
        }

        var docDiscount = 0L
        doc.documentDiscounts.forEachIndexed { j, d ->
            amount(d.amountText, "documentDiscounts[$j].amountText")?.let { docDiscount += if (it < 0) -it else it }
        }

        val complete = partials.all { it.afterItem != null }

        // --- document discount split (D6) ---
        val isUntaxedDeposit = { p: Partial -> p.item.itemType == "DEPOSIT" && p.item.vatRate.isNullOrEmpty() }
        var allocated = List(partials.size) { 0L }
        if (docDiscount > 0 && complete) {
            val weights = partials.map { p ->
                val v = p.afterItem ?: 0L
                if (p.item.itemType == "DEPOSIT" || v <= 0) 0L else v
            }
            try {
                allocated = DiscountAllocation.allocate(docDiscount, weights)
            } catch (e: AllocationException) {
                error("documentDiscounts", e.message ?: "cannot split discount")
            }
        }

        val lines = partials.mapIndexed { k, p ->
            NormalizedLine(
                position = p.item.position ?: (p.index + 1),
                nameRaw = p.item.nameRaw,
                quantity = p.qty,
                unit = p.item.unit,
                unitPrice = p.price,
                grossValue = p.gross,
                itemDiscount = p.discount,
                valueAfterItemDiscounts = p.afterItem,
                allocatedDiscount = allocated[k],
                finalValue = p.afterItem?.let { it - allocated[k] },
                vatRate = p.item.vatRate?.takeIf { it.isNotEmpty() },
                itemType = p.item.itemType,
            )
        }

        // --- totals ---
        val amountDue = amount(doc.amountDueText, "amountDueText")
        val fiscalTotal = amount(doc.fiscalTotalText, "fiscalTotalText", required = false)
        val itemsTotal = LineMath.sum(partials.mapNotNull { it.afterItem }) - docDiscount
        if (lines.isEmpty()) error("items", "no items read")
        if (amountDue != null && complete && itemsTotal != amountDue) {
            error("amountDueText",
                "items sum to ${fmt(itemsTotal)}, document says ${fmt(amountDue)} (difference ${fmt(amountDue - itemsTotal)})")
        }

        val untaxedDeposits = LineMath.sum(partials.filter(isUntaxedDeposit).map { it.afterItem ?: 0L })
        if (fiscalTotal != null && amountDue != null && fiscalTotal != amountDue && fiscalTotal != amountDue - untaxedDeposits) {
            warn("fiscalTotalText",
                "total ${fmt(fiscalTotal)} matches neither amount due ${fmt(amountDue)} " +
                    "nor amount due without deposits ${fmt(amountDue - untaxedDeposits)}")
        }

        val printedDiscount = amount(doc.totalDiscountText, "totalDiscountText", required = false)
        val itemDiscountTotal = LineMath.sum(partials.map { it.discount })
        if (printedDiscount != null) {
            val abs = if (printedDiscount < 0) -printedDiscount else printedDiscount
            if (abs != itemDiscountTotal + docDiscount) {
                warn("totalDiscountText", "discounts read: ${fmt(itemDiscountTotal + docDiscount)}, printed total ${fmt(abs)}")
            }
        }

        // --- VAT summary ---
        if (doc.vatSummary.isNotEmpty()) {
            var vatTotal = 0L
            val allRated = partials.filterNot(isUntaxedDeposit).all { !it.item.vatRate.isNullOrEmpty() }
            doc.vatSummary.forEachIndexed { k, v ->
                val p = "vatSummary[$k]"
                val taxable = amount(v.taxableText, "$p.taxableText") ?: return@forEachIndexed
                vatTotal += taxable
                if (allRated && complete) {
                    // Values after item discounts, as printed per rate; document discounts are not split here.
                    val s = LineMath.sum(partials.filter { it.item.vatRate == v.vatRate }.map { it.afterItem!! })
                    if (s != taxable) warn("$p.taxableText", "items with rate ${v.vatRate} sum to ${fmt(s)}, summary says ${fmt(taxable)}")
                }
                if (!v.percentText.isNullOrEmpty() && !v.taxText.isNullOrEmpty()) {
                    try {
                        val expected = Money.vatFromGross(taxable, Money.parsePercent(v.percentText))
                        val tax = Money.parseAmount(v.taxText)
                        if (kotlin.math.abs(expected - tax) > 1) warn("$p.taxText", "expected tax about ${fmt(expected)}, printed ${fmt(tax)}")
                    } catch (e: MoneyParseException) {
                        warn(p, e.message ?: "cannot parse tax line")
                    }
                }
            }
            if (amountDue != null && vatTotal + untaxedDeposits != amountDue) {
                error("vatSummary",
                    "taxable sales ${fmt(vatTotal)} + deposits ${fmt(untaxedDeposits)} != amount due ${fmt(amountDue)}")
            }
        }

        val pays = doc.payments.mapIndexed { k, pay -> amount(pay.amountText, "payments[$k].amountText") }
        if (pays.isNotEmpty() && null !in pays && amountDue != null) {
            val paid = LineMath.sum(pays.filterNotNull())
            if (paid != amountDue) warn("payments", "payments ${fmt(paid)} != amount due ${fmt(amountDue)}")
        }

        doc.uncertainFields.forEach { u -> warn(u.path ?: "?", "model unsure: ${u.reason ?: ""}") }

        val sumOk = amountDue != null && complete && itemsTotal == amountDue
        val purchase = NormalizedPurchase(
            storeChain = StoreChains.detect(taxIdRaw, doc.store.name),
            storeChainRead = doc.store.chain,
            storeNameRaw = doc.store.name,
            sellerTaxId = taxId ?: taxIdRaw?.filter { it.isDigit() }?.takeIf { it.isNotEmpty() },
            purchaseDate = date,
            purchaseTime = time,
            totalAmount = amountDue,
            fiscalTotal = fiscalTotal,
            itemsTotal = if (complete) itemsTotal else null,
            totalItemDiscount = itemDiscountTotal,
            documentDiscount = docDiscount,
            currency = doc.currency?.takeIf { it.isNotEmpty() } ?: "PLN",
            sumCheck = if (sumOk && issues.none { it.level == IssueLevel.ERROR }) SumCheck.OK else SumCheck.MISMATCH,
            lines = lines,
        )
        return ValidationResult(purchase, issues.toList())
    }
}
