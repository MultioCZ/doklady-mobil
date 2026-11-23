package cz.multio.doklady.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cz.multio.doklady.Navigator
import cz.multio.doklady.Screen
import cz.multio.doklady.data.Api
import cz.multio.doklady.data.OdometerResult
import cz.multio.doklady.data.RouteResult
import cz.multio.doklady.data.Trip
import cz.multio.doklady.data.TripCreate
import cz.multio.doklady.data.Vehicle
import cz.multio.doklady.platform.decodeImage
import cz.multio.doklady.platform.openUrl
import cz.multio.doklady.platform.rememberCamera
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus

private val MONTH_NAMES = listOf("Leden", "Únor", "Březen", "Duben", "Květen", "Červen", "Červenec", "Srpen",
    "Září", "Říjen", "Listopad", "Prosinec")

private fun monthKey(d: LocalDate) = "${d.year}-${d.monthNumber.toString().padStart(2, '0')}"

/** "123.4" → "123,4 km" */
private fun km(value: String?): String = value?.toDoubleOrNull()?.let {
    val s = if (it == kotlin.math.floor(it)) it.toLong().toString() else ((kotlin.math.round(it * 10) / 10).toString())
    s.replace(".", ",") + " km"
} ?: "—"

// ------------------------------------------------------------------------ seznam jízd

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripsScreen(nav: Navigator) {
    var month by remember { mutableStateOf(LocalDate(today().year, today().monthNumber, 1)) }
    var vehicles by remember { mutableStateOf<List<Vehicle>>(emptyList()) }
    var vehicle by remember { mutableStateOf<Int?>(null) }
    var trips by remember { mutableStateOf<List<Trip>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    suspend fun load() {
        try {
            error = null
            if (vehicles.isEmpty()) vehicles = Api.vehicles().filter { it.active }
            trips = Api.trips(monthKey(month), vehicle)
        } catch (e: Exception) {
            error = nav.errorText(e)
            trips = emptyList()
        }
    }
    // návrat z nové jízdy / tachometru → načíst znovu
    LaunchedEffect(month, vehicle, nav.stack.size) { load() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Kniha jízd") },
                navigationIcon = { IconButton(onClick = { nav.pop() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zpět") } },
                actions = {
                    IconButton(onClick = { nav.push(Screen.Odometer) }, enabled = vehicles.isNotEmpty()) {
                        Icon(Icons.Filled.Speed, "Stav tachometru")
                    }
                },
            )
        },
        floatingActionButton = {
            if (vehicles.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = { nav.push(Screen.NewTrip(vehicle ?: vehicles.first().id)) },
                    icon = { Icon(Icons.Filled.Add, null) },
                    text = { Text("Nová jízda") },
                )
            }
        },
    ) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(bottom = 100.dp)) {
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { month = month.minus(DatePeriod(months = 1)) }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Předchozí měsíc")
                    }
                    Text("${MONTH_NAMES[month.monthNumber - 1]} ${month.year}", style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f))
                    IconButton(onClick = { month = month.plus(DatePeriod(months = 1)) }, enabled = month < LocalDate(today().year, today().monthNumber, 1)) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Další měsíc")
                    }
                }
                if (vehicles.size > 1) {
                    Dropdown("Vozidlo", linkedMapOf("" to "Všechna vozidla") + vehicles.associate { it.id.toString() to it.label },
                        vehicle?.toString() ?: "", { vehicle = it.toIntOrNull() }, modifier = Modifier.padding(horizontal = 16.dp))
                }
                ErrorBanner(error) { scope.launch { load() } }
                val list = trips.orEmpty()
                if (list.isNotEmpty()) {
                    val business = list.filter { it.kind == "business" }.sumOf { it.distanceKm.toDoubleOrNull() ?: 0.0 }
                    val privateKm = list.filter { it.kind != "business" }.sumOf { it.distanceKm.toDoubleOrNull() ?: 0.0 }
                    Text("Služebně ${km(business.toString())} · soukromě ${km(privateKm.toString())} · ${list.size} jízd",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                }
            }
            val list = trips
            when {
                list == null -> item { Box(Modifier.fillMaxWidth().height(300.dp)) { Loading() } }
                vehicles.isEmpty() && error == null -> item {
                    Box(Modifier.height(400.dp)) {
                        EmptyState("Žádné vozidlo", "Vozidla zakládá účetní kancelář v portálu (Kniha jízd → Vozidla).")
                    }
                }
                list.isEmpty() -> item {
                    Box(Modifier.height(400.dp)) { EmptyState("Žádné jízdy", "Tlačítkem Nová jízda zapíšete cestu.") }
                }
                else -> items(list, key = { it.id }) { t ->
                    val v = vehicles.firstOrNull { it.id == t.vehicleId }
                    ListItem(
                        leadingContent = { Icon(Icons.Filled.DirectionsCar, null) },
                        headlineContent = { Text(t.points.joinToString(" → "), maxLines = 2, overflow = TextOverflow.Ellipsis) },
                        supportingContent = {
                            Text(listOfNotNull(
                                czDate(t.date) + (t.timeFrom?.let { " $it" } ?: ""),
                                if (t.kind == "business") t.purpose else "soukromá",
                                if (vehicles.size > 1) v?.name else null,
                            ).joinToString("  ·  "), maxLines = 2, overflow = TextOverflow.Ellipsis)
                        },
                        trailingContent = { Text(km(t.distanceKm), fontWeight = FontWeight.SemiBold) },
                    )
                }
            }
        }
    }
}

