package cz.multio.doklady.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cz.multio.doklady.Navigator
import cz.multio.doklady.Screen
import cz.multio.doklady.data.*
import cz.multio.doklady.platform.KeyValueStore
import kotlinx.coroutines.launch

/** 2.2: režim „Kancelář“ – účetní a administrátor. Přepínač se pamatuje v telefonu. */
object OfficeMode {
    var on by mutableStateOf(KeyValueStore.get("office_mode") != "0")
        private set

    fun set(value: Boolean) {
        on = value
        KeyValueStore.put("office_mode", if (value) "1" else "0")
    }
}

/** Pruh nahoře v účtu klienta: je jasné, že účetní prohlíží klienta. */
@Composable
fun ViewingAsAccountantBar(company: Company?) {
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.inverseSurface).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Prohlížíte jako účetní: ${company?.name ?: ""}", color = MaterialTheme.colorScheme.inverseOnSurface,
            style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        TextButton(onClick = { OfficeMode.set(true) }) { Text("Zpět do kanceláře", color = MaterialTheme.colorScheme.inversePrimary, fontWeight = FontWeight.Bold) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OfficeShell(nav: Navigator, me: LoginResponse?, onOpenClient: (Int) -> Unit) {
    var tab by remember { mutableStateOf("today") }
    var waiting by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { BrandLogo(Modifier.height(34.dp)) },
                actions = {
                    Surface(color = MaterialTheme.colorScheme.inverseSurface, shape = RoundedCornerShape(7.dp)) {
                        Text("KANCELÁŘ", color = MaterialTheme.colorScheme.inverseOnSurface, style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                },
            )
        },
        bottomBar = {
            NavigationBar {
                @Composable
                fun item(key: String, label: String, icon: ImageVector, count: Int = 0) {
                    NavigationBarItem(selected = tab == key, onClick = { tab = key }, label = { Text(label) },
                        icon = { if (count > 0) BadgedBox(badge = { Badge { Text(count.toString()) } }) { Icon(icon, null) } else Icon(icon, null) })
                }
                item("today", "Dnes", Icons.Outlined.Home)
                item("clients", "Klienti", Icons.Outlined.People)
                item("messages", "Zprávy", Icons.Outlined.ChatBubbleOutline, waiting)
                item("season", "Sezóna", Icons.Outlined.Description)
                item("more", "Více", Icons.Outlined.Menu)
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            when (tab) {
                "today" -> OfficeTodayTab(nav, onTab = { tab = it }, onWaiting = { waiting = it })
                "clients" -> OfficeClientsTab(me?.companies ?: emptyList(), onOpenClient)
                "messages" -> OfficeThreadsTab(nav) { waiting = it }
                "season" -> OfficeSeasonTab(nav)
                else -> OfficeMoreTab(nav, admin = me?.user?.role == "admin") {
                    scope.launch { Api.logoutEverywhere() }
                    nav.reset(Screen.Welcome)
                }
            }
        }
    }
}

@Composable
private fun OfficeRow(title: String, sub: String, icon: ImageVector, count: Int = 0, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            if (sub.isNotBlank()) Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        if (count > 0) Badge { Text(count.toString()) }
    }
}

@Composable
fun OfficeTodayTab(nav: Navigator, onTab: (String) -> Unit, onWaiting: (Int) -> Unit) {
    var data by remember { mutableStateOf<OfficeToday?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        runCatching { Api.officeToday() }.onSuccess { data = it; onWaiting(it.threads.count) }.onFailure { error = nav.errorText(it) }
    }
    val d = data
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 8.dp)) {
        Text("Dnes k vyřízení", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        ErrorBanner(error)
        if (d == null) { if (error == null) Loading(); return@Column }
        Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            if (d.admin) OfficeRow("Nové žádosti klientů", d.applications.items.firstOrNull()?.let { "${it.name} · ${it.serviceLabel}" } ?: "žádné",
                Icons.Outlined.PersonAdd, d.applications.count) { nav.push(Screen.OfficeApplications) }
            OfficeRow("Zprávy čekají na odpověď", d.threads.items.firstOrNull()?.let { "${it.client}: ${it.last}" } ?: "žádné",
                Icons.Outlined.ChatBubbleOutline, d.threads.count) { onTab("messages") }
            OfficeRow("Klienti doplnili podklady", d.taxUpdates.items.firstOrNull()?.let { "${it.client} · ${it.year}" } ?: "nic nového",
                Icons.Outlined.Description, d.taxUpdates.count) { onTab("season") }
            if (d.admin) OfficeRow("Datová schránka – k zařazení", d.dsInbox.items.firstOrNull()?.let { "${it.sender}: ${it.annotation}" } ?: "nic",
                Icons.Outlined.Mail, d.dsInbox.count) { nav.push(Screen.OfficeDsInbox) }
        }
        Text("Kontrola dokladů, výsledky přiznání a mzdy jsou na webu.", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp))
    }
}

