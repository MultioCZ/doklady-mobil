package cz.multio.doklady.ui

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
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import cz.multio.doklady.Navigator
import cz.multio.doklady.Screen
import cz.multio.doklady.data.Api
import cz.multio.doklady.data.Customer
import cz.multio.doklady.data.Invoice
import cz.multio.doklady.data.InvoiceCreate
import cz.multio.doklady.data.InvoiceItem
import cz.multio.doklady.data.RecurringCreate
import cz.multio.doklady.platform.openPdf
import kotlinx.coroutines.launch
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.plus

// ------------------------------------------------------------------------ detail faktury

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvoiceDetailScreen(nav: Navigator, id: String) {
    var inv by remember { mutableStateOf<Invoice?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var info by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var sendDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun run(block: suspend () -> Unit) {
        busy = true; error = null; info = null
        scope.launch {
            try { block() } catch (e: Exception) { error = nav.errorText(e) } finally { busy = false }
        }
    }
    LaunchedEffect(id) { run { inv = Api.invoice(id) } }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(inv?.let { "Faktura ${it.number}" } ?: "Faktura") },
            navigationIcon = { IconButton(onClick = { nav.pop() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zpět") } },
        )
    }) { padding ->
        val i = inv
        if (i == null) {
            Box(Modifier.padding(padding)) { if (error != null) ErrorBanner(error) else Loading() }
            return@Scaffold
        }
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ErrorBanner(error)
            ErrorBanner(i.warning)
            info?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(money(i.total, i.currency), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                        StatusChip(i.status)
                    }
                    Spacer(Modifier.height(8.dp))
                    LabelValue("Odběratel", i.customer.name)
                    LabelValue("IČO / DIČ", listOfNotNull(i.customer.ico, i.customer.dic).joinToString(" / "))
                    LabelValue("Vystaveno", czDate(i.issueDate))
                    LabelValue("Splatnost", czDate(i.dueDate))
                    LabelValue("Var. symbol", i.variableSymbol)
                    if (i.reverseCharge) LabelValue("Režim", "Přenesená daňová povinnost")
                    i.sentTo?.let { LabelValue("Odesláno", "$it (${czDate(i.sentAt)})") }
                }
            }
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Položky", style = MaterialTheme.typography.titleSmall)
                    i.items.forEach { it ->
                        HorizontalDivider(Modifier.padding(vertical = 6.dp))
                        Text(it.description)
                        Text("${it.quantity.removeSuffix(".000").replace('.', ',')} ${it.unit ?: ""} × ${money(it.unitPrice, i.currency)}  ·  ${it.vatRate} %",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    HorizontalDivider(Modifier.padding(vertical = 6.dp))
                    LabelValue("Základ", money(i.totalBase, i.currency))
                    LabelValue("DPH", money(i.totalVat, i.currency))
                    Row { Text("Celkem", fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.42f))
                        Text(money(i.total, i.currency), fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.58f)) }
                }
            }
            OutlinedButton(enabled = !busy, modifier = Modifier.fillMaxWidth(),
                onClick = { run { openPdf(Api.invoicePdf(id), "faktura_${i.number}.pdf") } }) {
                Icon(Icons.Filled.PictureAsPdf, null); Spacer(Modifier.width(8.dp)); Text("Zobrazit PDF")
            }
            Button(enabled = !busy && i.status != "cancelled", modifier = Modifier.fillMaxWidth(), onClick = { sendDialog = true }) {
                Icon(Icons.AutoMirrored.Filled.Send, null); Spacer(Modifier.width(8.dp))
                Text(if (i.sentAt == null) "Odeslat e-mailem" else "Odeslat znovu")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (i.status != "paid" && i.status != "cancelled") {
                    OutlinedButton(enabled = !busy, modifier = Modifier.weight(1f),
                        onClick = { run { inv = Api.setInvoiceStatus(id, "paid"); info = "Označeno jako uhrazené." } }) {
                        Icon(Icons.Filled.Done, null); Spacer(Modifier.width(6.dp)); Text("Uhrazeno")
                    }
                }
                if (i.status != "cancelled" && i.status != "paid") {
                    OutlinedButton(enabled = !busy, modifier = Modifier.weight(1f),
                        onClick = { run { inv = Api.setInvoiceStatus(id, "cancelled"); info = "Faktura stornována – portál k ní vystavil opravný doklad (dobropis)." } }) {
                        Icon(Icons.Filled.Close, null); Spacer(Modifier.width(6.dp)); Text("Storno")
                    }
                }
            }
        }
    }

    if (sendDialog) {
        val i = inv!!
        var email by remember { mutableStateOf(i.sentTo ?: i.customer.email ?: "") }
        AlertDialog(
            onDismissRequest = { sendDialog = false },
            title = { Text("Odeslat fakturu") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Faktura ${i.number} se odešle jako PDF v příloze.")
                    Field("E-mail odběratele", email, { email = it }, keyboard = KeyboardType.Email)
                }
            },
            confirmButton = {
                Button(enabled = email.contains("@"), onClick = {
                    sendDialog = false
                    run { inv = Api.sendInvoice(id, email.trim()); info = "Odesláno na ${email.trim()}." }
                }) { Text("Odeslat") }
            },
            dismissButton = { TextButton(onClick = { sendDialog = false }) { Text("Zrušit") } },
        )
    }
}

