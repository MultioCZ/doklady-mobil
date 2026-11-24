package cz.multio.doklady.data

/**
 * QR Platba (SPD – Short Payment Descriptor, „SPD*1.0*ACC:CZ…*AM:1210.00*CC:CZK*X-VS:123*DT:20261130“).
 * Telefon ji jen přečte a ukáže; údaje do dokladu doplňuje portál po vytěžení (nic nepřepisuje).
 */
data class QrPayment(
    val raw: String,
    val iban: String? = null,
    val amount: String? = null,
    val currency: String? = null,
    val vs: String? = null,
    /** RRRR-MM-DD */
    val due: String? = null,
    val message: String? = null,
) {
    companion object {
        fun parse(text: String?): QrPayment? {
            val t = text?.trim().orEmpty()
            if (!t.uppercase().startsWith("SPD*") || t.length > 600) return null
            var iban: String? = null; var amount: String? = null; var currency: String? = null
            var vs: String? = null; var due: String? = null; var message: String? = null
            for (part in t.split('*').drop(2)) {
                val i = part.indexOf(':')
                if (i <= 0) continue
                val key = part.substring(0, i).trim().uppercase()
                val value = part.substring(i + 1).trim()
                when (key) {
                    "ACC" -> value.substringBefore('+').replace(" ", "").uppercase()
                        .takeIf { Regex("[A-Z]{2}\\d{2}[A-Z0-9]{10,30}").matches(it) }?.let { iban = it }
                    "AM" -> value.toDoubleOrNull()?.takeIf { it > 0 }?.let { amount = value }
                    "CC" -> value.uppercase().takeIf { Regex("[A-Z]{3}").matches(it) }?.let { currency = it }
                    "X-VS" -> value.takeIf { it.length <= 10 && it.all(Char::isDigit) }?.let { vs = it }
                    "DT" -> value.takeIf { it.length == 8 && it.all(Char::isDigit) }
                        ?.let { due = "${it.take(4)}-${it.substring(4, 6)}-${it.substring(6)}" }
                    "MSG" -> message = value.take(60)
                }
            }
            if (iban == null && amount == null) return null
            return QrPayment(t, iban, amount, currency, vs, due, message)
        }
    }
}
