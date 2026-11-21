package cz.multio.doklady.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Login
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cz.multio.doklady.Navigator
import cz.multio.doklady.Screen
import cz.multio.doklady.data.Api
import cz.multio.doklady.data.ApplyCompany
import cz.multio.doklady.data.ApplyOptions
import cz.multio.doklady.data.ApplyResult
import cz.multio.doklady.data.Choice
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

// ------------------------------------------------------------------------ úvod

/** Úvodní obrazovka bez přihlášení (1.8): stávající klient → přihlášení, nový → žádost. */
@Composable
fun WelcomeScreen(nav: Navigator) {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        Column(
            Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 28.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(48.dp))
            BrandLogo(Modifier.width(220.dp))
            Spacer(Modifier.height(44.dp))
            Column(Modifier.widthIn(max = 420.dp).fillMaxWidth()) {
                Text("Vítejte", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Text("Doklady, banka a komunikace s účetní na jednom místě.", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp, bottom = 22.dp))
                BigChoice("Jsem klient", "Přihlásit se – stejné údaje jako do portálu", Icons.AutoMirrored.Outlined.Login, primary = true) {
                    nav.push(Screen.Login())
                }
                Spacer(Modifier.height(12.dp))
                BigChoice("Chci se stát klientem", "Vedení účetnictví nebo jen daňové přiznání", Icons.Outlined.PersonAdd) {
                    nav.push(Screen.Apply)
                }
            }
            Spacer(Modifier.height(28.dp))
            Text("Multio CZ s.r.o. · Poradce v oblasti účetnictví", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun BigChoice(title: String, text: String, icon: ImageVector, primary: Boolean = false, selected: Boolean = primary, onClick: () -> Unit) {
    Surface(
        onClick = onClick, shape = RoundedCornerShape(16.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f) else MaterialTheme.colorScheme.surface,
        border = BorderStroke(if (selected) 1.5.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBadge(icon, if (selected) Tone.Primary else Tone.Neutral)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(text, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// ------------------------------------------------------------------------ štítky

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChoiceChips(options: List<Choice>, selected: String?, onSelect: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 14.dp)) {
        options.forEach { o -> AppChip(o.label, o.key == selected) { onSelect(o.key) } }
    }
}

/** Více voleb; exclusive = volby, které ruší ostatní (Žádné, Nevím, Nic z toho). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MultiChips(options: List<Choice>, selected: SnapshotStateList<String>, exclusive: Set<String> = emptySet()) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 14.dp)) {
        options.forEach { o ->
            AppChip(o.label, o.key in selected) {
                if (o.key in selected) selected.remove(o.key)
                else {
                    if (o.key in exclusive) selected.clear() else selected.removeAll(exclusive)
                    selected.add(o.key)
                }
            }
        }
    }
}

@Composable
private fun AppChip(label: String, on: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = on, onClick = onClick, label = { Text(label) }, shape = RoundedCornerShape(18.dp),
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primary, selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
        ),
    )
}

@Composable
fun Question(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp), color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(bottom = 8.dp))
}

@Composable
fun Hint(text: String, amber: Boolean = false) {
    Surface(color = if (amber) Brand.AmberSoft else MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
            Icon(Icons.Outlined.Info, null, tint = if (amber) Brand.Amber else MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp))
        }
    }
}

// ------------------------------------------------------------------------ žádost

private class Contract {
    var kind by mutableStateOf<String?>(null)
    var declaration by mutableStateOf<String?>(null)
    var limit by mutableStateOf<String?>(null)
}

/** Odpovědi žádosti – drží se v paměti obrazovky, nic se neukládá do telefonu. */
private class ApplyForm {
    var service by mutableStateOf<String?>(null)
    var ico by mutableStateOf("")
    var company by mutableStateOf<ApplyCompany?>(null)
    var aresInfo by mutableStateOf<String?>(null)
    var contact by mutableStateOf("")
    var phone by mutableStateOf("")
    var email by mutableStateOf("")
    // celý rok
    var field by mutableStateOf<String?>(null)
    var fieldOther by mutableStateOf("")
    var description by mutableStateOf("")
    var premises by mutableStateOf("")
    var website by mutableStateOf("")
    var employees by mutableStateOf<String?>(null)
    var bookkeeping by mutableStateOf<String?>(null)
    var docs by mutableStateOf<String?>(null)
    var vat by mutableStateOf<String?>(null)
    var note by mutableStateOf("")
    // jen přiznání
    var year by mutableStateOf<String?>(null)
    val incomes = mutableStateListOf<String>()
    var employers by mutableStateOf<String?>(null)
    var declaration by mutableStateOf<String?>(null)
    var abroad by mutableStateOf<String?>(null)
    val contracts = mutableStateListOf(Contract())
    var bizMode by mutableStateOf<String?>(null)
    var bizExpenses by mutableStateOf<String?>(null)
    var revenue by mutableStateOf<String?>(null)
    val advances = mutableStateListOf<String>()
    var interruption by mutableStateOf<String?>(null)
    var overviews by mutableStateOf<String?>(null)
    val deductions = mutableStateListOf<String>()
    var children by mutableStateOf<String?>(null)
    var childrenOther by mutableStateOf<String?>(null)
    var consent by mutableStateOf(false)

    val tax get() = service == "tax_return"

    fun steps(): List<String> = buildList {
        add("service"); add("contact")
        if (service == "full") { add("business"); add("office") }
        if (tax) {
            add("situation")
            if ("employment" in incomes) add("employment")
            if ("contracts" in incomes) add("contracts")
            if ("business" in incomes) add("taxbusiness")
            add("deductions")
        }
        add("summary")
    }

    /** Text chyby, když krok není vyplněný; null = jde pokračovat. */
    fun missing(step: String): String? = when (step) {
        "service" -> if (service == null) "Vyberte, o co máte zájem." else null
        "contact" -> when {
            !tax && ico.filter { it.isDigit() }.length != 8 -> "Zadejte IČO (8 číslic)."
            ico.isNotBlank() && ico.filter { it.isDigit() }.length != 8 -> "IČO má 8 číslic."
            contact.isBlank() -> "Vyplňte jméno."
            phone.count { it.isDigit() } < 9 -> "Vyplňte telefon."
            !email.contains("@") || !email.contains(".") -> "Vyplňte e-mail."
            else -> null
        }
        "business" -> when {
            field == null -> "Vyberte obor."
            field == "other" && fieldOther.isBlank() -> "Napište, čím se zabýváte."
            description.isBlank() -> "Popište krátce činnost."
            else -> null
        }
        "office" -> if (employees == null || bookkeeping == null || docs == null || vat == null) "Odpovězte na všechny otázky (jde i „Nevím“)." else null
        "situation" -> if (year == null || incomes.isEmpty()) "Vyberte rok a alespoň jeden druh příjmů." else null
        "employment" -> if (employers == null || declaration == null || abroad == null) "Odpovězte na všechny otázky (jde i „Nevím“)." else null
        "contracts" -> if (contracts.any { it.kind == null || it.declaration == null || it.limit == null }) "U každé dohody vyberte odpovědi (jde i „Nevím“)." else null
        "taxbusiness" -> if (bizMode == null || bizExpenses == null || revenue == null || advances.isEmpty() || interruption == null || overviews == null) "Odpovězte na všechny otázky." else null
        "deductions" -> when {
            deductions.isEmpty() -> "Vyberte slevy, nebo „Nic z toho“ / „Nevím“."
            "children" in deductions && (children == null || childrenOther == null) -> "Doplňte údaje o dětech."
            else -> null
        }
        "summary" -> if (!consent) "Potvrďte souhlas se zpracováním údajů." else null
        else -> null
    }

    fun taxJson(): Map<String, kotlinx.serialization.json.JsonElement> = buildMap {
        put("year", JsonPrimitive(year ?: ""))
        put("incomes", JsonArray(incomes.map { JsonPrimitive(it) }))
        if ("employment" in incomes) put("employment", buildJsonObject {
            put("employers", JsonPrimitive(employers ?: "")); put("declaration", JsonPrimitive(declaration ?: "")); put("abroad", JsonPrimitive(abroad == "yes"))
        })
        if ("contracts" in incomes) put("contracts", JsonArray(contracts.map { c ->
            buildJsonObject { put("kind", JsonPrimitive(c.kind ?: "")); put("declaration", JsonPrimitive(c.declaration ?: "")); put("limit", JsonPrimitive(c.limit ?: "")) }
        }))
        if ("business" in incomes) put("business", buildJsonObject {
            put("mode", JsonPrimitive(bizMode ?: "")); put("expenses", JsonPrimitive(bizExpenses ?: "")); put("revenue", JsonPrimitive(revenue ?: ""))
            put("advances", JsonArray(advances.map { JsonPrimitive(it) })); put("interruption", JsonPrimitive(interruption == "yes")); put("overviews", JsonPrimitive(overviews == "yes"))
        })
        put("deductions", JsonArray(deductions.map { JsonPrimitive(it) }))
        if ("children" in deductions) { put("children", JsonPrimitive(children?.removeSuffix("+")?.toIntOrNull() ?: 4)); put("children_other", JsonPrimitive(childrenOther ?: "")) }
    }

    fun json(token: String): JsonObject = JsonObject(buildMap {
        put("token", JsonPrimitive(token))
        put("service", JsonPrimitive(service ?: "full"))
        put("ico", JsonPrimitive(ico.filter { it.isDigit() }))
        put("contact_name", JsonPrimitive(contact.trim())); put("phone", JsonPrimitive(phone.trim()))
        put("note", JsonPrimitive(note.trim())); put("consent", JsonPrimitive(consent))
        if (tax) putAll(taxJson())
        else {
            put("field", JsonPrimitive(field ?: "")); put("field_other", JsonPrimitive(fieldOther.trim())); put("description", JsonPrimitive(description.trim()))
            put("premises", JsonPrimitive(premises.trim())); put("website", JsonPrimitive(website.trim()))
            put("employees", JsonPrimitive(employees ?: "")); put("bookkeeping", JsonPrimitive(bookkeeping ?: "")); put("docs", JsonPrimitive(docs ?: "")); put("vat", JsonPrimitive(vat ?: ""))
        }
    })
}

private val YES_NO_SIMPLE = listOf(Choice("no", "Ne"), Choice("yes", "Ano"))

/**
 * Žádost nového klienta (1.8): zájem → kontakt (IČO z ARES) → podle zájmu buď podnikání a vedení, nebo krátký dotazník
 * k přiznání (situace, zaměstnání, dohody, podnikání, slevy) → shrnutí → ověření e-mailu kódem → odeslání.
 * Doklady se v žádosti neposílají – po přijetí se klientovi otevřou Podklady k přiznání.
 */
@Composable
fun ApplyScreen(nav: Navigator) {
    val form = remember { ApplyForm() }
    var options by remember { mutableStateOf<ApplyOptions?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var step by remember { mutableStateOf("service") }
    var busy by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf<ApplyResult?>(null) }
    var result by remember { mutableStateOf<ApplyResult?>(null) }
    var codeRequest by remember { mutableStateOf<String?>(null) }
    var code by remember { mutableStateOf("") }
    var codeError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    suspend fun load() {
        try { error = null; options = Api.applyOptions() } catch (e: Exception) { error = e.message }
    }
    LaunchedEffect(Unit) { load() }

    val steps = form.steps()
    val index = steps.indexOf(step).coerceAtLeast(0)

    fun back() { if (index == 0) nav.pop() else step = steps[index - 1] }

    fun next() {
        form.missing(step)?.let { error = it; return }
        error = null
        if (index + 1 < steps.size) step = steps[index + 1]
    }

    LaunchedEffect(step) {
        if (step == "summary" && form.tax) {
            preview = runCatching { Api.applyPreview(JsonObject(form.taxJson())) }.getOrNull()
        }
    }

    fun sendCode() {
        busy = true; error = null
        scope.launch {
            try { codeRequest = Api.applyCode(form.email).requestId; code = ""; codeError = null }
            catch (e: Exception) { error = e.message }
            finally { busy = false }
        }
    }

    fun verifyAndSubmit() {
        val req = codeRequest ?: return
        busy = true; codeError = null
        scope.launch {
            try {
                val token = Api.applyVerify(req, code).token
                codeRequest = null
                result = Api.applySubmit(form.json(token))
            } catch (e: Exception) {
                if (codeRequest != null) codeError = e.message else error = e.message
            } finally { busy = false }
        }
    }

    cz.multio.doklady.platform.PlatformBackHandler(enabled = result == null) { back() }

    val done = result
    if (done != null) {
        ApplyDone(form, done) { nav.pop() }
        return
    }

    val title = if (form.tax) "Žádost – přiznání" else "Žádost o vedení"
    androidx.compose.material3.Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            androidx.compose.material3.TopAppBar(
                title = { Text(title, style = MaterialTheme.typography.titleLarge) },
                navigationIcon = { IconButton(onClick = ::back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zpět") } },
                colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        val o = options
        Column(Modifier.padding(padding).fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
            LinearProgressIndicator(progress = { (index + 1f) / steps.size }, modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp))
            Text("Krok ${index + 1} z ${steps.size}" + if (index == 0) " · zabere asi 3 minuty" else "",
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 10.dp))
            if (o == null) {
                if (error == null) Box(Modifier.fillMaxWidth().height(200.dp)) { Loading() } else ErrorBanner(error) { scope.launch { load() } }
                return@Column
            }
            val t = o.tax
            val rules = form.year?.let { t.rules[it] } ?: t.rules.values.firstOrNull()
            when (step) {
                "service" -> {
                    Heading("O co máte zájem?")
                    BigChoice("Vedení po celý rok", "Účetnictví, daňová evidence nebo paušál", Icons.Outlined.Description, selected = form.service == "full") { form.service = "full" }
                    Spacer(Modifier.height(12.dp))
                    BigChoice("Jen daňové přiznání", "Jednou za rok – zaměstnání, dohody, podnikání", Icons.Outlined.CheckCircle, selected = form.service == "tax_return") { form.service = "tax_return" }
                    Spacer(Modifier.height(16.dp))
                }
                "contact" -> {
                    Heading("Kontakt")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Field(if (form.tax) "IČO (jen když podnikáte)" else "IČO", form.ico, { form.ico = it.filter { c -> c.isDigit() }.take(8); form.company = null; form.aresInfo = null },
                            keyboard = KeyboardType.Number, modifier = Modifier.weight(1f))
                        Spacer(Modifier.width(8.dp))
                        OutlinedButton(enabled = form.ico.length == 8 && !busy, onClick = {
                            busy = true
                            scope.launch {
                                try { val c = Api.applyAres(form.ico); form.company = c; form.aresInfo = null; if (form.contact.isBlank() && form.tax) form.contact = c.name ?: "" }
                                catch (e: Exception) { form.company = null; form.aresInfo = e.message }
                                finally { busy = false }
                            }
                        }) { Text("Načíst") }
                    }
                    form.company?.let { c ->
                        Surface(color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f), shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
                            Column(Modifier.padding(12.dp)) {
                                Text("✓ Nalezeno v ARES", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                Text(c.name ?: "", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                                Text(listOfNotNull(c.address, c.dic?.let { "DIČ $it" }).joinToString(" · "), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    form.aresInfo?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Brand.Amber, modifier = Modifier.padding(top = 6.dp)) }
                    Spacer(Modifier.height(14.dp))
                    Field("Jméno a příjmení", form.contact, { form.contact = it }); Spacer(Modifier.height(10.dp))
                    Field("Telefon", form.phone, { form.phone = it }, keyboard = KeyboardType.Phone); Spacer(Modifier.height(10.dp))
                    Field("E-mail", form.email, { form.email = it.trim() }, keyboard = KeyboardType.Email)
                    Text("Na e-mail vám před odesláním pošleme ověřovací kód.", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp, bottom = 16.dp))
                }
                "business" -> {
                    Heading("Čím se zabýváte?")
                    ChoiceChips(o.fields, form.field) { form.field = it }
                    if (form.field == "other") { Field("Napište vlastními slovy", form.fieldOther, { form.fieldOther = it }); Spacer(Modifier.height(10.dp)) }
                    Field("Popis činnosti", form.description, { form.description = it }, singleLine = false); Spacer(Modifier.height(10.dp))
                    Field("Adresa provozovny (nepovinné)", form.premises, { form.premises = it }); Spacer(Modifier.height(10.dp))
                    Field("Web (nepovinné)", form.website, { form.website = it }, keyboard = KeyboardType.Uri); Spacer(Modifier.height(16.dp))
                }
                "office" -> {
                    Heading("Vaše firma")
                    Question("Zaměstnanci"); ChoiceChips(o.employees, form.employees) { form.employees = it }
                    Question("Vedení"); ChoiceChips(o.bookkeeping, form.bookkeeping) { form.bookkeeping = it }
                    Question("Dokladů za měsíc"); ChoiceChips(o.docs, form.docs) { form.docs = it }
                    Question("DPH"); ChoiceChips(o.vat, form.vat) { form.vat = it }
                    Field("Od kdy začít, poznámka (nepovinné)", form.note, { form.note = it }, singleLine = false); Spacer(Modifier.height(16.dp))
                }
                "situation" -> {
                    Heading("Z čeho jste měli příjmy?")
                    Question("Za rok")
                    ChoiceChips(t.years.map { Choice(it, if (it == "older") "starší" else it) }, form.year) { form.year = it }
                    Question("Vyberte vše, co platí")
                    t.incomes.forEach { inc ->
                        ToggleRow(inc.label, inc.hint ?: "", inc.key in form.incomes) {
                            if (inc.key in form.incomes) form.incomes.remove(inc.key) else form.incomes.add(inc.key)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                "employment" -> {
                    Heading("Pracovní poměr")
                    Question("Kolik jste měli zaměstnavatelů?"); ChoiceChips(t.employers, form.employers) { form.employers = it }
                    Question("Podepsali jste u nich prohlášení k dani?"); ChoiceChips(t.declaration, form.declaration) { form.declaration = it }
                    HelpLine("Prohlášení = formulář, který se podepisuje při nástupu do práce.")
                    Question("Práce v zahraničí?"); ChoiceChips(YES_NO_SIMPLE, form.abroad) { form.abroad = it }
                    rules?.noteAnnual?.takeIf { form.incomes.size == 1 }?.let { Hint(it) }
                }
                "contracts" -> {
                    Heading("Dohody DPP / DPČ")
                    form.contracts.forEachIndexed { i, c ->
                        AppCard(Modifier.padding(bottom = 12.dp)) {
                            Column(Modifier.padding(start = 14.dp, end = 14.dp, top = 12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Dohoda ${i + 1}", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                                    if (form.contracts.size > 1) IconButton(onClick = { form.contracts.removeAt(i) }) { Icon(Icons.Outlined.Close, "Odebrat dohodu") }
                                }
                                Question("Druh"); ChoiceChips(t.contractKind, c.kind) { c.kind = it }
                                Question("Podepsali jste u ní prohlášení?"); ChoiceChips(t.yesNo, c.declaration) { c.declaration = it }
                                Question("Výdělek za měsíc byl"); ChoiceChips(t.limit, c.limit) { c.limit = it }
                                val limit = if (c.kind == "dpc") rules?.dpcLimit else rules?.dppLimit
                                if (limit != null) HelpLine("Limit pro rok ${rules?.year}: ${limit.cz()} Kč měsíčně.")
                            }
                        }
                    }
                    if (form.contracts.size < 10) TextButton(onClick = { form.contracts.add(Contract()) }) {
                        Icon(Icons.Outlined.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Přidat další dohodu")
                    }
                    rules?.noteWithholding?.let { Hint(it) }
                }
                "taxbusiness" -> {
                    Heading("Živnost (OSVČ)")
                    Question("Činnost byla"); ChoiceChips(t.businessMode, form.bizMode) { form.bizMode = it }
                    Question("Výdaje"); ChoiceChips(t.businessExpenses, form.bizExpenses) { form.bizExpenses = it }
                    Question("Příjmy za rok přibližně"); ChoiceChips(t.revenue, form.revenue) { form.revenue = it }
                    Question("Zaplacené zálohy"); MultiChips(t.advances, form.advances, exclusive = setOf("none", "unknown"))
                    Question("Přerušení činnosti nebo nemocenská?"); ChoiceChips(YES_NO_SIMPLE, form.interruption) { form.interruption = it }
                    Question("Podat i přehledy ČSSZ a zdravotní pojišťovně?"); ChoiceChips(listOf(Choice("yes", "Ano, obojí"), Choice("no", "Ne")), form.overviews) { form.overviews = it }
                    if ("employment" in form.incomes) rules?.noteBusiness?.let { Hint(it) }
                }
                "deductions" -> {
                    Heading("Na co máte nárok?")
                    MultiChips(t.deductions, form.deductions, exclusive = setOf("none", "unknown"))
                    if ("children" in form.deductions) {
                        AppCard(Modifier.padding(bottom = 12.dp)) {
                            Column(Modifier.padding(start = 14.dp, end = 14.dp, top = 12.dp)) {
                                Text("Děti", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(bottom = 8.dp))
                                Question("Počet dětí"); ChoiceChips(listOf("1", "2", "3", "4+").map { Choice(it, it) }, form.children) { form.children = it }
                                Question("Uplatňuje je druhý rodič?"); ChoiceChips(t.childrenOther, form.childrenOther) { form.childrenOther = it }
                            }
                        }
                        HelpLine("Jména a rodná čísla dětí zadáte až po přijetí, ne do žádosti.")
                    }
                }
                "summary" -> {
                    Heading("Shrnutí")
                    if (form.tax) {
                        preview?.summary?.takeIf { it.isNotBlank() }?.let {
                            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 12.dp))
                        }
                        Question("Po přijetí od vás budeme potřebovat")
                        AppCard(Modifier.padding(bottom = 12.dp)) {
                            val rows = preview?.checklist.orEmpty()
                            if (preview == null) Box(Modifier.fillMaxWidth().height(80.dp)) { Loading() }
                            rows.forEachIndexed { i, r -> ListRow(r.title, r.note, Icons.Outlined.Description, divider = i > 0) }
                        }
                        Hint("Doklady teď neposílejte. Po přijetí vám v aplikaci otevřeme Podklady k přiznání, kam je vyfotíte nebo nahrajete.", amber = true)
                    } else {
                        AppCard(Modifier.padding(bottom = 12.dp)) {
                            KeyValue("Firma", form.company?.name ?: form.ico, divider = false)
                            KeyValue("Obor", if (form.field == "other") form.fieldOther else o.fields.firstOrNull { it.key == form.field }?.label)
                            KeyValue("Vedení", o.bookkeeping.firstOrNull { it.key == form.bookkeeping }?.label)
                            KeyValue("Dokladů za měsíc", o.docs.firstOrNull { it.key == form.docs }?.label)
                        }
                    }
                    KeyValueCard(form)
                    if (form.tax) { Field("Poznámka pro účetní (nepovinné)", form.note, { form.note = it }, singleLine = false); Spacer(Modifier.height(10.dp)) }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 6.dp)) {
                        Checkbox(form.consent, { form.consent = it })
                        Text("Souhlasím se zpracováním údajů pro posouzení žádosti. Odmítnuté žádosti mažeme do 6 měsíců.",
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            ErrorBanner(error)
            Spacer(Modifier.height(12.dp))
            Button(
                enabled = !busy,
                onClick = { if (step == "summary") { form.missing(step)?.let { error = it } ?: sendCode() } else next() },
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                if (busy) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                else Text(if (step == "summary") "Odeslat žádost" else "Pokračovat")
            }
        }
    }

    if (codeRequest != null) {
        AlertDialog(
            onDismissRequest = { if (!busy) codeRequest = null },
            title = { Text("Ověření e-mailu") },
            text = {
                Column {
                    Text("Poslali jsme 6místný kód na ${form.email}. Platí 15 minut.", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(12.dp))
                    Field("Kód", code, { code = it.filter { c -> c.isDigit() }.take(6) }, keyboard = KeyboardType.NumberPassword)
                    codeError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp)) }
                    TextButton(enabled = !busy, onClick = ::sendCode) { Text("Poslat kód znovu") }
                }
            },
            confirmButton = { Button(enabled = !busy && code.length == 6, onClick = ::verifyAndSubmit) { Text(if (busy) "Odesílám…" else "Ověřit a odeslat") } },
            dismissButton = { TextButton(enabled = !busy, onClick = { codeRequest = null }) { Text("Zrušit") } },
        )
    }
}

@Composable
private fun KeyValueCard(form: ApplyForm) {
    AppCard(Modifier.padding(bottom = 12.dp)) {
        KeyValue("Kontakt", form.contact, divider = false)
        KeyValue("Telefon", form.phone)
        KeyValue("E-mail", form.email)
    }
}

@Composable
private fun Heading(text: String) {
    Text(text, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 14.dp))
}

@Composable
private fun HelpLine(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 12.dp))
}

@Composable
private fun ToggleRow(title: String, sub: String, on: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick, shape = RoundedCornerShape(14.dp),
        color = if (on) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f) else MaterialTheme.colorScheme.surface,
        border = BorderStroke(if (on) 1.5.dp else 1.dp, if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
    ) {
        Row(Modifier.padding(horizontal = 6.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(on, { onClick() })
            Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                if (sub.isNotBlank()) Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ApplyDone(form: ApplyForm, result: ApplyResult, onClose: () -> Unit) {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(48.dp))
            Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(84.dp)) {
                Box(contentAlignment = Alignment.Center) { Icon(Icons.Outlined.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(42.dp)) }
            }
            Spacer(Modifier.height(20.dp))
            Text("Žádost odeslána", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Text("Děkujeme. Ozveme se vám na ${form.email}. Po přijetí přijde pozvánka – nastavíte si heslo a přihlásíte se v aplikaci.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp, bottom = 22.dp))
            if (result.checklist.isNotEmpty()) {
                Text("Mezitím si můžete připravit", style = MaterialTheme.typography.labelLarge, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp))
                AppCard { result.checklist.forEachIndexed { i, r -> ListRow(r.title, r.note, Icons.Outlined.Description, divider = i > 0) } }
            }
            Spacer(Modifier.height(22.dp))
            Button(onClick = onClose, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Hotovo") }
        }
    }
}

private fun Int.cz(): String = toString().reversed().chunked(3).joinToString(" ").reversed()
