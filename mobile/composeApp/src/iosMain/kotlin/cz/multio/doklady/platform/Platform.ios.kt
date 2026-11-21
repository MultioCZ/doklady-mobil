@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package cz.multio.doklady.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.darwin.Darwin
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import org.jetbrains.skia.Image
import platform.Foundation.NSData
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSError
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUserDefaults
import platform.Foundation.NSUserDomainMask
import platform.Foundation.create
import platform.Foundation.dataWithContentsOfFile
import platform.Foundation.dataWithContentsOfURL
import platform.Foundation.writeToFile
import platform.PDFKit.PDFDocument
import platform.PDFKit.PDFPage
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIAlertAction
import platform.UIKit.UIAlertActionStyleCancel
import platform.UIKit.UIAlertActionStyleDefault
import platform.UIKit.UIAlertController
import platform.UIKit.UIAlertControllerStyleActionSheet
import platform.UIKit.UIAlertControllerStyleAlert
import platform.UIKit.UIApplication
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.UIKit.UIImagePickerController
import platform.UIKit.UIImagePickerControllerDelegateProtocol
import platform.UIKit.UIImagePickerControllerOriginalImage
import platform.UIKit.UIImagePickerControllerSourceType
import platform.UIKit.UINavigationControllerDelegateProtocol
import platform.UIKit.UIViewController
import platform.UniformTypeIdentifiers.UTTypeImage
import platform.UniformTypeIdentifiers.UTTypePDF
import platform.VisionKit.VNDocumentCameraScan
import platform.VisionKit.VNDocumentCameraViewController
import platform.VisionKit.VNDocumentCameraViewControllerDelegateProtocol
import platform.darwin.NSObject
import platform.Vision.VNBarcodeObservation
import platform.Vision.VNBarcodeSymbologyQR
import platform.Vision.VNDetectBarcodesRequest
import platform.Vision.VNImageRequestHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import platform.posix.memcpy

// ---------------------------------------------------------------- převody NSData <-> ByteArray

fun NSData.toByteArray(): ByteArray {
    val size = length.toInt()
    val out = ByteArray(size)
    if (size > 0) out.usePinned { memcpy(it.addressOf(0), bytes, length) }
    return out
}

fun ByteArray.toNSData(): NSData =
    if (isEmpty()) NSData() else usePinned { NSData.create(bytes = it.addressOf(0), length = size.toULong()) }

private fun topViewController(): UIViewController? {
    @Suppress("DEPRECATION")
    var vc = UIApplication.sharedApplication.keyWindow?.rootViewController
    while (vc?.presentedViewController != null) vc = vc.presentedViewController
    return vc
}

private fun showAlert(title: String, message: String) {
    val alert = UIAlertController.alertControllerWithTitle(title, message, UIAlertControllerStyleAlert)
    alert.addAction(UIAlertAction.actionWithTitle("OK", UIAlertActionStyleDefault, null))
    topViewController()?.presentViewController(alert, animated = true, completion = null)
}

// ---------------------------------------------------------------- skener (VisionKit)

/**
 * Apple VisionKit – systémový skener dokumentů (stejný jako v aplikaci Poznámky):
 * živá detekce okrajů, automatické vyfocení, ruční úprava rohů, srovnání perspektivy,
 * filtry (barevný / odstíny šedi / černobílý / fotka), více stran.
 */
private class ScanDelegate(private val onResult: (PickedDocument?) -> Unit) :
    NSObject(), VNDocumentCameraViewControllerDelegateProtocol {

    override fun documentCameraViewController(
        controller: VNDocumentCameraViewController,
        didFinishWithScan: VNDocumentCameraScan,
    ) {
        val pdf = PDFDocument()
        val pages = mutableListOf<ByteArray>()
        val count = didFinishWithScan.pageCount.toInt()
        for (i in 0 until count) {
            val img: UIImage = didFinishWithScan.imageOfPageAtIndex(i.toULong())
            UIImageJPEGRepresentation(img, 0.85)?.let { pages += it.toByteArray() }
            PDFPage(image = img).let { pdf.insertPage(it, atIndex = i.toULong()) }
        }
        val pdfBytes = pdf.dataRepresentation()?.toByteArray()
        controller.dismissViewControllerAnimated(true, completion = null)
        onResult(if (pages.isEmpty()) null else PickedDocument(pages, pdfBytes))
    }

    override fun documentCameraViewControllerDidCancel(controller: VNDocumentCameraViewController) {
        controller.dismissViewControllerAnimated(true, completion = null)
        onResult(null)
    }

    override fun documentCameraViewController(controller: VNDocumentCameraViewController, didFailWithError: NSError) {
        controller.dismissViewControllerAnimated(true, completion = null)
        showAlert("Skener", didFailWithError.localizedDescription)
        onResult(null)
    }
}

