package cz.multio.doklady.data

import cz.multio.doklady.AppConfig
import cz.multio.doklady.platform.KeyValueStore
import cz.multio.doklady.platform.PushPlatform

/** Registrace telefonu pro upozornění (2.0). Bez Firebase / bez přihlášení nedělá nic. */
object PushSetup {
    suspend fun register(knownToken: String? = null) {
        if (Api.token.isNullOrBlank()) return
        val token = knownToken ?: PushPlatform.token() ?: KeyValueStore.get("fcm_token") ?: return
        KeyValueStore.put("fcm_token", token)
        runCatching { Api.registerDevice(token, PushPlatform.platform, AppConfig.VERSION_NAME) }
    }

    /** Při odhlášení – ať upozornění nechodí na telefon, kde už nikdo přihlášený není. */
    suspend fun unregister() {
        val token = KeyValueStore.get("fcm_token") ?: return
        runCatching { Api.unregisterDevice(token) }
    }
}
