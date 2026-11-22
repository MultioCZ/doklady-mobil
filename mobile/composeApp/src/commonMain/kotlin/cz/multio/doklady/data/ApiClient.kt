package cz.multio.doklady.data

import cz.multio.doklady.platform.KeyValueStore
import cz.multio.doklady.platform.httpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.delete
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.readRawBytes
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class ApiException(message: String, val status: Int = 0) : Exception(message)

/**
 * Komunikace s webovou aplikací. Telefon nikdy nepracuje s databází přímo –
 * vše jde přes REST API webové aplikace (stejná databáze, stejná data na webu i v mobilu).
 */
object Api {
    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    val json = Json { ignoreUnknownKeys = true; explicitNulls = false; encodeDefaults = true }

    private val client = httpClient {
        install(ContentNegotiation) { json(json) }
        install(HttpTimeout) {
            requestTimeoutMillis = 120_000
            connectTimeoutMillis = 15_000
        }
        expectSuccess = false
    }

    /** Adresa serveru je pevná (AppConfig). Jinou lze nastavit jen pro vývoj/testování. */
    var baseUrl: String
        get() = KeyValueStore.get("server_override")?.takeIf { it.isNotBlank() } ?: cz.multio.doklady.AppConfig.SERVER_URL
        set(v) = KeyValueStore.put("server_override", normalizeUrl(v).takeIf { it.isNotBlank() && it != cz.multio.doklady.AppConfig.SERVER_URL })

    var token: String?
        get() = Session.token()
        set(v) = Session.setToken(v)

    var companyId: Int?
        get() = KeyValueStore.get("company")?.toIntOrNull()
        set(v) = KeyValueStore.put("company", v?.toString())

    fun normalizeUrl(url: String): String {
        var u = url.trim().trimEnd('/')
        if (u.isNotEmpty() && !u.startsWith("http://") && !u.startsWith("https://")) u = "https://$u"
        return u
    }

    private fun url(path: String) = "$baseUrl/api$path"

    private fun HttpRequestBuilder.auth(company: Int? = companyId) {
        token?.let { header(HttpHeaders.Authorization, "Bearer $it") }
        company?.let { header("X-Company-Id", it.toString()) }
    }

    private suspend fun check(r: HttpResponse): HttpResponse {
        if (r.status.isSuccess()) return r
        val text = runCatching { r.bodyAsText() }.getOrDefault("")
        val detail = runCatching {
            when (val d = json.parseToJsonElement(text).jsonObject["detail"]) {
                is JsonPrimitive -> d.content
                is JsonArray -> d.joinToString("; ") { it.jsonObject["msg"]?.jsonPrimitive?.content ?: it.toString() }
                else -> null
            }
        }.getOrNull()
        val msg = detail ?: when (r.status.value) {
            401 -> "Přihlášení vypršelo – přihlaste se znovu."
            404 -> "Nenalezeno."
            413 -> "Soubor je příliš velký."
            in 500..599 -> "Chyba serveru (${r.status.value})."
            else -> "Chyba ${r.status.value}"
        }
        throw ApiException(msg, r.status.value)
    }

    private suspend inline fun <reified T> call(block: () -> HttpResponse): T {
        val r = try {
            block()
        } catch (e: ApiException) {
            throw e
        } catch (e: Exception) {
            throw ApiException("Server není dostupný. Zkontrolujte připojení k internetu. (${e.message ?: e::class.simpleName})")
        }
        return check(r).body()
    }

    // ---------------------------------------------------------------- přihlášení

