package cz.multio.doklady.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

// Částky posílá server jako text ("1434.00") – nepřichází se tak o přesnost.

@Serializable
data class LoginRequest(val email: String, val password: String)

@Serializable
data class ActivateRequest(val code: String, val password: String)

@Serializable
data class Company(
    val id: Int,
    val name: String,
    val ico: String? = null,
    val dic: String? = null,
    @SerialName("vat_payer") val vatPayer: Boolean = true,
    /** Firma má v portálu vyplněné údaje pro faktury. */
    @SerialName("has_profile") val hasProfile: Boolean = true,
    /** Moduly edice portálu (invoicing, invoicing_plus, …). Prázdné = starší server, vše povoleno. */
    val modules: List<String> = emptyList(),
    /** Název edice (balíčku) portálu; null = bez edice (vše povoleno). Portál 1.14. */
    val edition: String? = null,
    /** {modul: nejlevnější balíček, který ho obsahuje} pro moduly, které firma nemá. */
    val upgrade: Map<String, String> = emptyMap(),
    /** full | tax | annual – klient „jen přiznání“ / „roční zúčtování“ vidí jen své podklady (portál 1.17). */
    val access: String = "full",
) {
    val limited get() = access == "tax" || access == "annual"
    fun has(module: String) = modules.isEmpty() || module in modules

    /** null = funkce je v balíčku; jinak název balíčku, ve kterém je („Expert“). */
    fun lockedIn(module: String): String? = if (has(module)) null else (upgrade[module] ?: "vyšší balíček")
}

@Serializable
data class User(
    val id: Int,
    val email: String,
    val name: String? = null,
    val role: String,
    @SerialName("company_id") val companyId: Int? = null,
) {
    val isAdmin get() = role == "admin"
}

@Serializable
data class LoginResponse(val token: String, val user: User, val companies: List<Company>)

@Serializable
data class Document(
    /** UUID dokladu v portálu (text). */
    val id: String,
    @SerialName("company_id") val companyId: Int,
    @SerialName("created_at") val createdAt: String,
    val source: String = "mobile",
    val status: String,
    val error: String? = null,
    @SerialName("original_filename") val originalFilename: String? = null,
    @SerialName("original_mime") val originalMime: String,
    @SerialName("has_processed") val hasProcessed: Boolean = false,
    @SerialName("doc_type") val docType: String? = null,
    @SerialName("doc_number") val docNumber: String? = null,
    @SerialName("variable_symbol") val variableSymbol: String? = null,
    @SerialName("supplier_name") val supplierName: String? = null,
    @SerialName("supplier_ico") val supplierIco: String? = null,
    @SerialName("supplier_dic") val supplierDic: String? = null,
    @SerialName("supplier_address") val supplierAddress: String? = null,
    @SerialName("supplier_account") val supplierAccount: String? = null,
    @SerialName("customer_name") val customerName: String? = null,
    @SerialName("customer_ico") val customerIco: String? = null,
    @SerialName("customer_dic") val customerDic: String? = null,
    @SerialName("issue_date") val issueDate: String? = null,
    @SerialName("taxable_date") val taxableDate: String? = null,
    @SerialName("due_date") val dueDate: String? = null,
    val currency: String? = null,
    val total: String? = null,
    @SerialName("total_base") val totalBase: String? = null,
    @SerialName("total_vat") val totalVat: String? = null,
    @SerialName("vat_breakdown") val vatBreakdown: JsonElement? = null,
    @SerialName("payment_method") val paymentMethod: String? = null,
    @SerialName("reverse_charge") val reverseCharge: Boolean? = null,
    val confidence: Double? = null,
    @SerialName("extraction_method") val extractionMethod: String? = null,
    val checks: Map<String, Boolean>? = null,
    val note: String? = null,
    // rozšíření portálu Multio
    @SerialName("accounting_block") val accountingBlock: String? = null,
    val direction: String = "purchase",
    /** final | order | proforma | advance | delivery_note */
    val stage: String = "final",
    @SerialName("stage_label") val stageLabel: String? = null,
    @SerialName("cost_center") val costCenter: String? = null,
    val job: String? = null,
    @SerialName("chain_id") val chainId: Int? = null,
    /** Portál 1.15: druh podle klienta („Za co to je?“), návrh portálu a jestli ho ještě jde změnit. */
    @SerialName("client_category") val clientCategory: CategoryOption? = null,
    @SerialName("category_suggestion") val categorySuggestion: CategoryOption? = null,
    @SerialName("category_editable") val categoryEditable: Boolean = false,
) {
    val isPdf get() = originalMime == "application/pdf"
    val isWorking get() = status == "processing" || status == "uploaded"
}

