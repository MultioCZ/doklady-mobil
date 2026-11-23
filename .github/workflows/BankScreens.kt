package cz.multio.doklady.ui

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cz.multio.doklady.Navigator
import cz.multio.doklady.Screen
import cz.multio.doklady.data.Api
import cz.multio.doklady.data.BankAnswerOption
import cz.multio.doklady.data.BankItem
import cz.multio.doklady.data.BankList
import cz.multio.doklady.data.Pdf
import cz.multio.doklady.data.QrPayment
import cz.multio.doklady.platform.PickedDocument
import cz.multio.doklady.platform.readQrCodes
import cz.multio.doklady.platform.rememberDocumentScanner
import cz.multio.doklady.platform.rememberFilePicker
import cz.multio.doklady.platform.rememberStatementPicker
import kotlinx.coroutines.launch
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus

private val MONTHS = listOf("Leden", "Únor", "Březen", "Duben", "Květen", "Červen", "Červenec", "Srpen", "Září", "Říjen", "Listopad", "Prosinec")

private fun stateTone(state: String) = when (state) {
    "missing" -> Tone.Amber
    "paired", "not_needed" -> Tone.Primary
    "waiting" -> Tone.Neutral
    else -> Tone.Neutral
}

private fun stateIcon(state: String) = when (state) {
    "missing" -> Icons.Outlined.WarningAmber
    "paired", "not_needed" -> Icons.Outlined.Check
    "waiting" -> Icons.Outlined.HourglassEmpty
    "open" -> Icons.Outlined.Remove
    else -> Icons.Outlined.HelpOutline
}

/** „-8470.00“ → „−8 470,00“ (s mínusem, bez měny pro CZK). */
fun signedMoney(amount: String, currency: String = "CZK"): String {
    val negative = amount.startsWith("-")
    val text = money(amount.removePrefix("-"), currency)
    return (if (negative) "−" else "+") + if (currency == "CZK") text.removeSuffix(" Kč") else text
}

// ------------------------------------------------------------------------ seznam pohybů

