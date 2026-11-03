package pl.paragony.reading

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

/**
 * Lenient JSON -> [ReadDocument]. Missing or wrongly typed fields become null/empty instead of
 * failing, so the validator can report them. A number where text is expected is kept as its text,
 * so "14.59" stays exactly as the model wrote it and is parsed by `money`.
 *
 * Uses the JsonElement tree (no serialization compiler plugin needed).
 */
object ReadDocumentJson {

    class InvalidJsonException(message: String, cause: Throwable? = null) : IllegalArgumentException(message, cause)

    fun parse(text: String): ReadDocument {
        val root = try {
            Json.parseToJsonElement(text)
        } catch (e: Exception) {
            throw InvalidJsonException("not valid JSON: ${e.message}", e)
        }
        val obj = root as? JsonObject ?: throw InvalidJsonException("top level must be a JSON object")
        return document(obj)
    }

    private fun document(o: JsonObject) = ReadDocument(
        documentType = o.str("documentType"),
        store = (o["store"] as? JsonObject)?.let {
            ReadStore(it.str("name"), it.str("chain"), it.str("address"), it.str("taxId"))
        } ?: ReadStore(),
        purchaseDate = o.str("purchaseDate"),
        purchaseTime = o.str("purchaseTime"),
        documentNumber = o.str("documentNumber"),
        transactionNumber = o.str("transactionNumber"),
        currency = o.str("currency"),
        items = o.objects("items").map(::item),
        documentDiscounts = o.objects("documentDiscounts").map(::discount),
        totalDiscountText = o.str("totalDiscountText"),
        vatSummary = o.objects("vatSummary").map {
            ReadVatLine(it.str("vatRate"), it.str("percentText"), it.str("taxableText"), it.str("taxText"))
        },
        fiscalTotalText = o.str("fiscalTotalText"),
        amountDueText = o.str("amountDueText"),
        payments = o.objects("payments").map { ReadPayment(it.str("method"), it.str("amountText")) },
        codeContent = o.str("codeContent"),
        uncertainFields = o.objects("uncertainFields").map { ReadUncertainField(it.str("path"), it.str("reason")) },
        notes = o.str("notes"),
    )

    private fun item(o: JsonObject) = ReadItem(
        position = (o["position"] as? JsonPrimitive)?.intOrNull,
        nameRaw = o.str("nameRaw"),
        quantityText = o.str("quantityText"),
        unit = o.str("unit"),
        unitPriceText = o.str("unitPriceText"),
        grossValueText = o.str("grossValueText"),
        discounts = o.objects("discounts").map(::discount),
        finalValueText = o.str("finalValueText"),
        vatRate = o.str("vatRate"),
        itemType = o.str("itemType"),
    )

    private fun discount(o: JsonObject) = ReadDiscount(o.str("label"), o.str("amountText"))

    private fun JsonObject.str(key: String): String? {
        val e: JsonElement = this[key] ?: return null
        if (e is JsonNull || e !is JsonPrimitive) return null
        return e.content
    }

    private fun JsonObject.objects(key: String): List<JsonObject> =
        (this[key] as? JsonArray)?.filterIsInstance<JsonObject>() ?: emptyList()
}
