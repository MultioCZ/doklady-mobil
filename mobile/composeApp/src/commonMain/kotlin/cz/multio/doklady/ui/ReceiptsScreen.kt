package cz.multio.doklady.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CallMerge
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cz.multio.doklady.Navigator
import cz.multio.doklady.data.Outbox
import cz.multio.doklady.data.Pdf
import cz.multio.doklady.data.QrPayment
import cz.multio.doklady.platform.decodeImage
import cz.multio.doklady.platform.readQrCodes
import cz.multio.doklady.platform.rememberDocumentScanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Jedna účtenka = jedna nebo víc fotek (spojené „dlouhá účtenka na dvě fotky“). */
private class Receipt(val pages: List<ByteArray>) {
    val key = pages.first().contentHashCode() xor pages.size
}

/**
 * Režim Účtenky (1.5): každá vyfocená strana je samostatný doklad. Před odesláním: náhledy, upozornění na možná
 * rozmazanou fotku, vyfotit znovu / smazat / spojit s předchozí, nákup – prodej, poznámka. Každá účtenka se uloží
 * jako jednostránkové PDF (fotka uvnitř beze změny) a odejde přes frontu v telefonu (bez signálu počká).
 */
@Composable
fun ReceiptsScreen(nav: Navigator, initial: List<ByteArray>) {
    val receipts = remember { mutableStateListOf<Receipt>().apply { addAll(initial.map { Receipt(listOf(it)) }) } }
    val thumbs = remember { mutableStateMapOf<Int, ImageBitmap>() }
    val blurry = remember { mutableStateMapOf<Int, Boolean>() }
    var direction by remember { mutableStateOf("purchase") }
    var note by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf("") }
    var menuFor by remember { mutableStateOf<Int?>(null) }
    var retakeIndex by remember { mutableStateOf<Int?>(null) }
    val scope = rememberCoroutineScope()

    // náhledy a kontrola ostrosti postupně na pozadí (velké fotky nedržet v paměti)
    LaunchedEffect(receipts.size, receipts.map { it.key }) {
        for (r in receipts.toList()) {
            if (thumbs.containsKey(r.key)) continue
            val first = r.pages.first()
            val result = withContext(Dispatchers.Default) {
                val bmp = decodeImage(first, 700)
                bmp to (bmp?.let { Sharpness.isBlurry(it) } ?: false)
            }
            result.first?.let { thumbs[r.key] = it }
            blurry[r.key] = result.second
        }
    }

    val more = rememberDocumentScanner(pageLimit = 30) { picked -> picked?.pages?.forEach { receipts.add(Receipt(listOf(it))) } }
    val retake = rememberDocumentScanner(pageLimit = 1) { picked ->
        val i = retakeIndex
        val page = picked?.pages?.firstOrNull()
        if (i != null && page != null && i in receipts.indices) receipts[i] = Receipt(listOf(page))
        retakeIndex = null
    }

    fun send() {
        busy = true
        scope.launch {
            var ok = 0
            val list = receipts.toList()
            list.forEachIndexed { i, r ->
                progress = "Ukládám ${i + 1} z ${list.size}…"
                val pdf = withContext(Dispatchers.Default) { Pdf.fromJpegs(r.pages) }
                val qr = r.pages.firstNotNullOfOrNull { img -> runCatching { readQrCodes(img) }.getOrDefault(emptyList()).firstNotNullOfOrNull { QrPayment.parse(it)?.raw } }
                val item = Outbox.enqueue(
                    original = pdf ?: r.pages.first(), originalMime = if (pdf != null) "application/pdf" else "image/jpeg",
                    originalName = if (pdf != null) "uctenka-${i + 1}.pdf" else "uctenka-${i + 1}.jpg",
                    processed = r.pages.first(), note = note.ifBlank { null }, direction = direction, qr = qr, docType = "uctenka",
                )
                progress = "Odesílám ${i + 1} z ${list.size}…"
                if (Outbox.send(item) != null) ok++
            }
            busy = false
            // co se neodeslalo, zůstává ve frontě – v Přehledu je karta „Čeká na odeslání“
            nav.pop()
        }
    }

    SimpleScreen(nav, if (receipts.size == 1) "1 účtenka" else "${receipts.size} ${if (receipts.size in 2..4) "účtenky" else "účtenek"}") {
        Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
            Text("Každá účtenka se uloží jako samostatný doklad (PDF). Klepnutím na náhled ji vyfotíte znovu, smažete nebo spojíte s předchozí.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 14.dp))
            receipts.chunked(3).forEachIndexed { row, chunk ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(bottom = 10.dp)) {
                    chunk.forEachIndexed { col, r ->
                        val index = row * 3 + col
                        val bad = blurry[r.key] == true
                        Box(Modifier.weight(1f)) {
                            Surface(
                                onClick = { menuFor = index }, shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(if (bad) 1.5.dp else 1.dp, if (bad) Brand.Amber else MaterialTheme.colorScheme.outlineVariant),
                            ) {
                                Column(Modifier.padding(8.dp)) {
                                    Box(Modifier.fillMaxWidth().aspectRatio(0.72f), contentAlignment = Alignment.Center) {
                                        val t = thumbs[r.key]
                                        if (t != null) Image(t, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                        else CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                                    }
                                    Text("Účtenka ${index + 1}", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
                                    Text(
                                        when {
                                            r.pages.size > 1 -> "${r.pages.size} fotky"
                                            blurry[r.key] == null -> "Kontroluji…"
                                            bad -> "Možná rozmazaná"
                                            else -> "Čitelná"
                                        },
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (bad) Brand.Amber else MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                            DropdownMenu(expanded = menuFor == index, onDismissRequest = { menuFor = null }) {
                                DropdownMenuItem(text = { Text("Vyfotit znovu") }, leadingIcon = { Icon(Icons.Outlined.CameraAlt, null) },
                                    onClick = { menuFor = null; retakeIndex = index; retake() })
                                if (index > 0) DropdownMenuItem(text = { Text("Spojit s předchozí") }, leadingIcon = { Icon(Icons.Outlined.CallMerge, null) },
                                    onClick = {
                                        menuFor = null
                                        val prev = receipts[index - 1]
                                        receipts[index - 1] = Receipt(prev.pages + r.pages)
                                        receipts.removeAt(index)
                                    })
                                DropdownMenuItem(text = { Text("Smazat", color = MaterialTheme.colorScheme.error) }, leadingIcon = { Icon(Icons.Outlined.Delete, null, tint = MaterialTheme.colorScheme.error) },
                                    onClick = { menuFor = null; receipts.removeAt(index); if (receipts.isEmpty()) nav.pop() })
                            }
                        }
                    }
                    repeat(3 - chunk.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            OutlinedButton(onClick = more, enabled = !busy, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                Icon(Icons.Outlined.Add, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text("Vyfotit další účtenky")
            }
            if (blurry.values.any { it }) {
                Spacer(Modifier.height(14.dp))
                Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Info, null, tint = Brand.Amber, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Text("Některá fotka vypadá rozmazaně. Zkontrolujte ji – nečitelný doklad účetní nezpracuje.", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(direction == "purchase", { direction = "purchase" }, SegmentedButtonDefaults.itemShape(0, 2)) { Text("Nákup") }
                SegmentedButton(direction == "sale", { direction = "sale" }, SegmentedButtonDefaults.itemShape(1, 2)) { Text("Prodej") }
            }
            Spacer(Modifier.height(14.dp))
            Field("Poznámka pro účetní (nepovinné)", note, { note = it }, singleLine = false)
            Spacer(Modifier.height(18.dp))
            Button(onClick = ::send, enabled = !busy && receipts.isNotEmpty(), modifier = Modifier.fillMaxWidth().height(52.dp)) {
                if (busy) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    Spacer(Modifier.width(10.dp)); Text(progress)
                } else {
                    Icon(Icons.Outlined.FileUpload, null, Modifier.size(20.dp)); Spacer(Modifier.width(10.dp))
                    Text(if (receipts.size == 1) "Odeslat 1 doklad" else "Odeslat ${receipts.size} ${if (receipts.size in 2..4) "doklady" else "dokladů"}")
                }
            }
            Text("Bez signálu se doklady uloží v telefonu a odešlou se samy.", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp).align(Alignment.CenterHorizontally))
        }
    }
}

/**
 * Hrubá kontrola ostrosti: rozptyl Laplaceova operátoru v 12 výsecích zmenšené fotky, rozhoduje nejostřejší výseč
 * (text). Je to jen upozornění – práh není vyladěný na všech telefonech, fotku vždy posoudí člověk.
 */
object Sharpness {
    private const val THRESHOLD = 60.0

    fun isBlurry(bitmap: ImageBitmap): Boolean {
        val w = bitmap.width; val h = bitmap.height
        if (w < 64 || h < 64) return false
        val tile = minOf(96, w / 4, h / 4)
        var best = 0.0
        for (ty in 0 until 4) for (tx in 0 until 3) {
            val x0 = (w - tile) * (tx + 1) / 4
            val y0 = (h - tile) * (ty + 1) / 5
            val px = IntArray(tile * tile)
            bitmap.readPixels(px, x0, y0, tile, tile)
            val g = DoubleArray(px.size) { val c = px[it]; 0.299 * ((c shr 16) and 255) + 0.587 * ((c shr 8) and 255) + 0.114 * (c and 255) }
            var sum = 0.0; var sum2 = 0.0; var n = 0
            for (y in 1 until tile - 1) for (x in 1 until tile - 1) {
                val i = y * tile + x
                val lap = 4 * g[i] - g[i - 1] - g[i + 1] - g[i - tile] - g[i + tile]
                sum += lap; sum2 += lap * lap; n++
            }
            val mean = sum / n
            best = maxOf(best, sum2 / n - mean * mean)
        }
        return best < THRESHOLD
    }
}
