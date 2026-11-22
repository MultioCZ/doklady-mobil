package cz.multio.doklady.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cz.multio.doklady.Navigator
import cz.multio.doklady.Screen
import cz.multio.doklady.data.Api
import cz.multio.doklady.data.Choice
import cz.multio.doklady.data.Pdf
import cz.multio.doklady.data.TaxPack
import cz.multio.doklady.data.TaxPackItem
import cz.multio.doklady.platform.decodeImage
import cz.multio.doklady.platform.openPdf
import cz.multio.doklady.platform.shareFile
import cz.multio.doklady.platform.openUrl
import cz.multio.doklady.platform.rememberDocumentScanner
import cz.multio.doklady.platform.rememberFilePicker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

private fun czDateShort(iso: String?): String? = iso?.takeIf { it.length >= 10 }?.let { "${it.substring(8, 10).trimStart('0')}. ${it.substring(5, 7).trimStart('0')}." }

// ------------------------------------------------------------------------ karta na Přehledu

/** Karta „Podklady k přiznání“ na Přehledu – jen když klient nějaké má a nejsou zpracované. */
@Composable
fun TaxPackCard(nav: Navigator) {
    var packs by remember { mutableStateOf<List<TaxPack>>(emptyList()) }
    LaunchedEffect(Unit) { packs = runCatching { Api.taxPacks() }.getOrDefault(emptyList()) }
    val p = packs.firstOrNull { it.state != "done" } ?: return
    val pr = p.progress
    Surface(
        onClick = { nav.push(Screen.TaxPack(p.id)) }, shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("Podklady k přiznání za ${p.year}", color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                if (p.state == "submitted") "Odesláno účetní · ${pr.done} z ${pr.total}"
                else "${pr.done} z ${pr.total} hotovo" + (czDateShort(p.deadline)?.let { " · prosíme do $it" } ?: ""),
                color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.bodyMedium,
            )
            LinearProgressIndicator(
                progress = { if (pr.total == 0) 0f else pr.done.toFloat() / pr.total }, color = Color.White, trackColor = Color.White.copy(alpha = 0.3f),
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            )
        }
    }
}

// ------------------------------------------------------------------------ podklady

/**
 * Podklady k přiznání (1.8): položky podle příjmů – ke každé Vyfotit (víc stran = jedno PDF) nebo Nahrát soubor,
 * u pronájmu a ostatních příjmů částka, u dětí text. Co klient nemá, přeskočí. Nakonec Odeslat účetní.
 */
