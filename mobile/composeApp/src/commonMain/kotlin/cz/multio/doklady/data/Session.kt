package cz.multio.doklady.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import cz.multio.doklady.platform.BiometricKeyInvalidated
import cz.multio.doklady.platform.Biometrics
import cz.multio.doklady.platform.KeyValueStore
import kotlinx.datetime.Clock

/**
 * Přihlášení v telefonu. Bez odemykání otiskem je token uložený v soukromých datech aplikace.
 * S odemykáním otiskem je token zašifrovaný klíčem v bezpečném úložišti telefonu (Android Keystore), který se
 * dá použít jen po ověření otiskem – po zavření aplikace (nebo po minutě na pozadí) je potřeba aplikaci znovu odemknout.
 */
object Session {
    private var memory: String? = null

    val biometricEnabled get() = KeyValueStore.get("bio") == "1"

    fun token(): String? = memory ?: KeyValueStore.get("token")

    fun setToken(value: String?) {
        memory = value
        if (value == null) {
            KeyValueStore.put("token", null)
            KeyValueStore.put("token_enc", null)
        } else if (!biometricEnabled) {
            KeyValueStore.put("token", value)
        }
    }

    /** Aplikace čeká na odemknutí otiskem (token je jen zašifrovaný). */
    val locked get() = biometricEnabled && memory == null && KeyValueStore.get("token_enc") != null

    /** → true = odemčeno; false = zrušeno; BiometricKeyInvalidated = v telefonu se změnily otisky (nutné heslo). */
    suspend fun unlock(): Boolean {
        val blob = KeyValueStore.get("token_enc") ?: return false
        val token = try {
            Biometrics.decrypt(blob)
        } catch (e: BiometricKeyInvalidated) {
            disableBiometric(keepToken = false)
            throw e
        } ?: return false
        memory = token
        return true
    }

    /** Zapne odemykání otiskem pro aktuální přihlášení (zobrazí systémové okno). */
    suspend fun enableBiometric(): Boolean {
        val token = token() ?: return false
        val blob = Biometrics.encrypt(token) ?: return false
        KeyValueStore.put("token_enc", blob)
        KeyValueStore.put("bio", "1")
        KeyValueStore.put("token", null)
        memory = token
        return true
    }

    fun disableBiometric(keepToken: Boolean = true) {
        val token = memory
        KeyValueStore.put("bio", null)
        KeyValueStore.put("token_enc", null)
        Biometrics.reset()
        if (keepToken && token != null) KeyValueStore.put("token", token) else if (!keepToken) memory = null
    }

    fun lock() {
        if (biometricEnabled && KeyValueStore.get("token_enc") != null) memory = null
    }
}

/** Zámek aplikace: při spuštění a po návratu z pozadí (déle než minutu), když je zapnuté odemykání otiskem. */
object AppLock {
    var locked by mutableStateOf(false)
    private var backgroundAt = 0L

    fun init() { locked = Session.locked }

    fun onBackground() { backgroundAt = Clock.System.now().toEpochMilliseconds() }

    fun onForeground() {
        val away = Clock.System.now().toEpochMilliseconds() - backgroundAt
        if (backgroundAt > 0 && away > 60_000 && Session.biometricEnabled) {
            Session.lock()
            locked = Session.locked
        }
        backgroundAt = 0
    }
}
