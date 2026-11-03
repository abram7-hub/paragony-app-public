package pl.paragony.reading

/**
 * A document as proposed by the reading model, shape of `prototype/reading/schema.json` (D13).
 * Amounts and quantities are text copied from the document; numbers come only from `money`.
 */
data class ReadDocument(
    val documentType: String? = null,
    val store: ReadStore = ReadStore(),
    val purchaseDate: String? = null,
    val purchaseTime: String? = null,
    val documentNumber: String? = null,
    val transactionNumber: String? = null,
    val currency: String? = null,
    val items: List<ReadItem> = emptyList(),
    val documentDiscounts: List<ReadDiscount> = emptyList(),
    val totalDiscountText: String? = null,
    val vatSummary: List<ReadVatLine> = emptyList(),
    val fiscalTotalText: String? = null,
    val amountDueText: String? = null,
    val payments: List<ReadPayment> = emptyList(),
    val codeContent: String? = null,
    val uncertainFields: List<ReadUncertainField> = emptyList(),
    val notes: String? = null,
)

data class ReadStore(
    val name: String? = null,
    val chain: String? = null,
    val address: String? = null,
    val taxId: String? = null,
)

data class ReadItem(
    val position: Int? = null,
    val nameRaw: String? = null,
    val quantityText: String? = null,
    val unit: String? = null,
    val unitPriceText: String? = null,
    val grossValueText: String? = null,
    val discounts: List<ReadDiscount> = emptyList(),
    val finalValueText: String? = null,
    val vatRate: String? = null,
    val itemType: String? = null,
)

data class ReadDiscount(val label: String? = null, val amountText: String? = null)

data class ReadVatLine(
    val vatRate: String? = null,
    val percentText: String? = null,
    val taxableText: String? = null,
    val taxText: String? = null,
)

data class ReadPayment(val method: String? = null, val amountText: String? = null)

data class ReadUncertainField(val path: String? = null, val reason: String? = null)
