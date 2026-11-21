package cz.multio.doklady.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import cz.multio.doklady.Navigator
import cz.multio.doklady.Screen
import cz.multio.doklady.data.Api
import cz.multio.doklady.data.Dimensions
import cz.multio.doklady.data.Outbox
import cz.multio.doklady.platform.PickedDocument
import cz.multio.doklady.platform.decodeImage
import cz.multio.doklady.platform.readQrCodes
import cz.multio.doklady.data.QrPayment
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material3.Card
import kotlinx.coroutines.launch

/** Předvyplnění při nahrání: druh dokladu (např. chybějící dodací list), nákup/prodej, poznámka. */
data class UploadPreset(val stage: String = "", val direction: String = "purchase", val note: String = "")

/** Náhled naskenovaného dokladu před odesláním. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UploadScreen(nav: Navigator, picked: PickedDocument, preset: UploadPreset = UploadPreset()) {
    var note by remember { mutableStateOf(preset.note) }
    var stage by remember { mutableStateOf(preset.stage) }
    var direction by remember { mutableStateOf(preset.direction) }
    var centre by remember { mutableStateOf("") }
    var job by remember { mutableStateOf("") }
    var dims by remember { mutableStateOf(Dimensions()) }
    LaunchedEffect(Unit) { runCatching { Api.dimensions() }.onSuccess { dims = it } }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    // „Za co to je?“ (portál 1.15) – jen u nákupu a když to balíček obsahuje
    val categories = rememberCategories()
    var categoryId by remember { mutableStateOf<Int?>(null) }
    val scope = rememberCoroutineScope()
    val previews = remember(picked) {
        val images = picked.pages.ifEmpty {
            listOfNotNull(picked.rawFile?.takeIf { picked.rawMime?.startsWith("image/") == true })
        }
        images.mapNotNull { decodeImage(it) }
    }
    // QR platba na faktuře – přečte se v telefonu, portál po vytěžení doplní účet, VS, splatnost a částku
    var qr by remember { mutableStateOf<QrPayment?>(null) }
    LaunchedEffect(picked) {
        val images = picked.pages.ifEmpty {
            listOfNotNull(picked.rawFile?.takeIf { picked.rawMime?.startsWith("image/") == true })
        }
        for (img in images.take(4)) {
            val found = runCatching { readQrCodes(img) }.getOrDefault(emptyList()).firstNotNullOfOrNull { QrPayment.parse(it) }
            if (found != null) { qr = found; break }
        }
    }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(if (picked.isScan) "Naskenovaný doklad" else "Vybraný soubor") },
            navigationIcon = { IconButton(onClick = { nav.pop() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zpět") } },
        )
    }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().imePadding().verticalScroll(rememberScrollState())) {
            Box(Modifier.height(360.dp).fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant)) {
                if (previews.isNotEmpty()) {
                    LazyRow(
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        itemsIndexed(previews) { i, img ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Image(img, "Strana ${i + 1}", contentScale = ContentScale.Fit,
                                    modifier = Modifier.fillMaxHeight(0.92f).width(if (previews.size == 1) 340.dp else 260.dp))
                                Text("Strana ${i + 1} / ${previews.size}", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                } else {
                    Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Filled.PictureAsPdf, null, Modifier.size(72.dp))
                        Text(picked.fileName ?: "PDF dokument")
                    }
                }
            }
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (!picked.isScan && picked.rawMime?.startsWith("image/") == true) {
                    Text("Fotka se na serveru automaticky ořízne a vyrovná se osvětlení. Pro nejlepší výsledek použijte Skenovat.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SegmentedButton(direction == "purchase", { direction = "purchase" }, SegmentedButtonDefaults.itemShape(0, 2)) { Text("Nákup") }
                    SegmentedButton(direction == "sale", { direction = "sale" }, SegmentedButtonDefaults.itemShape(1, 2)) { Text("Prodej") }
                }
                Dropdown("Druh dokladu", linkedMapOf("" to "Rozpoznat automaticky") + stageNames, stage, { stage = it })
                if (dims.centres.isNotEmpty() || dims.jobs.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (dims.centres.isNotEmpty()) {
                            Dropdown("Středisko", linkedMapOf("" to "—") + dims.centres.associate { it.code to listOf(it.code, it.name).filter { x -> x.isNotBlank() }.joinToString(" · ") },
                                centre, { centre = it }, modifier = Modifier.weight(1f))
                        }
                        if (dims.jobs.isNotEmpty()) {
                            Dropdown("Zakázka", linkedMapOf("" to "—") + dims.jobs.associate { it.code to listOf(it.code, it.name).filter { x -> x.isNotBlank() }.joinToString(" · ") },
                                job, { job = it }, modifier = Modifier.weight(1f))
                        }
                    }
                }
                qr?.let { q ->
                    Card(Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.QrCode2, null)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text("QR platba nalezena", style = MaterialTheme.typography.titleSmall)
                                Text(listOfNotNull(
                                    q.amount?.let { money(it, q.currency ?: "CZK") },
                                    q.vs?.let { "VS $it" },
                                    q.due?.let { "splatnost ${czDate(it)}" },
                                ).joinToString("  ·  "), style = MaterialTheme.typography.bodyMedium)
                                Text("Po vytěžení doplní prázdný účet, VS, splatnost a částku. Jiná částka než na dokladu = upozornění pro účetní.",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                if (categories?.enabled == true && direction == "purchase" && categories.options.isNotEmpty()) {
                    CategoryChips(categories, categoryId) { categoryId = it }
                }
                Field("Poznámka pro účetní (nepovinné)", note, { note = it }, singleLine = false)
                message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Row {
                    Button(
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        onClick = {
                            busy = true
                            scope.launch {
                                val (original, mime, name, processed) = payload(picked)
                                // nejdřív uložit do telefonu, pak odeslat – bez signálu se nic neztratí
                                val item = Outbox.enqueue(original, mime, name, processed, note.ifBlank { null },
                                    stage = stage.ifBlank { null }, costCenter = centre.ifBlank { null },
                                    job = job.ifBlank { null }, direction = direction, qr = qr?.raw,
                                    categoryId = categoryId.takeIf { direction == "purchase" })
                                val doc = Outbox.send(item)
                                busy = false
                                if (doc != null) {
                                    nav.replace(Screen.DocumentDetail(doc.id))
                                } else {
                                    message = "Nepodařilo se odeslat – doklad je uložený v telefonu a odešle se později."
                                    nav.pop()
                                }
                            }
                        },
                    ) {
                        if (busy) {
                            CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(12.dp))
                            Text("Odesílám…")
                        } else {
                            Text("Odeslat do účetnictví")
                        }
                    }
                }
            }
        }
    }
}

internal data class Payload(val original: ByteArray, val mime: String, val name: String, val processed: ByteArray?)

/**
 * Sken: originál = PDF se všemi stranami (archiv, vytěžení více stran), upravený = 1. strana JPEG (náhled).
 * Soubor: posílá se tak, jak je – server fotku sám upraví.
 */
internal fun payload(p: PickedDocument): Payload = when {
    p.isScan && p.pdf != null -> Payload(p.pdf, "application/pdf", "sken.pdf", p.pages.first())
    p.isScan -> Payload(p.pages.first(), "image/jpeg", "sken.jpg", p.pages.first())
    p.pdf != null -> Payload(p.pdf, "application/pdf", p.fileName ?: "doklad.pdf", null)
    else -> Payload(p.rawFile ?: ByteArray(0), p.rawMime ?: "image/jpeg", p.fileName ?: "foto.jpg", null)
}
