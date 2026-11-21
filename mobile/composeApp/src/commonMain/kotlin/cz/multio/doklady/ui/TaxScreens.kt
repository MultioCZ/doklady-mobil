package cz.multio.doklady.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import cz.multio.doklady.data.TaxPhoto
import cz.multio.doklady.platform.rememberDocumentScanner
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import cz.multio.doklady.Navigator
import cz.multio.doklady.Screen
import cz.multio.doklady.data.Api
import cz.multio.doklady.data.TaxReturn
import cz.multio.doklady.platform.openPdf
import cz.multio.doklady.platform.shareFile
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/*
 * Přiznání k dani z příjmů FO v mobilu. Výpočet dělá portál (stejně jako web /dane/) –
 * telefon jen sbírá odpovědi, posílá je na server a ukazuje výsledek. XML pro Moje daně
 * a PDF s výpočtem se stáhnou ze serveru a nasdílí / uloží.
 */

private val UFO = listOf(
    451 to "Praha", 452 to "Středočeský kraj", 453 to "Jihočeský kraj", 454 to "Plzeňský kraj", 455 to "Karlovarský kraj", 456 to "Ústecký kraj",
    457 to "Liberecký kraj", 458 to "Královéhradecký kraj", 459 to "Pardubický kraj", 460 to "Kraj Vysočina", 461 to "Jihomoravský kraj",
    462 to "Olomoucký kraj", 463 to "Moravskoslezský kraj", 464 to "Zlínský kraj",
)

/** Klíče formuláře → cesta v JSON „data“ (stejná struktura jako web). */
private val SIMPLE = listOf(
    "person.first_name", "person.last_name", "person.street", "person.house_no", "person.city", "person.zip", "person.phone", "person.email", "person.ufo",
    "s7.income", "s7.expenses", "s7.mode", "s7.flat", "s8.income", "s9.income", "s9.expenses", "past_loss",
    "deductions.gifts", "deductions.interest", "deductions.interest_months", "deductions.pension", "deductions.life", "deductions.dip", "deductions.care",
    "credits.spouse", "credits.spouse_months", "credits.disability", "credits.disability_months", "credits.ztpp_months",
    "payments.advances_paid", "payments.flat_tax_paid",
)
private val EMP = listOf("name", "income", "advances", "bonuses")
private val KID = listOf("first_name", "last_name", "birth_number", "months", "ztpp_months")

private fun JsonObject.text(path: String): String {
    var cur: JsonObject = this
    val parts = path.split('.')
    for (p in parts.dropLast(1)) cur = (cur[p] as? JsonObject) ?: return ""
    val v = cur[parts.last()] ?: return ""
    return (v as? JsonPrimitive)?.content?.takeIf { it != "null" && it != "false" } ?: ""
}