// ------------------------------------------------------------------------ nová faktura

internal class ItemForm(desc: String = "", qty: String = "1", unit: String = "ks", price: String = "", vat: Int = 21) {
    var description by mutableStateOf(desc)
    var quantity by mutableStateOf(qty)
    var unit by mutableStateOf(unit)
    var price by mutableStateOf(price)
    var vat by mutableStateOf(vat)
    fun valid() = description.isNotBlank() && parseAmount(price) != null && parseAmount(quantity) != null
    fun base() = (parseAmount(quantity)?.toDouble() ?: 0.0) * (parseAmount(price)?.toDouble() ?: 0.0)
    fun toItem() = InvoiceItem(description = description.trim(), quantity = parseAmount(quantity) ?: "1",
        unit = unit.ifBlank { null }, unitPrice = parseAmount(price) ?: "0", vatRate = vat)
}

/** Stav formuláře žije mimo kompozici – přežije odskok na založení odběratele. */
class NewInvoiceState {
    val company = Api.cachedMe()?.companies?.firstOrNull { it.id == Api.companyId }
    val vatPayer = company?.vatPayer ?: true
    var customers by mutableStateOf<List<Customer>>(emptyList())
    var customer by mutableStateOf<Customer?>(null)
    internal val items = mutableStateListOf(ItemForm(vat = if (vatPayer) 21 else 0))
    var issueDate by mutableStateOf(czDate(today().toString()))
    var dueDays by mutableStateOf("14")
    var reverseCharge by mutableStateOf(false)
    var note by mutableStateOf("")
    var send by mutableStateOf(true)
    var sendTo by mutableStateOf("")
    var recurring by mutableStateOf(false)
    var interval by mutableStateOf("1")

