package cz.multio.doklady.ui

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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
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
import cz.multio.doklady.data.TaskItem
import cz.multio.doklady.platform.PickedDocument
import cz.multio.doklady.platform.rememberDocumentScanner
import cz.multio.doklady.platform.rememberFilePicker
import kotlinx.coroutines.launch

// ------------------------------------------------------------------------ seznam požadavků

/** Záložka „Úkoly“: požadavky od účetní (výpis z banky, chybějící doklad, podklady k přiznání…). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TasksTab(nav: Navigator, onCount: (Int) -> Unit = {}) {
    var tasks by remember { mutableStateOf<List<TaskItem>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var refreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    suspend fun load() {
        try {
            error = null
            val list = Api.tasks("open")
            tasks = list
            onCount(list.count { it.isOpen })
        } catch (e: Exception) {
            error = nav.errorText(e)
        }
    }
    LaunchedEffect(Unit) { load() }

    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = { scope.launch { refreshing = true; load(); refreshing = false } },
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 40.dp)) {
            item { ErrorBanner(error) { scope.launch { load() } } }
            val list = tasks
            when {
                list == null && error == null -> item { Box(Modifier.fillMaxWidth().height(300.dp)) { Loading() } }
                list != null && list.isEmpty() -> item {
                    Box(Modifier.height(400.dp)) {
                        EmptyState("Nic k doplnění", "Když bude účetní něco potřebovat (výpis, chybějící doklad), objeví se to tady a přijde vám e-mail.")
                    }
                }
                list != null -> items(list, key = { it.id }) { t ->
                    ListItem(
                        modifier = Modifier.clickable { nav.push(Screen.TaskDetail(t.id)) },
                        leadingContent = { Icon(Icons.Filled.TaskAlt, null) },
                        headlineContent = { Text(t.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                        supportingContent = {
                            Text(
                                t.dueDate?.let { "Termín ${czDate(it)}" + if (t.overdue) " · po termínu" else "" } ?: "Bez termínu",
                                color = if (t.overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        trailingContent = { StatusChip(t.state) },
                    )
                }
            }
        }
    }
}

// ------------------------------------------------------------------------ detail požadavku

/**
 * Požadavek od účetní: popis, termín a odpověď s doklady. Doklady se nasbírají (sken / soubor)
 * a odešlou najednou spolu s odpovědí. Bez signálu se odeslat nedá – aplikace to řekne.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskDetailScreen(nav: Navigator, id: String) {
    var task by remember { mutableStateOf<TaskItem?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var info by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var response by remember { mutableStateOf("") }
    val files = remember { mutableStateListOf<PickedDocument>() }
    val scope = rememberCoroutineScope()

    val scanner = rememberDocumentScanner { picked -> picked?.let { files.add(it) } }
    val picker = rememberFilePicker { picked -> picked?.let { files.add(it) } }

    LaunchedEffect(id) {
        try { task = Api.task(id) } catch (e: Exception) { error = nav.errorText(e) }
    }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Požadavek účetní") },
            navigationIcon = { IconButton(onClick = { nav.pop() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zpět") } },
        )
    }) { padding ->
        val t = task
        if (t == null) {
            Box(Modifier.padding(padding)) { if (error != null) ErrorBanner(error) else Loading() }
            return@Scaffold
        }
        Column(
            Modifier.padding(padding).fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(t.title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                StatusChip(t.state)
            }
            t.dueDate?.let {
                Text("Termín ${czDate(it)}" + if (t.overdue) " – po termínu" else "",
                    color = if (t.overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (t.description.isNotBlank()) {
                Card(Modifier.fillMaxWidth()) { Text(t.description, Modifier.padding(16.dp)) }
            }
            t.reviewNote?.let { ErrorBanner("Účetní vrátila k doplnění: $it") }
            ErrorBanner(error)
            info?.let { Text(it, color = MaterialTheme.colorScheme.primary) }

            if (t.documents.isNotEmpty()) {
                Text("Odeslané doklady", style = MaterialTheme.typography.titleSmall)
                t.documents.forEach { d ->
                    ListItem(
                        modifier = Modifier.clickable { nav.push(Screen.DocumentDetail(d.id)) },
                        headlineContent = { Text(d.supplierName ?: d.originalFilename ?: "Doklad", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        supportingContent = { Text(czDate(d.createdAt)) },
                        trailingContent = { StatusChip(d.status) },
                    )
                }
                t.response?.let { LabelValue("Vaše odpověď", it) }
            }

            if (t.isOpen) {
                HorizontalDivider()
                if (t.uploads != "none") {
                    Text(if (t.uploads == "one") "Přiložte jeden doklad" else "Přiložte doklady", style = MaterialTheme.typography.titleSmall)
                    files.forEachIndexed { i, f ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${i + 1}. " + (f.fileName ?: if (f.isScan) "Sken (${f.pages.size} str.)" else "Soubor"), Modifier.weight(1f))
                            IconButton(onClick = { files.removeAt(i) }) { Icon(Icons.Filled.Close, "Odebrat") }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = scanner, modifier = Modifier.weight(1f), enabled = !busy && !(t.uploads == "one" && files.size >= 1)) {
                            Icon(Icons.Filled.DocumentScanner, null); Spacer(Modifier.width(6.dp)); Text("Skenovat")
                        }
                        OutlinedButton(onClick = picker, modifier = Modifier.weight(1f), enabled = !busy && !(t.uploads == "one" && files.size >= 1)) {
                            Icon(Icons.Filled.FolderOpen, null); Spacer(Modifier.width(6.dp)); Text("Soubor")
                        }
                    }
                }
                Field("Odpověď pro účetní" + if (t.uploads == "none") "" else " (nepovinné)", response, { response = it }, singleLine = false)
                val ready = when (t.uploads) {
                    "none" -> response.isNotBlank()
                    "one" -> files.size == 1
                    else -> files.isNotEmpty() || response.isNotBlank()
                }
                Button(
                    enabled = ready && !busy,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    onClick = {
                        busy = true; error = null; info = null
                        scope.launch {
                            try {
                                val parts = files.mapIndexed { i, f ->
                                    val p = payload(f)
                                    Triple(p.original, p.mime, if (files.size > 1) "${i + 1}-${p.name}" else p.name)
                                }
                                task = Api.submitTask(id, response.trim(), parts)
                                files.clear(); response = ""
                                info = "Odesláno účetní. Děkujeme."
                            } catch (e: Exception) {
                                error = nav.errorText(e)
                            } finally { busy = false }
                        }
                    },
                ) {
                    if (busy) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    else { Icon(Icons.AutoMirrored.Filled.Send, null); Spacer(Modifier.width(8.dp)); Text("Odeslat účetní", fontWeight = FontWeight.SemiBold) }
                }
            } else if (t.state == "submitted") {
                Text("Odesláno – účetní požadavek zkontroluje.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