@Composable
fun TaxPackScreen(nav: Navigator, id: String) {
    var pack by remember { mutableStateOf<TaxPack?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busyItem by remember { mutableStateOf<Int?>(null) }
    var target by remember { mutableStateOf<TaxPackItem?>(null) }
    var editing by remember { mutableStateOf<TaxPackItem?>(null) }
    var confirmSubmit by remember { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }
    var addOwn by remember { mutableStateOf(false) }
    var addSection by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    suspend fun load() {
        try { error = null; pack = Api.taxPack(id) } catch (e: Exception) { error = nav.errorText(e) }
    }

    fun download(what: String, name: String, mime: String) {
        scope.launch {
            try {
                val bytes = Api.taxPackDownload(id, what)
                if (mime == "application/pdf") openPdf(bytes, name) else shareFile(bytes, name, mime)
            } catch (e: Exception) { error = nav.errorText(e) }
        }
    }
    // návrat z kontroly fotek → načíst znovu
    LaunchedEffect(nav.stack.size) { if (nav.current is Screen.TaxPack) load() }

    val scanner = rememberDocumentScanner(pageLimit = 20) { picked ->
        val item = target
        val pages = picked?.pages.orEmpty()
        if (item != null && pages.isNotEmpty()) nav.push(Screen.TaxPackPages(id, item.id, item.title, pages))
    }
    val picker = rememberFilePicker { picked ->
        val item = target ?: return@rememberFilePicker
        val pk = picked ?: return@rememberFilePicker
        val data = pk.rawFile ?: pk.pdf ?: return@rememberFilePicker
        val mime = if (pk.rawFile != null) pk.rawMime ?: "application/octet-stream" else "application/pdf"
        busyItem = item.id
        scope.launch {
            try {
                Api.taxPackUpload(id, item.id, data, mime, pk.fileName ?: if (mime == "application/pdf") "doklad.pdf" else "doklad.jpg", "upload", pk.pages.size.coerceAtLeast(1))
                load()
            } catch (e: Exception) { error = nav.errorText(e) } finally { busyItem = null }
        }
    }

    val p = pack
    SimpleScreen(nav, p?.let { (if (it.kind == "annual") "Roční zúčtování " else "Přiznání ") + it.year } ?: "Podklady",
        actions = { TextButton(onClick = { download("zip", "podklady-${p?.year}.zip", "application/zip") }) { Text("ZIP") } }) {
        if (p == null) { if (error == null) Loading() else ErrorBanner(error) { scope.launch { load() } }; return@SimpleScreen }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
            val pr = p.progress
            LinearProgressIndicator(progress = { if (pr.total == 0) 0f else pr.done.toFloat() / pr.total }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
            Text(
                "${pr.done} z ${pr.total}" + (czDateShort(p.deadline)?.let { " · prosíme do $it" } ?: "") + " · co nemáte, přeskočte a doplňte později",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp, bottom = 4.dp),
            )
            if (p.purgedAt != null) Hint("Podklady jsme smazali podle dohody. Zůstalo přiznání, doručenka a váš souhlas.")
            if (p.state != "open") ResultSection(p, busy = sending, onDownload = ::download,
                onFiling = { method, ds -> scope.launch { try { error = null; pack = Api.taxPackFiling(id, method, ds) } catch (e: Exception) { error = nav.errorText(e) } } },
                onApprove = { scope.launch {
                    sending = true
                    try { val r = Api.taxPackApprove(id); pack = r; r.paymentUrl?.let { openUrl(it) } } catch (e: Exception) { error = nav.errorText(e) } finally { sending = false }
                } },
                onHanded = { scope.launch { try { pack = Api.taxPackHanded(id) } catch (e: Exception) { error = nav.errorText(e) } } })
            if (p.clientUpdatedAt != null && p.state != "open") Hint("Doplnili jste podklady po odeslání – účetní dostala upozornění." +
                if (p.filedAt != null) " Přiznání už bylo podáno, účetní posoudí, zda je potřeba dodatečné přiznání." else "")
            ErrorBanner(error)
            p.groups.forEach { g ->
                SectionTitle(g.title)
                AppCard {
                    g.items.forEachIndexed { i, it ->
                        if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        PackItemRow(
                            it, busy = busyItem == it.id, editable = p.editable, canDelete = p.canDeleteFiles,
                            onCamera = { target = it; scanner() },
                            onUpload = { target = it; picker() },
                            onEdit = { editing = it },
                            onOpen = { f ->
                                scope.launch {
                                    try {
                                        val bytes = Api.taxPackFile(id, f.id)
                                        if (f.name.lowercase().endsWith(".pdf")) openPdf(bytes, f.name)
                                        else shareFile(bytes, f.name, if (f.name.lowercase().endsWith(".png")) "image/png" else "image/jpeg")
                                    } catch (e: Exception) { error = nav.errorText(e) }
                                }
                            },
                            onDelete = { f ->
                                scope.launch {
                                    try { Api.taxPackDeleteFile(id, f.id); load() } catch (e: Exception) { error = nav.errorText(e) }
                                }
                            },
                        )
                    }
                }
            }
            if (p.editable) {
                Spacer(Modifier.height(12.dp))
                TextButton(onClick = { addOwn = true }) { Icon(Icons.Outlined.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Přidat jiný doklad") }
                if (p.kind == "tax" && p.addIncomes.isNotEmpty()) TextButton(onClick = { addSection = true }) {
                    Icon(Icons.Outlined.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Přidat příjem nebo slevu, na kterou jsem zapomněl")
                }
            }
            if (p.editable && p.state == "open") {
                Spacer(Modifier.height(22.dp))
                Button(enabled = !sending, onClick = { if (pr.missingRequired > 0) confirmSubmit = true else scope.launch {
                    sending = true
                    try { Api.taxPackSubmit(id); load() } catch (e: Exception) { error = nav.errorText(e) } finally { sending = false }
                } }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    if (sending) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    else Text("Odeslat účetní")
                }
                Text("Fotky se neukládají do galerie telefonu – jen sem.", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp).align(Alignment.CenterHorizontally))
            }
        }
    }

    if (confirmSubmit) {
        ConfirmDialog(
            "Ještě něco chybí", "Chybí ${p?.progress?.missingRequired} povinných položek. Odeslat i tak? Doplnit je můžete později.", "Odeslat",
            onConfirm = { scope.launch { sending = true; try { Api.taxPackSubmit(id); load() } catch (e: Exception) { error = nav.errorText(e) } finally { sending = false } } },
            onDismiss = { confirmSubmit = false },
        )
    }
    if (addOwn) TextInputDialog("Přidat jiný doklad", "Např. Potvrzení o studiu dítěte", onDismiss = { addOwn = false }) { title ->
        scope.launch { try { Api.taxPackAddItem(id, title); addOwn = false; load() } catch (e: Exception) { error = nav.errorText(e); addOwn = false } }
    }
    if (addSection) p?.let { pk ->
        AddSectionDialog(pk, onDismiss = { addSection = false }) { inc, ded ->
            scope.launch { try { pack = Api.taxPackAddSection(id, inc, ded); addSection = false } catch (e: Exception) { error = nav.errorText(e); addSection = false } }
        }
    }
    editing?.let { item ->
        ValueDialog(item, p?.rentExpenses.orEmpty(), onDismiss = { editing = null }) { body ->
            scope.launch {
                try { Api.taxPackValue(id, item.id, body); editing = null; load() } catch (e: Exception) { error = nav.errorText(e); editing = null }
            }
        }
    }
}

@Composable
private fun PackItemRow(
    item: TaxPackItem, busy: Boolean, editable: Boolean, canDelete: Boolean = editable,
    onCamera: () -> Unit, onUpload: () -> Unit, onEdit: () -> Unit,
    onOpen: (cz.multio.doklady.data.TaxPackFile) -> Unit, onDelete: (cz.multio.doklady.data.TaxPackFile) -> Unit,
) {
    Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(if (item.kind == "amount") Icons.Outlined.Payments else Icons.Outlined.Description, if (item.filled) Tone.Primary else if (item.required) Tone.Amber else Tone.Neutral)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(item.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                if (item.sub.isNotBlank()) Text(item.sub, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(8.dp))
            when {
                busy -> CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                item.kind == "file" && item.files.isNotEmpty() -> Tag("${item.files.size} nahráno", Tone.Primary)
                item.filled -> Tag("vyplněno", Tone.Primary)
                item.required -> Tag("chybí", Tone.Amber)
                else -> Tag("nepovinné")
            }
        }
        if (item.kind == "file") {
            item.files.forEach { f ->
                var menu by remember { mutableStateOf(false) }
                Box {
                    Row(Modifier.fillMaxWidth().clickable { menu = true }.padding(start = 50.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${f.name} · ${f.pages} str.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Zobrazit") }, leadingIcon = { Icon(Icons.Outlined.Visibility, null) }, onClick = { menu = false; onOpen(f) })
                        if (canDelete) DropdownMenuItem(text = { Text("Smazat", color = MaterialTheme.colorScheme.error) },
                            leadingIcon = { Icon(Icons.Outlined.Delete, null, tint = MaterialTheme.colorScheme.error) }, onClick = { menu = false; onDelete(f) })
                    }
                }
            }
            if (editable) Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onCamera, enabled = !busy, modifier = Modifier.weight(1f).height(42.dp)) {
                    Icon(Icons.Outlined.CameraAlt, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Vyfotit")
                }
                OutlinedButton(onClick = onUpload, enabled = !busy, modifier = Modifier.weight(1f).height(42.dp)) {
                    Icon(Icons.Outlined.FileUpload, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Nahrát soubor")
                }
            }
        } else {
            val value = when {
                item.kind == "amount" && item.amount != null -> "${formatAmount(item.amount)} Kč" + (item.text.takeIf { it.isNotBlank() }?.let { " · $it" } ?: "")
                item.kind == "text" && item.text.isNotBlank() -> item.text.lines().first() + if (item.text.lines().size > 1) " …" else ""
                else -> null
            }
            value?.let { Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 50.dp, top = 6.dp)) }
            if (editable) TextButton(onClick = onEdit, modifier = Modifier.padding(start = 40.dp)) {
                Icon(Icons.Outlined.Edit, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp))
                Text(if (item.filled) "Upravit" else if (item.kind == "amount") "Zadat částku" else "Vyplnit")
            }
        }
    }
}