/** Pole, která jde v aplikaci opravit (PATCH /api/documents/{id}). Null = neměnit. */
@Serializable
data class DocumentPatch(
    @SerialName("doc_type") val docType: String? = null,
    @SerialName("doc_number") val docNumber: String? = null,
    @SerialName("variable_symbol") val variableSymbol: String? = null,
    @SerialName("supplier_name") val supplierName: String? = null,
    @SerialName("supplier_ico") val supplierIco: String? = null,
    @SerialName("supplier_dic") val supplierDic: String? = null,
    @SerialName("issue_date") val issueDate: String? = null,
    @SerialName("taxable_date") val taxableDate: String? = null,
    @SerialName("due_date") val dueDate: String? = null,
    val currency: String? = null,
    val total: String? = null,
    @SerialName("total_base") val totalBase: String? = null,
    @SerialName("total_vat") val totalVat: String? = null,
    @SerialName("payment_method") val paymentMethod: String? = null,
    val note: String? = null,
    val status: String? = null,
)

@Serializable
data class Customer(
    val id: Int = 0,
    val name: String,
    val ico: String? = null,
    val dic: String? = null,
    val street: String? = null,
    val city: String? = null,
    val zip: String? = null,
    val country: String = "Česká republika",
    val email: String? = null,
    @SerialName("due_days") val dueDays: Int? = null,
    val note: String? = null,
)

@Serializable
data class AresResult(
    val name: String? = null,
    val ico: String? = null,
    val dic: String? = null,
    val street: String? = null,
    val city: String? = null,
    val zip: String? = null,
    val country: String? = null,
)

@Serializable
data class InvoiceItem(
    val id: Int? = null,
    val description: String,
    val quantity: String = "1",
    val unit: String? = null,
    @SerialName("unit_price") val unitPrice: String,
    @SerialName("vat_rate") val vatRate: Int = 21,
)

@Serializable
data class Invoice(
    /** UUID faktury v portálu (text). */
    val id: String,
    val number: String,
    @SerialName("variable_symbol") val variableSymbol: String,
    val customer: Customer,
    @SerialName("issue_date") val issueDate: String,
    @SerialName("taxable_date") val taxableDate: String,
    @SerialName("due_date") val dueDate: String,
    val currency: String = "CZK",
    @SerialName("reverse_charge") val reverseCharge: Boolean = false,
    val status: String,
    @SerialName("sent_at") val sentAt: String? = null,
    @SerialName("sent_to") val sentTo: String? = null,
    @SerialName("total_base") val totalBase: String,
    @SerialName("total_vat") val totalVat: String,
    val total: String,
    val note: String? = null,
    val items: List<InvoiceItem> = emptyList(),
    @SerialName("document_id") val documentId: String? = null,
    /** Faktura vystavená, ale něco se nepovedlo (např. odeslání e-mailem). */
    val warning: String? = null,
    @SerialName("payment_method") val paymentMethod: String? = null,
    @SerialName("order_number") val orderNumber: String? = null,
    @SerialName("cost_center") val costCenter: String? = null,
    val job: String? = null,
    @SerialName("show_qr") val showQr: Boolean = true,
)

