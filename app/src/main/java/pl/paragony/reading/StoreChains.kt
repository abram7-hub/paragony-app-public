package pl.paragony.reading

import java.text.Normalizer
import java.util.Locale

/**
 * Store chain decided by code, not by the model (D15): first by seller tax id (NIP), then by
 * keywords in the store name from the receipt header. Unknown store -> null (user picks it later).
 *
 * NIPs here come only from the anonymized test documents in `testdata/` (they are company data
 * printed on every receipt). Add more as new shops show up.
 */
object StoreChains {

    private val BY_TAX_ID = mapOf(
        "7791011327" to "Biedronka",
        "9370008168" to "Carrefour",
    )

    private val BY_NAME = listOf(
        "biedronka" to "Biedronka",
        "jeronimo martins" to "Biedronka",
        "lidl" to "Lidl",
        "carrefour" to "Carrefour",
        "kaufland" to "Kaufland",
        "auchan" to "Auchan",
        "zabka" to "Żabka",
        "dino" to "Dino",
        "netto" to "Netto",
        "rossmann" to "Rossmann",
        "stokrotka" to "Stokrotka",
        "lewiatan" to "Lewiatan",
        "aldi" to "Aldi",
        "intermarche" to "Intermarché",
        "polomarket" to "POLOmarket",
    )

    /** Digits only, or null when it is not a 10-digit NIP. Accepts "PL 779-101-13-27". */
    fun normalizeTaxId(taxId: String?): String? {
        val digits = taxId?.filter { it.isDigit() } ?: return null
        return digits.takeIf { it.length == 10 }
    }

    fun detect(taxId: String?, storeName: String?): String? {
        normalizeTaxId(taxId)?.let { nip -> BY_TAX_ID[nip]?.let { return it } }
        val name = storeName?.let(::fold) ?: return null
        return BY_NAME.firstOrNull { (keyword, _) -> Regex("\\b${Regex.escape(keyword)}\\b").containsMatchIn(name) }?.second
    }

    /** Lowercase without Polish diacritics: "ŻABKA" -> "zabka". */
    private fun fold(s: String): String {
        val lower = s.lowercase(Locale.ROOT).replace('ł', 'l')
        return Normalizer.normalize(lower, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")
    }
}