private fun formatAmount(raw: String): String {
    val whole = raw.substringBefore('.')
    val dec = raw.substringAfter('.', "").trimEnd('0')
    val grouped = whole.reversed().chunked(3).joinToString(" ").reversed()
    return if (dec.isEmpty()) grouped else "$grouped,$dec"
}

@Composable
private fun ValueDialog(item: TaxPackItem, rentExpenses: List<Choice>, onDismiss: () -> Unit, onSave: (kotlinx.serialization.json.JsonObject) -> Unit) {
    var amount by remember { mutableStateOf(item.amount?.let { formatAmount(it) } ?: "") }
    var text by remember { mutableStateOf(item.text) }
    var expenses by remember { mutableStateOf(item.expenses) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(item.title) },
        text = {
            Column {
                if (item.sub.isNotBlank()) Text(item.sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 10.dp))
                if (item.kind == "amount") {
                    if (item.key.startsWith("inv-other") || item.key.startsWith("extra")) { Field("Co to bylo", text, { text = it }); Spacer(Modifier.height(10.dp)) }
                    Field("Částka celkem (Kč)", amount, { amount = it.filter { c -> c.isDigit() || c == ',' || c == '.' || c == ' ' } }, keyboard = KeyboardType.Decimal)
                    if (item.key == "rent-amount") {
                        Spacer(Modifier.height(12.dp)); Question("Výdaje"); ChoiceChips(rentExpenses, expenses) { expenses = it }
                    }
                } else {
                    Field("Text", text, { text = it }, singleLine = false)
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                onSave(buildJsonObject {
                    if (item.kind == "amount") {
                        put("amount", JsonPrimitive(amount.replace(" ", "").replace(',', '.')))
                        put("expenses", JsonPrimitive(expenses ?: ""))
                    }
                    put("text", JsonPrimitive(text))
                })
            }) { Text("Uložit") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Zrušit") } },
    )
}