@Serializable
data class InvoiceCreate(
    @SerialName("customer_id") val customerId: Int,
    val items: List<InvoiceItem>,
    @SerialName("issue_date") val issueDate: String? = null,
    @SerialName("due_days") val dueDays: Int? = null,
    val currency: String = "CZK",
    @SerialName("reverse_charge") val reverseCharge: Boolean = false,
    @SerialName("payment_method") val paymentMethod: String = "prevod",
    val note: String? = null,
    @SerialName("send_to") val sendTo: String? = null,
    /** Portál 1.14: číslo objednávky (tiskne se), středisko a zakázka (jen pro účetnictví), QR kód, DUZP. */
    @SerialName("order_number") val orderNumber: String? = null,
    @SerialName("cost_center") val costCenter: String? = null,
    val job: String? = null,
    @SerialName("show_qr") val showQr: Boolean = true,
    @SerialName("taxable_date") val taxableDate: String? = null,
)

@Serializable
data class SendRequest(val email: String? = null, val message: String? = null)

@Serializable
data class RecurringCreate(
    @SerialName("customer_id") val customerId: Int,
    val name: String,
    @SerialName("interval_months") val intervalMonths: Int,
    @SerialName("next_date") val nextDate: String,
    @SerialName("due_days") val dueDays: Int? = null,
    val currency: String = "CZK",
    @SerialName("reverse_charge") val reverseCharge: Boolean = false,
    val note: String? = null,
    val items: List<InvoiceItem>,
    @SerialName("auto_send") val autoSend: Boolean = true,
    @SerialName("email_to") val emailTo: String? = null,
)

@Serializable
data class Recurring(
    val id: String,
    val customer: Customer,
    val name: String,
    @SerialName("interval_months") val intervalMonths: Int,
    @SerialName("next_date") val nextDate: String,
    @SerialName("auto_send") val autoSend: Boolean,
    @SerialName("email_to") val emailTo: String? = null,
    val active: Boolean,
    @SerialName("last_error") val lastError: String? = null,
    val warning: String? = null,
)

@Serializable
data class ApiError(val detail: JsonElement? = null)

// ---------------------------------------------------------------- přehledy (GET /api/reports/summary)

@Serializable
data class VatRow(val rate: Int, val base: String, val vat: String)

@Serializable
data class ReportDocuments(
    val count: Int,
    val total: String,
    @SerialName("by_status") val byStatus: Map<String, Int> = emptyMap(),
    @SerialName("to_check") val toCheck: Int = 0,
)

@Serializable
data class ReportInvoices(val count: Int, val total: String)

@Serializable
data class ReportVat(
    val received: List<VatRow> = emptyList(),
    val issued: List<VatRow> = emptyList(),
    @SerialName("input_total") val inputTotal: String,
    @SerialName("output_total") val outputTotal: String,
    val balance: String,
)

@Serializable
data class ReportSupplier(
    val key: String = "",
    val name: String,
    val ico: String? = null,
    val count: Int,
    val total: String,
    val vat: String? = null,
)

@Serializable
data class ReportSummary(
    val from: String,
    val to: String,
    val documents: ReportDocuments,
    val invoices: ReportInvoices,
    val vat: ReportVat,
    val suppliers: List<ReportSupplier> = emptyList(),
    val note: String? = null,
)


// ---------------------------------------------------------------- portál Multio (verze API 0.32)

@Serializable
data class PasswordResetRequest(val email: String)

@Serializable
data class Detail(val detail: String? = null)

@Serializable
data class Dimension(val code: String, val name: String = "")

@Serializable
data class Dimensions(val centres: List<Dimension> = emptyList(), val jobs: List<Dimension> = emptyList())

/** Požadavek účetní (úkol): např. „Výpis z banky – září“. */
@Serializable
data class TaskItem(
    val id: String,
    val title: String,
    val description: String = "",
    /** open | submitted | done | cancelled */
    val state: String,
    @SerialName("state_label") val stateLabel: String = "",
    @SerialName("due_date") val dueDate: String? = null,
    val overdue: Boolean = false,
    /** many | one | none */
    val uploads: String = "many",
    @SerialName("review_note") val reviewNote: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    val response: String? = null,
    val documents: List<Document> = emptyList(),
) {
    val isOpen get() = state == "open"
}

