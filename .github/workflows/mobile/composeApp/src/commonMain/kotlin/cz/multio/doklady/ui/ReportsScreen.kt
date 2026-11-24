package cz.multio.doklady.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cz.multio.doklady.Navigator
import cz.multio.doklady.Screen
import cz.multio.doklady.platform.openPdf
import cz.multio.doklady.data.ReportSupplier
import cz.multio.doklady.data.Api
import cz.multio.doklady.data.ReportSummary
import cz.multio.doklady.data.VatRow
import kotlinx.coroutines.launch
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus

private val MONTHS = listOf("Leden", "Únor", "Březen", "Duben", "Květen", "Červen", "Červenec", "Srpen",
    "Září", "Říjen", "Listopad", "Prosinec")

/** Období přehledu: 0 = měsíc, 1 = čtvrtletí, 2 = rok; [start] = první den období. */
private fun periodStart(kind: Int, d: LocalDate): LocalDate = when (kind) {
    0 -> LocalDate(d.year, d.monthNumber, 1)
    1 -> LocalDate(d.year, (d.monthNumber - 1) / 3 * 3 + 1, 1)
    else -> LocalDate(d.year, 1, 1)
}

private fun periodLength(kind: Int) = when (kind) { 0 -> DatePeriod(months = 1); 1 -> DatePeriod(months = 3); else -> DatePeriod(years = 1) }

fun periodLabel(kind: Int, start: LocalDate) = when (kind) {
    0 -> "${MONTHS[start.monthNumber - 1]} ${start.year}"
    1 -> "${(start.monthNumber - 1) / 3 + 1}. čtvrtletí ${start.year}"
    else -> "Rok ${start.year}"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsTab(nav: Navigator) {
    var kind by rememberSaveable { mutableStateOf(0) }
    var startIso by rememberSaveable { mutableStateOf(periodStart(0, today()).toString()) }
    val start = periodStart(kind, LocalDate.parse(startIso))
    val end = start.plus(periodLength(kind)).minus(DatePeriod(days = 1))
    var report by remember { mutableStateOf<ReportSummary?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var refreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    suspend fun load() {
        try {
            error = null
            report = Api.reportSummary(start.toString(), end.toString())
        } catch (e: Exception) {
            error = nav.errorText(e)
        }
    }
    LaunchedEffect(kind, startIso) { report = null; load() }

    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = { scope.launch { refreshing = true; load(); refreshing = false } },
        modifier = Modifier.fillMaxSize(),
    ) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            // --- výběr období
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                listOf("Měsíc", "Čtvrtletí", "Rok").forEachIndexed { i, name ->
                    SegmentedButton(kind == i, {
                        kind = i
                        startIso = periodStart(i, LocalDate.parse(startIso)).toString()
                    }, SegmentedButtonDefaults.itemShape(i, 3)) { Text(name) }
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { startIso = start.minus(periodLength(kind)).toString() }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Předchozí")
                }
                Text(periodLabel(kind, start), style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                IconButton(onClick = { startIso = start.plus(periodLength(kind)).toString() }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Další")
                }
            }
            ErrorBanner(error) { scope.launch { load() } }

            val r = report
            if (r == null) {
                if (error == null) Box(Modifier.fillMaxWidth().height(300.dp)) { Loading() }
                return@Column
            }

            // --- doklady
            Section("Doklady") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Stat("Počet", r.documents.count.toString(), Modifier.weight(1f))
                    Stat("Náklady celkem", money(r.documents.total), Modifier.weight(1.4f))
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Stat("Zkontrolováno", (r.documents.byStatus["checked"] ?: 0).toString(), Modifier.weight(1f))
                    Stat("Čeká na kontrolu", r.documents.toCheck.toString(), Modifier.weight(1f),
                        warn = r.documents.toCheck > 0)
                    Stat("Zpracovává se", ((r.documents.byStatus["processing"] ?: 0) +
                        (r.documents.byStatus["uploaded"] ?: 0)).toString(), Modifier.weight(1f))
                }
                if (r.invoices.count > 0) {
                    Spacer(Modifier.height(8.dp))
                    LabelValue("Vydané faktury", "${r.invoices.count} × celkem ${money(r.invoices.total)}")
                }
            }

            // --- DPH
            Section("DPH za období") {
                VatTable("Přijaté doklady (vstup)", r.vat.received)
                Spacer(Modifier.height(10.dp))
                VatTable("Vydané faktury (výstup)", r.vat.issued)
                HorizontalDivider(Modifier.padding(vertical = 10.dp))
                LabelValue("DPH na vstupu", money(r.vat.inputTotal))
                LabelValue("DPH na výstupu", money(r.vat.outputTotal))
                val bal = r.vat.balance.toDoubleOrNull() ?: 0.0
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Text(if (bal >= 0) "Odhad k odvodu" else "Odhad nadměrného odpočtu",
                        fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.42f))
                    Text(money(kotlin.math.abs(bal).toString()), fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.58f))
                }
                Text(r.note ?: "Orientační přehled – nenahrazuje přiznání k DPH.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            // --- dodavatelé
            Section("Náklady podle dodavatelů") {
                if (r.suppliers.isEmpty()) {
                    Text("Za toto období zatím žádné vytěžené doklady.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                val label = periodLabel(kind, start)
                val max = r.suppliers.maxOfOrNull { it.total.toDoubleOrNull() ?: 0.0 }?.takeIf { it > 0 } ?: 1.0
                r.suppliers.forEach { s ->
                    SupplierBar(s, max) { nav.push(Screen.SupplierDocuments(s, start.toString(), end.toString(), label)) }
                }
                if (r.suppliers.isNotEmpty()) {
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { nav.push(Screen.Suppliers(start.toString(), end.toString(), label)) },
                            modifier = Modifier.weight(1f),
                        ) { Text("Všichni dodavatelé") }
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    runCatching { openPdf(Api.suppliersPdf(start.toString(), end.toString()), "doklady_dodavatele_$label.pdf") }
                                        .onFailure { error = nav.errorText(it) }
                                }
                            },
                            modifier = Modifier.weight(1f),
                        ) { Icon(Icons.Filled.PictureAsPdf, null); Spacer(Modifier.width(6.dp)); Text("Seznam PDF") }
                    }
                }
            }
        }
    }
}