// ------------------------------------------------------------------------ nová jízda

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewTripScreen(nav: Navigator, vehicleId: Int) {
    var vehicles by remember { mutableStateOf<List<Vehicle>>(emptyList()) }
    var vehicle by remember { mutableStateOf(vehicleId) }
    var date by remember { mutableStateOf(czDate(today().toString())) }
    var timeFrom by remember { mutableStateOf("") }
    var timeTo by remember { mutableStateOf("") }
    var driver by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf("business") }
    var purpose by remember { mutableStateOf("") }
    val points = remember { mutableStateListOf("", "") }
    var distance by remember { mutableStateOf("") }
    var odoStart by remember { mutableStateOf("") }
    var odoEnd by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var route by remember { mutableStateOf<RouteResult?>(null) }
    var routePoints by remember { mutableStateOf<List<String>>(emptyList()) }
    var confirmed by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    // jedno ID na formulář: opakované odeslání (slabý signál) jízdu nezdvojí
    val clientRef = remember { "m-${Clock.System.now().toEpochMilliseconds()}-${(1000..9999).random()}" }
    val scope = rememberCoroutineScope()

    fun applyVehicle(v: Vehicle?) {
        if (v == null) return
        if (driver.isBlank()) driver = v.defaultDriver.orEmpty()
        val home = v.home.orEmpty()
        if (home.isNotBlank() && points.all { it.isBlank() }) { points[0] = home; points[points.lastIndex] = home }
    }
    LaunchedEffect(Unit) {
        runCatching { Api.vehicles().filter { it.active } }
            .onSuccess { list -> vehicles = list; applyVehicle(list.firstOrNull { it.id == vehicle }) }
            .onFailure { message = nav.errorText(it) }
    }
    val cleanPoints = points.map { it.trim() }.filter { it.isNotEmpty() }
    // změna trasy po výpočtu = výpočet už neplatí
    val routeValid = route != null && routePoints == cleanPoints

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Nová jízda") },
            navigationIcon = { IconButton(onClick = { nav.pop() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zpět") } },
        )
    }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (vehicles.size > 1) {
                Dropdown("Vozidlo", vehicles.associate { it.id.toString() to it.label }, vehicle.toString(), { key ->
                    key.toIntOrNull()?.let { id -> vehicle = id; applyVehicle(vehicles.firstOrNull { it.id == id }) }
                })
            } else {
                vehicles.firstOrNull()?.let { LabelValue("Vozidlo", it.label) }
            }
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(kind == "business", { kind = "business" }, SegmentedButtonDefaults.itemShape(0, 2)) { Text("Služební") }
                SegmentedButton(kind == "private", { kind = "private" }, SegmentedButtonDefaults.itemShape(1, 2)) { Text("Soukromá") }
            }
            Field("Datum", date, { date = it }, placeholder = "17. 10. 2026")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Field("Odjezd", timeFrom, { timeFrom = it }, modifier = Modifier.weight(1f), placeholder = "7:30")
                Field("Příjezd", timeTo, { timeTo = it }, modifier = Modifier.weight(1f), placeholder = "9:15")
            }
            Field("Řidič", driver, { driver = it })
            if (kind == "business") Field("Účel cesty", purpose, { purpose = it }, placeholder = "Jednání u zákazníka")

            Text("Trasa", style = MaterialTheme.typography.titleSmall)
            points.forEachIndexed { i, p ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val label = when (i) { 0 -> "Odkud"; points.lastIndex -> "Kam"; else -> "Přes" }
                    Field(label, p, { points[i] = it }, modifier = Modifier.weight(1f), placeholder = "Obec, ulice")
                    if (points.size > 2) {
                        IconButton(onClick = { points.removeAt(i) }) { Icon(Icons.Filled.Close, "Odebrat zastávku") }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { points.add(points.lastIndex, "") }) { Text("+ zastávka") }
                TextButton(onClick = { points.add(points.first()) }, enabled = points.first().isNotBlank()) { Text("+ zpět na začátek") }
            }
            OutlinedButton(
                enabled = !busy && cleanPoints.size >= 2,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    busy = true; message = null
                    scope.launch {
                        try {
                            val r = Api.tripRoute(cleanPoints)
                            route = r; routePoints = cleanPoints; confirmed = false
                            distance = r.km.replace(".", ",")
                        } catch (e: Exception) {
                            message = nav.errorText(e)
                        }
                        busy = false
                    }
                },
            ) {
                Icon(Icons.Filled.Map, null); Spacer(Modifier.width(8.dp)); Text("Spočítat trasu")
            }
            route?.takeIf { routeValid }?.let { r ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Vypočteno: ${km(r.km)}" + (r.minutes?.let { " · asi $it min" } ?: ""), fontWeight = FontWeight.SemiBold)
                        r.warnings.forEach { Text("⚠ $it", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                        r.mapsUrl?.let { url -> TextButton(onClick = { openUrl(url) }) { Text("Zobrazit trasu v mapách") } }
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { confirmed = !confirmed }) {
                            Checkbox(confirmed, { confirmed = it })
                            Text("Trasa odpovídá skutečné jízdě")
                        }
                    }
                }
            }
            Field("Ujeto km", distance, { distance = it }, keyboard = KeyboardType.Decimal,
                placeholder = "nebo vyplňte oba stavy tachometru")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Field("Tachometr na začátku", odoStart, { odoStart = it.filter(Char::isDigit) }, modifier = Modifier.weight(1f), keyboard = KeyboardType.Number)
                Field("na konci", odoEnd, { odoEnd = it.filter(Char::isDigit) }, modifier = Modifier.weight(1f), keyboard = KeyboardType.Number)
            }
            Field("Poznámka (nepovinné)", note, { note = it })
            message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(
                enabled = !busy,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                onClick = {
                    val iso = parseCzDate(date)
                    val dist = distance.replace(" ", "").replace(",", ".").takeIf { it.isNotBlank() }
                    message = when {
                        iso == null -> "Neplatné datum."
                        cleanPoints.size < 2 -> "Zadejte trasu – alespoň odkud a kam."
                        kind == "business" && purpose.isBlank() -> "U služební jízdy vyplňte účel cesty."
                        dist != null && dist.toDoubleOrNull() == null -> "Neplatný počet kilometrů."
                        dist == null && (odoStart.isBlank() || odoEnd.isBlank()) -> "Zadejte ujeté km, nebo oba stavy tachometru."
                        routeValid && !confirmed -> "Potvrďte, že vypočtená trasa odpovídá skutečné jízdě."
                        listOf(timeFrom, timeTo).any { it.isNotBlank() && normTime(it) == null } -> "Čas zadejte jako HH:MM."
                        else -> null
                    }
                    if (message != null || iso == null) return@Button
                    busy = true
                    scope.launch {
                        try {
                            Api.createTrip(TripCreate(
                                vehicleId = vehicle, date = iso,
                                timeFrom = normTime(timeFrom), timeTo = normTime(timeTo),
                                driver = driver.trim().ifBlank { null },
                                purpose = purpose.trim().ifBlank { null }.takeIf { kind == "business" },
                                points = cleanPoints, kind = kind, distanceKm = dist,
                                calcKm = route?.takeIf { routeValid }?.km,
                                calcMinutes = route?.takeIf { routeValid }?.minutes,
                                odometerStart = odoStart.toIntOrNull(), odometerEnd = odoEnd.toIntOrNull(),
                                confirmed = routeValid && confirmed,
                                note = note.trim().ifBlank { null }, clientRef = clientRef,
                            ))
                            nav.pop()
                        } catch (e: Exception) {
                            message = nav.errorText(e)
                        }
                        busy = false
                    }
                },
            ) {
                if (busy) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp) else Text("Uložit jízdu")
            }
        }
    }
}