/** Doklad, který chybí v cestě faktury (objednávka, dodací list, záloha, faktura). */
@Serializable
data class MissingItem(
    val key: String,
    /** order | proforma | advance | delivery_note | final */
    val stage: String,
    val label: String,
    val number: String? = null,
    val reason: String = "",
    val requested: String? = null,
    val partner: String? = null,
    val direction: String = "purchase",
    @SerialName("chain_id") val chainId: Int? = null,
    @SerialName("document_id") val documentId: String? = null,
)

@Serializable
data class ChainDoc(
    val id: String,
    val stage: String,
    @SerialName("stage_label") val stageLabel: String = "",
    @SerialName("doc_number") val docNumber: String? = null,
    @SerialName("issue_date") val issueDate: String? = null,
    val total: String? = null,
    val currency: String? = null,
    val status: String = "",
)

@Serializable
data class ChainStep(
    val stage: String,
    val label: String,
    val documents: List<ChainDoc> = emptyList(),
    val missing: List<MissingItem> = emptyList(),
)

@Serializable
data class ChainProblem(val item: String, val text: String)

/** Cesta faktury: objednávka → zálohová faktura → daňový doklad k záloze → dodací list → faktura. */
@Serializable
data class ChainInfo(
    val partner: String? = null,
    val steps: List<ChainStep> = emptyList(),
    val missing: List<MissingItem> = emptyList(),
    val problems: List<ChainProblem> = emptyList(),
)


// ---------------------------------------------------------------- daň z příjmů FO (portál 0.49)

@kotlinx.serialization.Serializable
data class TaxReturnList(val years: List<Int> = emptyList(), val returns: List<TaxReturn> = emptyList())

@kotlinx.serialization.Serializable
data class TaxReturn(
    val id: String,
    val year: Int,
    val kind: String = "dpfo",
    val state: String = "draft",
    @kotlinx.serialization.SerialName("updated_at") val updatedAt: String? = null,
    @kotlinx.serialization.SerialName("result_amount") val resultAmount: String? = null,
    @kotlinx.serialization.SerialName("result_kind") val resultKind: String? = null,
    val data: kotlinx.serialization.json.JsonObject? = null,
    val result: kotlinx.serialization.json.JsonObject? = null,
    @kotlinx.serialization.SerialName("has_rc") val hasRc: Boolean = false,
)


// ---------------------------------------------------------------- fotka potvrzení / přehledu OSVČ (portál 0.52)

@kotlinx.serialization.Serializable
data class TaxPhoto(
    val id: Int,
    val kind: String,
    val label: String = "",
    val values: Map<String, String> = emptyMap(),
    val warnings: List<String> = emptyList(),
    val source: String = "ai",
)

@kotlinx.serialization.Serializable
data class TaxPhotoApplied(
    val id: String,
    val year: Int,
    val state: String = "draft",
    val data: kotlinx.serialization.json.JsonObject? = null,
    val result: kotlinx.serialization.json.JsonObject? = null,
    @kotlinx.serialization.SerialName("has_rc") val hasRc: Boolean = false,
    val applied: List<String> = emptyList(),
) {
    fun toReturn() = TaxReturn(id = id, year = year, state = state, data = data, result = result, hasRc = hasRc)
}


// ---------------------------------------------------------------- kniha jízd (portál 0.44, aplikace 1.4)

@Serializable
data class Vehicle(
    val id: Int,
    val name: String,
    val plate: String = "",
    val use: String = "business",
    val fuel: String = "",
    @SerialName("odometer_start") val odometerStart: Int? = null,
    val home: String? = null,
    @SerialName("default_driver") val defaultDriver: String? = null,
    val active: Boolean = true,
) {
    val label get() = listOf(name, plate).filter { it.isNotBlank() }.joinToString(" · ")
}

