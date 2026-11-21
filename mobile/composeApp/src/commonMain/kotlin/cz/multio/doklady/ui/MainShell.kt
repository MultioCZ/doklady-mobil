package cz.multio.doklady.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Business
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cz.multio.doklady.Navigator
import cz.multio.doklady.Screen
import cz.multio.doklady.data.Api
import cz.multio.doklady.data.Home
import cz.multio.doklady.data.Outbox
import cz.multio.doklady.data.Session
import cz.multio.doklady.platform.IncomingShare
import cz.multio.doklady.platform.KeyValueStore
import cz.multio.doklady.platform.rememberDocumentScanner
import cz.multio.doklady.platform.rememberFilePicker
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import androidx.compose.material.icons.automirrored.filled.ArrowBack

/**
 * Hlavní obrazovka (1.5): nahoře logo a menu, dole Přehled · Doklady · Vyfotit · Banka · Úkoly – stejně jako
 * spodní lišta portálu v telefonu. Faktury, kniha jízd, přehledy, daně a zabezpečení jsou v menu vpravo nahoře.
 */
@Composable
fun MainScreen(nav: Navigator) {
    var tab by rememberSaveable { mutableStateOf("home") }
    var bankFilter by rememberSaveable { mutableStateOf("all") }
    var companyId by remember { mutableStateOf(Api.companyId) }
    var me by remember { mutableStateOf(Api.cachedMe()) }
    // balíček a firmy načíst znovu při každém otevření – změna na webu se projeví bez odhlášení
    LaunchedEffect(Unit) { Api.refreshMe()?.let { me = it; companyId = Api.companyId } }
    var lockedInfo by remember { mutableStateOf<Pair<String, String>?>(null) }
    val company = me?.companies?.firstOrNull { it.id == companyId }
    var openTasks by remember { mutableIntStateOf(0) }
    var missingBank by remember { mutableIntStateOf(0) }
    var captureSheet by remember { mutableStateOf(false) }
    var companyMenu by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val multiScanner = rememberDocumentScanner { picked -> picked?.let { nav.push(Screen.Upload(it)) } }
    val receiptScanner = rememberDocumentScanner(pageLimit = 30) { picked -> picked?.takeIf { it.pages.isNotEmpty() }?.let { nav.push(Screen.Receipts(it.pages)) } }
    val picker = rememberFilePicker { picked -> picked?.let { nav.push(Screen.Upload(it)) } }
    val shared = IncomingShare.pending
    LaunchedEffect(shared) {
        if (shared != null) {
            IncomingShare.pending = null
            nav.push(Screen.Upload(shared))
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                title = { BrandLogo(Modifier.height(36.dp)) },
                actions = {
                    if ((me?.companies?.size ?: 0) > 1) {
                        Box {
                            IconButton(onClick = { companyMenu = true }) { Icon(Icons.Outlined.Business, "Vybrat firmu") }
                            DropdownMenu(expanded = companyMenu, onDismissRequest = { companyMenu = false }) {
                                me?.companies?.forEach { c ->
                                    DropdownMenuItem(
                                        text = { Text(c.name + (c.ico?.let { "  ·  IČO $it" } ?: "")) },
                                        leadingIcon = { RadioButton(c.id == companyId, null) },
                                        onClick = { Api.companyId = c.id; companyId = c.id; companyMenu = false },
                                    )
                                }
                            }
                        }
                    }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.Menu, "Menu") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            /** module = funkce balíčku; bez ní se položka ukáže se štítkem balíčku a po klepnutí vysvětlí. */
                            @Composable
                            fun item(text: String, icon: ImageVector, screen: Screen, module: String? = null) {
                                val lockedIn = module?.let { company?.lockedIn(it) }
                                DropdownMenuItem(
                                    text = { Text(text, color = if (lockedIn != null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface) },
                                    leadingIcon = { Icon(icon, null, tint = if (lockedIn != null) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurfaceVariant) },
                                    trailingIcon = lockedIn?.let { e -> @Composable { Tag(e) } },
                                    onClick = { menu = false; if (lockedIn != null) lockedInfo = text to lockedIn else nav.push(screen) },
                                )
                            }
                            item("Faktury", Icons.AutoMirrored.Outlined.ReceiptLong, Screen.Invoices, "invoicing")
                            item("Údaje firmy", Icons.Outlined.Business, Screen.CompanyProfile, "invoicing")
                            item("Asistent", Icons.Outlined.AutoAwesome, Screen.Assistant, "assistant")
                            item("Kniha jízd", Icons.Outlined.DirectionsCar, Screen.Trips, "trips")
                            item("Přehledy a dodavatelé", Icons.Outlined.BarChart, Screen.Reports)
                            item("Daň z příjmů", Icons.Outlined.Description, Screen.TaxList)
                            HorizontalDivider()
                            item("Zabezpečení", Icons.Outlined.Lock, Screen.Security)
                            item("Nastavení", Icons.Outlined.Settings, Screen.Settings)
                            DropdownMenuItem(text = { Text("Odhlásit") }, leadingIcon = { Icon(Icons.AutoMirrored.Outlined.Logout, null) },
                                onClick = { menu = false; scope.launch { Api.logoutEverywhere() }; nav.reset(Screen.Login()) })
                        }
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
                @Composable
                fun tabItem(key: String, label: String, icon: ImageVector, count: Int = 0, onClick: () -> Unit = { tab = key }) {
                    val selected = tab == key
                    NavigationBarItem(
                        selected = selected, onClick = onClick,
                        icon = { if (count > 0) BadgedBox(badge = { Badge { Text(count.toString()) } }) { Icon(icon, null) } else Icon(icon, null) },
                        label = { Text(label, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium, fontSize = 11.5.sp) },
                        colors = NavigationBarItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedIconColor = MaterialTheme.colorScheme.primary, selectedTextColor = MaterialTheme.colorScheme.onSurface),
                    )
                }
                tabItem("home", "Přehled", Icons.Outlined.Home)
                tabItem("docs", "Doklady", Icons.Outlined.Description)
                NavigationBarItem(
                    selected = false, onClick = { captureSheet = true },
                    icon = {
                        Box(Modifier.size(width = 56.dp, height = 32.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Outlined.CameraAlt, "Vyfotit", tint = MaterialTheme.colorScheme.onPrimary)
                        }
                    },
                    label = { Text("Vyfotit", fontSize = 11.5.sp, fontWeight = FontWeight.Medium) },
                    colors = NavigationBarItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.surface),
                )
                tabItem("bank", "Banka", Icons.Outlined.AccountBalance, missingBank)
                tabItem("tasks", "Úkoly", Icons.Outlined.TaskAlt, openTasks)
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            androidx.compose.runtime.key(companyId) {
                when (tab) {
                    "home" -> HomeTab(nav, companyName = company?.name, onBank = { bankFilter = "missing"; tab = "bank" }, onDocs = { tab = "docs" },
                        onCounts = { tasks, bank -> openTasks = tasks; missingBank = bank })
                    "docs" -> DocumentsTab(nav)
                    "bank" -> BankTab(nav, initialFilter = bankFilter, onMissing = { missingBank = it })
                    else -> TasksTab(nav) { openTasks = it }
                }
            }
        }
    }

    lockedInfo?.let { (feature, edition) ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { lockedInfo = null },
            icon = { Icon(Icons.Outlined.Lock, null) },
            title = { Text(feature) },
            text = { Text("Tato funkce je součástí balíčku $edition" + (company?.edition?.let { " (máte $it)" } ?: "") + ". O rozšíření se domluvte se svou účetní kanceláří.") },
            confirmButton = { androidx.compose.material3.TextButton(onClick = { lockedInfo = null }) { Text("Rozumím") } },
        )
    }

    if (captureSheet) {
        CaptureSheet(onDismiss = { captureSheet = false }) { mode ->
            captureSheet = false
            when (mode) {
                "receipts" -> receiptScanner()
                "multi" -> multiScanner()
                else -> picker()
            }
        }
    }
}

