package cz.multio.doklady.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cz.multio.doklady.Navigator
import cz.multio.doklady.Screen
import cz.multio.doklady.data.Api
import cz.multio.doklady.data.Document
import cz.multio.doklady.data.ReportSupplier
import cz.multio.doklady.platform.openPdf
import kotlinx.coroutines.launch

/** Tlačítko v horní liště: stáhne a otevře/nasdílí PDF seznam (pro účetní, klienta, e-mail…). */
@Composable
private fun PdfAction(nav: Navigator, from: String, to: String, key: String?, fileName: String, onError: (String?) -> Unit) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    IconButton(enabled = !busy, onClick = {
        busy = true
        scope.launch {
            runCatching { openPdf(Api.suppliersPdf(from, to, key), fileName) }.onFailure { onError(nav.errorText(it)) }
            busy = false
        }
    }) { Icon(Icons.Filled.PictureAsPdf, "Seznam do PDF") }
}

// ------------------------------------------------------------------------ všichni dodavatelé

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SuppliersScreen(nav: Navigator, from: String, to: String, label: String) {
    var list by remember { mutableStateOf<List<ReportSupplier>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }

    LaunchedEffect(from, to) {
        try { list = Api.suppliers(from, to) } catch (e: Exception) { error = nav.errorText(e) }
    }

    Scaffold(topBar = {
        TopAppBar(
            title = {
                Column {
                    Text("Dodavatelé")
                    Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            navigationIcon = { IconButton(onClick = { nav.pop() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zpět") } },
            actions = { PdfAction(nav, from, to, null, "doklady_dodavatele_$label.pdf") { error = it } },
        )
    }) { padding ->
        val all = list
        Column(Modifier.padding(padding).fillMaxSize()) {
            ErrorBanner(error)
            if (all == null) {
                if (error == null) Loading()
                return@Column
            }
            if (all.isEmpty()) {
                EmptyState("Žádní dodavatelé", "Za toto období nejsou žádné vytěžené doklady.")
                return@Column
            }
            OutlinedTextField(query, { query = it }, singleLine = true, placeholder = { Text("Hledat dodavatele nebo IČO") },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp))
            val shown = all.filter {
                query.isBlank() || fold(it.name).contains(fold(query)) || (it.ico ?: "").contains(query.trim())
            }
            val max = all.maxOf { it.total.toDoubleOrNull() ?: 0.0 }.takeIf { it > 0 } ?: 1.0
            val sum = shown.sumOf { it.total.toDoubleOrNull() ?: 0.0 }
            Text("${shown.size} dodavatelů · celkem ${money(sum.toString())}",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp))
            LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
                items(shown, key = { it.key.ifEmpty { it.name } }) { s ->
                    SupplierBar(s, max) { nav.push(Screen.SupplierDocuments(s, from, to, label)) }
                }
            }
        }
    }
}

/** Porovnání bez diakritiky a velikosti písmen („cez“ najde „ČEZ“). */
private fun fold(s: String): String {
    val map = mapOf('á' to 'a', 'č' to 'c', 'ď' to 'd', 'é' to 'e', 'ě' to 'e', 'í' to 'i', 'ň' to 'n', 'ó' to 'o',
        'ř' to 'r', 'š' to 's', 'ť' to 't', 'ú' to 'u', 'ů' to 'u', 'ý' to 'y', 'ž' to 'z')
    return s.lowercase().map { map[it] ?: it }.joinToString("")
}

// ------------------------------------------------------------------------ doklady jednoho dodavatele

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupplierDocumentsScreen(nav: Navigator, supplier: ReportSupplier, from: String, to: String, label: String) {
    var docs by remember { mutableStateOf<List<Document>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var searchOpen by remember { mutableStateOf(false) }
    var filter by remember { mutableStateOf(DocFilter()) }

    LaunchedEffect(supplier.key, from, to) {
        try { docs = Api.supplierDocuments(supplier.key, from, to) } catch (e: Exception) { error = nav.errorText(e) }
    }

    Scaffold(topBar = {
        TopAppBar(
            title = {
                Column {
                    Text(supplier.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            navigationIcon = { IconButton(onClick = { nav.pop() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zpět") } },
            actions = {
                // lupa – hledání podle parametrů dokladu; zvýrazněná, když je filtr zapnutý
                IconButton(onClick = { searchOpen = !searchOpen }) {
                    Icon(Icons.Filled.Search, "Hledat",
                        tint = if (filter.isActive) MaterialTheme.colorScheme.primary else LocalContentColor.current)
                }
                PdfAction(nav, from, to, supplier.key, "doklady_${supplier.ico ?: "dodavatel"}_$label.pdf") { error = it }
            },
        )
    }) { padding ->
        val list = docs?.filter { filter.matches(it) }
        LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                ErrorBanner(error)
                if (searchOpen) DocFilterPanel(filter) { filter = it }
                Card(Modifier.fillMaxWidth().padding(16.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        if (filter.isActive && list != null) {
                            // součet jen nalezených dokladů (vytěžené a zkontrolované)
                            val sum = list.filter { it.status == "extracted" || it.status == "checked" }
                                .sumOf { it.total?.toDoubleOrNull() ?: 0.0 }
                            Text(money(sum.toString()), style = MaterialTheme.typography.headlineSmall)
                            Text("Nalezeno ${list.size} z ${docs?.size ?: 0} ${docWord(docs?.size ?: 0)}",
                                color = MaterialTheme.colorScheme.primary)
                        } else {
                            Text(money(supplier.total), style = MaterialTheme.typography.headlineSmall)
                            Text("${supplier.count} ${docWord(supplier.count)}" +
                                (supplier.vat?.let { " · DPH ${money(it)}" } ?: ""),
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        supplier.ico?.let {
                            Spacer(Modifier.height(6.dp))
                            LabelValue("IČO", it)
                        }
                    }
                }
            }
            when {
                list == null && error == null -> item { Box(Modifier.fillMaxWidth().height(200.dp)) { Loading() } }
                list != null && list.isEmpty() -> item {
                    Text(if (filter.isActive) "Žádný doklad neodpovídá filtru." else "Žádné doklady.", modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                list != null -> items(list, key = { it.id }) { d ->
                    ListItem(
                        modifier = Modifier.clickable { nav.push(Screen.DocumentDetail(d.id)) },
                        // nahoře datum, pod ním variabilní symbol (u účtenek bez VS číslo dokladu)
                        headlineContent = { Text(czDate(d.taxableDate ?: d.issueDate ?: d.createdAt)) },
                        supportingContent = {
                            Text(d.variableSymbol?.let { "VS $it" } ?: d.docNumber?.let { "č. dokladu $it" } ?: "—")
                        },
                        trailingContent = {
                            Column(horizontalAlignment = Alignment.End) {
                                Text(money(d.total, d.currency), fontWeight = FontWeight.SemiBold)
                                Spacer(Modifier.height(4.dp))
                                StatusChip(d.status)
                            }
                        },
                    )
                }
            }
        }
    }
}
