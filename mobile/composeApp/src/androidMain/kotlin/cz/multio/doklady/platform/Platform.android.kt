package cz.multio.doklady.platform

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.okhttp.OkHttp
import java.io.File
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

@SuppressLint("StaticFieldLeak")
object AndroidPlatform {
    lateinit var context: Context
        private set

    /** Aktuální obrazovka (pro systémová okna: otisk prstu, přístupové klíče). */
    private var activityRef: java.lang.ref.WeakReference<Activity>? = null
    val activity: Activity? get() = activityRef?.get()

    fun init(ctx: Context) {
        context = ctx.applicationContext
    }

    fun attach(activity: Activity?) {
        activityRef = activity?.let { java.lang.ref.WeakReference(it) }
    }
}

private fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

private fun readBytes(ctx: Context, uri: Uri): ByteArray? =
    ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() }

/** Načte vybraný / nasdílený soubor (fotka nebo PDF). */
fun readPicked(ctx: Context, uri: Uri): PickedDocument? {
    val mime = ctx.contentResolver.getType(uri) ?: "application/octet-stream"
    val name = ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
        if (c.moveToFirst()) c.getString(0) else null
    }
    val bytes = readBytes(ctx, uri) ?: return null
    return if (mime == "application/pdf") {
        PickedDocument(pages = emptyList(), pdf = bytes, fileName = name)
    } else {
        PickedDocument(pages = emptyList(), pdf = null, rawFile = bytes, rawMime = mime, fileName = name)
    }
}

/**
 * Google ML Kit Document Scanner – plnohodnotný skener (SCANNER_MODE_FULL):
 * živá detekce okrajů, automatické vyfocení, ruční úprava rohů, srovnání perspektivy,
 * filtry (auto vylepšení, odstínění, černobílý), otočení, více stran, odstranění prstů/skvrn.
 * Vše běží v telefonu přes Google Play služby, aplikace nepotřebuje oprávnění ke kameře.
 */
@Composable
actual fun rememberDocumentScanner(pageLimit: Int, onResult: (PickedDocument?) -> Unit): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { res: ActivityResult ->
        if (res.resultCode != Activity.RESULT_OK) {
            onResult(null); return@rememberLauncherForActivityResult
        }
        val result = GmsDocumentScanningResult.fromActivityResultIntent(res.data)
        if (result == null) {
            onResult(null); return@rememberLauncherForActivityResult
        }
        val pages = result.pages.orEmpty().mapNotNull { readBytes(context, it.imageUri) }
        val pdf = result.pdf?.uri?.let { readBytes(context, it) }
        onResult(if (pages.isEmpty() && pdf == null) null else PickedDocument(pages, pdf))
    }
    val scanner = remember(pageLimit) {
        GmsDocumentScanning.getClient(
            GmsDocumentScannerOptions.Builder()
                .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
                .setGalleryImportAllowed(true)
                .setPageLimit(pageLimit.coerceIn(1, 100))
                .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG, GmsDocumentScannerOptions.RESULT_FORMAT_PDF)
                .build()
        )
    }
    return {
        val activity = context.findActivity()
        if (activity != null) {
            scanner.getStartScanIntent(activity)
                .addOnSuccessListener { sender -> launcher.launch(IntentSenderRequest.Builder(sender).build()) }
                .addOnFailureListener { e ->
                    Toast.makeText(context, "Skener není dostupný: ${e.message}. Zkuste aktualizovat Google Play služby.",
                        Toast.LENGTH_LONG).show()
                }
        }
    }
}

@Composable
actual fun rememberFilePicker(onResult: (PickedDocument?) -> Unit): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        onResult(uri?.let { readPicked(context, it) })
    }
    return { launcher.launch(arrayOf("image/jpeg", "image/png", "image/webp", "application/pdf")) }
}

@Composable
actual fun rememberStatementPicker(onResult: (List<PickedFile>) -> Unit): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        onResult(uris.take(6).mapNotNull { uri ->
            val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            } ?: "vypis"
            val data = readBytes(context, uri) ?: return@mapNotNull null
            PickedFile(data, context.contentResolver.getType(uri) ?: "application/octet-stream", name)
        })
    }
    return { launcher.launch(arrayOf("*/*")) }
}

