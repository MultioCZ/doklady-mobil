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
    val scope = rememberCoroutineScope()

    suspend fun load() {
        try { error = null; pack = Api.taxPack(id) } catch (e: Exception) { error = nav.errorText(e) }
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
    SimpleScreen(nav, p?.let { "Podklady k přiznání ${it.year}" } ?: "Podklady k přiznání") {
        if (p == null) { if (error == null) Loading() else ErrorBanner(error) { scope.launch { load() } }; return@SimpleScreen }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
            val pr = p.progress
            LinearProgressIndicator(progress = { if (pr.total == 0) 0f else pr.done.toFloat() / pr.total }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
            Text(
                "${pr.done} z ${pr.total}" + (czDateShort(p.deadline)?.let { " · prosíme do $it" } ?: "") + " · co nemáte, přeskočte a doplňte později",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp, bottom = 4.dp),
            )
            if (p.state == "submitted") Hint("Odesláno účetní. Když něco doplníte, uvidí to.")
            if (p.state == "done") Hint("Účetní podklady zpracovala. Další doklad pošlete přes Doklady nebo zprávou.")
            ErrorBanner(error)
            p.groups.forEach { g ->
                SectionTitle(g.title)
                AppCard {
                    g.items.forEachIndexed { i, it ->
                        if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        PackItemRow(
                            it, busy = busyItem == it.id, editable = p.editable,
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
                Spacer(Modifier.height(22.dp))
                Button(enabled = !sending, onClick = { if (pr.missingRequired > 0) confirmSubmit = true else scope.launch {
                    sending = true
                    try { Api.taxPackSubmit(id); load() } catch (e: Exception) { error = nav.errorText(e) } finally { sending = false }
                } }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    if (sending) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    else Text(if (p.state == "submitted") "Poslat účetní znovu" else "Odeslat účetní")
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
    item: TaxPackItem, busy: Boolean, editable: Boolean,
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
                        if (editable) DropdownMenuItem(text = { Text("Smazat", color = MaterialTheme.colorScheme.error) },
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
