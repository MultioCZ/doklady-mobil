package cz.multio.doklady.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cz.multio.doklady.Navigator
import cz.multio.doklady.Screen
import cz.multio.doklady.data.Api
import cz.multio.doklady.data.Company
import cz.multio.doklady.data.OfficeMessage
import cz.multio.doklady.data.TaxPack
import kotlinx.coroutines.launch

/**
 * Přehled pro klienta „jen přiznání“ / „roční zúčtování“ (1.9, portál 1.17): jediná obrazovka – moje přiznání.
 * Žádné Doklady, Banka ani Úkoly. Menu: Podklady, Napsat účetní, Hotová přiznání, Zabezpečení, Odhlásit.
 * Plný přístup jen po schválení kanceláře („Mám zájem“ pošle žádost).
 */
@Composable
fun LimitedHomeScreen(nav: Navigator, company: Company) {
    var packs by remember { mutableStateOf<List<TaxPack>?>(null) }
    var detail by remember { mutableStateOf<TaxPack?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var menu by remember { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }
    var upsellSent by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val annual = company.access == "annual"

    suspend fun load() {
        try {
            error = null
            val list = Api.taxPacks()
            packs = list
            detail = list.firstOrNull { it.state != "done" }?.let { Api.taxPack(it.id) }
        } catch (e: Exception) { error = nav.errorText(e) }
    }
    LaunchedEffect(nav.stack.size) { if (nav.current is Screen.Main) load() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { BrandLogo(Modifier.height(36.dp)) },
                actions = {
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.Menu, "Menu") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            detail?.let { d -> DropdownMenuItem(text = { Text("Podklady ${d.year}") }, leadingIcon = { Icon(Icons.Outlined.Description, null) },
                                onClick = { menu = false; nav.push(Screen.TaxPack(d.id)) }) }
                            DropdownMenuItem(text = { Text("Napsat účetní") }, leadingIcon = { Icon(Icons.Outlined.ChatBubbleOutline, null) }, onClick = { menu = false; nav.push(Screen.Messages) })
                            DropdownMenuItem(text = { Text(if (annual) "Hotová zúčtování" else "Hotová přiznání") }, leadingIcon = { Icon(Icons.Outlined.FolderOpen, null) },
                                onClick = { menu = false; nav.push(Screen.TaxPacks) })
                            DropdownMenuItem(text = { Text("Upozornění") }, leadingIcon = { Icon(Icons.Outlined.Notifications, null) }, onClick = { menu = false; nav.push(Screen.Notifications) })
                            DropdownMenuItem(text = { Text("Zabezpečení") }, leadingIcon = { Icon(Icons.Outlined.Lock, null) }, onClick = { menu = false; nav.push(Screen.Security) })
                            DropdownMenuItem(text = { Text("Odhlásit") }, leadingIcon = { Icon(Icons.AutoMirrored.Outlined.Logout, null) },
                                onClick = { menu = false; scope.launch { Api.logoutEverywhere() }; nav.reset(Screen.Welcome) })
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        PullToRefreshBox(isRefreshing = refreshing, onRefresh = { scope.launch { refreshing = true; load(); refreshing = false } },
            modifier = Modifier.padding(padding).fillMaxSize()) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
                Text(company.name, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val d = detail
                Text(if (annual) "Roční zúčtování${d?.let { " ${it.year}" } ?: ""}" else "Moje přiznání${d?.let { " ${it.year}" } ?: ""}",
                    style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(bottom = 14.dp))
                ErrorBanner(error) { scope.launch { load() } }
                when {
                    packs == null && error == null -> Box(Modifier.fillMaxWidth().height(200.dp)) { Loading() }
                    d == null -> AppCard { ListRow("Zatím tu nic není", "Až vám účetní otevře podklady, objeví se tady.", Icons.Outlined.Description) }
                    d.state == "open" -> {
                        val pr = d.progress
                        Surface(onClick = { nav.push(Screen.TaxPack(d.id)) }, shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.primary, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp)) {
                                Text(if (d.kind == "annual") "Podklady k ročnímu zúčtování" else "Podklady k přiznání", color = Color.White,
                                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Text("${pr.done} z ${pr.total}" + (d.deadline?.let { " · prosíme do ${czDate(it)}" } ?: ""), color = Color.White.copy(alpha = 0.9f))
                                LinearProgressIndicator(progress = { if (pr.total == 0) 0f else pr.done.toFloat() / pr.total }, color = Color.White,
                                    trackColor = Color.White.copy(alpha = 0.3f), modifier = Modifier.fillMaxWidth().padding(top = 10.dp))
                            }
                        }
                        val missing = d.groups.flatMap { it.items }.filter { it.required && !it.filled }.take(5)
                        if (missing.isNotEmpty()) {
                            SectionTitle("Co ještě chybí")
                            AppCard {
                                missing.forEachIndexed { i, it ->
                                    ListRow(it.title, it.sub, Icons.Outlined.WarningAmber, Tone.Amber, divider = i > 0, onClick = { nav.push(Screen.TaxPack(d.id)) })
                                }
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = { nav.push(Screen.TaxPack(d.id)) }, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Doplnit podklady") }
                    }
                    else -> {
                        AppCard { Column(Modifier.padding(16.dp)) { TimelineView(d.timeline) } }
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = { nav.push(Screen.TaxPack(d.id)) }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                            Text(if (d.state == "ready") (if (d.kind == "annual") "Otevřít žádost" else "Zkontrolovat a odsouhlasit") else "Otevřít")
                        }
                    }
                }
                TextButton(onClick = { nav.push(Screen.Messages) }, modifier = Modifier.fillMaxWidth()) { Text("Napsat účetní") }
                Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)), modifier = Modifier.fillMaxWidth().padding(top = 14.dp)) {
                    Column(Modifier.padding(14.dp)) {
                        Text("Podnikáte a chcete mít klid celý rok?", fontWeight = FontWeight.SemiBold)
                        Text("Vedení účetnictví nebo daňové evidence – doklady, banka a faktury v aplikaci.", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        TextButton(enabled = !upsellSent, onClick = { scope.launch { runCatching { Api.upgradeRequest() }; upsellSent = true } }) {
                            Text(if (upsellSent) "Děkujeme, ozveme se vám" else "Mám zájem →")
                        }
                    }
                }
            }
        }
    }
}