actual fun decodeImage(bytes: ByteArray, maxSize: Int): ImageBitmap? {
    // velké fotky zmenšit, ať nedojde paměť
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / sample > maxSize) sample *= 2
    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)?.asImageBitmap()
}

actual fun openPdf(bytes: ByteArray, fileName: String) {
    val ctx = AndroidPlatform.context
    val dir = File(ctx.cacheDir, "shared").apply { mkdirs() }
    val safe = fileName.replace(Regex("[^A-Za-z0-9._-]"), "_")
    val file = File(dir, safe).apply { writeBytes(bytes) }
    val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", file)
    val view = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "application/pdf")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val chooser = Intent.createChooser(view, fileName).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
    ctx.startActivity(chooser)
}

actual fun shareFile(bytes: ByteArray, fileName: String, mime: String) {
    val ctx = AndroidPlatform.context
    val dir = File(ctx.cacheDir, "shared").apply { mkdirs() }
    val safe = fileName.replace(Regex("[^A-Za-z0-9._-]"), "_")
    val file = File(dir, safe).apply { writeBytes(bytes) }
    val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = mime
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val chooser = Intent.createChooser(send, fileName).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
    ctx.startActivity(chooser)
}

actual fun httpClient(config: HttpClientConfig<*>.() -> Unit): HttpClient = HttpClient(OkHttp) { config() }

actual object KeyValueStore {
    private val prefs by lazy { AndroidPlatform.context.getSharedPreferences("doklady", Context.MODE_PRIVATE) }
    actual fun get(key: String): String? = prefs.getString(key, null)
    actual fun put(key: String, value: String?) {
        prefs.edit().apply { if (value == null) remove(key) else putString(key, value) }.apply()
    }
}

actual object LocalFiles {
    private val dir by lazy { File(AndroidPlatform.context.filesDir, "outbox").apply { mkdirs() } }
    actual fun write(name: String, bytes: ByteArray) = File(dir, name).writeBytes(bytes)
    actual fun read(name: String): ByteArray? = File(dir, name).takeIf { it.exists() }?.readBytes()
    actual fun delete(name: String) { File(dir, name).delete() }
}

// ---------------------------------------------------------------- QR platba (ML Kit, v telefonu, bez internetu)

private val qrScanner by lazy {
    BarcodeScanning.getClient(BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build())
}

actual suspend fun readQrCodes(image: ByteArray): List<String> {
    val bitmap = withContext(Dispatchers.Default) {
        // stačí ~2000 px: QR na faktuře je velký, menší obrázek = rychlejší a bez nedostatku paměti
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(image, 0, image.size, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 2000) sample *= 2
        BitmapFactory.decodeByteArray(image, 0, image.size, BitmapFactory.Options().apply { inSampleSize = sample })
    } ?: return emptyList()
    return suspendCancellableCoroutine { cont ->
        qrScanner.process(InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener { codes -> if (cont.isActive) cont.resume(codes.mapNotNull { it.rawValue }) }
            .addOnFailureListener { if (cont.isActive) cont.resume(emptyList()) }
    }
}

// ---------------------------------------------------------------- fotka (tachometr)

@Composable
actual fun rememberCamera(onResult: (ByteArray?) -> Unit): () -> Unit {
    val context = LocalContext.current
    val target = remember {
        val dir = File(context.cacheDir, "shared").apply { mkdirs() }
        File(dir, "fotka.jpg")
    }
    val uri = remember { FileProvider.getUriForFile(context, "${context.packageName}.files", target) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val bytes = if (ok && target.exists()) target.readBytes() else null
        target.delete()
        onResult(bytes?.let { shrinkJpeg(it) })
    }
    return {
        try {
            launcher.launch(uri)
        } catch (e: Exception) {
            Toast.makeText(context, "Fotoaparát není dostupný: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}

/** Zmenší fotku na nejvýš 2400 px (server bere do 15 MB, menší soubor = rychlejší odeslání). */
private fun shrinkJpeg(bytes: ByteArray): ByteArray {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (maxOf(bounds.outWidth, bounds.outHeight) <= 2400) return bytes
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 2400) sample *= 2
    val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return bytes
    val out = java.io.ByteArrayOutputStream()
    bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 88, out)
    return out.toByteArray()
}

actual fun openUrl(url: String) {
    val ctx = AndroidPlatform.context
    runCatching {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
    }
}

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) = BackHandler(enabled, onBack)