@Composable
actual fun rememberDocumentScanner(onResult: (PickedDocument?) -> Unit): () -> Unit {
    // delegát musí žít, dokud je skener otevřený (UIKit drží jen slabý odkaz)
    val delegate = remember { ScanDelegate { onResult(it) } }
    return {
        val cameraAvailable = UIImagePickerController.isSourceTypeAvailable(
            UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera
        )
        if (!cameraAvailable) {
            showAlert("Skener", "Fotoaparát není k dispozici (simulátor?). Použijte výběr ze souboru.")
        } else {
            val scanner = VNDocumentCameraViewController()
            scanner.delegate = delegate
            topViewController()?.presentViewController(scanner, animated = true, completion = null)
        }
    }
}

// ---------------------------------------------------------------- výběr fotky / souboru

private fun mimeFor(url: NSURL): String = when (url.pathExtension?.lowercase()) {
    "pdf" -> "application/pdf"
    "png" -> "image/png"
    "webp" -> "image/webp"
    "heic", "heif" -> "image/heic"
    else -> "image/jpeg"
}

private class FileDelegate(private val onResult: (PickedDocument?) -> Unit) : NSObject(), UIDocumentPickerDelegateProtocol {
    override fun documentPicker(controller: UIDocumentPickerViewController, didPickDocumentsAtURLs: List<*>) {
        val url = didPickDocumentsAtURLs.firstOrNull() as? NSURL
        if (url == null) { onResult(null); return }
        val access = url.startAccessingSecurityScopedResource()
        val data = NSData.dataWithContentsOfURL(url)?.toByteArray()
        if (access) url.stopAccessingSecurityScopedResource()
        if (data == null) { onResult(null); return }
        var mime = mimeFor(url)
        var bytes = data
        if (mime == "image/heic") {
            // HEIC z iPhonu převést na JPEG – server a vytěžování s HEIC nepracují
            val jpeg = UIImage.imageWithData(data.toNSData())?.let { UIImageJPEGRepresentation(it, 0.9) }
            if (jpeg != null) { bytes = jpeg.toByteArray(); mime = "image/jpeg" }
        }
        onResult(
            if (mime == "application/pdf") PickedDocument(emptyList(), bytes, fileName = url.lastPathComponent)
            else PickedDocument(emptyList(), null, rawFile = bytes, rawMime = mime, fileName = url.lastPathComponent)
        )
    }

    override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) = onResult(null)
}

private class PhotoDelegate(private val onResult: (PickedDocument?) -> Unit) :
    NSObject(), UIImagePickerControllerDelegateProtocol, UINavigationControllerDelegateProtocol {

    override fun imagePickerController(picker: UIImagePickerController, didFinishPickingMediaWithInfo: Map<Any?, *>) {
        val image = didFinishPickingMediaWithInfo[UIImagePickerControllerOriginalImage] as? UIImage
        picker.dismissViewControllerAnimated(true, completion = null)
        val jpeg = image?.let { UIImageJPEGRepresentation(it, 0.9)?.toByteArray() }
        onResult(jpeg?.let { PickedDocument(emptyList(), null, rawFile = it, rawMime = "image/jpeg", fileName = "foto.jpg") })
    }

    override fun imagePickerControllerDidCancel(picker: UIImagePickerController) {
        picker.dismissViewControllerAnimated(true, completion = null)
        onResult(null)
    }
}

@Composable
actual fun rememberFilePicker(onResult: (PickedDocument?) -> Unit): () -> Unit {
    val fileDelegate = remember { FileDelegate { onResult(it) } }
    val photoDelegate = remember { PhotoDelegate { onResult(it) } }
    return {
        val sheet = UIAlertController.alertControllerWithTitle("Přidat doklad", null, UIAlertControllerStyleActionSheet)
        sheet.addAction(UIAlertAction.actionWithTitle("Fotky", UIAlertActionStyleDefault) { _ ->
            val picker = UIImagePickerController()
            picker.sourceType = UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypePhotoLibrary
            picker.delegate = photoDelegate
            topViewController()?.presentViewController(picker, animated = true, completion = null)
        })
        sheet.addAction(UIAlertAction.actionWithTitle("Soubory (PDF, obrázky)", UIAlertActionStyleDefault) { _ ->
            val picker = UIDocumentPickerViewController(forOpeningContentTypes = listOf(UTTypePDF, UTTypeImage), asCopy = true)
            picker.delegate = fileDelegate
            topViewController()?.presentViewController(picker, animated = true, completion = null)
        })
        sheet.addAction(UIAlertAction.actionWithTitle("Zrušit", UIAlertActionStyleCancel, null))
        val top = topViewController()
        // iPad: action sheet musí mít kotvu
        sheet.popoverPresentationController?.sourceView = top?.view
        top?.presentViewController(sheet, animated = true, completion = null)
    }
}