    suspend fun login(email: String, password: String): LoginResponse = signedIn(call {
        client.post(url("/auth/login")) {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest(email.trim(), password))
        }
    })

    /** Aktivace klientského portálu kódem z pozvánky; klient si tím nastaví heslo a je rovnou přihlášen. */
    suspend fun activate(code: String, password: String): LoginResponse = signedIn(call {
        client.post(url("/activate")) {
            contentType(ContentType.Application.Json)
            setBody(ActivateRequest(code.trim(), password))
        }
    })

    private fun signedIn(res: LoginResponse): LoginResponse {
        token = res.token
        KeyValueStore.put("me", json.encodeToString(LoginResponse.serializer(), res.copy(token = "")))
        if (companyId == null || res.companies.none { it.id == companyId }) {
            companyId = res.user.companyId ?: res.companies.firstOrNull()?.id
        }
        return res
    }

    suspend fun me(): LoginResponse = call { client.get(url("/me")) { auth() } }

    /** Načte znovu firmy a balíčky (změna balíčku na webu se projeví bez odhlášení). */
    suspend fun refreshMe(): LoginResponse? = runCatching { me() }.getOrNull()?.also { res ->
        KeyValueStore.put("me", json.encodeToString(LoginResponse.serializer(), res.copy(token = "")))
        if (companyId == null || res.companies.none { it.id == companyId }) companyId = res.user.companyId ?: res.companies.firstOrNull()?.id
    }

    fun cachedMe(): LoginResponse? =
        KeyValueStore.get("me")?.let { runCatching { json.decodeFromString(LoginResponse.serializer(), it) }.getOrNull() }

    fun logout() {
        token = null
        KeyValueStore.put("me", null)
    }

    /** Odhlášení i na serveru (zneplatní token). Bez signálu se token jen zapomene v telefonu. */
    suspend fun logoutEverywhere() {
        val t = token
        logout()
        if (t.isNullOrBlank()) return
        runCatching { client.post(url("/auth/logout")) { header(HttpHeaders.Authorization, "Bearer $t") } }
    }

    // ---------------------------------------------------------------- doklady

    suspend fun uploadDocument(
        original: ByteArray,
        originalMime: String,
        originalName: String,
        processed: ByteArray?,
        note: String?,
        mode: String = "gray",
        company: Int? = companyId,
        stage: String? = null,
        costCenter: String? = null,
        job: String? = null,
        direction: String? = null,
        qr: String? = null,
        docType: String? = null,
        categoryId: Int? = null,
    ): Document = call {
        client.submitFormWithBinaryData(
            url = url("/documents"),
            formData = formData {
                append("original", original, Headers.build {
                    append(HttpHeaders.ContentType, originalMime)
                    append(HttpHeaders.ContentDisposition, "filename=\"$originalName\"")
                })
                if (processed != null) {
                    append("processed", processed, Headers.build {
                        append(HttpHeaders.ContentType, "image/jpeg")
                        append(HttpHeaders.ContentDisposition, "filename=\"processed.jpg\"")
                    })
                }
                append("enhance", "true")
                append("mode", mode)
                append("extract", "true")
                if (!note.isNullOrBlank()) append("note", note)
                if (!stage.isNullOrBlank()) append("stage", stage)
                if (!costCenter.isNullOrBlank()) append("cost_center", costCenter)
                if (!job.isNullOrBlank()) append("job", job)
                if (!direction.isNullOrBlank()) append("direction", direction)
                // QR platba přečtená v telefonu (portál 1.12) – po vytěžení doplní účet, VS, splatnost a částku
                if (!qr.isNullOrBlank()) append("qr", qr)
                // faktura | uctenka – u účtenek z režimu „Účtenky“ (1.5)
                if (!docType.isNullOrBlank()) append("doc_type", docType)
                // druh podle klienta (portál 1.15)
                categoryId?.let { append("category_id", it.toString()) }
            },
        ) { auth(company) }
    }

    suspend fun documents(status: String? = null, query: String? = null, limit: Int = 100): List<Document> = call {
        client.get(url("/documents")) {
            auth()
            status?.let { parameter("status", it) }
            query?.takeIf { it.isNotBlank() }?.let { parameter("q", it) }
            parameter("limit", limit)
        }
    }

    suspend fun document(id: String): Document = call { client.get(url("/documents/$id")) { auth() } }

    suspend fun updateDocument(id: String, patch: DocumentPatch): Document = call {
        client.patch(url("/documents/$id")) {
            auth()
            contentType(ContentType.Application.Json)
            setBody(patch)
        }
    }

    suspend fun deleteDocument(id: String) {
        val r = try { client.delete(url("/documents/$id")) { auth() } } catch (e: Exception) {
            throw ApiException("Server není dostupný.")
        }
        check(r)
    }

    /** kind = "original" | "processed" */
    suspend fun documentFile(id: String, kind: String): ByteArray {
        val r = try { client.get(url("/documents/$id/file/$kind")) { auth() } } catch (e: Exception) {
            throw ApiException("Server není dostupný.")
        }
        return check(r).readRawBytes()
    }

    // ---------------------------------------------------------------- přehledy

    /** from/to ve formátu RRRR-MM-DD */
    suspend fun reportSummary(from: String, to: String): ReportSummary = call {
        client.get(url("/reports/summary")) { auth(); parameter("from", from); parameter("to", to) }
    }

    /** Všichni dodavatelé za období (klíč = IČO, bez IČO „nazev:…“). */
    suspend fun suppliers(from: String, to: String): List<ReportSupplier> = call {
        client.get(url("/reports/suppliers")) { auth(); parameter("from", from); parameter("to", to) }
    }

    /** Přijaté doklady jednoho dodavatele za období. */
    suspend fun supplierDocuments(key: String, from: String, to: String): List<Document> = call {
        client.get(url("/documents")) {
            auth(); parameter("supplier", key); parameter("from", from); parameter("to", to); parameter("limit", 500)
        }
    }

    /** PDF seznam dokladů podle dodavatelů (key = null → všichni dodavatelé). */
    suspend fun suppliersPdf(from: String, to: String, key: String? = null): ByteArray {
        val r = try {
            client.get(url("/reports/suppliers.pdf")) {
                auth(); parameter("from", from); parameter("to", to); key?.let { parameter("supplier", it) }
            }
        } catch (e: Exception) {
            throw ApiException("Server není dostupný.")
        }
        return check(r).readRawBytes()
    }

    // ---------------------------------------------------------------- odběratelé

    suspend fun ares(ico: String): AresResult = call { client.get(url("/ares/$ico")) { auth() } }

    suspend fun customers(): List<Customer> = call { client.get(url("/customers")) { auth() } }

    suspend fun saveCustomer(c: Customer): Customer = call {
        if (c.id == 0) {
            client.post(url("/customers")) { auth(); contentType(ContentType.Application.Json); setBody(c) }
        } else {
            client.put(url("/customers/${c.id}")) { auth(); contentType(ContentType.Application.Json); setBody(c) }
        }
    }

    // ---------------------------------------------------------------- faktury

    suspend fun invoices(): List<Invoice> = call { client.get(url("/invoices")) { auth() } }

    suspend fun invoice(id: String): Invoice = call { client.get(url("/invoices/$id")) { auth() } }

    suspend fun createInvoice(body: InvoiceCreate): Invoice = call {
        client.post(url("/invoices")) { auth(); contentType(ContentType.Application.Json); setBody(body) }
    }

    suspend fun invoicePdf(id: String): ByteArray {
        val r = try { client.get(url("/invoices/$id/pdf")) { auth() } } catch (e: Exception) {
            throw ApiException("Server není dostupný.")
        }
        return check(r).readRawBytes()
    }

    suspend fun sendInvoice(id: String, email: String?, message: String? = null): Invoice = call {
        client.post(url("/invoices/$id/send")) {
            auth(); contentType(ContentType.Application.Json); setBody(SendRequest(email, message))
        }
    }

    suspend fun setInvoiceStatus(id: String, status: String): Invoice = call {
        client.post(url("/invoices/$id/status/$status")) { auth() }
    }

    suspend fun recurring(): List<Recurring> = call { client.get(url("/recurring")) { auth() } }

    suspend fun createRecurring(body: RecurringCreate): Recurring = call {
        client.post(url("/recurring")) { auth(); contentType(ContentType.Application.Json); setBody(body) }
    }

    suspend fun deleteRecurring(id: String) {
        val r = try { client.delete(url("/recurring/$id")) { auth() } } catch (e: Exception) {
            throw ApiException("Server není dostupný.")
        }
        check(r)
    }

    // ---------------------------------------------------------------- zapomenuté heslo

    /** Pošle na e-mail odkaz na nové heslo. Vrací text pro uživatele. */
    suspend fun passwordReset(email: String): String = call<Detail> {
        client.post(url("/auth/password-reset")) {
            contentType(ContentType.Application.Json)
            setBody(PasswordResetRequest(email.trim()))
        }
    }.detail ?: "Odkaz na nové heslo jsme poslali e-mailem."

    // ---------------------------------------------------------------- střediska a zakázky

    suspend fun dimensions(): Dimensions = call { client.get(url("/dimensions")) { auth() } }

    // ---------------------------------------------------------------- požadavky od účetní

    /** state = open (k doplnění + odesláno) | all | done */
    suspend fun tasks(state: String = "open"): List<TaskItem> = call {
        client.get(url("/tasks")) { auth(); parameter("state", state) }
    }

    suspend fun task(id: String): TaskItem = call { client.get(url("/tasks/$id")) { auth() } }

    /** Odpověď na požadavek s doklady. files = (data, mime, název). */
    suspend fun submitTask(id: String, response: String, files: List<Triple<ByteArray, String, String>>): TaskItem = call {
        client.submitFormWithBinaryData(
            url = url("/tasks/$id/submit"),
            formData = formData {
                append("response", response)
                files.forEach { (data, mime, name) ->
                    append("files", data, Headers.build {
                        append(HttpHeaders.ContentType, mime)
                        append(HttpHeaders.ContentDisposition, "filename=\"$name\"")
                    })
                }
            },
        ) { auth() }
    }

    // ---------------------------------------------------------------- cesta faktury

    /** Doklady, které chybějí v cestách faktur (dodací listy, objednávky, zálohy…). */
    suspend fun missing(): List<MissingItem> = call { client.get(url("/missing")) { auth() } }

    suspend fun documentChain(id: String): ChainInfo = call { client.get(url("/documents/$id/chain")) { auth() } }

    // ---------------------------------------------------------------- druh dokladu podle klienta (portál 1.15)

    private var categoriesCache: Pair<Int?, CategoriesInfo>? = null

    /** Nabídka „Za co to je?“ (podle vedení firmy). Drží se v paměti pro aktuální firmu. */
    suspend fun categories(refresh: Boolean = false): CategoriesInfo {
        categoriesCache?.takeIf { !refresh && it.first == companyId }?.let { return it.second }
        val info: CategoriesInfo = call { client.get(url("/categories")) { auth() } }
        categoriesCache = companyId to info
        return info
    }

    /** null = nechat na účetní. */
    suspend fun setDocumentCategory(id: String, categoryId: Int?): Document = call {
        client.patch(url("/documents/$id")) {
            auth(); contentType(ContentType.Application.Json)
            setBody(kotlinx.serialization.json.buildJsonObject { put("category_id", categoryId?.let { JsonPrimitive(it) } ?: kotlinx.serialization.json.JsonNull) })
        }
    }

    // ---------------------------------------------------------------- údaje firmy a asistent (portál 1.14)

    suspend fun companyProfile(): CompanyProfileData = call { client.get(url("/company")) { auth() } }

    /** Posílá jen měněná pole (ostatní zůstanou). */
    suspend fun saveCompanyProfile(changes: kotlinx.serialization.json.JsonObject): CompanyProfileData = call {
        client.put(url("/company")) { auth(); contentType(ContentType.Application.Json); setBody(changes) }
    }

    suspend fun assistant(): AssistantState = call { client.get(url("/assistant")) { auth(null) } }

    suspend fun assistantAsk(question: String): AssistantReply = call {
        client.post(url("/assistant")) { auth(null); contentType(ContentType.Application.Json); setBody(AssistantQuestion(question)) }
    }

    suspend fun assistantNew(): AssistantState = call {
        client.post(url("/assistant")) { auth(null); contentType(ContentType.Application.Json); setBody(kotlinx.serialization.json.buildJsonObject { put("new", JsonPrimitive(true)) }) }
    }

    suspend fun assistantForward(question: String): AssistantForwardResult = call {
        client.post(url("/assistant/forward")) { auth(null); contentType(ContentType.Application.Json); setBody(AssistantQuestion(question)) }
    }

    // ---------------------------------------------------------------- přehled a banka (portál 1.13)

    suspend fun home(): Home = call { client.get(url("/home")) { auth() } }

    /** month = RRRR-MM, filter = all | missing | paired */
    suspend fun bank(month: String, filter: String = "all"): BankList = call {
        client.get(url("/bank")) { auth(); parameter("month", month); parameter("filter", filter) }
    }

    suspend fun bankItem(id: String): BankItem = call { client.get(url("/bank/$id")) { auth() } }

    suspend fun bankAnswer(id: String, answerId: Int, note: String?): BankItem = call {
        client.post(url("/bank/$id/answer")) { auth(); contentType(ContentType.Application.Json); setBody(BankAnswerRequest(answerId, note)) }
    }

    suspend fun bankClearAnswer(id: String): BankItem = call {
        client.post(url("/bank/$id/answer")) { auth(); contentType(ContentType.Application.Json); setBody(BankAnswerRequest(clear = true)) }
    }

    /** Doklad k platbě – nahraje se a rovnou připojí k pohybu. */
    suspend fun bankDocument(id: String, original: ByteArray, mime: String, name: String, processed: ByteArray?, qr: String?): BankItem = call {
        client.submitFormWithBinaryData(
            url = url("/bank/$id/document"),
            formData = formData {
                append("original", original, Headers.build {
                    append(HttpHeaders.ContentType, mime)
                    append(HttpHeaders.ContentDisposition, "filename=\"${name.replace("\"", "")}\"")
                })
                if (processed != null) append("processed", processed, Headers.build {
                    append(HttpHeaders.ContentType, "image/jpeg")
                    append(HttpHeaders.ContentDisposition, "filename=\"processed.jpg\"")
                })
                if (!qr.isNullOrBlank()) append("qr", qr)
            },
        ) { auth() }
    }

    /** Výpis z banky (GPC/ABO, XML, CSV, PDF; víc souborů najednou). files = (data, mime, název). */
    suspend fun uploadStatement(files: List<Triple<ByteArray, String, String>>): StatementResult {
        val r = try {
            client.submitFormWithBinaryData(
                url = url("/bank/statements"),
                formData = formData {
                    files.forEach { (data, mime, name) ->
                        append("file", data, Headers.build {
                            append(HttpHeaders.ContentType, mime)
                            append(HttpHeaders.ContentDisposition, "filename=\"${name.replace("\"", "")}\"")
                        })
                    }
                },
            ) { auth() }
        } catch (e: Exception) {
            throw ApiException("Server není dostupný. Zkontrolujte připojení k internetu.")
        }
        // 400 = výpis se nenačetl, ale odpověď obsahuje vysvětlení
        if (r.status.value == 400) runCatching { return json.decodeFromString(StatementResult.serializer(), r.bodyAsText()) }
        return check(r).body()
    }

    // ---------------------------------------------------------------- přístupové klíče (portál 1.13)

    suspend fun passkeyLoginOptions(): PasskeyChallenge = call { client.post(url("/auth/passkey/options")) }

    suspend fun passkeyLogin(credentialJson: String, state: String): LoginResponse = signedIn(call {
        client.post(url("/auth/passkey/login")) {
            contentType(ContentType.Application.Json)
            setBody(PasskeyLoginRequest(json.parseToJsonElement(credentialJson), state))
        }
    })

    suspend fun passkeys(): List<PasskeyInfo> = call { client.get(url("/passkeys")) { auth(null) } }

    suspend fun passkeyRegisterOptions(): PasskeyChallenge = call { client.post(url("/passkeys/options")) { auth(null) } }

    suspend fun passkeyRegister(credentialJson: String, state: String, name: String): PasskeyInfo = call {
        client.post(url("/passkeys")) {
            auth(null); contentType(ContentType.Application.Json)
            setBody(PasskeyRegisterRequest(json.parseToJsonElement(credentialJson), state, name))
        }
    }

    suspend fun deletePasskey(id: Int) {
        val r = try { client.delete(url("/passkeys/$id")) { auth(null) } } catch (e: Exception) {
            throw ApiException("Server není dostupný.")
        }
        check(r)
    }

    // ---------------------------------------------------------------- kniha jízd (portál 0.44)

    suspend fun vehicles(): List<Vehicle> = call { client.get(url("/vehicles")) { auth() } }

    /** month = RRRR-MM */
    suspend fun trips(month: String, vehicle: Int? = null): List<Trip> = call {
        client.get(url("/trips")) { auth(); parameter("month", month); vehicle?.let { parameter("vehicle", it) } }
    }

    suspend fun createTrip(body: TripCreate): Trip = call {
        client.post(url("/trips")) { auth(); contentType(ContentType.Application.Json); setBody(body) }
    }

    /** Výpočet trasy na serveru (Google Maps podle nastavení portálu). */
    suspend fun tripRoute(points: List<String>): RouteResult = call {
        client.post(url("/trips/route")) { auth(); contentType(ContentType.Application.Json); setBody(RouteRequest(points)) }
    }

    /**
     * Stav tachometru. km = null → portál jen přečte návrh z fotky (nic neuloží).
     * S km uloží stav (s fotkou jako dokladem) a vrátí porovnání se zapsanými jízdami.
     */
    suspend fun odometer(vehicleId: Int, photo: ByteArray?, km: Int? = null, readKm: Int? = null, date: String? = null): OdometerResult = call {
        client.submitFormWithBinaryData(
            url = url("/odometer"),
            formData = formData {
                append("vehicle_id", vehicleId.toString())
                if (photo != null) append("photo", photo, Headers.build {
                    append(HttpHeaders.ContentType, "image/jpeg")
                    append(HttpHeaders.ContentDisposition, "filename=\"tachometr.jpg\"")
                })
                km?.let { append("km", it.toString()) }
                readKm?.let { append("read_km", it.toString()) }
                date?.let { append("date", it) }
            },
        ) { auth() }
    }

    // ---------------------------------------------------------------- daň z příjmů FO (portál 0.49)

    suspend fun taxReturns(): TaxReturnList = call { client.get(url("/tax/returns")) { auth(null) } }

    suspend fun newTaxReturn(year: Int): TaxReturn = call {
        client.post(url("/tax/returns")) { auth(null); contentType(ContentType.Application.Json); setBody(kotlinx.serialization.json.buildJsonObject { put("year", JsonPrimitive(year)) }) }
    }

    suspend fun taxReturn(id: String): TaxReturn = call { client.get(url("/tax/returns/$id")) { auth(null) } }

    /** Uloží odpovědi průvodce (stejná struktura jako na webu) a vrátí přepočet. Rodné číslo jen když se mění. */
    suspend fun saveTaxReturn(id: String, data: kotlinx.serialization.json.JsonObject, rc: String?): TaxReturn = call {
        client.put(url("/tax/returns/$id")) {
            auth(null)
            contentType(ContentType.Application.Json)
            setBody(kotlinx.serialization.json.buildJsonObject {
                put("data", data)
                if (!rc.isNullOrBlank()) put("rc", JsonPrimitive(rc))
            })
        }
    }

    /** Fotky / PDF potvrzení o příjmech nebo přehledu OSVČ → přečtené údaje ke kontrole. pages = JPEG stránky ze skeneru. */
    suspend fun taxPhoto(id: String, pages: List<ByteArray>, pdf: ByteArray? = null): TaxPhoto = call {
        client.submitFormWithBinaryData(
            url = url("/tax/returns/$id/photo"),
            formData = formData {
                if (pages.isEmpty() && pdf != null) append("photo", pdf, Headers.build {
                    append(HttpHeaders.ContentType, "application/pdf"); append(HttpHeaders.ContentDisposition, "filename=\"doklad.pdf\"")
                })
                pages.take(4).forEachIndexed { i, data ->
                    append("photo", data, Headers.build { append(HttpHeaders.ContentType, "image/jpeg"); append(HttpHeaders.ContentDisposition, "filename=\"strana-${i + 1}.jpg\"") })
                }
            },
        ) { auth(null) }
    }

    /** Potvrdí (případně opravené) údaje a vloží je do přiznání. */
    suspend fun applyTaxPhoto(id: String, photoId: Int, values: Map<String, String>, fillS7: Boolean): TaxPhotoApplied = call {
        client.post(url("/tax/returns/$id/photo/$photoId/apply")) {
            auth(null)
            contentType(ContentType.Application.Json)
            setBody(kotlinx.serialization.json.buildJsonObject {
                put("values", kotlinx.serialization.json.buildJsonObject { values.forEach { (k, v) -> put(k, JsonPrimitive(v)) } })
                put("fill_s7", JsonPrimitive(fillS7))
            })
        }
    }

    /** fmt = xml | pdf */
    suspend fun taxReturnFile(id: String, fmt: String): ByteArray {
        val r = try { client.get(url("/tax/returns/$id/$fmt")) { auth(null) } } catch (e: Exception) {
            throw ApiException("Server není dostupný.")
        }
        return check(r).readRawBytes()
    }

    // ---------------------------------------------------------------- žádost nového klienta (bez přihlášení, portál 1.16)

    suspend fun applyOptions(): ApplyOptions = call { client.get(url("/apply/options")) }

    suspend fun applyAres(ico: String): ApplyCompany = call { client.get(url("/apply/ares/${ico.filter { it.isDigit() }}")) }

    suspend fun applyCode(email: String): ApplyCodeResult = call {
        client.post(url("/apply/code")) {
            contentType(ContentType.Application.Json)
            setBody(kotlinx.serialization.json.buildJsonObject { put("email", JsonPrimitive(email.trim())) })
        }
    }

    suspend fun applyVerify(requestId: String, code: String): ApplyVerifyResult = call {
        client.post(url("/apply/verify")) {
            contentType(ContentType.Application.Json)
            setBody(kotlinx.serialization.json.buildJsonObject { put("request_id", JsonPrimitive(requestId)); put("code", JsonPrimitive(code.trim())) })
        }
    }

    suspend fun applyPreview(body: kotlinx.serialization.json.JsonObject): ApplyResult = call {
        client.post(url("/apply/preview")) { contentType(ContentType.Application.Json); setBody(body) }
    }

    suspend fun applySubmit(body: kotlinx.serialization.json.JsonObject): ApplyResult = call {
        client.post(url("/apply")) { contentType(ContentType.Application.Json); setBody(body) }
    }

    // ---------------------------------------------------------------- podklady k přiznání (portál 1.16)

    suspend fun taxPacks(): List<TaxPack> = call { client.get(url("/tax-packs")) { auth() } }

    suspend fun taxPack(id: String): TaxPack = call { client.get(url("/tax-packs/$id")) { auth() } }

    suspend fun taxPackUpload(packId: String, itemId: Int, data: ByteArray, mime: String, name: String, source: String, pages: Int): TaxPackItem = call {
        client.submitFormWithBinaryData(
            url = url("/tax-packs/$packId/items/$itemId/files"),
            formData = formData {
                append("source", source)
                append("pages", pages.toString())
                append("file", data, Headers.build {
                    append(HttpHeaders.ContentType, mime)
                    append(HttpHeaders.ContentDisposition, "filename=\"$name\"")
                })
            },
        ) { auth() }
    }

    suspend fun taxPackValue(packId: String, itemId: Int, body: kotlinx.serialization.json.JsonObject): TaxPackItem = call {
        client.patch(url("/tax-packs/$packId/items/$itemId")) { auth(); contentType(ContentType.Application.Json); setBody(body) }
    }

    suspend fun taxPackDeleteFile(packId: String, fileId: String) {
        call<Detail> { client.delete(url("/tax-packs/$packId/files/$fileId")) { auth() } }
    }

    suspend fun taxPackFile(packId: String, fileId: String): ByteArray {
        val r = try { client.get(url("/tax-packs/$packId/files/$fileId")) { auth() } } catch (e: Exception) {
            throw ApiException("Server není dostupný.")
        }
        return check(r).readRawBytes()
    }

    suspend fun taxPackSubmit(packId: String): TaxPack = call { client.post(url("/tax-packs/$packId/submit")) { auth() } }

    // ---------------------------------------------------------------- podklady 1.17: doplnění, výsledek, podání, zprávy

    private fun obj(vararg pairs: Pair<String, kotlinx.serialization.json.JsonElement>) = kotlinx.serialization.json.JsonObject(pairs.toMap())

    suspend fun taxPackAddItem(packId: String, title: String): TaxPackItem = call {
        client.post(url("/tax-packs/$packId/items")) { auth(); contentType(ContentType.Application.Json); setBody(obj("title" to JsonPrimitive(title))) }
    }

    suspend fun taxPackAddSection(packId: String, incomes: List<String>, deductions: List<String>): TaxPack = call {
        client.post(url("/tax-packs/$packId/sections")) {
            auth(); contentType(ContentType.Application.Json)
            setBody(obj("incomes" to JsonArray(incomes.map { JsonPrimitive(it) }), "deductions" to JsonArray(deductions.map { JsonPrimitive(it) })))
        }
    }

    suspend fun taxPackFiling(packId: String, method: String, dsId: String): TaxPack = call {
        client.post(url("/tax-packs/$packId/filing")) { auth(); contentType(ContentType.Application.Json); setBody(obj("method" to JsonPrimitive(method), "ds_id" to JsonPrimitive(dsId))) }
    }

    suspend fun taxPackApprove(packId: String): TaxPack = call { client.post(url("/tax-packs/$packId/approve")) { auth() } }

    suspend fun taxPackHanded(packId: String): TaxPack = call { client.post(url("/tax-packs/$packId/handed")) { auth() } }

    /** what = result/pdf | result/xml | result/receipt | zip | poa */
    suspend fun taxPackDownload(packId: String, what: String): ByteArray {
        val r = try { client.get(url("/tax-packs/$packId/$what")) { auth() } } catch (e: Exception) {
            throw ApiException("Server není dostupný.")
        }
        return check(r).readRawBytes()
    }

    suspend fun messages(): List<OfficeMessage> = call { client.get(url("/messages")) { auth() } }

    suspend fun sendMessage(body: String): List<OfficeMessage> = call {
        client.post(url("/messages")) { auth(); contentType(ContentType.Application.Json); setBody(obj("body" to JsonPrimitive(body))) }
    }

    suspend fun upgradeRequest(note: String = ""): Detail = call {
        client.post(url("/upgrade-request")) { auth(); contentType(ContentType.Application.Json); setBody(obj("note" to JsonPrimitive(note))) }
    }
}