/** Seznam podkladů všech let (Hotová přiznání). */
@Composable
fun TaxPacksScreen(nav: Navigator) {
    var packs by remember { mutableStateOf<List<TaxPack>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { try { packs = Api.taxPacks() } catch (e: Exception) { error = nav.errorText(e) } }
    SimpleScreen(nav, "Hotová přiznání") {
        Column(Modifier.fillMaxSize().padding(20.dp)) {
            ErrorBanner(error)
            val list = packs
            if (list == null) { if (error == null) Loading(); return@Column }
            AppCard {
                if (list.isEmpty()) ListRow("Zatím nic", null, Icons.Outlined.Description)
                list.forEachIndexed { i, p ->
                    ListRow((if (p.kind == "annual") "Roční zúčtování " else "Přiznání ") + p.year, p.stateLabel, Icons.Outlined.Description, divider = i > 0,
                        onClick = { nav.push(Screen.TaxPack(p.id)) })
                }
            }
        }
    }
}

/** Zprávy účetní (jednoduchý chat). */
@Composable
fun MessagesScreen(nav: Navigator) {
    var msgs by remember { mutableStateOf<List<OfficeMessage>?>(null) }
    var text by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val list = rememberLazyListState()
    LaunchedEffect(Unit) { try { msgs = Api.messages() } catch (e: Exception) { error = nav.errorText(e) } }
    LaunchedEffect(msgs?.size) { msgs?.size?.takeIf { it > 0 }?.let { list.scrollToItem(it - 1) } }
    SimpleScreen(nav, "Napsat účetní") {
        Column(Modifier.fillMaxSize().imePadding()) {
            ErrorBanner(error)
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = list, contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val m = msgs
                if (m == null) item { Box(Modifier.fillMaxWidth().height(160.dp)) { Loading() } }
                else if (m.isEmpty()) item { Text("Napište dotaz – účetní odpoví sem i e-mailem.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                else items(m, key = { it.id }) { msg ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (msg.fromAccountant) Arrangement.Start else Arrangement.End) {
                        Surface(shape = RoundedCornerShape(14.dp), color = if (msg.fromAccountant) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.primaryContainer,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), modifier = Modifier.widthIn(max = 300.dp)) {
                            Column(Modifier.padding(12.dp)) {
                                if (msg.fromAccountant) Text(msg.author.ifBlank { "Účetní" }, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                Text(msg.body, style = MaterialTheme.typography.bodyMedium)
                                Text(czDate(msg.createdAt) ?: "", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(text, { text = it }, placeholder = { Text("Zpráva") }, modifier = Modifier.weight(1f), shape = MaterialTheme.shapes.medium)
                Spacer(Modifier.width(8.dp))
                IconButton(enabled = text.isNotBlank() && !busy, onClick = {
                    busy = true
                    scope.launch {
                        try { msgs = Api.sendMessage(text.trim()); text = "" } catch (e: Exception) { error = nav.errorText(e) } finally { busy = false }
                    }
                }) { Icon(Icons.AutoMirrored.Outlined.Send, "Odeslat") }
            }
        }
    }
}
