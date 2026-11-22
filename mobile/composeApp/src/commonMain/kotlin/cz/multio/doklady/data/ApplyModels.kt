package cz.multio.doklady.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ---------------------------------------------------------------- žádost nového klienta (portál 1.16)

@Serializable
data class Choice(val key: String, val label: String, val hint: String? = null)

@Serializable
data class TaxRulesInfo(
    val year: Int? = null,
    @SerialName("dpp_limit") val dppLimit: Int? = null,
    @SerialName("dpc_limit") val dpcLimit: Int? = null,
    @SerialName("flat_rates") val flatRates: String? = null,
    val deadline: String? = null,
    @SerialName("note_annual") val noteAnnual: String? = null,
    @SerialName("note_withholding") val noteWithholding: String? = null,
    @SerialName("note_business") val noteBusiness: String? = null,
    @SerialName("note_annual_check") val noteAnnualCheck: String? = null,
    @SerialName("annual_deadline") val annualDeadline: String? = null,
)

@Serializable
data class TaxApplyOptions(
    val years: List<String> = emptyList(),
    val incomes: List<Choice> = emptyList(),
    val employers: List<Choice> = emptyList(),
    val declaration: List<Choice> = emptyList(),
    @SerialName("contract_kind") val contractKind: List<Choice> = emptyList(),
    @SerialName("yes_no") val yesNo: List<Choice> = emptyList(),
    val limit: List<Choice> = emptyList(),
    @SerialName("business_mode") val businessMode: List<Choice> = emptyList(),
    @SerialName("business_expenses") val businessExpenses: List<Choice> = emptyList(),
    val revenue: List<Choice> = emptyList(),
    val advances: List<Choice> = emptyList(),
    val deductions: List<Choice> = emptyList(),
    @SerialName("children_other") val childrenOther: List<Choice> = emptyList(),
    @SerialName("only_employment") val onlyEmployment: List<Choice> = emptyList(),
    @SerialName("annual_employers") val annualEmployers: List<Choice> = emptyList(),
    @SerialName("who_files") val whoFiles: List<Choice> = emptyList(),
    @SerialName("has_databox") val hasDatabox: List<Choice> = emptyList(),
    val rules: Map<String, TaxRulesInfo> = emptyMap(),
)

@Serializable
data class ApplyOptions(
    val fields: List<Choice> = emptyList(),
    val employees: List<Choice> = emptyList(),
    val bookkeeping: List<Choice> = emptyList(),
    val docs: List<Choice> = emptyList(),
    val vat: List<Choice> = emptyList(),
    val tax: TaxApplyOptions = TaxApplyOptions(),
)

@Serializable
data class ApplyCompany(
    val ico: String? = null,
    val name: String? = null,
    val dic: String? = null,
    val address: String? = null,
    @SerialName("vat_payer") val vatPayer: Boolean? = null,
)

@Serializable
data class ApplyCodeResult(@SerialName("request_id") val requestId: String, val minutes: Int = 15)

@Serializable
data class ApplyVerifyResult(val token: String)

@Serializable
data class ChecklistRow(val title: String, val note: String = "")

@Serializable
data class ApplyResult(val id: String? = null, val summary: String = "", val checklist: List<ChecklistRow> = emptyList())

// ---------------------------------------------------------------- podklady k přiznání

@Serializable
data class TaxPackProgress(val done: Int = 0, val total: Int = 0, @SerialName("missing_required") val missingRequired: Int = 0)

@Serializable
data class TaxPackFile(
    val id: String,
    val name: String,
    val size: Long = 0,
    val pages: Int = 1,
    val source: String = "upload",
    val url: String = "",
)

@Serializable
data class TaxPackItem(
    val id: Int,
    val key: String,
    val group: String,
    val title: String,
    val sub: String = "",
    val kind: String = "file",
    val required: Boolean = true,
    val filled: Boolean = false,
    val amount: String? = null,
    val expenses: String? = null,
    val text: String = "",
    val files: List<TaxPackFile> = emptyList(),
)

@Serializable
data class TaxPackGroup(val key: String, val title: String, val tag: String = "", val items: List<TaxPackItem> = emptyList())

@Serializable
data class TimelineStep(val label: String, val done: Boolean = false, val now: Boolean = false)

@Serializable
data class TaxResult(
    val kind: String = "",
    val label: String = "",
    val amount: String? = null,
    val note: String = "",
    val pdf: Boolean = false,
    val xml: Boolean = false,
    val receipt: Boolean = false,
)

@Serializable
data class TaxPack(
    val id: String,
    val year: Int,
    val kind: String = "tax",
    val state: String = "open",
    @SerialName("state_label") val stateLabel: String = "",
    val deadline: String? = null,
    val progress: TaxPackProgress = TaxPackProgress(),
    @SerialName("submitted_at") val submittedAt: String? = null,
    val editable: Boolean = true,
    @SerialName("can_delete_files") val canDeleteFiles: Boolean = true,
    val timeline: List<TimelineStep> = emptyList(),
    val result: TaxResult? = null,
    @SerialName("filing_method") val filingMethod: String? = null,
    @SerialName("ds_id") val dsId: String? = null,
    @SerialName("consent_at") val consentAt: String? = null,
    val price: String? = null,
    @SerialName("paid_at") val paidAt: String? = null,
    @SerialName("filed_at") val filedAt: String? = null,
    @SerialName("handed_at") val handedAt: String? = null,
    @SerialName("purged_at") val purgedAt: String? = null,
    @SerialName("client_updated_at") val clientUpdatedAt: String? = null,
    val summary: String = "",
    val groups: List<TaxPackGroup> = emptyList(),
    @SerialName("rent_expenses") val rentExpenses: List<Choice> = emptyList(),
    @SerialName("add_incomes") val addIncomes: List<Choice> = emptyList(),
    @SerialName("add_deductions") val addDeductions: List<Choice> = emptyList(),
    @SerialName("payment_url") val paymentUrl: String? = null,
)

@Serializable
data class OfficeMessage(
    val id: Int,
    val body: String,
    @SerialName("from_accountant") val fromAccountant: Boolean = false,
    val author: String = "",
    @SerialName("created_at") val createdAt: String = "",
)
