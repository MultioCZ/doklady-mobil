package cz.multio.doklady.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import cz.multio.doklady.data.Api
import cz.multio.doklady.data.AssistantTurn
import cz.multio.doklady.data.CompanyProfileData
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

// ------------------------------------------------------------------------ údaje firmy

/** Údaje firmy (dodavatele) pro faktury – stejné jako na webu v Údajích firmy. Účet / IBAN = QR platba na fakturách. */
@Composable
fun CompanyProfileScreen(nav: Navigator) {
    var loaded by remember { mutableStateOf<CompanyProfileData?>(null) }
    var name by remember { mutableStateOf("") }
    var ico by remember { mutableStateOf("") }
    var dic by remember { mutableStateOf("") }
    var street by remember { mutableStateOf("") }
    var city by remember { mutableStateOf("") }
    var zip by remember { mutableStateOf("") }
    var account by remember { mutableStateOf("") }
    var iban by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var due by remember { mutableStateOf("14") }
    var registry by remember { mutableStateOf("") }
    var vatPayer by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var saved by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        runCatching { Api.companyProfile() }.onSuccess { p ->
            loaded = p
            name = p.name.orEmpty(); ico = p.ico.orEmpty(); dic = p.dic.orEmpty(); street = p.street.orEmpty(); city = p.city.orEmpty(); zip = p.zip.orEmpty()
            account = p.bankAccount.orEmpty(); iban = p.iban.orEmpty(); email = p.email.orEmpty(); due = (p.defaultDueDays ?: 14).toString()
            registry = p.registryNote.orEmpty(); vatPayer = p.vatPayer
        }.onFailure { error = nav.errorText(it) }
    }

    SimpleScreen(nav, "Údaje firmy") {
        val p = loaded
        if (p == null) {
            Column(Modifier.padding(20.dp)) { ErrorBanner(error); if (error == null) Box(Modifier.fillMaxWidth().height(240.dp)) { Loading() } }
            return@SimpleScreen
        }
        Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Použijí se jako dodavatel na nových fakturách (na webu i v aplikaci).", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            SectionTitle("Platba na faktuře", Modifier.padding(top = 0.dp))
            Field("Číslo účtu", account, { account = it; saved = null }, placeholder = "123456789/0100")
            Field("IBAN (nepovinné)", iban, { iban = it.uppercase(); saved = null }, placeholder = "CZ65 0800 0000 1920 0014 5399")
            Text(if (p.qrReady) "QR platba se na fakturách vytvoří." else "Vyplňte číslo účtu – bez něj nejde vystavit fakturu převodem ani vytvořit QR platbu.",
                style = MaterialTheme.typography.bodySmall, color = if (p.qrReady) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
            Field("Výchozí splatnost (dní)", due, { due = it.filter(Char::isDigit) }, keyboard = KeyboardType.Number)
            Field("E-mail pro faktury", email, { email = it }, keyboard = KeyboardType.Email)
            SectionTitle("Firma")
            Field("Název firmy / jméno", name, { name = it })
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Field("IČO", ico, { ico = it.filter(Char::isDigit).take(8) }, Modifier.weight(1f), KeyboardType.Number)
                Field("DIČ", dic, { dic = it.uppercase() }, Modifier.weight(1f))
            }
            Field("Ulice a číslo", street, { street = it })
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Field("PSČ", zip, { zip = it }, Modifier.weight(0.4f), KeyboardType.Number)
                Field("Obec", city, { city = it }, Modifier.weight(0.6f))
            }
            Field("Zápis v rejstříku / živnostenský úřad", registry, { registry = it })
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Plátce DPH", style = MaterialTheme.typography.bodyLarge)
                    Text("Faktury pak obsahují DPH a DUZP.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(vatPayer, { vatPayer = it })
            }
            error?.let { ErrorBanner(it) }
            saved?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
            Button(enabled = !busy && name.isNotBlank(), modifier = Modifier.fillMaxWidth().height(52.dp), onClick = {
                busy = true; error = null
                scope.launch {
                    try {
                        val r = Api.saveCompanyProfile(buildJsonObject {
                            put("name", JsonPrimitive(name.trim())); put("ico", JsonPrimitive(ico)); put("dic", JsonPrimitive(dic.replace(" ", "")))
                            put("street", JsonPrimitive(street.trim())); put("city", JsonPrimitive(city.trim())); put("zip", JsonPrimitive(zip.trim()))
                            put("bank_account", JsonPrimitive(account.trim())); put("iban", JsonPrimitive(iban.replace(" ", "")))
                            put("email", JsonPrimitive(email.trim())); put("default_due_days", JsonPrimitive(due.toIntOrNull() ?: 14))
                            put("registry_note", JsonPrimitive(registry.trim())); put("vat_payer", JsonPrimitive(vatPayer))
                        })
                        loaded = r
                        saved = "Uloženo."
                    } catch (e: Exception) {
                        error = nav.errorText(e)
                    }
                    busy = false
                }
            }) { if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary) else Text("Uložit") }
        }
    }
}

// ------------------------------------------------------------------------ AI asistent

