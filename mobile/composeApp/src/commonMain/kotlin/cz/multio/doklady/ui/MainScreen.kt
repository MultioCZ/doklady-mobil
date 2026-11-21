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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cz.multio.doklady.Navigator
import cz.multio.doklady.Screen
import cz.multio.doklady.data.Api
import cz.multio.doklady.data.ApiException
import cz.multio.doklady.data.Document
import cz.multio.doklady.data.Invoice
import cz.multio.doklady.data.Outbox
import cz.multio.doklady.data.Recurring
import cz.multio.doklady.platform.IncomingShare
import cz.multio.doklady.platform.rememberDocumentScanner
import cz.multio.doklady.platform.rememberFilePicker
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Zpracuje chybu API: při vypršeném přihlášení vrátí na přihlašovací obrazovku. */
fun Navigator.errorText(e: Throwable): String? {
    if (e is ApiException && e.status == 401) {
        Api.logout()
        reset(Screen.Login())
        return null
    }
    return e.message ?: "Neznámá chyba"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(nav: Navigator) {
    var selectedTab by rememberSaveable { mutableStateOf("docs") }
    var companyId by remember { mutableStateOf(Api.companyId) }
    val me = remember { Api.cachedMe() }
    var companyMenu by remember { mutableStateOf(false) }
    var openTasks by remember { mutableIntStateOf(0) }
    val company = me?.companies?.firstOrNull { it.id == companyId }
    // záložky podle edice portálu: faktury jen s modulem fakturace
    val tabs = buildList {
        add("docs" to "Doklady"); add("tasks" to "Úkoly")
        if (company?.has("invoicing") != false) add("invoices" to "Faktury")
        add("reports" to "Přehledy"); add("settings" to "Nastavení")
    }
    val tabKey = if (tabs.any { it.first == selectedTab }) selectedTab else "docs"
    LaunchedEffect(companyId) { runCatching { Api.tasks("open") }.onSuccess { list -> openTasks = list.count { it.isOpen } } }

    val scanner = rememberDocumentScanner { picked -> picked?.let { nav.push(Screen.Upload(it)) } }
    val picker = rememberFilePicker { picked -> picked?.let { nav.push(Screen.Upload(it)) } }
    val shared = IncomingShare.pending
    LaunchedEffect(shared) {
        if (shared != null) {
            IncomingShare.pending = null
            nav.push(Screen.Upload(shared))
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column(Modifier.clickable(enabled = (me?.companies?.size ?: 0) > 1) { companyMenu = true }) {
                        Text(tabs.first { it.first == tabKey }.second)
                        if (company != null) {
                            Text(company.name, style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                                overflow = TextOverflow.Ellipsis)
                        }
                    }
                    DropdownMenu(expanded = companyMenu, onDismissRequest = { companyMenu = false }) {
                        me?.companies?.forEach { c ->
                            DropdownMenuItem(
                                text = { Text(c.name + (c.ico?.let { "  ·  IČO $it" } ?: "")) },
                                leadingIcon = { Icon(Icons.Filled.Business, null) },
                                onClick = { Api.companyId = c.id; companyId = c.id; companyMenu = false },
                            )
                        }
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                tabs.forEach { (key, name) ->
                    NavigationBarItem(tabKey == key, { selectedTab = key }, {
                        val icon = when (key) {
                            "docs" -> Icons.Filled.DocumentScanner
                            "tasks" -> Icons.Filled.TaskAlt
                            "invoices" -> Icons.Filled.ReceiptLong
                            "reports" -> Icons.Filled.BarChart
                            else -> Icons.Filled.Settings
                        }
                        if (key == "tasks" && openTasks > 0) {
                            BadgedBox(badge = { Badge { Text(openTasks.toString()) } }) { Icon(icon, null) }
                        } else {
                            Icon(icon, null)
                        }
                    }, label = { Text(name) })
                }
            }
        },
        floatingActionButton = {
            when (tabKey) {
                "docs" -> Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SmallFloatingActionButton(onClick = picker) { Icon(Icons.Filled.FolderOpen, "Ze souboru") }
                    ExtendedFloatingActionButton(
                        onClick = scanner,
                        icon = { Icon(Icons.Filled.DocumentScanner, null) },
                        text = { Text("Skenovat") },
                    )
                }
                "invoices" -> ExtendedFloatingActionButton(
                    onClick = { nav.push(Screen.NewInvoice()) },
                    icon = { Icon(Icons.Filled.Add, null) },
                    text = { Text("Nová faktura") },
                )
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            // klíč companyId → při přepnutí firmy se seznamy načtou znovu
            when (tabKey) {
                "docs" -> androidx.compose.runtime.key(companyId) { DocumentsTab(nav) }
                "tasks" -> androidx.compose.runtime.key(companyId) { TasksTab(nav) { openTasks = it } }
                "invoices" -> androidx.compose.runtime.key(companyId) { InvoicesTab(nav) }
                "reports" -> androidx.compose.runtime.key(companyId) { ReportsTab(nav) }
                else -> SettingsTab(nav)
            }
        }
    }
}

// ------------------------------------------------------------------------ doklady

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentsTab(nav: Navigator) {
    var docs by remember { mutableStateOf<List<Document>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var refreshing by remember { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf<String?>(null) }
    var searchOpen by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val outbox = Outbox.items

    suspend fun load() {
        try {
            error = null
            if (Outbox.items.isNotEmpty()) Outbox.flush()
            docs = Api.documents(status = filter, query = query)
        } catch (e: Exception) {
            error = nav.errorText(e)
        }
    }

    LaunchedEffect(filter, query) { load() }
    // dokud se něco vytěžuje, obnovuj seznam
    LaunchedEffect(docs) {
        if (docs?.any { it.isWorking } == true) {
            delay(3000)
            load()
        }
    }

    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = { scope.launch { refreshing = true; load(); refreshing = false } },
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 140.dp)) {
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    listOf(null to "Vše", "extracted" to "Ke kontrole", "checked" to "Zkontrolované", "error" to "Chyby")
                        .forEach { (key, name) ->
                            FilterChip(selected = filter == key, onClick = { filter = key }, label = { Text(name) },
                                modifier = Modifier.padding(end = 6.dp))
                        }
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = { searchOpen = !searchOpen }) { Icon(Icons.Filled.Search, "Hledat") }
                }
                if (searchOpen) {
                    OutlinedTextField(query, { query = it }, singleLine = true,
                        placeholder = { Text("Dodavatel, IČO, číslo dokladu") },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp))
                }
                ErrorBanner(error) { scope.launch { load() } }
                MissingBanner(nav)
            }
            if (outbox.isNotEmpty()) {
                item {
                    Card(Modifier.fillMaxWidth().padding(12.dp)) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.CloudUpload, null)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Čeká na odeslání: ${outbox.size}", fontWeight = FontWeight.SemiBold)
                                outbox.last().lastError?.let {
                                    Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                                }
                            }
                            TextButton(onClick = { scope.launch { Outbox.flush(); load() } }) { Text("Odeslat") }
                        }
                    }
                }
            }
            val list = docs
            when {
                list == null && error == null -> item { Box(Modifier.fillMaxWidth().height(300.dp)) { Loading() } }
                list != null && list.isEmpty() -> item {
                    Box(Modifier.height(400.dp)) {
                        EmptyState("Zatím žádné doklady", "Tlačítkem Skenovat vyfoťte účtenku nebo fakturu.")
                    }
                }
                list != null -> items(list, key = { it.id }) { d -> DocumentRow(d) { nav.push(Screen.DocumentDetail(d.id)) } }
            }
        }
    }
}

