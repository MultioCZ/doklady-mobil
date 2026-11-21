package cz.multio.doklady.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cz.multio.doklady.Navigator
import cz.multio.doklady.Screen
import cz.multio.doklady.data.Api
import cz.multio.doklady.data.Document
import cz.multio.doklady.data.DocumentPatch
import cz.multio.doklady.platform.decodeImage
import cz.multio.doklady.platform.openPdf
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Detail dokladu – jen ke čtení. Vytěžuje a opravuje webová aplikace (účetní),
 * klient tu vidí originál, upravený sken a vytěžené náležitosti a může připsat poznámku / dotaz.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentDetailScreen(nav: Navigator, id: String) {
    var doc by remember { mutableStateOf<Document?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var info by remember { mutableStateOf<String?>(null) }
    var note by remember { mutableStateOf("") }
    var showProcessed by remember { mutableStateOf(true) }
    var confirmDelete by remember { mutableStateOf(false) }
    val images = remember { mutableStateMapOf<String, ImageBitmap?>() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(id) {
        try {
            val d = Api.document(id)
            doc = d
            note = d.note ?: ""
        } catch (e: Exception) {
            error = nav.errorText(e)
        }
    }
    // dokud web doklad vytěžuje, obnovuj
    LaunchedEffect(doc?.status) {
        var tries = 0
        while (doc?.isWorking == true && tries++ < 90) {
            delay(3000)
            runCatching { Api.document(id) }.onSuccess { doc = it }
        }
    }
    LaunchedEffect(doc?.id, showProcessed) {
        val d = doc ?: return@LaunchedEffect
        val kind = if (showProcessed && d.hasProcessed) "processed" else "original"
        if (kind == "original" && d.isPdf) return@LaunchedEffect
        if (images.containsKey(kind)) return@LaunchedEffect
        images[kind] = runCatching { decodeImage(Api.documentFile(id, kind)) }.getOrNull()
    }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(doc?.supplierName ?: "Doklad") },
            navigationIcon = { IconButton(onClick = { nav.pop() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zpět") } },
            actions = {
                // smazat jde jen doklad, který účetní ještě nezkontrolovala
                if (doc != null && doc?.status != "checked") {
                    IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, "Smazat") }
                }
            },
        )
    }) { padding ->
        val d = doc
        if (d == null) {
            Box(Modifier.padding(padding)) { if (error != null) ErrorBanner(error) else Loading() }
            return@Scaffold
        }
        Column(Modifier.padding(padding).fillMaxSize().imePadding().verticalScroll(rememberScrollState())) {
            if (d.hasProcessed) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    SegmentedButton(showProcessed, { showProcessed = true }, SegmentedButtonDefaults.itemShape(0, 2)) { Text("Upravený") }
                    SegmentedButton(!showProcessed, { showProcessed = false }, SegmentedButtonDefaults.itemShape(1, 2)) { Text("Originál") }
                }
            }
            val kind = if (showProcessed && d.hasProcessed) "processed" else "original"
            Box(Modifier.fillMaxWidth().height(400.dp).background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center) {
                if (kind == "original" && d.isPdf) {
                    OutlinedButton(onClick = {
                        scope.launch {
                            runCatching { openPdf(Api.documentFile(id, "original"), d.originalFilename ?: "doklad_$id.pdf") }
                                .onFailure { error = it.message }
                        }
                    }) { Icon(Icons.Filled.PictureAsPdf, null); Spacer(Modifier.width(8.dp)); Text("Otevřít PDF originál") }
                } else if (!images.containsKey(kind)) {
                    CircularProgressIndicator()
                } else {
                    images[kind]?.let { ZoomableImage(it) } ?: Text("Náhled není k dispozici")
                }
            }

            ErrorBanner(error)
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                StatusChip(d.status)
                Spacer(Modifier.weight(1f))
                Text("nahráno ${czDate(d.createdAt)}", style = MaterialTheme.typography.bodySmall)
            }
            when {
                d.isWorking -> Column(Modifier.padding(horizontal = 16.dp)) {
                    Text("Účetní systém doklad zpracovává…")
                    LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
                }
                d.status == "error" -> ErrorBanner(d.error ?: "Doklad se nepodařilo přečíst – účetní ho zpracuje ručně.")
                d.status == "extracted" -> Hint("Vytěženo, čeká na kontrolu účetní." + (d.error?.let { "\n$it" } ?: ""))
            }

            if (!d.isWorking && d.status != "error") {
                Card(Modifier.fillMaxWidth().padding(16.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Náležitosti dokladu", style = MaterialTheme.typography.titleSmall)
                        HorizontalDivider(Modifier.padding(vertical = 8.dp))
                        LabelValue("Typ", d.docType?.let { docTypeNames[it] ?: it })
                        if (d.stage != "final") LabelValue("Druh v cestě", d.stageLabel ?: stageNames[d.stage])
                        if (!d.costCenter.isNullOrBlank()) LabelValue("Středisko", d.costCenter)
                        if (!d.job.isNullOrBlank()) LabelValue("Zakázka", d.job)
                        LabelValue("Dodavatel", d.supplierName)
                        LabelValue("IČO / DIČ", listOfNotNull(d.supplierIco, d.supplierDic).joinToString(" / "))
                        LabelValue("Adresa", d.supplierAddress)
                        LabelValue("Číslo dokladu", d.docNumber)
                        LabelValue("Var. symbol", d.variableSymbol)
                        LabelValue("Vystaveno", czDate(d.issueDate))
                        LabelValue("DUZP", czDate(d.taxableDate))
                        if (d.dueDate != null) LabelValue("Splatnost", czDate(d.dueDate))
                        LabelValue("Úhrada", d.paymentMethod?.let { paymentNames[it] ?: it })
                        if (d.reverseCharge == true) LabelValue("Režim", "Přenesená daňová povinnost")
                        HorizontalDivider(Modifier.padding(vertical = 8.dp))
                        VatBreakdown(d)
                        Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                            Text("Celkem", fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.42f))
                            Text(money(d.total, d.currency), fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.58f))
                        }
                    }
                }
            }

            d.accountingBlock?.let { ErrorBanner(it) }
            OutlinedButton(onClick = { nav.push(Screen.Chain(d.id)) }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Icon(Icons.Filled.AccountTree, null); Spacer(Modifier.width(8.dp))
                Text(if (d.chainId != null) "Cesta faktury" else "Cesta faktury – připojit objednávku, dodací list…")
            }
            Spacer(Modifier.height(12.dp))
            // poznámka / dotaz pro účetní – jediné, co klient v aplikaci mění
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Field("Poznámka nebo dotaz pro účetní", note, { note = it; info = null }, singleLine = false)
                info?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
                Button(
                    enabled = !busy && note != (d.note ?: ""),
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        busy = true; error = null
                        scope.launch {
                            try {
                                doc = Api.updateDocument(id, DocumentPatch(note = note))
                                info = "Poznámka uložena – účetní ji uvidí u dokladu."
                            } catch (e: Exception) {
                                error = nav.errorText(e)
                            } finally { busy = false }
                        }
                    },
                ) { Text("Uložit poznámku") }
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            "Smazat doklad?", "Doklad se smaže i z webové aplikace. Tuto akci nelze vrátit.", "Smazat",
            onConfirm = {
                scope.launch {
                    try { Api.deleteDocument(id); nav.pop() } catch (e: Exception) { error = nav.errorText(e) }
                }
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
private fun Hint(text: String) {
    Surface(color = Color(0xFFFFF8E1), modifier = Modifier.fillMaxWidth()) {
        Text(text, color = Color(0xFF6D4C00), modifier = Modifier.padding(12.dp))
    }
}

@Composable
private fun VatBreakdown(d: Document) {
    val rows = runCatching {
        (d.vatBreakdown as? kotlinx.serialization.json.JsonArray)?.map { el ->
            val o = el as kotlinx.serialization.json.JsonObject
            fun v(k: String) = (o[k] as? kotlinx.serialization.json.JsonPrimitive)?.content
            Triple(v("rate"), v("base"), v("vat"))
        }
    }.getOrNull().orEmpty()
    if (rows.isEmpty()) {
        LabelValue("Základ / DPH", listOf(money(d.totalBase, d.currency), money(d.totalVat, d.currency)).joinToString(" / "))
        return
    }
    Row(Modifier.fillMaxWidth()) {
        Text("Sazba", style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(0.6f))
        Text("Základ", style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
        Text("DPH", style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
    }
    rows.forEach { (rate, base, vat) ->
        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
            Text("${rate?.removeSuffix(".0") ?: "?"} %", Modifier.weight(0.6f))
            Text(money(base, d.currency), Modifier.weight(1f))
            Text(money(vat, d.currency), Modifier.weight(1f))
        }
    }
}

/** Obrázek s přiblížením dvěma prsty. */
@Composable
private fun ZoomableImage(img: ImageBitmap) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    Image(
        img, null,
        modifier = Modifier.fillMaxSize().clipToBounds()
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(1f, 6f)
                    offset = if (scale == 1f) Offset.Zero else offset + pan
                }
            }
            .graphicsLayer(scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y),
    )
}