@Composable
fun BankTab(nav: Navigator, initialFilter: String = "all", onMissing: (Int) -> Unit = {}) {
    var month by rememberSaveable { mutableStateOf(LocalDate(today().year, today().monthNumber, 1).toString()) }
    var filter by rememberSaveable { mutableStateOf(initialFilter) }
    var data by remember { mutableStateOf<BankList?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var refreshing by remember { mutableStateOf(false) }
    var uploadResult by remember { mutableStateOf<String?>(null) }
    var uploading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val first = LocalDate.parse(month)

    suspend fun load() {
        try {
            error = null
            val d = Api.bank("${first.year}-${first.monthNumber.toString().padStart(2, '0')}", filter)
            data = d
            onMissing(d.missingTotal)
        } catch (e: Exception) {
            error = nav.errorText(e)
        }
    }
    LaunchedEffect(month, filter) { data = null; load() }

    val statementPicker = rememberStatementPicker { files ->
        if (files.isEmpty()) return@rememberStatementPicker
        uploading = true
        scope.launch {
            uploadResult = try {
                val r = Api.uploadStatement(files.map { Triple(it.data, it.mime, it.name) })
                r.messages.joinToString("\n") { it.text }.ifBlank { if (r.ok) "Výpis nahrán." else "Výpis se nepodařilo načíst." }
            } catch (e: Exception) {
                nav.errorText(e)
            }
            uploading = false
            load()
        }
    }

    PullToRefreshBox(isRefreshing = refreshing, onRefresh = { scope.launch { refreshing = true; load(); refreshing = false } }, modifier = Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp)) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)) {
                    Text("Banka", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                    if (uploading) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    else IconButton(onClick = statementPicker) { Icon(Icons.Outlined.FileUpload, "Nahrát výpis") }
                }
                ErrorBanner(error) { scope.launch { load() } }
                val account = data?.accounts?.firstOrNull()
                AppCard {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        FilledTonalIconButton(onClick = { month = first.minus(DatePeriod(months = 1)).toString() }) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Předchozí měsíc")
                        }
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("${MONTHS[first.monthNumber - 1]} ${first.year}", style = MaterialTheme.typography.titleMedium)
                            Text(account?.let { listOfNotNull(if (it.source == "fio") "Fio · stahuje se samo" else "Nahraný výpis", it.updatedAt?.let { u -> "aktualizováno ${czDate(u)}" }).joinToString(" · ") }
                                ?: "Žádný výpis", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        val current = LocalDate(today().year, today().monthNumber, 1)
                        FilledTonalIconButton(onClick = { month = first.plus(DatePeriod(months = 1)).toString() }, enabled = first < current) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Další měsíc")
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    val missing = data?.missingTotal ?: 0
                    listOf("all" to "Vše", "missing" to (if (missing > 0) "Bez dokladu · $missing" else "Bez dokladu"), "paired" to "Vyřešené").forEachIndexed { i, (key, label) ->
                        SegmentedButton(filter == key, { filter = key }, SegmentedButtonDefaults.itemShape(i, 3), icon = {}) {
                            Text(label, fontSize = 13.sp, maxLines = 1)
                        }
                    }
                }
                uploadResult?.let {
                    Spacer(Modifier.height(12.dp))
                    AppCard { Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(14.dp)) }
                }
                Spacer(Modifier.height(12.dp))
            }
            val list = data?.items
            item {
                when {
                    list == null && error == null -> Box(Modifier.fillMaxWidth().height(240.dp)) { Loading() }
                    list.isNullOrEmpty() -> AppCard {
                        ListRow(
                            if (filter == "missing") "Žádné platby bez dokladu" else "Žádné pohyby",
                            if (filter == "missing") "Za tento měsíc účetní nic nepotřebuje." else "Nahrajte výpis tlačítkem vpravo nahoře, nebo se domluvte s kanceláří na automatickém stahování z Fio.",
                            Icons.Outlined.Check, Tone.Primary,
                        )
                    }
                    else -> AppCard {
                        list.forEachIndexed { i, it ->
                            ListRow(
                                it.counterparty ?: it.description ?: if (it.isOutgoing) "Platba" else "Příjem",
                                listOfNotNull(czDate(it.date).removeSuffix(" ${first.year}"), it.variableSymbol?.let { vs -> "VS $vs" }, it.answer).joinToString(" · "),
                                stateIcon(it.state), stateTone(it.state), divider = i > 0,
                                onClick = { nav.push(Screen.BankItemDetail(it.id)) },
                                trailing = {
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(signedMoney(it.amount, it.currency), style = AmountStyle,
                                            color = if (it.isOutgoing) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary)
                                        if (it.state == "missing") Text("Chybí doklad", style = MaterialTheme.typography.labelSmall, color = Brand.Amber)
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------------ detail pohybu

@Composable
fun BankItemScreen(nav: Navigator, id: String) {
    var item by remember { mutableStateOf<BankItem?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var noteFor by remember { mutableStateOf<BankAnswerOption?>(null) }
    var note by remember { mutableStateOf("") }
    var done by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(id) { runCatching { Api.bankItem(id) }.onSuccess { item = it }.onFailure { error = nav.errorText(it) } }

    fun upload(picked: PickedDocument) {
        busy = true; error = null
        scope.launch {
            try {
                val pages = picked.pages
                val (data, mime, name) = when {
                    pages.isNotEmpty() -> Triple(picked.pdf ?: Pdf.fromJpegs(pages) ?: pages.first(), if (picked.pdf != null || Pdf.jpegInfo(pages.first()) != null) "application/pdf" else "image/jpeg", "doklad-k-platbe.pdf")
                    picked.pdf != null -> Triple(picked.pdf, "application/pdf", picked.fileName ?: "doklad.pdf")
                    else -> Triple(picked.rawFile ?: ByteArray(0), picked.rawMime ?: "image/jpeg", picked.fileName ?: "doklad.jpg")
                }
                val images = pages.ifEmpty { listOfNotNull(picked.rawFile?.takeIf { picked.rawMime?.startsWith("image/") == true }) }
                val qr = images.take(3).firstNotNullOfOrNull { img -> runCatching { readQrCodes(img) }.getOrDefault(emptyList()).firstNotNullOfOrNull { QrPayment.parse(it)?.raw } }
                item = Api.bankDocument(id, data, mime, name, pages.firstOrNull(), qr)
                done = "Doklad je připojený k platbě. Účetní ho zkontroluje."
            } catch (e: Exception) {
                error = nav.errorText(e)
            }
            busy = false
        }
    }
    val scanner = rememberDocumentScanner { picked -> picked?.let { upload(it) } }
    val picker = rememberFilePicker { picked -> picked?.let { upload(it) } }

    fun answer(option: BankAnswerOption, text: String?) {
        busy = true; error = null
        scope.launch {
            try {
                item = Api.bankAnswer(id, option.id, text)
                done = if (option.noDocument) "Uloženo: ${option.label}. Doklad už není potřeba." else "Uloženo: ${option.label}."
            } catch (e: Exception) {
                error = nav.errorText(e)
            }
            busy = false
        }
    }

    SimpleScreen(nav, "Platba") {
        val it = item
        if (it == null) {
            Column(Modifier.padding(20.dp)) { ErrorBanner(error); if (error == null) Box(Modifier.fillMaxWidth().height(300.dp)) { Loading() } }
            return@SimpleScreen
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
            AppCard {
                Column(Modifier.padding(16.dp)) {
                    Tag(if (it.state == "missing") "Chybí doklad" else it.stateLabel, stateTone(it.state))
                    Text(signedMoney(it.amount, it.currency) + if (it.currency == "CZK") " Kč" else "",
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold), modifier = Modifier.padding(top = 10.dp))
                    Text(it.counterparty ?: it.description ?: "", style = MaterialTheme.typography.titleMedium)
                }
                KeyValue("Datum", czDate(it.date))
                if (it.variableSymbol != null) KeyValue("Variabilní symbol", it.variableSymbol)
                if (it.counterpartyAccount != null) KeyValue("Protiúčet", it.counterpartyAccount)
                if (!it.description.isNullOrBlank() && it.description != it.counterparty) KeyValue("Popis", it.descriptionFull ?: it.description)
                if (it.documentLabel != null) KeyValue("Doklad", it.documentLabel)
                if (it.answer != null) KeyValue("Vaše odpověď", listOfNotNull(it.answer, it.answerNote).joinToString(" – "))
            }
            done?.let { Spacer(Modifier.height(12.dp)); AppCard { ListRow(it, null, Icons.Outlined.Check, Tone.Primary) } }
            error?.let { Spacer(Modifier.height(12.dp)); ErrorBanner(it) }
            if (it.documentId != null) {
                Spacer(Modifier.height(16.dp))
                OutlinedButton(onClick = { nav.push(Screen.DocumentDetail(it.documentId)) }, modifier = Modifier.fillMaxWidth().height(48.dp)) { Text("Otevřít doklad") }
            }
            if (it.needsDocument && it.answer == null) {
                Spacer(Modifier.height(18.dp))
                Button(onClick = scanner, enabled = !busy, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    if (busy) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    else { Icon(Icons.Outlined.CameraAlt, null, Modifier.size(20.dp)); Spacer(Modifier.width(10.dp)); Text("Vyfotit doklad") }
                }
                Spacer(Modifier.height(10.dp))
                OutlinedButton(onClick = picker, enabled = !busy, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    Icon(Icons.Outlined.FolderOpen, null, Modifier.size(20.dp)); Spacer(Modifier.width(10.dp)); Text("Vybrat soubor")
                }
                if (it.answers.isNotEmpty()) {
                    SectionTitle("Nebo odpovězte")
                    AppCard {
                        it.answers.forEachIndexed { i, a ->
                            ListRow(a.label, if (a.noDocument) "Doklad nebude potřeba" else if (a.noteRequired) "Napíšete krátkou poznámku" else null, divider = i > 0,
                                onClick = { if (!busy) { if (a.noteRequired) { noteFor = a; note = "" } else answer(a, null) } })
                        }
                    }
                }
            } else if (it.answer != null && it.documentId == null) {
                Spacer(Modifier.height(16.dp))
                TextButton(onClick = {
                    busy = true
                    scope.launch { runCatching { Api.bankClearAnswer(id) }.onSuccess { r -> item = r; done = null }.onFailure { e -> error = nav.errorText(e) }; busy = false }
                }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Zrušit odpověď") }
            }
            if (it.events.isNotEmpty()) {
                SectionTitle("Historie")
                AppCard {
                    it.events.forEachIndexed { i, e -> ListRow(e.message, czDate(e.at), divider = i > 0) }
                }
            }
        }
    }

    noteFor?.let { option ->
        AlertDialog(
            onDismissRequest = { noteFor = null },
            title = { Text(option.label) },
            text = { Field("Poznámka pro účetní", note, { note = it }, singleLine = false) },
            confirmButton = { Button(enabled = note.isNotBlank(), onClick = { noteFor = null; answer(option, note.trim()) }) { Text("Odeslat") } },
            dismissButton = { TextButton(onClick = { noteFor = null }) { Text("Zrušit") } },
        )
    }
}