// ------------------------------------------------------------------------ volba focení

/** Co chcete nahrát: účtenky (každá fotka = samostatný doklad), vícestránkový doklad, soubor. Volba se pamatuje. */
@Composable
fun CaptureSheet(onDismiss: () -> Unit, onChoose: (String) -> Unit) {
    var mode by remember { mutableStateOf(KeyValueStore.get("capture_mode") ?: "receipts") }
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state, containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp).navigationBarsPadding()) {
            Text("Co chcete nahrát?", style = MaterialTheme.typography.titleLarge)
            Text("Volba se zapamatuje pro příští focení.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp, bottom = 16.dp))
            @Composable
            fun option(key: String, icon: ImageVector, title: String, text: String) {
                val sel = mode == key
                Surface(
                    onClick = { mode = key }, shape = MaterialTheme.shapes.large,
                    color = if (sel) MaterialTheme.colorScheme.primaryContainer.copy(alpha = .45f) else MaterialTheme.colorScheme.surface,
                    border = BorderStroke(if (sel) 1.5.dp else 1.dp, if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconBadge(icon, if (sel) Tone.Primary else Tone.Neutral)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(title, style = MaterialTheme.typography.titleMedium)
                            Text(text, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        RadioButton(sel, { mode = key })
                    }
                }
            }
            option("receipts", Icons.Outlined.Receipt, "Účtenky", "Každá fotka se uloží jako samostatný doklad (PDF)")
            option("multi", Icons.Outlined.ContentCopy, "Vícestránkový doklad", "Všechny strany se spojí do jednoho dokladu")
            option("file", Icons.Outlined.FolderOpen, "Soubor z telefonu", "PDF nebo obrázek z e-mailu či galerie")
            Button(onClick = { KeyValueStore.put("capture_mode", mode); onChoose(mode) }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp).height(52.dp)) {
                Icon(if (mode == "file") Icons.Outlined.FolderOpen else Icons.Outlined.CameraAlt, null, Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp)); Text(if (mode == "file") "Vybrat soubor" else "Pokračovat")
            }
        }
    }
}