    fun pick(c: Customer) {
        customer = c
        sendTo = c.email ?: ""
        c.dueDays?.let { dueDays = it.toString() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewInvoiceScreen(nav: Navigator, st: NewInvoiceState) {
    val vatPayer = st.vatPayer
    var customers by st::customers
    val customer = st.customer
    val items = st.items
    var issueDate by st::issueDate
    var dueDays by st::dueDays
    var reverseCharge by st::reverseCharge
    var note by st::note
    var send by st::send
    var sendTo by st::sendTo
    var recurring by st::recurring
    var interval by st::interval
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    fun pick(c: Customer) = st.pick(c)

    LaunchedEffect(Unit) {
        if (customers.isNotEmpty()) return@LaunchedEffect
        try { customers = Api.customers() } catch (e: Exception) { error = nav.errorText(e) }
    }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Nová faktura") },
            navigationIcon = { IconButton(onClick = { nav.pop() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zpět") } },
        )
    }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ErrorBanner(error)

            // --- odběratel
            Text("Odběratel", style = MaterialTheme.typography.titleSmall)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Dropdown(
                    "Vyberte odběratele",
                    customers.associate { it.id.toString() to (it.name + (it.ico?.let { ico -> " ($ico)" } ?: "")) },
                    customer?.id?.toString(),
                    { key -> customers.firstOrNull { it.id.toString() == key }?.let(::pick) },
                    Modifier.weight(1f),
                )
                IconButton(onClick = {
                    nav.push(Screen.CustomerEdit(null) { saved -> st.customers = st.customers + saved; st.pick(saved) })
                }) { Icon(Icons.Filled.PersonAdd, "Nový odběratel") }
            }

            // --- položky
            Text("Položky", style = MaterialTheme.typography.titleSmall)
            items.forEachIndexed { index, it ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Field("Popis", it.description, { v -> it.description = v }, Modifier.weight(1f))
                            if (items.size > 1) IconButton(onClick = { items.removeAt(index) }) { Icon(Icons.Filled.Close, "Odebrat") }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Field("Množství", it.quantity, { v -> it.quantity = v }, Modifier.weight(0.8f), KeyboardType.Decimal)
                            Field("Jedn.", it.unit, { v -> it.unit = v }, Modifier.weight(0.6f))
                            Field("Cena/j. bez DPH", it.price, { v -> it.price = v }, Modifier.weight(1.3f), KeyboardType.Decimal)
                        }
                        if (vatPayer && !reverseCharge) {
                            Dropdown("Sazba DPH", linkedMapOf("21" to "21 %", "12" to "12 %", "0" to "0 % (osvobozeno)"),
                                it.vat.toString(), { v -> it.vat = v.toInt() })
                        }
                    }
                }
            }
            TextButton(onClick = { items.add(ItemForm(vat = if (vatPayer) 21 else 0)) }) {
                Icon(Icons.Filled.Add, null); Text("Přidat položku")
            }

            // --- souhrn
            val base = items.sumOf { it.base() }
            val vat = if (!vatPayer || reverseCharge) 0.0 else items.sumOf { it.base() * it.vat / 100.0 }
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    LabelValue("Základ", money(base.toString()))
                    LabelValue("DPH", money(vat.toString()))
                    LabelValue("Celkem", money((base + vat).toString()))
                }
            }

            // --- podmínky
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Field("Datum vystavení", issueDate, { issueDate = it }, Modifier.weight(1f))
                Field("Splatnost (dní)", dueDays, { dueDays = it }, Modifier.weight(1f), KeyboardType.Number)
            }
            if (vatPayer) {
                SwitchRow("Přenesená daňová povinnost (§ 92a)", reverseCharge) { reverseCharge = it }
            }
            Field("Poznámka na faktuře", note, { note = it }, singleLine = false)

            // --- odeslání
            HorizontalDivider()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(send, { send = it })
                Text("Po vystavení odeslat e-mailem")
            }
            if (send) Field("E-mail odběratele", sendTo, { sendTo = it }, keyboard = KeyboardType.Email)

            SwitchRow("Opakovat fakturu (uložit jako šablonu)", recurring) { recurring = it }
            if (recurring) {
                Dropdown("Opakovat", linkedMapOf("1" to "každý měsíc", "3" to "každé čtvrtletí", "12" to "každý rok"),
                    interval, { interval = it })
                Text("Další faktury portál vystaví automaticky a pošle vám upozornění. Odběrateli je zatím odešlete sami tlačítkem Odeslat e-mailem.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            val canSubmit = customer != null && items.all { it.valid() } && !busy &&
                (!send || sendTo.contains("@")) && parseCzDate(issueDate) != null &&
                (!reverseCharge || !customer?.dic.isNullOrBlank())
            if (reverseCharge && customer != null && customer?.dic.isNullOrBlank()) {
                Text("Pro přenesenou daňovou povinnost musí mít odběratel DIČ.", color = MaterialTheme.colorScheme.error)
            }
            Button(
                enabled = canSubmit,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                onClick = {
                    busy = true; error = null
                    scope.launch {
                        try {
                            val c = customer!!
                            val list = items.map { it.toItem() }
                            val iso = parseCzDate(issueDate)!!
                            val invoice = Api.createInvoice(InvoiceCreate(
                                customerId = c.id, items = list, issueDate = iso, dueDays = dueDays.toIntOrNull(),
                                reverseCharge = reverseCharge, note = note.ifBlank { null },
                                sendTo = if (send) sendTo.trim() else null,
                            ))
                            if (recurring) {
                                val months = interval.toInt()
                                Api.createRecurring(RecurringCreate(
                                    customerId = c.id,
                                    name = list.first().description,
                                    intervalMonths = months,
                                    nextDate = kotlinx.datetime.LocalDate.parse(iso).plus(DatePeriod(months = months)).toString(),
                                    dueDays = dueDays.toIntOrNull(), reverseCharge = reverseCharge,
                                    note = note.ifBlank { null }, items = list,
                                    autoSend = send, emailTo = if (send) sendTo.trim() else null,
                                ))
                            }
                            nav.replace(Screen.InvoiceDetail(invoice.id))
                        } catch (e: Exception) {
                            error = nav.errorText(e)
                        } finally { busy = false }
                    }
                },
            ) { Text(if (send) "Vystavit a odeslat" else "Vystavit fakturu") }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, Modifier.weight(1f))
        Switch(checked, onChange)
    }
}