// ---------------------------------------------------------------- ostatní

actual fun decodeImage(bytes: ByteArray): ImageBitmap? =
    runCatching { Image.makeFromEncoded(bytes).toComposeImageBitmap() }.getOrNull()

actual fun openPdf(bytes: ByteArray, fileName: String) {
    val safe = fileName.replace(Regex("[^A-Za-z0-9._-]"), "_")
    val path = NSTemporaryDirectory() + safe
    bytes.toNSData().writeToFile(path, atomically = true)
    val url = NSURL.fileURLWithPath(path)
    val share = UIActivityViewController(activityItems = listOf(url), applicationActivities = null)
    val top = topViewController()
    share.popoverPresentationController?.sourceView = top?.view
    top?.presentViewController(share, animated = true, completion = null)
}

actual fun shareFile(bytes: ByteArray, fileName: String, mime: String) = openPdf(bytes, fileName)

actual fun httpClient(config: HttpClientConfig<*>.() -> Unit): HttpClient = HttpClient(Darwin) { config() }

actual object KeyValueStore {
    private val defaults get() = NSUserDefaults.standardUserDefaults
    actual fun get(key: String): String? = defaults.stringForKey(key)
    actual fun put(key: String, value: String?) {
        if (value == null) defaults.removeObjectForKey(key) else defaults.setObject(value, forKey = key)
    }
}

actual object LocalFiles {
    private val dir: String by lazy {
        val docs = NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true).first() as String
        "$docs/outbox".also {
            NSFileManager.defaultManager.createDirectoryAtPath(it, withIntermediateDirectories = true, attributes = null, error = null)
        }
    }

    actual fun write(name: String, bytes: ByteArray) {
        bytes.toNSData().writeToFile("$dir/$name", atomically = true)
    }

    actual fun read(name: String): ByteArray? = NSData.dataWithContentsOfFile("$dir/$name")?.toByteArray()

    actual fun delete(name: String) {
        NSFileManager.defaultManager.removeItemAtPath("$dir/$name", error = null)
    }
}

// ---------------------------------------------------------------- QR platba (Apple Vision, v telefonu)

actual suspend fun readQrCodes(image: ByteArray): List<String> = withContext(Dispatchers.IO) {
    runCatching {
        val request = VNDetectBarcodesRequest()
        request.symbologies = listOf(VNBarcodeSymbologyQR)
        val handler = VNImageRequestHandler(data = image.toNSData(), options = emptyMap<Any?, Any?>())
        handler.performRequests(listOf(request), error = null)
        request.results.orEmpty().mapNotNull { (it as? VNBarcodeObservation)?.payloadStringValue }
    }.getOrDefault(emptyList())
}

// ---------------------------------------------------------------- fotka (tachometr)

private class CameraDelegate(private val onResult: (ByteArray?) -> Unit) :
    NSObject(), UIImagePickerControllerDelegateProtocol, UINavigationControllerDelegateProtocol {

    override fun imagePickerController(picker: UIImagePickerController, didFinishPickingMediaWithInfo: Map<Any?, *>) {
        val image = didFinishPickingMediaWithInfo[UIImagePickerControllerOriginalImage] as? UIImage
        picker.dismissViewControllerAnimated(true, completion = null)
        onResult(image?.let { UIImageJPEGRepresentation(it, 0.85)?.toByteArray() })
    }

    override fun imagePickerControllerDidCancel(picker: UIImagePickerController) {
        picker.dismissViewControllerAnimated(true, completion = null)
        onResult(null)
    }
}

@Composable
actual fun rememberCamera(onResult: (ByteArray?) -> Unit): () -> Unit {
    val delegate = remember { CameraDelegate { onResult(it) } }
    return {
        val source = UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera
        if (!UIImagePickerController.isSourceTypeAvailable(source)) {
            showAlert("Fotoaparát", "Fotoaparát není k dispozici.")
        } else {
            val picker = UIImagePickerController()
            picker.sourceType = source
            picker.delegate = delegate
            topViewController()?.presentViewController(picker, animated = true, completion = null)
        }
    }
}

actual fun openUrl(url: String) {
    val u = NSURL.URLWithString(url) ?: return
    UIApplication.sharedApplication.openURL(u, options = emptyMap<Any?, Any?>(), completionHandler = null)
}

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
    // iOS: zpět řeší šipka v horní liště
}
