package cz.multio.doklady.ui

import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn

/** "1434.5" → "1 434,50 Kč" */
fun money(value: String?, currency: String? = "CZK"): String {
    if (value.isNullOrBlank()) return "—"
    val d = value.toDoubleOrNull() ?: return value
    val negative = d < 0
    val cents = kotlin.math.round(kotlin.math.abs(d) * 100).toLong()
    val whole = (cents / 100).toString().reversed().chunked(3).joinToString(" ").reversed()
    val frac = (cents % 100).toString().padStart(2, '0')
    val cur = when (currency ?: "CZK") { "CZK" -> "Kč"; "EUR" -> "€"; else -> currency }
    return "${if (negative) "-" else ""}$whole,$frac $cur"
}

/** "2026-10-17" nebo "2026-10-17T10:20:00" → "17. 10. 2026" */
fun czDate(iso: String?): String {
    if (iso.isNullOrBlank() || iso.length < 10) return "—"
    val (y, m, d) = iso.substring(0, 10).split("-")
    return "${d.trimStart('0')}. ${m.trimStart('0')}. $y"
}

/** Přijme "17.10.2026", "17. 10. 2026" i "2026-10-17" → ISO, nebo null. */
fun parseCzDate(text: String): String? {
    val t = text.trim()
    if (t.isEmpty()) return null
    runCatching { return LocalDate.parse(t).toString() }
    val parts = t.split('.', '/', '-').map { it.trim() }.filter { it.isNotEmpty() }
    if (parts.size != 3) return null
    return runCatching {
        LocalDate(parts[2].toInt().let { if (it < 100) 2000 + it else it }, parts[1].toInt(), parts[0].toInt()).toString()
    }.getOrNull()
}

/** Text částky od uživatele ("1 234,50") → "1234.50" pro server, nebo null. */
fun parseAmount(text: String): String? {
    val t = text.replace(" ", "").replace(" ", "").replace(",", ".").removeSuffix("Kč")
    if (t.isEmpty()) return null
    return t.toDoubleOrNull()?.let { t }
}

fun amountForInput(value: String?): String = value?.replace(".", ",") ?: ""

fun today(): LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault())

val docTypeNames = linkedMapOf(
    "faktura" to "Faktura přijatá",
    "uctenka" to "Účtenka",
    "zalohova" to "Zálohová faktura",
    "dobropis" to "Dobropis",
    "jine" to "Jiný doklad",
)

/** Druh dokladu v cestě faktury (portál: stage). Prázdný klíč = rozpoznat automaticky. */
val stageNames = linkedMapOf(
    "final" to "Faktura / účtenka",
    "order" to "Objednávka",
    "proforma" to "Zálohová faktura (proforma)",
    "advance" to "Daňový doklad k záloze",
    "delivery_note" to "Dodací list",
)

val paymentNames = linkedMapOf(
    "prevod" to "Převodem",
    "hotove" to "Hotově",
    "karta" to "Kartou",
    "dobirka" to "Dobírkou",
    "jine" to "Jinak",
)

fun statusName(status: String) = when (status) {
    "uploaded" -> "Nahráno"
    "processing" -> "Vytěžuje se…"
    "extracted" -> "Vytěženo"
    "checked" -> "Zkontrolováno"
    "error" -> "Chyba"
    "issued" -> "Vystaveno"
    "sent" -> "Odesláno"
    "paid" -> "Uhrazeno"
    "cancelled" -> "Stornováno"
    "open" -> "K doplnění"
    "submitted" -> "Odesláno účetní"
    "done" -> "Splněno"
    else -> status
}