private fun formToJson(form: Map<String, String>, flat9: Boolean): JsonObject = buildJsonObject {
    val groups = SIMPLE.groupBy { it.substringBefore('.', "") }
    SIMPLE.filter { '.' !in it }.forEach { put(it, JsonPrimitive(form[it].orEmpty())) }
    groups.filterKeys { it.isNotEmpty() && SIMPLE.any { s -> s.startsWith("$it.") } }.forEach { (group, keys) ->
        put(group, buildJsonObject {
            keys.filter { it.startsWith("$group.") }.forEach { k -> put(k.substringAfter('.'), JsonPrimitive(form[k].orEmpty())) }
            if (group == "s9") put("flat", JsonPrimitive(flat9))
        })
    }
    put("employers", buildJsonArray {
        for (i in 0 until 3) {
            if (EMP.any { !form["e$i.$it"].isNullOrBlank() }) add(buildJsonObject { EMP.forEach { put(it, JsonPrimitive(form["e$i.$it"].orEmpty())) } })
        }
    })
    put("children", buildJsonArray {
        for (i in 0 until 4) {
            if (KID.any { !form["k$i.$it"].isNullOrBlank() }) add(buildJsonObject {
                KID.forEach { put(it, JsonPrimitive(form["k$i.$it"].orEmpty())) }
                put("order", JsonPrimitive((i + 1).toString()))
            })
        }
    })
    put("s10", JsonArray(emptyList()))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaxListScreen(nav: Navigator) {
    var list by remember { mutableStateOf<List<TaxReturn>?>(null) }
    var years by remember { mutableStateOf<List<Int>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        try { val r = Api.taxReturns(); list = r.returns; years = r.years } catch (e: Exception) { error = nav.errorText(e) }
    }
    Scaffold(topBar = {
        TopAppBar(title = { Text("Daň z příjmů") }, navigationIcon = { IconButton(onClick = { nav.pop() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zpět") } })
    }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ErrorBanner(error) {}
            Text("Průvodce spočítá daň z příjmů za rok a připraví soubor XML pro podání na Moje daně a PDF s výpočtem. Za správnost údajů odpovídáte vy.",
                 style = MaterialTheme.typography.bodyMedium)
            years.sortedDescending().forEach { y ->
                Button(onClick = { scope.launch { try { val t = Api.newTaxReturn(y); nav.push(Screen.TaxWizard(t.id)) } catch (e: Exception) { error = nav.errorText(e) } } },
                       modifier = Modifier.fillMaxWidth()) { Text("Nové přiznání za rok $y") }
            }
            list?.forEach { t ->
                Card(Modifier.fillMaxWidth().clickable { nav.push(Screen.TaxWizard(t.id)) }) {
                    ListItem(headlineContent = { Text("Přiznání ${t.year}") }, supportingContent = {
                        Text(when (t.state) { "ready" -> "Připraveno k podání"; "filed" -> "Podáno"; "rz_sent" -> "Žádost o roční zúčtování odeslána"; else -> "Rozpracováno" } +
                             (t.resultAmount?.let { a -> " · " + (if (a.startsWith("-")) "přeplatek ${czMoney(a.removePrefix("-"))}" else "doplatek ${czMoney(a)}") } ?: ""))
                    })
                }
            }
        }
    }
}

private fun czMoney(v: String): String {
    val n = v.substringBefore('.').toLongOrNull() ?: return "$v Kč"
    return n.toString().reversed().chunked(3).joinToString(" ").reversed() + " Kč"
}

@OptIn(ExperimentalMaterial3Api::class)
private val PHOTO_EMP = listOf("employer_name" to "Zaměstnavatel", "income" to "Úhrn zúčtovaných příjmů", "advances" to "Sražené zálohy", "bonuses" to "Vyplacené bonusy")
private val PHOTO_OSVC = listOf("osvc_income" to "Příjmy z podnikání", "osvc_expenses" to "Výdaje", "flat_rate" to "Paušál %", "assessment_base" to "Vyměřovací základ",
                                "advances_paid" to "Zaplacené zálohy", "difference" to "Nedoplatek / přeplatek", "new_advance" to "Nová měsíční záloha", "new_advance_from" to "Platí od (RRRR-MM)")

@Composable
fun TaxWizardScreen(nav: Navigator, id: String) {
    val form = remember { mutableStateMapOf<String, String>() }
    var flat9 by remember { mutableStateOf(false) }
    var rc by remember { mutableStateOf("") }
    var ret by remember { mutableStateOf<TaxReturn?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var ufoMenu by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun fill(t: TaxReturn) {
        val d = t.data ?: return
        SIMPLE.forEach { form[it] = d.text(it) }
        flat9 = d.text("s9.flat") == "true"
        (d["employers"] as? JsonArray)?.forEachIndexed { i, e -> if (i < 3) EMP.forEach { k -> form["e$i.$k"] = (e.jsonObject[k] as? JsonPrimitive)?.content.orEmpty() } }
        (d["children"] as? JsonArray)?.forEachIndexed { i, e -> if (i < 4) KID.forEach { k -> form["k$i.$k"] = (e.jsonObject[k] as? JsonPrimitive)?.content.orEmpty() } }
    }
    LaunchedEffect(id) { try { Api.taxReturn(id).also { ret = it; fill(it) } } catch (e: Exception) { error = nav.errorText(e) } }
    var photo by remember { mutableStateOf<TaxPhoto?>(null) }
    val photoValues = remember { mutableStateMapOf<String, String>() }
    var fillS7 by remember { mutableStateOf(false) }
    var reading by remember { mutableStateOf(false) }
    val scanner = rememberDocumentScanner { picked ->
        if (picked != null) scope.launch {
            reading = true
            try {
                val p = Api.taxPhoto(id, picked.pages, picked.pdf ?: picked.rawFile)
                photoValues.clear(); photoValues.putAll(p.values)
                fillS7 = p.kind != "potvrzeni" && p.values["year"] == ret?.year?.toString() && form["s7.income"].isNullOrBlank()
                photo = p; error = null
            } catch (e: Exception) { error = nav.errorText(e) }
            reading = false
        }
    }

    @Composable
    fun field(key: String, label: String, number: Boolean = true) = OutlinedTextField(
        value = form[key].orEmpty(), onValueChange = { form[key] = it }, label = { Text(label) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(keyboardType = if (number) KeyboardType.Number else KeyboardType.Text),
    )

    @Composable
    fun section(title: String, content: @Composable () -> Unit) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(title, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
                content()
            }
        }
    }

    photo?.let { p ->
        val keys = if (p.kind == "potvrzeni") PHOTO_EMP else PHOTO_OSVC
        AlertDialog(
            onDismissRequest = { photo = null },
            title = { Text(p.label + (photoValues["year"]?.takeIf { it.isNotBlank() }?.let { " $it" } ?: "")) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (p.source == "ocr") Text("Čteno bez AI – kontrolujte pečlivě.", color = MaterialTheme.colorScheme.error)
                    p.warnings.forEach { Text("⚠ $it", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                    keys.forEach { (k, label) ->
                        OutlinedTextField(photoValues[k].orEmpty(), { photoValues[k] = it }, label = { Text(label) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    }
                    if (p.kind != "potvrzeni") Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(fillS7, { fillS7 = it }); Text("Vyplnit příjmy a výdaje do § 7") }
                }
            },
            confirmButton = {
                TextButton(enabled = p.kind != "jine" && !busy, onClick = {
                    scope.launch {
                        busy = true
                        try {
                            Api.saveTaxReturn(id, formToJson(form, flat9), rc.filter { it.isDigit() }.ifBlank { null })  // neztratit rozepsané údaje
                            rc = ""
                            val done = Api.applyTaxPhoto(id, p.id, photoValues.toMap(), fillS7)
                            ret = done.toReturn(); fill(done.toReturn()); photo = null; error = null
                        } catch (e: Exception) { error = nav.errorText(e) }
                        busy = false
                    }
                }) { Text("Vložit do přiznání") }
            },
            dismissButton = { TextButton(onClick = { photo = null }) { Text("Zrušit") } },
        )
    }

    Scaffold(topBar = {
        TopAppBar(title = { Text("Přiznání ${ret?.year ?: ""}") }, navigationIcon = { IconButton(onClick = { nav.pop() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zpět") } })
    }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ErrorBanner(error) {}
            ret?.result?.let { r ->
                val amount = r.text("r91")
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(if (amount.startsWith("-")) "Přeplatek ${czMoney(amount.removePrefix("-"))}" else if (amount == "0" || amount.isBlank()) "Bez doplatku" else "Doplatíte ${czMoney(amount)}",
                             style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text("Základ daně ${czMoney(r.text("r56"))} · daň po slevách ${czMoney(r.text("r74"))} · bonus ${czMoney(r.text("r75"))}", style = MaterialTheme.typography.bodySmall)
                        (r["warnings"] as? JsonArray)?.forEach { Text("⚠ " + it.jsonPrimitive.content, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                        (r["info"] as? JsonArray)?.forEach { Text(it.jsonPrimitive.content, style = MaterialTheme.typography.bodySmall) }
                        if (ret?.state != "draft") Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { scope.launch { try { shareFile(Api.taxReturnFile(id, "xml"), "dpfo-${ret?.year}.xml", "application/xml") } catch (e: Exception) { error = nav.errorText(e) } } }) { Text("XML pro Moje daně") }
                            OutlinedButton(onClick = { scope.launch { try { openPdf(Api.taxReturnFile(id, "pdf"), "dpfo-${ret?.year}.pdf") } catch (e: Exception) { error = nav.errorText(e) } } }) { Text("PDF") }
                        } else Text("Doplňte rodné číslo a finanční úřad.", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            section("Vyfotit potvrzení nebo přehled OSVČ") {
                Text("Potvrzení o zdanitelných příjmech od zaměstnavatele nebo přehled pro ČSSZ / zdravotní pojišťovnu. Údaje se ukážou ke kontrole, fotka se neukládá.", style = MaterialTheme.typography.bodySmall)
                OutlinedButton(enabled = !reading, onClick = scanner) { Text(if (reading) "Čtu doklad…" else "Vyfotit doklad") }
            }
            section("1. Osobní údaje") {
                field("person.first_name", "Jméno", false); field("person.last_name", "Příjmení", false)
                OutlinedTextField(rc, { rc = it }, label = { Text(if (ret?.hasRc == true) "Rodné číslo (uložené – vyplňte jen při změně)" else "Rodné číslo") },
                                  singleLine = true, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                field("person.street", "Ulice", false); field("person.house_no", "Číslo popisné"); field("person.city", "Obec", false); field("person.zip", "PSČ")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = { ufoMenu = true }) { Text(UFO.firstOrNull { it.first.toString() == form["person.ufo"] }?.let { "FÚ – ${it.second}" } ?: "Vyberte finanční úřad") }
                    DropdownMenu(expanded = ufoMenu, onDismissRequest = { ufoMenu = false }) {
                        UFO.forEach { (code, name) -> DropdownMenuItem(text = { Text(name) }, onClick = { form["person.ufo"] = code.toString(); ufoMenu = false }) }
                    }
                }
            }
            section("2. Zaměstnání – z potvrzení zaměstnavatelů") {
                for (i in 0 until 3) {
                    if (i > 0) Spacer(Modifier.height(4.dp))
                    field("e$i.name", "Zaměstnavatel ${i + 1}", false); field("e$i.income", "Úhrn příjmů"); field("e$i.advances", "Sražené zálohy"); field("e$i.bonuses", "Vyplacené bonusy")
                }
            }
            section("3. Podnikání (§ 7)") {
                field("s7.income", "Příjmy"); field("s7.expenses", "Skutečné výdaje")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(form["s7.mode"] == "flat", { form["s7.mode"] = if (it) "flat" else "actual" }); Text("Výdaje paušálem")
                }
                if (form["s7.mode"] == "flat") Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("80", "60", "40", "30").forEach { pct -> OutlinedButton(onClick = { form["s7.flat"] = pct }) { Text((if (form["s7.flat"] == pct) "✓ " else "") + "$pct %") } }
                }
            }
            section("4. Nájem a kapitálové příjmy") {
                field("s9.income", "Nájem – příjmy"); field("s9.expenses", "Nájem – výdaje")
                Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(flat9, { flat9 = it }); Text("Výdaje z nájmu paušálem 30 %") }
                field("s8.income", "Kapitálové příjmy nezdaněné srážkou")
            }
            section("5. Odpočty") {
                field("deductions.gifts", "Dary"); field("deductions.interest", "Úroky z úvěru na bydlení"); field("deductions.interest_months", "Úvěr – měsíců")
                field("deductions.pension", "Penzijní spoření (odečitatelná část)"); field("deductions.life", "Životní pojištění"); field("deductions.dip", "Dlouhodobý investiční produkt")
            }
            section("6. Slevy a děti") {
                Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(form["credits.spouse"] == "1", { form["credits.spouse"] = if (it) "1" else "" }); Text("Sleva na manžela/manželku") }
                if (form["credits.spouse"] == "1") field("credits.spouse_months", "Manžel/ka – měsíců")
                for (i in 0 until 4) {
                    Text("${i + 1}. dítě", style = MaterialTheme.typography.labelLarge)
                    field("k$i.first_name", "Jméno", false); field("k$i.last_name", "Příjmení", false); field("k$i.birth_number", "Rodné číslo"); field("k$i.months", "Měsíců v roce")
                }
            }
            section("7. Zálohy zaplacené finančnímu úřadu") { field("payments.advances_paid", "Zaplacené zálohy") }
            Button(enabled = !busy, onClick = {
                scope.launch {
                    busy = true
                    try { ret = Api.saveTaxReturn(id, formToJson(form, flat9), rc.filter { it.isDigit() }.ifBlank { null }); rc = ""; error = null } catch (e: Exception) { error = nav.errorText(e) }
                    busy = false
                }
            }, modifier = Modifier.fillMaxWidth()) { Text(if (busy) "Počítám…" else "Spočítat daň") }
            Text("Výpočet dělá portál podle zadaných údajů; doklady ani nárok neověřuje. Za správnost přiznání odpovídá poplatník.", style = MaterialTheme.typography.bodySmall,
                 color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