// ------------------------------------------------------------------------ odběratel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerEditScreen(nav: Navigator, initial: Customer?, onSaved: (Customer) -> Unit) {
    var ico by remember { mutableStateOf(initial?.ico ?: "") }
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var dic by remember { mutableStateOf(initial?.dic ?: "") }
    var street by remember { mutableStateOf(initial?.street ?: "") }
    var city by remember { mutableStateOf(initial?.city ?: "") }
    var zip by remember { mutableStateOf(initial?.zip ?: "") }
    var email by remember { mutableStateOf(initial?.email ?: "") }
    var dueDays by remember { mutableStateOf(initial?.dueDays?.toString() ?: "") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(if (initial == null) "Nový odběratel" else "Odběratel") },
            navigationIcon = { IconButton(onClick = { nav.pop() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zpět") } },
        )
    }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ErrorBanner(error)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Field("IČO", ico, { ico = it }, Modifier.weight(1f), KeyboardType.Number)
                Spacer(Modifier.width(8.dp))
                OutlinedButton(enabled = ico.length >= 6 && !busy, onClick = {
                    busy = true; error = null
                    scope.launch {
                        try {
                            val a = Api.ares(ico.trim())
                            a.name?.let { name = it }; dic = a.dic ?: ""; street = a.street ?: ""
                            city = a.city ?: ""; zip = a.zip ?: ""; a.ico?.let { ico = it }
                        } catch (e: Exception) { error = nav.errorText(e) } finally { busy = false }
                    }
                }) { Icon(Icons.Filled.Search, null); Spacer(Modifier.width(4.dp)); Text("ARES") }
            }
            Field("Název / jméno", name, { name = it })
            Field("DIČ", dic, { dic = it })
            Field("Ulice a číslo", street, { street = it })
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Field("PSČ", zip, { zip = it }, Modifier.weight(0.4f), KeyboardType.Number)
                Field("Obec", city, { city = it }, Modifier.weight(0.6f))
            }
            Field("E-mail pro zasílání faktur", email, { email = it }, keyboard = KeyboardType.Email)
            Field("Splatnost (dní) – výchozí", dueDays, { dueDays = it }, keyboard = KeyboardType.Number)
            Button(enabled = name.isNotBlank() && !busy, modifier = Modifier.fillMaxWidth().height(50.dp), onClick = {
                busy = true; error = null
                scope.launch {
                    try {
                        val saved = Api.saveCustomer(Customer(
                            id = initial?.id ?: 0, name = name.trim(), ico = ico.trim().ifBlank { null },
                            dic = dic.trim().uppercase().ifBlank { null }, street = street.ifBlank { null },
                            city = city.ifBlank { null }, zip = zip.ifBlank { null }, email = email.trim().ifBlank { null },
                            dueDays = dueDays.toIntOrNull(),
                        ))
                        nav.pop()
                        onSaved(saved)
                    } catch (e: Exception) { error = nav.errorText(e) } finally { busy = false }
                }
            }) { Text("Uložit odběratele") }
        }
    }
}
