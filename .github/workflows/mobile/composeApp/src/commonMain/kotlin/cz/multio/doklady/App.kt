package cz.multio.doklady

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import cz.multio.doklady.data.Api
import cz.multio.doklady.data.Customer
import cz.multio.doklady.data.ReportSupplier
import cz.multio.doklady.ui.SupplierDocumentsScreen
import cz.multio.doklady.ui.SuppliersScreen
import cz.multio.doklady.platform.IncomingLink
import cz.multio.doklady.platform.PickedDocument
import androidx.compose.runtime.LaunchedEffect
import cz.multio.doklady.platform.PlatformBackHandler
import cz.multio.doklady.ui.CustomerEditScreen
import cz.multio.doklady.ui.DocumentDetailScreen
import cz.multio.doklady.ui.InvoiceDetailScreen
import cz.multio.doklady.ui.LoginScreen
import cz.multio.doklady.ui.MainScreen
import cz.multio.doklady.ui.NewInvoiceScreen
import cz.multio.doklady.ui.NewInvoiceState
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import cz.multio.doklady.ui.UploadScreen
import cz.multio.doklady.ui.UploadPreset
import cz.multio.doklady.ui.TaskDetailScreen
import cz.multio.doklady.ui.MissingScreen
import cz.multio.doklady.ui.ChainScreen
import cz.multio.doklady.ui.TaxListScreen
import cz.multio.doklady.ui.TaxWizardScreen
import cz.multio.doklady.ui.TripsScreen
import cz.multio.doklady.ui.NewTripScreen
import cz.multio.doklady.ui.OdometerScreen
import cz.multio.doklady.ui.BankItemScreen
import cz.multio.doklady.ui.DokladyTheme
import cz.multio.doklady.ui.InvoicesTab
import cz.multio.doklady.ui.LockScreen
import cz.multio.doklady.ui.ReceiptsScreen
import cz.multio.doklady.ui.ReportsTab
import cz.multio.doklady.ui.SecurityScreen
import cz.multio.doklady.ui.SettingsScreen
import cz.multio.doklady.ui.SimpleScreen
import cz.multio.doklady.data.AppLock
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text

sealed interface Screen {
    data class Login(val activationCode: String? = null) : Screen
    /** Úvod bez přihlášení (1.8): Jsem klient / Chci se stát klientem. */
    data object Welcome : Screen
    /** Žádost nového klienta. */
    data object Apply : Screen
    /** Podklady k přiznání (portál 1.16). */
    data class TaxPack(val id: String) : Screen
    /** Zprávy účetní a seznam podkladů všech let (1.9). */
    data object Messages : Screen
    /** Nastavení upozornění v telefonu (2.0). */
    data object Notifications : Screen
    data object TaxPacks : Screen
    class TaxPackPages(val packId: String, val itemId: Int, val title: String, val pages: List<ByteArray>) : Screen
    data object Main : Screen
    // 2.2: režim „Kancelář“
    data object OfficeApplications : Screen
    data class OfficeApplication(val id: String) : Screen
    data class OfficeThread(val id: String) : Screen
    data object OfficeDsInbox : Screen
    /** preset = předvyplněný druh dokladu (např. dodací list, který chybí v cestě faktury). */
    data class Upload(val picked: PickedDocument, val preset: UploadPreset = UploadPreset()) : Screen
    data class DocumentDetail(val id: String) : Screen
    data class InvoiceDetail(val id: String) : Screen
    /** Požadavek od účetní (úkol) s nahráním dokladů. */
    data class TaskDetail(val id: String) : Screen
    /** Doklady chybějící v cestách faktur. */
    data object Missing : Screen
    /** Cesta faktury od objednávky po fakturu. */
    data class Chain(val documentId: String) : Screen
    data object TaxList : Screen
    data class TaxWizard(val id: String) : Screen
    /** Kniha jízd (portál 0.44): jízdy za měsíc, nová jízda, stav tachometru z fotky. */
    data object Trips : Screen
    data class NewTrip(val vehicleId: Int) : Screen
    data object Odometer : Screen
    /** Režim Účtenky (1.5): každá fotka = samostatný doklad. */
    class Receipts(val pages: List<ByteArray>) : Screen
    data class BankItemDetail(val id: String) : Screen
    data object Security : Screen
    data object Settings : Screen
    data object Invoices : Screen
    data object Reports : Screen
    /** Údaje firmy pro faktury (účet, IBAN) a AI asistent – portál 1.14. */
    data object CompanyProfile : Screen
    data object Assistant : Screen
    data class NewInvoice(val state: NewInvoiceState = NewInvoiceState()) : Screen
    data class CustomerEdit(val customer: Customer?, val onSaved: (Customer) -> Unit) : Screen
    /** Seznam dodavatelů za období; label = název období („Říjen 2026“). */
    data class Suppliers(val from: String, val to: String, val label: String) : Screen
    data class SupplierDocuments(val supplier: ReportSupplier, val from: String, val to: String, val label: String) : Screen
}

