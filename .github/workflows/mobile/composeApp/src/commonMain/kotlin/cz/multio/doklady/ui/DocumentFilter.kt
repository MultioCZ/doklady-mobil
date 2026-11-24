package cz.multio.doklady.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import cz.multio.doklady.data.Document

/** Filtr dokladů podle parametrů – vyhodnocuje se v telefonu nad načteným seznamem. */
@Immutable
data class DocFilter(
    val text: String = "",        // VS, číslo dokladu, poznámka
    val dateFrom: String = "",    // „1. 10. 2026“
    val dateTo: String = "",
    val amountFrom: String = "",
    val amountTo: String = "",
    val status: String? = null,   // null = vše
    val type: String? = null,
) {
    val isActive get() = this != DocFilter()

    fun matches(d: Document): Boolean {
        val t = text.trim().lowercase()
        if (t.isNotEmpty()) {
            val hay = listOfNotNull(d.variableSymbol, d.docNumber, d.note, d.supplierName).joinToString(" ").lowercase()
            if (!hay.contains(t)) return false
        }
        val date = (d.taxableDate ?: d.issueDate ?: d.createdAt).take(10)
        parseCzDate(dateFrom)?.let { if (date < it) return false }
        parseCzDate(dateTo)?.let { if (date > it) return false }
        val total = d.total?.toDoubleOrNull()
        parseAmount(amountFrom)?.toDoubleOrNull()?.let { if (total == null || total < it) return false }
        parseAmount(amountTo)?.toDoubleOrNull()?.let { if (total == null || total > it) return false }
        status?.let { s ->
            val ok = when (s) {
                "tocheck" -> d.status == "extracted" || d.status == "error"
                "working" -> d.isWorking
                else -> d.status == s
            }
            if (!ok) return false
        }
        type?.let { if (d.docType != it) return false }
        return true
    }
}

private val STATUS_CHIPS = listOf(null to "Vše", "checked" to "Zkontrolované", "tocheck" to "Ke kontrole", "working" to "Zpracovává se")
private val TYPE_CHIPS = listOf(null to "Všechny typy") + docTypeNames.entries.map { it.key to it.value }

/** Panel vyhledávání, který se rozbalí pod horní lištou po klepnutí na lupu. */
@Composable
fun DocFilterPanel(filter: DocFilter, onChange: (DocFilter) -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Field("VS, číslo dokladu nebo poznámka", filter.text, { onChange(filter.copy(text = it)) })
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Field("Datum od", filter.dateFrom, { onChange(filter.copy(dateFrom = it)) }, Modifier.weight(1f),
                    placeholder = "1. 10. 2026")
                Field("Datum do", filter.dateTo, { onChange(filter.copy(dateTo = it)) }, Modifier.weight(1f),
                    placeholder = "31. 10. 2026")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Field("Částka od", filter.amountFrom, { onChange(filter.copy(amountFrom = it)) }, Modifier.weight(1f),
                    KeyboardType.Decimal)
                Field("Částka do", filter.amountTo, { onChange(filter.copy(amountTo = it)) }, Modifier.weight(1f),
                    KeyboardType.Decimal)
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(STATUS_CHIPS) { (key, name) ->
                    FilterChip(filter.status == key, { onChange(filter.copy(status = key)) }, label = { Text(name) })
                }
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(TYPE_CHIPS) { (key, name) ->
                    FilterChip(filter.type == key, { onChange(filter.copy(type = key)) }, label = { Text(name) })
                }
            }
            if (filter.isActive) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Filtr je zapnutý", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                    TextButton(onClick = { onChange(DocFilter()) }) { Text("Zrušit filtr") }
                }
            }
        }
    }
}