/** Řádek dodavatele s pruhem podle částky – po klepnutí otevře jeho doklady. */
@Composable
fun SupplierBar(s: ReportSupplier, max: Double, onClick: () -> Unit) {
    val value = s.total.toDoubleOrNull() ?: 0.0
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(s.name, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text(money(s.total), fontWeight = FontWeight.SemiBold)
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f).height(8.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp))) {
                Box(Modifier.fillMaxWidth((value / max).toFloat().coerceIn(0.02f, 1f)).height(8.dp)
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp)))
            }
            Text("  ${s.count} ${docWord(s.count)}", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

fun docWord(n: Int) = if (n == 1) "doklad" else if (n in 2..4) "doklady" else "dokladů"

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier = Modifier, warn: Boolean = false) {
    Column(modifier.background(MaterialTheme.colorScheme.surface, RoundedCornerShape(10.dp)).padding(10.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
            color = if (warn) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface, maxLines = 1)
    }
}

@Composable
private fun VatTable(title: String, rows: List<VatRow>) {
    Text(title, style = MaterialTheme.typography.labelLarge)
    if (rows.isEmpty()) {
        Text("—", color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
        Text("Sazba", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(0.5f))
        Text("Základ", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
        Text("DPH", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
    }
    rows.forEach {
        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
            Text("${it.rate} %", Modifier.weight(0.5f))
            Text(money(it.base), Modifier.weight(1f))
            Text(money(it.vat), Modifier.weight(1f))
        }
    }
}