@Composable
private fun DocumentRow(d: Document, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = {
            Text(d.supplierName ?: if (d.isWorking) "Zpracovává se…" else (d.originalFilename ?: "Doklad #${d.id}"),
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = {
            Text(listOfNotNull(
                if (d.stage != "final") (d.stageLabel ?: stageNames[d.stage]) else d.docType?.let { docTypeNames[it] },
                if (d.direction == "sale") "prodej" else null,
                d.docNumber,
                czDate(d.issueDate ?: d.createdAt),
            ).joinToString("  ·  "), maxLines = 1, overflow = TextOverflow.Ellipsis)
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

// ------------------------------------------------------------------------ faktury

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvoicesTab(nav: Navigator) {
    var invoices by remember { mutableStateOf<List<Invoice>?>(null) }
    var recurring by remember { mutableStateOf<List<Recurring>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var refreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    suspend fun load() {
        try {
            error = null
            invoices = Api.invoices()
            recurring = Api.recurring()
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
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 100.dp)) {
            item { ErrorBanner(error) { scope.launch { load() } } }
            if (recurring.isNotEmpty()) {
                item {
                    Text("Opakované faktury", style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(start = 16.dp, top = 12.dp))
                }
                items(recurring, key = { "r${it.id}" }) { r ->
                    ListItem(
                        leadingContent = { Icon(Icons.Filled.Repeat, null) },
                        headlineContent = { Text(r.name) },
                        supportingContent = {
                            Text("${r.customer.name} · každé ${if (r.intervalMonths == 1) "měsíc" else "${r.intervalMonths} měs."}" +
                                " · další ${czDate(r.nextDate)}" + if (!r.active) " · pozastaveno" else "")
                        },
                    )
                }
                item {
                    Text("Vystavené faktury", style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(start = 16.dp, top = 12.dp))
                }
            }
            val list = invoices
            when {
                list == null && error == null -> item { Box(Modifier.fillMaxWidth().height(300.dp)) { Loading() } }
                list != null && list.isEmpty() -> item {
                    Box(Modifier.height(400.dp)) { EmptyState("Žádné faktury", "Vystavte první fakturu tlačítkem Nová faktura.") }
                }
                list != null -> items(list, key = { it.id }) { inv ->
                    ListItem(
                        modifier = Modifier.clickable { nav.push(Screen.InvoiceDetail(inv.id)) },
                        headlineContent = { Text(inv.customer.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        supportingContent = { Text("${inv.number} · splatnost ${czDate(inv.dueDate)}") },
                        trailingContent = {
                            Column(horizontalAlignment = Alignment.End) {
                                Text(money(inv.total, inv.currency), fontWeight = FontWeight.SemiBold)
                                Spacer(Modifier.height(4.dp))
                                StatusChip(inv.status)
                            }
                        },
                    )
                }
            }
        }
    }
}

// ------------------------------------------------------------------------ nastavení

@Composable
fun SettingsTab(nav: Navigator) {
    val me = remember { Api.cachedMe() }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        LabelValue("Server", Api.baseUrl)
        LabelValue("Uživatel", me?.user?.let { "${it.name ?: ""} ${it.email}".trim() })
        LabelValue("Role", if (me?.user?.isAdmin == true) "Účetní (všechny firmy)" else "Klient")
        LabelValue("Neodeslané doklady", Outbox.items.size.toString())
        Spacer(Modifier.height(16.dp))
        val company = me?.companies?.firstOrNull { it.id == Api.companyId }
        if (company?.has("trips") == true) {
            OutlinedButton(onClick = { nav.push(Screen.Trips) }, modifier = Modifier.fillMaxWidth()) { Text("Kniha jízd") }
        }
        OutlinedButton(onClick = { nav.push(Screen.TaxList) }, modifier = Modifier.fillMaxWidth()) { Text("Daň z příjmů – přiznání") }
        val scope = rememberCoroutineScope()
        OutlinedButton(onClick = { scope.launch { Api.logoutEverywhere() }; nav.reset(Screen.Login()) }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.AutoMirrored.Filled.Logout, null)
            Spacer(Modifier.width(8.dp))
            Text("Odhlásit")
        }
        Text("Verze 1.4.0 (portál Multio 1.12)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
