package cz.multio.doklady.platform

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap
import io.ktor.client.HttpClient

/** Výsledek skenování nebo výběru souboru. */
class PickedDocument(
    /** Upravené stránky (JPEG) – oříznuté, srovnané, s vyrovnaným osvětlením. U souboru z galerie prázdné. */
    val pages: List<ByteArray>,
    /** Celý doklad jako PDF (sken se všemi stranami), nebo vybraný PDF soubor. */
    val pdf: ByteArray?,
    /** Neupravený soubor vybraný z galerie / souborů. */
    val rawFile: ByteArray? = null,
    val rawMime: String? = null,
    val fileName: String? = null,
) {
    val isScan get() = pages.isNotEmpty()
}

/**
 * Systémový skener dokladů: Android = Google ML Kit Document Scanner, iOS = Apple VisionKit.
 * Oba hlídají okraje papíru živě v náhledu, umí automatickou fotku, ruční doladění rohů,
 * srovnání perspektivy, ořez a filtry (vyrovnání světla, odstranění stínů, černobílý režim).
 * Vrací funkci, která skener spustí.
 */
@Composable
expect fun rememberDocumentScanner(onResult: (PickedDocument?) -> Unit): () -> Unit

/** Výběr existující fotky nebo PDF (např. faktura z e-mailu). */
@Composable
expect fun rememberFilePicker(onResult: (PickedDocument?) -> Unit): () -> Unit

expect fun decodeImage(bytes: ByteArray): ImageBitmap?

/** Otevře / nasdílí PDF (náhled, uložení, odeslání jinou aplikací). */
expect fun openPdf(bytes: ByteArray, fileName: String)

/** Nasdílí / uloží libovolný soubor (např. XML přiznání pro Moje daně). */
expect fun shareFile(bytes: ByteArray, fileName: String, mime: String)

expect fun httpClient(config: io.ktor.client.HttpClientConfig<*>.() -> Unit): HttpClient

/** Jednoduché úložiště nastavení (adresa serveru, token, vybraná firma, fronta). */
expect object KeyValueStore {
    fun get(key: String): String?
    fun put(key: String, value: String?)
}

/** Soubory v soukromém úložišti aplikace (fronta neodeslaných dokladů). */
expect object LocalFiles {
    fun write(name: String, bytes: ByteArray)
    fun read(name: String): ByteArray?
    fun delete(name: String)
}

/**
 * Přečte QR kód z fotky (JPEG/PNG) přímo v telefonu – Android ML Kit, iOS Vision.
 * Vrací texty nalezených QR kódů (prázdný seznam, když žádný není). Běží mimo hlavní vlákno.
 */
expect suspend fun readQrCodes(image: ByteArray): List<String>

/** Obyčejná fotka fotoaparátem (bez ořezu skeneru) – tachometr. Vrací JPEG, nebo null při zrušení. */
@Composable
expect fun rememberCamera(onResult: (ByteArray?) -> Unit): () -> Unit

/** Otevře odkaz v prohlížeči / mapách. */
expect fun openUrl(url: String)

@Composable
expect fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit)

/** Doklad nasdílený z jiné aplikace (Android „Sdílet → Doklady“). Hlavní obrazovka ho převezme. */
object IncomingShare {
    var pending by androidx.compose.runtime.mutableStateOf<PickedDocument?>(null)
}

/**
 * Odkaz z pozvánky (multiodoklady://activate?code=ABCD-EFGH nebo https://…/aktivace?code=…).
 * Volá se z Androidu (MainActivity) i z iOS (ContentView.onOpenURL → handleDeepLink).
 */
object IncomingLink {
    var activationCode by androidx.compose.runtime.mutableStateOf<String?>(null)

    fun handle(url: String) {
        parseActivationCode(url)?.let { activationCode = it }
    }
}

/** Z odkazu nebo opsaného textu vytáhne aktivační kód (8 znaků, pomlčka nepovinná). */
fun parseActivationCode(text: String): String? {
    val t = text.trim()
    val fromUrl = Regex("[?&]code=([A-Za-z0-9-]+)").find(t)?.groupValues?.get(1)
    val raw = (fromUrl ?: t).uppercase().filter { it.isLetterOrDigit() }
    return if (raw.length == 8) "${raw.take(4)}-${raw.drop(4)}" else null
}
