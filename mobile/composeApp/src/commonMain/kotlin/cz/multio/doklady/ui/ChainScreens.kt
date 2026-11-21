package cz.multio.doklady.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cz.multio.doklady.Navigator
import cz.multio.doklady.Screen
import cz.multio.doklady.data.Api
import cz.multio.doklady.data.ChainInfo
import cz.multio.doklady.data.MissingItem
import cz.multio.doklady.platform.rememberDocumentScanner
import cz.multio.doklady.platform.rememberFilePicker
import kotlinx.coroutines.launch

private val Green = Color(0xFF21735A)
private val Red = Color(0xFFB42318)

/** Karta nad seznamem dokladů: „Chybí 3 doklady k fakturám“. Nic nechybí → nic se neukáže. */
@Composable
fun MissingBanner(nav: Navigator) {
    var count by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) { runCatching { Api.missing() }.onSuccess { count = it.size } }
    if (count == 0) return
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp).clickable { nav.push(Screen.Missing) },
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF6F5)),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.ReportProblem, null, tint = Red)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("K fakturám chybí ${count} ${if (count == 1) "doklad" else if (count in 2..4) "doklady" else "dokladů"}",
                    fontWeight = FontWeight.SemiBold, color = Red)
                Text("Dodací listy, objednávky nebo zálohy – klepněte a vyfoťte je.", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

/** Seznam dokladů, které chybějí v cestách faktur; u každého jde rovnou vyfotit chybějící doklad. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MissingScreen(nav: Navigator) {
    var rows by remember { mutableStateOf<List<MissingItem>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var refreshing by remember { mutableStateOf(false) }
    var target by remember { mutableStateOf<MissingItem?>(null) }
    val scope = rememberCoroutineScope()

    fun presetFor(m: MissingItem) = UploadPreset(
        stage = m.stage, direction = m.direction,
        note = listOfNotNull(m.label + (m.number?.let { " č. $it" } ?: ""), m.partner).joinToString(" – "),
    )
    val scanner = rememberDocumentScanner { picked -> val m = target; if (picked != null && m != null) nav.push(Screen.Upload(picked, presetFor(m))) }
    val picker = rememberFilePicker { picked -> val m = target; if (picked != null && m != null) nav.push(Screen.Upload(picked, presetFor(m))) }

    suspend fun load() {
        try { error = null; rows = Api.missing() } catch (e: Exception) { error = nav.errorText(e) }
    }
    LaunchedEffect(Unit) { load() }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Chybějící doklady") },
            navigationIcon = { IconButton(onClick = { nav.pop() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zpět") } },
        )
    }) { padding ->
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = { scope.launch { refreshing = true; load(); refreshing = false } },
            modifier = Modifier.padding(padding).fillMaxSize(),
        ) {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item { ErrorBanner(error) { scope.launch { load() } } }
                val list = rows
                when {
                    list == null && error == null -> item { Box(Modifier.fillMaxWidth().height(300.dp)) { Loading() } }
                    list != null && list.isEmpty() -> item { Box(Modifier.height(400.dp)) { EmptyState("Nic nechybí", "Ke všem fakturám máte doklady.") } }
                    list != null -> items(list, key = { "${it.chainId}-${it.key}" }) { m ->
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(m.label + (m.number?.let { " č. $it" } ?: ""), fontWeight = FontWeight.SemiBold, color = Red)
                                m.partner?.let { Text(it) }
                                Text(m.reason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                m.requested?.let { Text("Účetní žádá od ${czDate(it)}", style = MaterialTheme.typography.bodySmall) }
                                if (m.key != "final:wait") {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedButton(onClick = { target = m; scanner() }, modifier = Modifier.weight(1f)) {
                                            Icon(Icons.Filled.DocumentScanner, null); Spacer(Modifier.width(6.dp)); Text("Vyfotit")
                                        }
                                        OutlinedButton(onClick = { target = m; picker() }, modifier = Modifier.weight(1f)) {
                                            Icon(Icons.Filled.FolderOpen, null); Spacer(Modifier.width(6.dp)); Text("Soubor")
                                        }
                                    }
                                }
                                m.documentId?.let { docId ->
                                    TextButton(onClick = { nav.push(Screen.Chain(docId)) }) { Text("Zobrazit cestu faktury") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Cesta faktury pod sebou: Objednávka → Zálohová faktura → Daňový doklad k záloze → Dodací list → Faktura. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChainScreen(nav: Navigator, documentId: String) {
    var chain by remember { mutableStateOf<ChainInfo?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(documentId) {
        try { chain = Api.documentChain(documentId) } catch (e: Exception) { error = nav.errorText(e) }
    }
    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Cesta faktury") },
            navigationIcon = { IconButton(onClick = { nav.pop() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zpět") } },
        )
    }) { padding ->
        val c = chain
        if (c == null) {
            Box(Modifier.padding(padding)) { if (error != null) ErrorBanner(error) else Loading() }
            return@Scaffold
        }
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
            c.partner?.let { Text(it, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 12.dp)) }
            c.steps.forEachIndexed { index, step ->
                val has = step.documents.isNotEmpty()
                val lacks = step.missing.isNotEmpty()
                Row {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(32.dp)) {
                        Box(
                            Modifier.size(26.dp).clip(CircleShape)
                                .background(if (has) Green else if (lacks) Red else MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center,
                        ) { Text(if (has) "✓" else if (lacks) "!" else "·", color = if (has || lacks) Color.White else MaterialTheme.colorScheme.onSurfaceVariant) }
                        if (index < c.steps.lastIndex) {
                            Box(Modifier.width(3.dp).height(if (has || lacks) 92.dp else 36.dp).background(if (has) Green else MaterialTheme.colorScheme.surfaceVariant))
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f).padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(step.label, fontWeight = FontWeight.SemiBold,
                            color = if (has || lacks) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                        step.documents.forEach { d ->
                            Column(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                                    .border(if (d.id == documentId) 2.dp else 1.dp, if (d.id == documentId) Green else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                                    .clickable { nav.push(Screen.DocumentDetail(d.id)) }.padding(10.dp),
                            ) {
                                Text(d.docNumber ?: "bez čísla", fontWeight = FontWeight.SemiBold, color = Green)
                                Text(listOfNotNull(d.issueDate?.let { czDate(it) }, d.total?.let { money(it, d.currency) }, d.status.ifBlank { null }).joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        step.missing.forEach { m ->
                            Column(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Color(0xFFFFF6F5))
                                    .border(1.dp, Red, RoundedCornerShape(8.dp)).padding(10.dp),
                            ) {
                                Text("Chybí" + (m.number?.let { " č. $it" } ?: ""), fontWeight = FontWeight.SemiBold, color = Red)
                                Text(m.reason, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        if (!has && !lacks) Text("nebyl", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (c.missing.isNotEmpty()) {
                OutlinedButton(onClick = { nav.push(Screen.Missing) }, modifier = Modifier.fillMaxWidth()) { Text("Vyfotit chybějící doklad") }
            }
            if (c.problems.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text("Účetní kontroluje", style = MaterialTheme.typography.titleSmall)
                c.problems.forEach { p -> Text("⚠ ${p.item}: ${p.text}", color = Red, style = MaterialTheme.typography.bodySmall) }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