@Serializable
data class Trip(
    val id: Int,
    @SerialName("vehicle_id") val vehicleId: Int,
    val date: String,
    @SerialName("time_from") val timeFrom: String? = null,
    @SerialName("time_to") val timeTo: String? = null,
    val driver: String? = null,
    val purpose: String? = null,
    val points: List<String> = emptyList(),
    val kind: String = "business",
    @SerialName("distance_km") val distanceKm: String = "0",
    @SerialName("calc_km") val calcKm: String? = null,
    val source: String = "",
    @SerialName("odometer_start") val odometerStart: Int? = null,
    @SerialName("odometer_end") val odometerEnd: Int? = null,
    val confirmed: Boolean = false,
    val note: String? = null,
    @SerialName("client_ref") val clientRef: String? = null,
)

/** Nová jízda. client_ref = jedinečné ID z telefonu – opakované odeslání nevytvoří jízdu dvakrát. */
@Serializable
data class TripCreate(
    @SerialName("vehicle_id") val vehicleId: Int,
    val date: String,
    @SerialName("time_from") val timeFrom: String? = null,
    @SerialName("time_to") val timeTo: String? = null,
    val driver: String? = null,
    val purpose: String? = null,
    val points: List<String>,
    val kind: String = "business",
    @SerialName("distance_km") val distanceKm: String? = null,
    @SerialName("calc_km") val calcKm: String? = null,
    @SerialName("calc_minutes") val calcMinutes: Int? = null,
    @SerialName("odometer_start") val odometerStart: Int? = null,
    @SerialName("odometer_end") val odometerEnd: Int? = null,
    val confirmed: Boolean = false,
    val note: String? = null,
    @SerialName("client_ref") val clientRef: String,
)

@Serializable
data class RouteRequest(val points: List<String>)

@Serializable
data class RouteResult(
    val km: String,
    val minutes: Int? = null,
    val legs: List<String> = emptyList(),
    @SerialName("maps_url") val mapsUrl: String? = null,
    val warnings: List<String> = emptyList(),
)

@Serializable
data class OdometerSuggestion(val km: Int? = null, val note: String = "", val method: String = "")

/** Odpověď /api/odometer: bez km jen návrh z fotky (saved=false), s km uložený stav a porovnání s jízdami. */
@Serializable
data class OdometerResult(
    val saved: Boolean = false,
    val suggested: OdometerSuggestion? = null,
    val id: Int? = null,
    val km: Int? = null,
    val driven: String? = null,
    val recorded: String? = null,
    val gap: String? = null,
    val state: String? = null,
    @SerialName("photo_warning") val photoWarning: String? = null,
)


// ---------------------------------------------------------------- přehled (portál 1.13)

@Serializable
data class HomeCounts(
    val todo: Int = 0,
    @SerialName("at_accountant") val atAccountant: Int = 0,
    @SerialName("done_month") val doneMonth: Int = 0,
)

/** kind = bank | task | readability */
@Serializable
data class TodoItem(val kind: String, val id: String? = null, val title: String, val subtitle: String = "")

@Serializable
data class Home(val counts: HomeCounts = HomeCounts(), val todo: List<TodoItem> = emptyList(), val recent: List<Document> = emptyList())

// ---------------------------------------------------------------- banka (portál 1.13)

@Serializable
data class BankAccount(
    val account: String? = null,
    val label: String = "",
    /** fio = stahuje se samo, statement = nahraný výpis */
    val source: String = "statement",
    val active: Boolean = true,
    @SerialName("updated_at") val updatedAt: String? = null,
)

@Serializable
data class BankAnswerOption(
    val id: Int,
    val label: String,
    @SerialName("note_required") val noteRequired: Boolean = false,
    @SerialName("no_document") val noDocument: Boolean = true,
)

@Serializable
data class BankEventInfo(val at: String, val message: String)

/** state = missing (účetní chce doklad) | open (bez dokladu, nevyžádáno) | waiting | paired | not_needed | split | other */
@Serializable
data class BankItem(
    val id: String,
    val date: String,
    val amount: String,
    val currency: String = "CZK",
    val counterparty: String? = null,
    @SerialName("counterparty_account") val counterpartyAccount: String? = null,
    @SerialName("variable_symbol") val variableSymbol: String? = null,
    val description: String? = null,
    val state: String = "other",
    @SerialName("state_label") val stateLabel: String = "",
    val requested: Boolean = false,
    @SerialName("document_id") val documentId: String? = null,
    @SerialName("document_label") val documentLabel: String? = null,
    val answer: String? = null,
    @SerialName("answer_note") val answerNote: String? = null,
    val reason: String? = null,
    @SerialName("description_full") val descriptionFull: String? = null,
    val answers: List<BankAnswerOption> = emptyList(),
    val events: List<BankEventInfo> = emptyList(),
) {
    val isOutgoing get() = amount.startsWith("-")
    val needsDocument get() = state == "missing" || state == "open"
}

