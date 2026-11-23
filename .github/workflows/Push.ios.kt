package cz.multio.doklady.platform

/** iPhone: upozornění přes APNs připravíme s vydáním do App Store. */
actual object PushPlatform {
    actual val platform: String = "ios"
    actual suspend fun token(): String? = null
    actual fun requestPermission() {}
}