@Composable
fun OfficeClientsTab(companies: List<Company>, onOpen: (Int) -> Unit) {
    var q by remember { mutableStateOf("") }
    val shown = companies.filter { q.isBlank() || it.name.contains(q, ignoreCase = true) || (it.ico ?: "").contains(q) }
    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(q, { q = it }, label = { Text("Hledat klienta, IČO…") }, singleLine = true,
            leadingIcon = { Icon(Icons.Outlined.Search, null) }, modifier = Modifier.fillMaxWidth().padding(16.dp))
        LazyColumn {
            items(shown, key = { it.id }) { c ->
                OfficeRow(c.name, listOfNotNull(c.ico?.let { "IČO $it" }, c.edition).joinToString(" · "), Icons.Outlined.Business) { onOpen(c.id) }
            }
        }
        if (shown.isEmpty()) Text("Žádný klient neodpovídá.", modifier = Modifier.padding(16.dp))
    }
}

@Composable
fun OfficeThreadsTab(nav: Navigator, onCount: (Int) -> Unit) {
    var rows by remember { mutableStateOf<List<OfficeThread>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { runCatching { Api.officeThreads() }.onSuccess { rows = it; onCount(it.size) }.onFailure { error = nav.errorText(it) } }
    Column(Modifier.fillMaxSize()) {
        Text("Čeká na odpověď", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(16.dp))
        ErrorBanner(error)
        val r = rows ?: run { if (error == null) Loading(); return@Column }
        if (r.isEmpty()) Text("Všechno zodpovězeno. 👍", modifier = Modifier.padding(16.dp))
        LazyColumn { items(r, key = { it.id }) { t -> OfficeRow(t.client, "${t.subject}: ${t.last}", Icons.Outlined.ChatBubbleOutline) { nav.push(Screen.OfficeThread(t.id)) } } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OfficeThreadScreen(nav: Navigator, id: String) {
    var t by remember { mutableStateOf<OfficeThread?>(null) }
    var text by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    suspend fun load() { runCatching { Api.officeThread(id) }.onSuccess { t = it }.onFailure { error = nav.errorText(it) } }
    LaunchedEffect(id) { load() }
    Scaffold(topBar = {
        TopAppBar(title = { Text(t?.client ?: "Zprávy") }, navigationIcon = { IconButton(onClick = { nav.pop() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zpět") } })
    }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().imePadding()) {
            ErrorBanner(error)
            LazyColumn(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                items(t?.messages ?: emptyList(), key = { it.id }) { m ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = if (m.fromAccountant) Arrangement.End else Arrangement.Start) {
                        Surface(color = if (m.fromAccountant) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(14.dp), modifier = Modifier.widthIn(max = 300.dp)) {
                            Column(Modifier.padding(10.dp)) {
                                Text(m.body)
                                Text("${m.author} · ${czDate(m.createdAt)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
            Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("Děkuji, zařídím.", "Pošlete prosím doklad.", "Zavolám vám.").forEach { q ->
                    AssistChip(onClick = { text = q }, label = { Text(q) })
                }
            }
            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(text, { text = it }, placeholder = { Text("Odpověď…") }, modifier = Modifier.weight(1f))
                IconButton(enabled = text.isNotBlank() && !busy, onClick = {
                    scope.launch {
                        busy = true
                        runCatching { Api.officeReply(id, text.trim()) }.onSuccess { text = ""; load() }.onFailure { error = nav.errorText(it) }
                        busy = false
                    }
                }) { Icon(Icons.AutoMirrored.Outlined.Send, "Odeslat") }
            }
        }
    }
}

@Composable
fun OfficeSeasonTab(nav: Navigator) {
    var data by remember { mutableStateOf<OfficeSeason?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var info by remember { mutableStateOf<String?>(null) }
    var filter by remember { mutableStateOf("open") }
    val chosen = remember { mutableStateListOf<String>() }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { runCatching { Api.officeSeason() }.onSuccess { data = it }.onFailure { error = nav.errorText(it) } }
    Column(Modifier.fillMaxSize()) {
        Text("Sezóna přiznání ${data?.year ?: ""}", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(16.dp))
        ErrorBanner(error)
        info?.let { Text(it, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 16.dp)) }
        Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("open" to "Čeká na podklady", "work" to "Ke zpracování", "done" to "Podáno").forEach { (k, l) ->
                FilterChip(selected = filter == k, onClick = { filter = k }, label = { Text(l) })
            }
        }
        val d = data ?: run { if (error == null) Loading(); return@Column }
        val rows = d.items.filter {
            when (filter) {
                "open" -> it.state == "open"
                "work" -> it.state in listOf("submitted", "processing", "ready", "approved")
                else -> it.state in listOf("filed", "done")
            }
        }
        LazyColumn(Modifier.weight(1f)) {
            items(rows, key = { it.id }) { p ->
                Row(Modifier.fillMaxWidth().clickable { if (p.id in chosen) chosen.remove(p.id) else chosen.add(p.id) }.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    if (filter == "open") Checkbox(checked = p.id in chosen, onCheckedChange = { if (it) chosen.add(p.id) else chosen.remove(p.id) })
                    Column(Modifier.weight(1f)) {
                        Text(p.client, style = MaterialTheme.typography.titleSmall)
                        Text("${p.stateLabel} · chybí ${p.progress.missingRequired} z ${p.progress.total}" + (p.deadline?.let { " · do ${czDate(it)}" } ?: ""),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (p.needsReview) Badge { Text("nové") }
                }
            }
        }
        if (filter == "open") {
            Button(enabled = chosen.isNotEmpty(), onClick = {
                scope.launch {
                    runCatching { Api.officeRemind(chosen.toList()) }.onSuccess { info = "Připomenuto: ${it.reminded}"; chosen.clear() }.onFailure { error = nav.errorText(it) }
                }
            }, modifier = Modifier.fillMaxWidth().padding(16.dp)) { Text("Připomenout vybraným (${chosen.size})") }
        }
    }
}

@Composable
fun OfficeMoreTab(nav: Navigator, admin: Boolean, onLogout: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 8.dp)) {
        if (admin) {
            OfficeRow("Žádosti klientů", "", Icons.Outlined.PersonAdd) { nav.push(Screen.OfficeApplications) }
            OfficeRow("Datová schránka", "", Icons.Outlined.Mail) { nav.push(Screen.OfficeDsInbox) }
            HorizontalDivider()
        }
        OfficeRow("Zabezpečení", "", Icons.Outlined.Lock) { nav.push(Screen.Security) }
        OfficeRow("Upozornění", "", Icons.Outlined.Notifications) { nav.push(Screen.Notifications) }
        OfficeRow("Nastavení", "", Icons.Outlined.Settings) { nav.push(Screen.Settings) }
        HorizontalDivider()
        OfficeRow("Odhlásit", "", Icons.AutoMirrored.Outlined.Logout, onClick = onLogout)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OfficeApplicationsScreen(nav: Navigator) {
    var rows by remember { mutableStateOf<List<OfficeApp>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { runCatching { Api.officeApplications() }.onSuccess { rows = it }.onFailure { error = nav.errorText(it) } }
    Scaffold(topBar = {
        TopAppBar(title = { Text("Žádosti klientů") }, navigationIcon = { IconButton(onClick = { nav.pop() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zpět") } })
    }) { padding ->
        Column(Modifier.padding(padding)) {
            ErrorBanner(error)
            val r = rows ?: run { if (error == null) Loading(); return@Column }
            if (r.isEmpty()) Text("Žádné nové žádosti.", modifier = Modifier.padding(16.dp))
            LazyColumn { items(r, key = { it.id }) { a -> OfficeRow(a.name, "${a.serviceLabel} · ${a.stateLabel}", Icons.Outlined.PersonAdd) { nav.push(Screen.OfficeApplication(a.id)) } } }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OfficeApplicationScreen(nav: Navigator, id: String) {
    var app by remember { mutableStateOf<OfficeApp?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var info by remember { mutableStateOf<String?>(null) }
    var dialog by remember { mutableStateOf<String?>(null) }
    var input by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    LaunchedEffect(id) { runCatching { Api.officeApplication(id) }.onSuccess { app = it }.onFailure { error = nav.errorText(it) } }
    fun run(action: String, data: Map<String, String>) {
        scope.launch {
            runCatching { Api.officeApplicationAction(id, action, data) }
                .onSuccess {
                    info = when (action) { "accept" -> "Klient založen" + (if (it.inviteSent) ", pozvánka odeslána." else "."); "ask" -> "Dotaz odeslán."; else -> "Žádost odmítnuta." }
                    dialog = null
                }
                .onFailure { error = nav.errorText(it) }
        }
    }
    Scaffold(topBar = {
        TopAppBar(title = { Text("Žádost") }, navigationIcon = { IconButton(onClick = { nav.pop() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zpět") } })
    }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
            ErrorBanner(error)
            info?.let { Text(it, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold); Spacer(Modifier.height(8.dp)) }
            val a = app ?: run { if (error == null) Loading(); return@Column }
            Text(a.serviceLabel.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            Text(a.name, style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("Kontakt: ${a.contactName}, ${a.phone}")
                    Text("E-mail: ${a.email}" + if (a.emailVerified) " ✓ ověřený" else "")
                    a.checklist.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                    a.description?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    a.note?.takeIf { it.isNotBlank() }?.let { Text("Poznámka: $it", style = MaterialTheme.typography.bodySmall) }
                }
            }
            if (a.state in listOf("new", "question") && info == null) {
                Spacer(Modifier.height(16.dp))
                Button(onClick = { input = a.suggestedUsername ?: ""; dialog = "accept" }, modifier = Modifier.fillMaxWidth()) { Text("Přijmout…") }
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { input = ""; dialog = "ask" }, modifier = Modifier.weight(1f)) { Text("Doplnit dotazem") }
                    OutlinedButton(onClick = { input = ""; dialog = "reject" }, modifier = Modifier.weight(1f)) { Text("Odmítnout", color = MaterialTheme.colorScheme.error) }
                }
            }
        }
    }
    dialog?.let { d ->
        AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text(when (d) { "accept" -> "Přijmout klienta"; "ask" -> "Dotaz klientovi"; else -> "Odmítnout žádost" }) },
            text = {
                OutlinedTextField(input, { input = it }, label = { Text(when (d) { "accept" -> "Uživatelské jméno"; "ask" -> "Co potřebujete doplnit"; else -> "Důvod (nepovinné)" }) },
                    singleLine = d == "accept")
            },
            confirmButton = {
                TextButton(enabled = d == "reject" || input.isNotBlank(), onClick = {
                    run(d, if (d == "accept") mapOf("username" to input.trim()) else mapOf("text" to input.trim()))
                }) { Text(if (d == "accept") "Přijmout a poslat pozvánku" else "Odeslat") }
            },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text("Zrušit") } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OfficeDsInboxScreen(nav: Navigator) {
    var rows by remember { mutableStateOf<List<OfficeDs>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var pick by remember { mutableStateOf<OfficeDs?>(null) }
    var deadline by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val companies = remember { Api.cachedMe()?.companies ?: emptyList() }
    suspend fun load() { runCatching { Api.officeDsInbox() }.onSuccess { rows = it }.onFailure { error = nav.errorText(it) } }
    LaunchedEffect(Unit) { load() }
    Scaffold(topBar = {
        TopAppBar(title = { Text("Datová schránka") }, navigationIcon = { IconButton(onClick = { nav.pop() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zpět") } })
    }) { padding ->
        Column(Modifier.padding(padding)) {
            ErrorBanner(error)
            val r = rows ?: run { if (error == null) Loading(); return@Column }
            if (r.isEmpty()) Text("Nic k zařazení.", modifier = Modifier.padding(16.dp))
            LazyColumn {
                items(r, key = { it.id }) { m ->
                    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
                        Column(Modifier.padding(12.dp)) {
                            Text(m.annotation.ifBlank { "Zpráva" }, style = MaterialTheme.typography.titleSmall)
                            Text("${m.sender} · ${czDate(m.receivedAt)}", style = MaterialTheme.typography.bodySmall)
                            m.suggestedClient?.let { Text("Návrh: $it (${m.reason})", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall) }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                                Button(onClick = { pick = m; deadline = "" }) { Text("Zařadit…") }
                                OutlinedButton(onClick = {
                                    scope.launch { runCatching { Api.officeDsAction(m.id, "ignore") }.onSuccess { load() }.onFailure { error = nav.errorText(it) } }
                                }) { Text("Nepatří klientovi") }
                            }
                        }
                    }
                }
            }
        }
    }
    pick?.let { m ->
        var client by remember(m.id) { mutableStateOf(m.suggestedClientId) }
        AlertDialog(
            onDismissRequest = { pick = null },
            title = { Text("Zařadit ke klientovi") },
            text = {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                    OutlinedTextField(deadline, { deadline = it }, label = { Text("Lhůta (RRRR-MM-DD, nepovinné)") }, singleLine = true)
                    companies.forEach { c ->
                        Row(Modifier.fillMaxWidth().clickable { client = c.id }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(client == c.id, { client = c.id }); Text(c.name)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = client != null, onClick = {
                    scope.launch {
                        runCatching { Api.officeDsAction(m.id, "assign", client, deadline.trim().ifBlank { null }) }
                            .onSuccess { pick = null; load() }.onFailure { error = nav.errorText(it); pick = null }
                    }
                }) { Text("Zařadit – klient dostane upozornění") }
            },
            dismissButton = { TextButton(onClick = { pick = null }) { Text("Zrušit") } },
        )
    }
}