/** "7:30", "07.30", "730" → "07:30"; prázdné → null; neplatné → null. */
internal fun normTime(text: String): String? {
    val t = text.trim().replace('.', ':')
    if (t.isEmpty()) return null
    val parts = if (':' in t) t.split(':') else if (t.length in 3..4) listOf(t.dropLast(2), t.takeLast(2)) else return null
    if (parts.size != 2) return null
    val h = parts[0].toIntOrNull() ?: return null
    val m = parts[1].toIntOrNull() ?: return null
    if (h !in 0..23 || m !in 0..59) return null
    return "${h.toString().padStart(2, '0')}:${m.toString().padStart(2, '0')}"
}

// ------------------------------------------------------------------------ stav tachometru

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OdometerScreen(nav: Navigator) {
    var vehicles by remember { mutableStateOf<List<Vehicle>>(emptyList()) }
    var vehicle by remember { mutableStateOf<Int?>(null) }
    var photo by remember { mutableStateOf<ByteArray?>(null) }
    var kmText by remember { mutableStateOf("") }
    var readKm by remember { mutableStateOf<Int?>(null) }
    var hint by remember { mutableStateOf<String?>(null) }
    var result by remember { mutableStateOf<OdometerResult?>(null) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val preview = remember(photo) { photo?.let { decodeImage(it) } }

    LaunchedEffect(Unit) {
        runCatching { Api.vehicles().filter { it.active } }
            .onSuccess { vehicles = it; vehicle = it.firstOrNull()?.id }
            .onFailure { message = nav.errorText(it) }
    }
    val camera = rememberCamera { bytes ->
        if (bytes == null) return@rememberCamera
        photo = bytes; result = null; readKm = null; hint = null
        val v = vehicle ?: return@rememberCamera
        busy = true; message = null
        scope.launch {
            // portál přečte číslo z fotky (AI / OCR) – jen návrh, řidič ho potvrdí nebo opraví
            try {
                val s = Api.odometer(v, bytes).suggested
                readKm = s?.km
                s?.km?.let { kmText = it.toString() }
                hint = s?.note?.takeIf { it.isNotBlank() } ?: if (s?.km == null) "Číslo se z fotky přečíst nepodařilo – opište ho." else "Zkontrolujte přečtené číslo."
            } catch (e: Exception) {
                message = nav.errorText(e)
            }
            busy = false
        }
    }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Stav tachometru") },
            navigationIcon = { IconButton(onClick = { nav.pop() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zpět") } },
        )
    }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (vehicles.size > 1) {
                Dropdown("Vozidlo", vehicles.associate { it.id.toString() to it.label }, vehicle?.toString(), { vehicle = it.toIntOrNull(); result = null })
            } else {
                vehicles.firstOrNull()?.let { LabelValue("Vozidlo", it.label) }
            }
            preview?.let {
                Image(it, "Fotka tachometru", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxWidth().height(240.dp))
            }
            OutlinedButton(onClick = camera, enabled = !busy && vehicle != null, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.CameraAlt, null); Spacer(Modifier.width(8.dp))
                Text(if (photo == null) "Vyfotit tachometr" else "Vyfotit znovu")
            }
            hint?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Field("Stav km", kmText, { kmText = it.filter(Char::isDigit); result = null }, keyboard = KeyboardType.Number)
            message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(
                enabled = !busy && vehicle != null && kmText.isNotBlank() && result == null,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                onClick = {
                    val v = vehicle ?: return@Button
                    val value = kmText.toIntOrNull() ?: run { message = "Neplatný stav km."; return@Button }
                    busy = true; message = null
                    scope.launch {
                        try {
                            result = Api.odometer(v, photo, km = value, readKm = readKm, date = today().toString())
                        } catch (e: Exception) {
                            message = nav.errorText(e)
                        }
                        busy = false
                    }
                },
            ) {
                if (busy) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp) else Text("Uložit stav")
            }
            result?.takeIf { it.saved }?.let { r ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Uloženo: ${r.km} km", fontWeight = FontWeight.SemiBold)
                        LabelValue("Najeto od minula", km(r.driven))
                        LabelValue("Zapsáno v jízdách", km(r.recorded))
                        when (r.state) {
                            "missing" -> Text("Chybí zapsat ${km(r.gap)} – doplňte jízdy.", color = MaterialTheme.colorScheme.error)
                            "over" -> Text("V jízdách je o ${km(r.gap?.removePrefix("-"))} víc, než ukazuje tachometr – zkontrolujte je.", color = MaterialTheme.colorScheme.error)
                            else -> Text("Jízdy odpovídají tachometru.")
                        }
                        r.photoWarning?.let { Text("⚠ $it", style = MaterialTheme.typography.bodySmall) }
                    }
                }
                TextButton(onClick = { nav.pop() }) { Text("Hotovo") }
            }
        }
    }
}