/** Jednoduchá navigace se zásobníkem obrazovek (bez další knihovny). */
class Navigator(start: Screen) {
    val stack = mutableStateListOf(start)
    val current get() = stack.last()
    fun push(s: Screen) { stack.add(s) }
    fun pop() { if (stack.size > 1) stack.removeAt(stack.lastIndex) }
    fun replace(s: Screen) { stack[stack.lastIndex] = s }
    fun reset(s: Screen) { stack.clear(); stack.add(s) }
}

@Composable
fun App() {
    DokladyTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            val nav = remember { Navigator(if (Api.token.isNullOrBlank() && !AppLock.locked) Screen.Welcome else Screen.Main) }
            // odkaz z pozvánky (e-mail / QR) → aktivace portálu
            val link = IncomingLink.activationCode
            LaunchedEffect(link) {
                if (link != null) {
                    IncomingLink.activationCode = null
                    if (Api.token.isNullOrBlank()) nav.reset(Screen.Login(link))
                }
            }
            // 2.0: verze aplikace – nabídka aktualizace, příliš stará verze se zastaví
            var release by remember { androidx.compose.runtime.mutableStateOf<cz.multio.doklady.data.AppVersionInfo?>(null) }
            var updateOffer by remember { androidx.compose.runtime.mutableStateOf(false) }
            LaunchedEffect(Unit) {
                release = runCatching { Api.appVersion() }.getOrNull()
                val r = release
                updateOffer = r != null && r.latestCode > AppConfig.VERSION_CODE && cz.multio.doklady.platform.KeyValueStore.get("update_dismissed") != r.latestCode.toString()
            }
            release?.takeIf { it.minCode > AppConfig.VERSION_CODE }?.let { cz.multio.doklady.ui.UpdateRequiredScreen(it); return@Surface }
            if (updateOffer) release?.let { cz.multio.doklady.ui.UpdateDialog(it) { updateOffer = false } }
            // 2.0: po přihlášení zaregistrovat telefon pro upozornění; klepnutí na upozornění otevře, co se týká
            val onMain = nav.current is Screen.Main
            LaunchedEffect(onMain) {
                if (onMain) { cz.multio.doklady.platform.PushPlatform.requestPermission(); cz.multio.doklady.data.PushSetup.register() }
            }
            val openUrl = cz.multio.doklady.platform.IncomingNotification.url
            LaunchedEffect(openUrl, onMain) {
                if (openUrl != null && !Api.token.isNullOrBlank()) {
                    cz.multio.doklady.platform.IncomingNotification.url = null
                    Regex("/ukoly/([0-9a-f-]{36})").find(openUrl)?.let { nav.push(Screen.TaskDetail(it.groupValues[1])) }
                        ?: Regex("/doklad/([0-9a-f-]{36})").find(openUrl)?.let { nav.push(Screen.DocumentDetail(it.groupValues[1])) }
                        ?: if (openUrl.contains("komunikace")) nav.push(Screen.Messages) else Unit
                }
            }
            if (AppLock.locked) {
                LockScreen(onUnlocked = { AppLock.locked = false }, onUsePassword = {
                    Api.logout(); AppLock.locked = false; nav.reset(Screen.Login())
                })
                return@Surface
            }
            PlatformBackHandler(enabled = nav.stack.size > 1) { nav.pop() }
            val saveable = rememberSaveableStateHolder()
            val s = nav.current
            // stav (např. vybraná záložka) se uchová i po návratu ze detailu
            saveable.SaveableStateProvider("${nav.stack.lastIndex}-${s::class.simpleName}") {
            when (s) {
                is Screen.Login -> LoginScreen(onLoggedIn = { nav.reset(Screen.Main) }, activationCode = s.activationCode,
                    onApply = { nav.push(Screen.Apply) })
                Screen.Welcome -> cz.multio.doklady.ui.WelcomeScreen(nav)
                Screen.Apply -> cz.multio.doklady.ui.ApplyScreen(nav)
                is Screen.TaxPack -> cz.multio.doklady.ui.TaxPackScreen(nav, s.id)
                Screen.Messages -> cz.multio.doklady.ui.MessagesScreen(nav)
                Screen.Notifications -> cz.multio.doklady.ui.NotificationSettingsScreen(nav)
                Screen.TaxPacks -> cz.multio.doklady.ui.TaxPacksScreen(nav)
                is Screen.TaxPackPages -> cz.multio.doklady.ui.TaxPackPagesScreen(nav, s.packId, s.itemId, s.title, s.pages)
                Screen.Main -> MainScreen(nav)
                Screen.OfficeApplications -> cz.multio.doklady.ui.OfficeApplicationsScreen(nav)
                is Screen.OfficeApplication -> cz.multio.doklady.ui.OfficeApplicationScreen(nav, s.id)
                is Screen.OfficeThread -> cz.multio.doklady.ui.OfficeThreadScreen(nav, s.id)
                Screen.OfficeDsInbox -> cz.multio.doklady.ui.OfficeDsInboxScreen(nav)
                is Screen.Upload -> UploadScreen(nav, s.picked, s.preset)
                is Screen.DocumentDetail -> DocumentDetailScreen(nav, s.id)
                is Screen.InvoiceDetail -> InvoiceDetailScreen(nav, s.id)
                is Screen.NewInvoice -> NewInvoiceScreen(nav, s.state)
                is Screen.CustomerEdit -> CustomerEditScreen(nav, s.customer, s.onSaved)
                is Screen.Suppliers -> SuppliersScreen(nav, s.from, s.to, s.label)
                is Screen.SupplierDocuments -> SupplierDocumentsScreen(nav, s.supplier, s.from, s.to, s.label)
                is Screen.TaskDetail -> TaskDetailScreen(nav, s.id)
                Screen.Missing -> MissingScreen(nav)
                is Screen.Chain -> ChainScreen(nav, s.documentId)
                Screen.TaxList -> TaxListScreen(nav)
                is Screen.TaxWizard -> TaxWizardScreen(nav, s.id)
                Screen.Trips -> TripsScreen(nav)
                is Screen.NewTrip -> NewTripScreen(nav, s.vehicleId)
                Screen.Odometer -> OdometerScreen(nav)
                is Screen.Receipts -> ReceiptsScreen(nav, s.pages)
                is Screen.BankItemDetail -> BankItemScreen(nav, s.id)
                Screen.Security -> SecurityScreen(nav)
                Screen.Settings -> SettingsScreen(nav)
                Screen.Invoices -> SimpleScreen(nav, "Faktury", fab = {
                    ExtendedFloatingActionButton(onClick = { nav.push(Screen.NewInvoice()) }, icon = { Icon(Icons.Filled.Add, null) }, text = { Text("Nová faktura") })
                }) { InvoicesTab(nav) }
                Screen.Reports -> SimpleScreen(nav, "Přehledy") { ReportsTab(nav) }
                Screen.CompanyProfile -> cz.multio.doklady.ui.CompanyProfileScreen(nav)
                Screen.Assistant -> cz.multio.doklady.ui.AssistantScreen(nav)
            }
            }
        }
    }
}