// ------------------------------------------------------------------------ kontrola vyfocených stran

/**
 * Kontrola fotek před uložením: náhled každé strany, upozornění na rozmazanou (Sharpness), vyfotit stranu znovu,
 * smazat, přidat další strany. Uloží se jako jedno PDF k vybrané položce.
 */
@Composable
fun TaxPackPagesScreen(nav: Navigator, packId: String, itemId: Int, title: String, initial: List<ByteArray>) {
    val pages = remember { mutableStateListOf<ByteArray>().apply { addAll(initial) } }
    val thumbs = remember { mutableStateMapOf<Int, ImageBitmap>() }
    val blurry = remember { mutableStateMapOf<Int, Boolean>() }
    var retakeIndex by remember { mutableStateOf<Int?>(null) }
    var menuFor by remember { mutableStateOf<Int?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    fun key(b: ByteArray) = b.contentHashCode() xor b.size

    LaunchedEffect(pages.size, pages.map { key(it) }) {
        for (pg in pages.toList()) {
            val k = key(pg)
            if (thumbs.containsKey(k)) continue
            val r = withContext(Dispatchers.Default) { val bmp = decodeImage(pg, 700); bmp to (bmp?.let { Sharpness.isBlurry(it) } ?: false) }
            r.first?.let { thumbs[k] = it }
            blurry[k] = r.second
        }
    }
    val more = rememberDocumentScanner(pageLimit = 20) { picked -> picked?.pages?.let { pages.addAll(it) } }
    val retake = rememberDocumentScanner(pageLimit = 1) { picked ->
        val i = retakeIndex; val pg = picked?.pages?.firstOrNull()
        if (i != null && pg != null && i in pages.indices) pages[i] = pg
        retakeIndex = null
    }

    fun save() {
        busy = true; error = null
        scope.launch {
            try {
                val pdf = withContext(Dispatchers.Default) { Pdf.fromJpegs(pages.toList()) }
                if (pdf != null) Api.taxPackUpload(packId, itemId, pdf, "application/pdf", "${title.take(40)}.pdf", "camera", pages.size)
                else Api.taxPackUpload(packId, itemId, pages.first(), "image/jpeg", "${title.take(40)}.jpg", "camera", 1)
                nav.pop()
            } catch (e: Exception) { error = nav.errorText(e) } finally { busy = false }
        }
    }

    val bad = pages.indices.filter { blurry[key(pages[it])] == true }
    SimpleScreen(nav, "Kontrola fotek") {
        Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
            Text("Je vše čitelné?", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Text("Ukládá se k: $title · ${pages.size} ${if (pages.size == 1) "strana" else if (pages.size in 2..4) "strany" else "stran"}",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp, bottom = 14.dp))
            pages.chunked(2).forEachIndexed { row, chunk ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(bottom = 12.dp)) {
                    chunk.forEachIndexed { col, pg ->
                        val index = row * 2 + col
                        val k = key(pg)
                        val isBad = blurry[k] == true
                        Box(Modifier.weight(1f)) {
                            Surface(onClick = { menuFor = index }, shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(if (isBad) 1.5.dp else 1.dp, if (isBad) Brand.Amber else MaterialTheme.colorScheme.outlineVariant)) {
                                Column(Modifier.padding(8.dp)) {
                                    Box(Modifier.fillMaxWidth().aspectRatio(0.72f), contentAlignment = Alignment.Center) {
                                        val t = thumbs[k]
                                        if (t != null) Image(t, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                        else CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                                    }
                                    Text("Strana ${index + 1}", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
                                    Text(when { blurry[k] == null -> "Kontroluji…"; isBad -> "⚠ možná rozmazané"; else -> "✓ v pořádku" },
                                        style = MaterialTheme.typography.labelMedium, color = if (isBad) Brand.Amber else MaterialTheme.colorScheme.primary)
                                }
                            }
                            DropdownMenu(expanded = menuFor == index, onDismissRequest = { menuFor = null }) {
                                DropdownMenuItem(text = { Text("Vyfotit znovu") }, leadingIcon = { Icon(Icons.Outlined.CameraAlt, null) },
                                    onClick = { menuFor = null; retakeIndex = index; retake() })
                                DropdownMenuItem(text = { Text("Smazat stranu", color = MaterialTheme.colorScheme.error) }, leadingIcon = { Icon(Icons.Outlined.Delete, null, tint = MaterialTheme.colorScheme.error) },
                                    onClick = { menuFor = null; pages.removeAt(index); if (pages.isEmpty()) nav.pop() })
                            }
                        }
                    }
                    if (chunk.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            if (bad.isNotEmpty()) {
                Hint("Strana ${bad.joinToString(", ") { (it + 1).toString() }} vypadá rozmazaně. Čísla by nemusela jít přečíst – klepněte na ni a vyfoťte ji znovu.", amber = true)
                OutlinedButton(onClick = { retakeIndex = bad.first(); retake() }, enabled = !busy, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                    Icon(Icons.Outlined.CameraAlt, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text("Vyfotit stranu ${bad.first() + 1} znovu")
                }
                Spacer(Modifier.height(10.dp))
            }
            OutlinedButton(onClick = more, enabled = !busy, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                Icon(Icons.Outlined.Add, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text("Přidat další stranu")
            }
            ErrorBanner(error)
            Spacer(Modifier.height(16.dp))
            Button(onClick = ::save, enabled = !busy && pages.isNotEmpty(), modifier = Modifier.fillMaxWidth().height(52.dp)) {
                if (busy) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                else { Icon(Icons.Outlined.CheckCircle, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text(if (bad.isEmpty()) "Uložit jako jedno PDF" else "Uložit i tak") }
            }
            Text("Fotky se neukládají do galerie telefonu.", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp).align(Alignment.CenterHorizontally))
        }
    }
}


// ------------------------------------------------------------------------ 1.9: stav, výsledek, podání

@Composable
fun TimelineView(steps: List<cz.multio.doklady.data.TimelineStep>) {
    Column(Modifier.padding(start = 4.dp)) {
        steps.forEach { st ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 5.dp)) {
                Surface(shape = androidx.compose.foundation.shape.CircleShape, color = if (st.done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                    border = BorderStroke(2.dp, if (st.done || st.now) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline), modifier = Modifier.size(14.dp)) {}
                Spacer(Modifier.width(12.dp))
                Text(st.label, style = MaterialTheme.typography.bodyMedium, fontWeight = if (st.done || st.now) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (st.done || st.now) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ResultSection(
    p: TaxPack, busy: Boolean, onDownload: (String, String, String) -> Unit, onFiling: (String, String) -> Unit, onApprove: () -> Unit, onHanded: () -> Unit,
) {
    var method by remember(p.filingMethod) { mutableStateOf(p.filingMethod) }
    var ds by remember(p.dsId) { mutableStateOf(p.dsId ?: "") }
    AppCard(Modifier.padding(top = 10.dp, bottom = 4.dp)) {
        Column(Modifier.padding(16.dp)) {
            TimelineView(p.timeline)
            val r = p.result
            if (r != null && r.pdf) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(vertical = 12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(if (p.kind == "annual") "Odhad vrácení" else "Výsledek", style = MaterialTheme.typography.labelLarge)
                        Text(r.label + if (r.note.isNotBlank()) " · ${r.note}" else "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text((if (r.kind == "refund") "+ " else if (r.kind == "payment") "− " else "") + formatAmount(r.amount ?: "0") + " Kč",
                        style = MaterialTheme.typography.headlineSmall, color = if (r.kind == "payment") Brand.Red else MaterialTheme.colorScheme.primary)
                }
                TextButton(onClick = { onDownload("result/pdf", if (p.kind == "annual") "zadost-rocni-zuctovani-${p.year}.pdf" else "priznani-${p.year}.pdf", "application/pdf") }) {
                    Text(if (p.kind == "annual") "Žádost o roční zúčtování (PDF)" else "Přiznání (PDF)")
                }
                if (r.xml) TextButton(onClick = { onDownload("result/xml", "priznani-${p.year}.xml", "application/xml") }) { Text("Soubor pro Moje daně (XML)") }
                if (r.receipt) TextButton(onClick = { onDownload("result/receipt", "dorucenka-${p.year}.zfo", "application/octet-stream") }) { Text("Doručenka") }
            }
            if (p.kind == "annual" && p.state == "ready") {
                Text("1. Vytiskněte a podepište žádost.\n2. Odevzdejte ji mzdové účetní zaměstnavatele s přílohami.\n3. Nejpozději do 15. 2. – vrácení přijde ve výplatě.",
                    style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 8.dp))
                Button(onClick = onHanded, modifier = Modifier.fillMaxWidth().height(48.dp)) { Text("Odevzdáno zaměstnavateli ✓") }
            }
            if (p.kind == "tax" && p.state == "ready") {
                Spacer(Modifier.height(10.dp))
                Question("Jak přiznání podat?")
                ChoiceChips(listOf(Choice("ds", "Podejte ho za mě datovou schránkou"), Choice("self", "Podám sám (PDF a XML)")), method) { method = it }
                if (method == "ds") {
                    Field("ID vaší datové schránky (nepovinné)", ds, { ds = it.filter { c -> c.isLetterOrDigit() }.lowercase().take(7) })
                    Text("Jen ID schránky (7 znaků). Přihlašovací jméno ani heslo nikdy nezadávejte.", style = MaterialTheme.typography.bodySmall,
                        color = Brand.Amber, modifier = Modifier.padding(top = 6.dp, bottom = 6.dp))
                    TextButton(onClick = { onDownload("poa", "plna-moc-${p.year}.pdf", "application/pdf") }) { Text("Plná moc (PDF) – vytiskněte, podepište a vyfoťte do části Podání") }
                }
                if (method != null && (method != p.filingMethod || ds != (p.dsId ?: ""))) {
                    OutlinedButton(onClick = { onFiling(method!!, ds) }, modifier = Modifier.fillMaxWidth().height(44.dp)) { Text("Uložit volbu") }
                }
                if (p.filingMethod != null && method == p.filingMethod) {
                    Spacer(Modifier.height(8.dp))
                    Button(enabled = !busy, onClick = onApprove, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                        Text(if (p.price != null) "Souhlasím a zaplatit ${formatAmount(p.price)} Kč" else if (p.filingMethod == "ds") "Souhlasím, můžete podat" else "Souhlasím")
                    }
                    Text("Před zaplacením si stáhněte podklady (ZIP nahoře) – po podání je smažeme.", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
                }
            }
            if (p.state == "approved" && p.price != null && p.paidAt == null) Hint("Čekáme na potvrzení platby. Když jste zaplatili, objeví se tu do pár minut.")
            else if (p.state == "approved" && p.filingMethod == "ds") Hint("Podáme datovou schránkou. Doručenka se objeví tady.")
            if (p.result?.xml == true && p.filingMethod == "self") Text("Moje daně: mojedane.cz → Elektronické formuláře → Načíst ze souboru → Kontrola → Podat.",
                style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

@Composable
fun TextInputDialog(title: String, placeholder: String, onDismiss: () -> Unit, onOk: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) },
        text = { Field("Název", text, { text = it }, placeholder = placeholder) },
        confirmButton = { Button(enabled = text.isNotBlank(), onClick = { onOk(text.trim()) }) { Text("Přidat") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Zrušit") } })
}

@Composable
private fun AddSectionDialog(p: TaxPack, onDismiss: () -> Unit, onOk: (List<String>, List<String>) -> Unit) {
    val inc = remember { mutableStateListOf<String>() }
    val ded = remember { mutableStateListOf<String>() }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Co přidat?") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Question("Příjmy"); MultiChips(p.addIncomes, inc)
                Question("Slevy a odpočty"); MultiChips(p.addDeductions, ded)
                Text("Přibudou jen nové položky, nic se nesmaže.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { Button(enabled = inc.isNotEmpty() || ded.isNotEmpty(), onClick = { onOk(inc.toList(), ded.toList()) }) { Text("Přidat") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Zrušit") } })
}