@Serializable
data class BankList(
    val from: String = "",
    val to: String = "",
    val accounts: List<BankAccount> = emptyList(),
    @SerialName("missing_total") val missingTotal: Int = 0,
    val items: List<BankItem> = emptyList(),
)

@Serializable
data class BankAnswerRequest(@SerialName("answer_id") val answerId: Int? = null, val note: String? = null, val clear: Boolean = false)

@Serializable
data class StatementMessage(val level: String, val text: String)

@Serializable
data class StatementResult(val ok: Boolean = false, val messages: List<StatementMessage> = emptyList())

// ---------------------------------------------------------------- přístupové klíče (portál 1.13)

@Serializable
data class PasskeyChallenge(val options: JsonElement, val state: String)

@Serializable
data class PasskeyInfo(
    val id: Int,
    val name: String,
    @SerialName("created_at") val createdAt: String = "",
    @SerialName("last_used_at") val lastUsedAt: String? = null,
    val synced: Boolean = false,
)

@Serializable
data class PasskeyLoginRequest(val credential: JsonElement, val state: String)

@Serializable
data class PasskeyRegisterRequest(val credential: JsonElement, val state: String, val name: String)


// ---------------------------------------------------------------- údaje firmy (portál 1.14)

@Serializable
data class CompanyProfileData(
    val name: String? = null,
    val ico: String? = null,
    val dic: String? = null,
    val street: String? = null,
    val city: String? = null,
    val zip: String? = null,
    val country: String? = null,
    @SerialName("vat_payer") val vatPayer: Boolean = false,
    @SerialName("vat_period") val vatPeriod: String? = null,
    @SerialName("bank_account") val bankAccount: String? = null,
    val iban: String? = null,
    @SerialName("registry_note") val registryNote: String? = null,
    @SerialName("default_due_days") val defaultDueDays: Int? = null,
    val email: String? = null,
    /** Účet / IBAN je vyplněný tak, že jde vytvořit QR platbu. */
    @SerialName("qr_ready") val qrReady: Boolean = false,
)

// ---------------------------------------------------------------- AI asistent (portál 1.14, balíček Expert)

@Serializable
data class AssistantRow(val label: String, val note: String? = null, val amount: String? = null)

@Serializable
data class AssistantTurn(
    val id: Int,
    val question: String,
    @SerialName("created_at") val createdAt: String = "",
    val legal: Boolean = false,
    val help: Boolean = false,
    val understood: String? = null,
    val title: String? = null,
    val answer: String? = null,
    val rows: List<AssistantRow> = emptyList(),
    val total: String? = null,
    val net: Boolean = false,
    val note: String? = null,
    val examples: List<String> = emptyList(),
)

@Serializable
data class AssistantState(val turns: List<AssistantTurn> = emptyList(), val suggestions: List<String> = emptyList())

@Serializable
data class AssistantReply(val turn: AssistantTurn, val suggestions: List<String> = emptyList())

@Serializable
data class AssistantQuestion(val question: String)

@Serializable
data class AssistantForwardResult(val ok: Boolean = false, val detail: String = "")


// ---------------------------------------------------------------- druh dokladu podle klienta (portál 1.15)

@Serializable
data class CategoryOption(val id: Int, val name: String, val icon: String = "other", val explanation: String? = null, val reason: String? = null)

@Serializable
data class CategoriesInfo(
    val enabled: Boolean = false,
    @SerialName("locked_in") val lockedIn: String? = null,
    val bookkeeping: String? = null,
    val options: List<CategoryOption> = emptyList(),
    val frequent: List<Int> = emptyList(),
)