/** Asistent (balíček Expert): otázky na vlastní čísla; právní otázky jedním klepnutím účetní. Konverzace je stejná jako na webu. */
@Composable
fun AssistantScreen(nav: Navigator) {
    val turns = remember { mutableStateListOf<AssistantTurn>() }
    var suggestions by remember { mutableStateOf<List<String>>(emptyList()) }
    var question by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var info by remember { mutableStateOf<String?>(null) }
    var loaded by remember { mutableStateOf(false) }
    val list = rememberLazyListState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        runCatching { Api.assistant() }.onSuccess { turns.clear(); turns.addAll(it.turns); suggestions = it.suggestions }.onFailure { error = nav.errorText(it) }
        loaded = true
    }
    LaunchedEffect(turns.size) { if (turns.isNotEmpty()) list.animateScrollToItem(turns.lastIndex) }

    fun ask(q: String) {
        val text = q.trim()
        if (text.isEmpty() || busy) return
        busy = true; error = null; info = null; question = ""
        scope.launch {
            try {
                val r = Api.assistantAsk(text)
                turns.add(r.turn); suggestions = r.suggestions
            } catch (e: Exception) {
                question = text; error = nav.errorText(e)
            }
            busy = false
        }
    }

    fun forward(q: String) {
        scope.launch {
            runCatching { Api.assistantForward(q) }.onSuccess { info = it.detail }.onFailure { error = nav.errorText(it) }
        }
    }

    SimpleScreen(nav, "Asistent", actions = {
        IconButton(onClick = { scope.launch { runCatching { Api.assistantNew() }.onSuccess { turns.clear(); suggestions = it.suggestions } } }) {
            Icon(Icons.Outlined.Refresh, "Nová konverzace")
        }
    }) {
        Column(Modifier.fillMaxSize().imePadding()) {
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = list, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (loaded && turns.isEmpty()) {
                    item {
                        AppCard {
                            Column(Modifier.padding(16.dp)) {
                                Text("Zeptejte se na svá čísla", style = MaterialTheme.typography.titleMedium)
                                Text("Třeba kolik jste letos utratili za PHM, jaké máte tržby nebo co máte zaplatit. Odpovídám z vašich dokladů a plateb v portálu. Na otázky, co se smí nebo musí, odpoví účetní.",
                                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                            }
                        }
                    }
                }
                items(turns, key = { it.id }) { t -> TurnView(t, onAsk = ::ask, onForward = { forward(t.question) }) }
                if (busy) item { Row(Modifier.padding(8.dp)) { CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) } }
                info?.let { item { Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium) } }
                error?.let { item { ErrorBanner(it) } }
            }
            if (suggestions.isNotEmpty()) {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(suggestions) { s -> AssistChip(onClick = { ask(s) }, label = { Text(s) }) }
                }
            }
            Surface(color = MaterialTheme.colorScheme.surface) {
                Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(question, { question = it }, placeholder = { Text("Napište otázku…") }, modifier = Modifier.weight(1f),
                        shape = MaterialTheme.shapes.extraLarge, maxLines = 3)
                    IconButton(onClick = { ask(question) }, enabled = question.isNotBlank() && !busy) { Icon(Icons.AutoMirrored.Outlined.Send, "Odeslat") }
                }
            }
        }
    }
}

@Composable
private fun TurnView(t: AssistantTurn, onAsk: (String) -> Unit, onForward: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
            Text(t.question, color = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.widthIn(max = 300.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp)).padding(horizontal = 14.dp, vertical = 10.dp))
        }
        Spacer(Modifier.height(8.dp))
        AppCard(Modifier.widthIn(max = 360.dp)) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                when {
                    t.legal -> {
                        Text("Na tohle se zeptejte účetní.", fontWeight = FontWeight.SemiBold)
                        Text("Otázka je o tom, co se smí nebo musí – odpověď vám má dát účetní, ne automat.", style = MaterialTheme.typography.bodyMedium)
                        Button(onClick = onForward) { Text("Poslat otázku účetní") }
                    }
                    t.help -> {
                        Text("Této otázce nerozumím. Zkuste ji napsat jinak, třeba:", style = MaterialTheme.typography.bodyMedium)
                        t.examples.take(4).forEach { e -> TextButton(onClick = { onAsk(e) }) { Text(e) } }
                        TextButton(onClick = onForward) { Text("Poslat otázku účetní") }
                    }
                    else -> {
                        t.understood?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        t.title?.let { Text(it, fontWeight = FontWeight.SemiBold) }
                        t.answer?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
                        if (t.rows.isNotEmpty()) {
                            t.rows.take(15).forEachIndexed { i, r ->
                                if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                                    Column(Modifier.weight(1f)) {
                                        Text(r.label, style = MaterialTheme.typography.bodyMedium)
                                        r.note?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                    }
                                    r.amount?.let { Text(money(it), style = AmountStyle) }
                                }
                            }
                            if (t.total != null && t.rows.size > 1) {
                                HorizontalDivider()
                                Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                                    Text("Celkem" + if (t.net) " bez DPH" else "", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                    Text(money(t.total), style = AmountStyle)
                                }
                            }
                        }
                        t.note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        TextButton(onClick = onForward, contentPadding = PaddingValues(0.dp)) { Text("Nesedí to? Poslat účetní") }
                    }
                }
            }
        }
    }
}
