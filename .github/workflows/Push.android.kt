package cz.multio.doklady.platform

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

actual object PushPlatform {
    actual val platform: String = "android"

    /** Firebase token telefonu; null, když aplikace nemá Firebase (chybí google-services.json) nebo služby Google. */
    actual suspend fun token(): String? = runCatching {
        suspendCancellableCoroutine<String?> { cont ->
            FirebaseMessaging.getInstance().token
                .addOnSuccessListener { cont.resume(it) }
                .addOnFailureListener { cont.resume(null) }
        }
    }.getOrNull()

    /** Android 13+: systémový dotaz na povolení upozornění (jen jednou). */
    actual fun requestPermission() {
        if (Build.VERSION.SDK_INT < 33) return
        val activity = AndroidPlatform.activity ?: return
        if (activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return
        if (KeyValueStore.get("push_asked") == "1") return
        KeyValueStore.put("push_asked", "1")
        activity.requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 4711)
    }
}