// ------------------------------------------------------------------------ přehled

@Composable
fun HomeTab(nav: Navigator, companyName: String?, onBank: () -> Unit, onDocs: () -> Unit, onCounts: (tasks: Int, bank: Int) -> Unit) {
    var home by remember { mutableStateOf<Home?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var refreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val outbox = Outbox.items

    suspend fun load() {
        try {
            error = null
            if (Outbox.items.isNotEmpty()) Outbox.flush()
            val h = Api.home()
            home = h
            onCounts(h.todo.count { it.kind == "task" }, h.todo.firstOrNull { it.kind == "bank" }?.title?.takeWhile { it.isDigit() }?.toIntOrNull() ?: 0)
        } catch (e: Exception) {
            error = nav.errorText(e)
        }
    }
    LaunchedEffect(Unit) { load() }

    PullToRefreshBox(isRefreshing = refreshing, onRefresh = { scope.launch { refreshing = true; load(); refreshing = false } }, modifier = Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp)) {
            item {
                if (!companyName.isNullOrBlank()) {
                    Text(companyName, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                }
                Text(greeting(), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(bottom = 16.dp))
                ErrorBanner(error) { scope.launch { load() } }
                val c = home?.counts
                AppCard {
                    Row(Modifier.padding(vertical = 14.dp)) {
                        @Composable
                        fun stat(value: Int?, label: String, highlight: Boolean = false, last: Boolean = false) {
                            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(value?.toString() ?: "–", style = MaterialTheme.typography.headlineSmall,
                                    color = if (highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                                Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        stat(c?.todo, "K doplnění")
                        stat(c?.atAccountant, "U účetní")
                        stat(c?.doneMonth, "Hotovo", highlight = true)
                    }
                }
                TaxPackCard(nav)
                if (outbox.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    AppCard {
                        ListRow("Čeká na odeslání: ${outbox.size}", outbox.last().lastError ?: "Odešle se, až bude signál.", Icons.Outlined.WarningAmber, Tone.Amber,
                            onClick = { scope.launch { Outbox.flush(); load() } })
                    }
                }
            }
            val todo = home?.todo.orEmpty()
            item {
                SectionTitle("Co od vás potřebujeme")
                AppCard {
                    if (home == null && error == null) Box(Modifier.fillMaxWidth().height(120.dp)) { Loading() }
                    else if (todo.isEmpty()) ListRow("Máte hotovo", "Účetní od vás teď nic nepotřebuje.", Icons.Outlined.CheckCircle, Tone.Primary)
                    todo.forEachIndexed { i, t ->
                        val (icon, tone) = when (t.kind) {
                            "bank" -> Icons.Outlined.AccountBalanceWallet to Tone.Amber
                            "readability" -> Icons.Outlined.WarningAmber to Tone.Red
                            else -> Icons.Outlined.TaskAlt to Tone.Neutral
                        }
                        ListRow(t.title, t.subtitle, icon, tone, divider = i > 0, onClick = {
                            when (t.kind) {
                                "bank" -> onBank()
                                "readability" -> t.id?.let { nav.push(Screen.DocumentDetail(it)) }
                                else -> t.id?.let { nav.push(Screen.TaskDetail(it)) }
                            }
                        })
                    }
                }
            }
            val recent = home?.recent.orEmpty()
            if (recent.isNotEmpty()) {
                item {
                    SectionTitle("Poslední doklady", action = "Vše", onAction = onDocs)
                    AppCard {
                        recent.forEachIndexed { i, d ->
                            ListRow(
                                d.supplierName ?: if (d.isWorking) "Zpracovává se…" else (d.originalFilename ?: "Doklad"),
                                listOfNotNull(czDate(d.issueDate ?: d.createdAt), d.docType?.let { docTypeNames[it] }).joinToString(" · "),
                                if (d.docType == "uctenka") Icons.Outlined.Receipt else Icons.Outlined.Description, divider = i > 0,
                                onClick = { nav.push(Screen.DocumentDetail(d.id)) },
                                trailing = { DocTag(d.status) },
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun greeting(): String {
    val h = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).hour
    return when (h) { in 4..9 -> "Dobré ráno"; in 18..23 -> "Dobrý večer"; else -> "Dobrý den" }
}

/** Stav dokladu jako štítek (stejné texty jako na webu). */
@Composable
fun DocTag(status: String) {
    val tone = when (status) {
        "checked" -> Tone.Primary
        "error" -> Tone.Red
        "extracted" -> Tone.Amber
        else -> Tone.Neutral
    }
    Tag(statusName(status), tone)
}

// ------------------------------------------------------------------------ nastavení

@Composable
fun SettingsScreen(nav: Navigator) {
    val me = remember { Api.cachedMe() }
    SimpleScreen(nav, "Nastavení") {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
            AppCard {
                KeyValue("Uživatel", me?.user?.let { listOfNotNull(it.name, it.email).joinToString(" · ") }, divider = false)
                KeyValue("Role", if (me?.user?.isAdmin == true) "Účetní (všechny firmy)" else "Klient")
                KeyValue("Server", Api.baseUrl)
                KeyValue("Neodeslané doklady", Outbox.items.size.toString())
                KeyValue("Odemykání otiskem", if (Session.biometricEnabled) "zapnuto" else "vypnuto")
            }
            Spacer(Modifier.height(16.dp))
            OutlinedButton(onClick = { nav.push(Screen.Security) }, modifier = Modifier.fillMaxWidth().height(48.dp)) { Text("Zabezpečení") }
            Spacer(Modifier.height(20.dp))
            Text("Doklady 1.8.0 · portál Multio 1.16", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Obrazovka s horní lištou a šipkou zpět (pro obsah, který dřív byl záložkou). */
@Composable
fun SimpleScreen(nav: Navigator, title: String, fab: (@Composable () -> Unit)? = null, actions: @Composable () -> Unit = {}, content: @Composable () -> Unit) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(title, style = MaterialTheme.typography.titleLarge) },
                navigationIcon = { IconButton(onClick = { nav.pop() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zpět") } },
                actions = { actions() },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        floatingActionButton = { fab?.invoke() },
    ) { padding -> Box(Modifier.padding(padding)) { content() } }
}
