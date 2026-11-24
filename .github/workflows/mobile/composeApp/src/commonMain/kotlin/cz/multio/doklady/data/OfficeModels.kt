package cz.multio.doklady.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// 2.2: režim „Kancelář“ – jen pro účetní a administrátora

@Serializable
data class OfficeApp(
    val id: String,
    val state: String = "new",
    @SerialName("state_label") val stateLabel: String = "",
    @SerialName("service_label") val serviceLabel: String = "",
    val name: String = "",
    @SerialName("contact_name") val contactName: String = "",
    val email: String = "",
    val phone: String = "",
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("email_verified") val emailVerified: Boolean = false,
    val description: String? = null,
    val note: String? = null,
    val checklist: List<String> = emptyList(),
    @SerialName("suggested_username") val suggestedUsername: String? = null,
)

@Serializable
data class OfficeThread(
    val id: String,
    @SerialName("client_id") val clientId: Int = 0,
    val client: String = "",
    val subject: String = "",
    val unread: Boolean = false,
    @SerialName("last_at") val lastAt: String? = null,
    val last: String = "",
    val messages: List<OfficeThreadMessage> = emptyList(),
)

@Serializable
data class OfficeThreadMessage(
    val id: Int,
    val body: String,
    @SerialName("from_accountant") val fromAccountant: Boolean = false,
    val author: String = "",
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class OfficeTaxUpdate(val id: String, @SerialName("client_id") val clientId: Int = 0, val client: String = "", val year: Int = 0, val kind: String = "tax")

@Serializable
data class OfficeDs(
    val id: String,
    val sender: String = "",
    val annotation: String = "",
    @SerialName("received_at") val receivedAt: String? = null,
    val files: List<String> = emptyList(),
    @SerialName("suggested_client_id") val suggestedClientId: Int? = null,
    @SerialName("suggested_client") val suggestedClient: String? = null,
    val reason: String = "",
)

@Serializable
data class OfficeBlock<T>(val count: Int = 0, val items: List<T> = emptyList())

@Serializable
data class OfficeToday(
    val admin: Boolean = false,
    val applications: OfficeBlock<OfficeApp> = OfficeBlock(),
    val threads: OfficeBlock<OfficeThread> = OfficeBlock(),
    @SerialName("tax_updates") val taxUpdates: OfficeBlock<OfficeTaxUpdate> = OfficeBlock(),
    @SerialName("ds_inbox") val dsInbox: OfficeBlock<OfficeDs> = OfficeBlock(),
)

@Serializable
data class OfficeSeasonItem(
    val id: String,
    @SerialName("client_id") val clientId: Int = 0,
    val client: String = "",
    val kind: String = "tax",
    val state: String = "",
    @SerialName("state_label") val stateLabel: String = "",
    val deadline: String? = null,
    val progress: TaxProgressInfo = TaxProgressInfo(),
    @SerialName("needs_review") val needsReview: Boolean = false,
)

@Serializable
data class TaxProgressInfo(val done: Int = 0, val total: Int = 0, @SerialName("missing_required") val missingRequired: Int = 0)

@Serializable
data class OfficeSeason(val year: Int = 0, val items: List<OfficeSeasonItem> = emptyList())

@Serializable
data class OfficeOk(val ok: Boolean = true, val reminded: Int = 0, @SerialName("client_id") val clientId: Int? = null, @SerialName("invite_sent") val inviteSent: Boolean = false)
