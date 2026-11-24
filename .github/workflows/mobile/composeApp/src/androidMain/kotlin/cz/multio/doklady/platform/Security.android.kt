package cz.multio.doklady.platform

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.credentials.CreatePublicKeyCredentialRequest
import androidx.credentials.CreatePublicKeyCredentialResponse
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetPublicKeyCredentialOption
import androidx.credentials.PublicKeyCredential
import androidx.credentials.exceptions.CreateCredentialCancellationException
import androidx.credentials.exceptions.CreateCredentialException
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.suspendCancellableCoroutine
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlin.coroutines.resume

// ---------------------------------------------------------------- otisk prstu (BiometricPrompt + Android Keystore)

actual object Biometrics {
    private const val KEY = "doklady_token_v1"

    actual fun available(): Boolean =
        BiometricManager.from(AndroidPlatform.context).canAuthenticate(BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS

    private fun keyStore() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    private fun key(create: Boolean): SecretKey? {
        (keyStore().getKey(KEY, null) as? SecretKey)?.let { return it }
        if (!create) return null
        val spec = KeyGenParameterSpec.Builder(KEY, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(true)   // klíč jde použít jen po ověření otiskem
            .setInvalidatedByBiometricEnrollment(true)   // nový otisk v telefonu = klíč neplatí
            .build()
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(spec)
        return generator.generateKey()
    }

    actual suspend fun encrypt(plain: String): String? {
        reset()
        val cipher = runCatching {
            Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key(create = true)) }
        }.getOrNull() ?: return null   // v telefonu není nastavený otisk
        val ok = prompt(cipher, "Zapnout odemykání otiskem", "Potvrďte otiskem prstu") ?: return null
        val data = ok.doFinal(plain.toByteArray(Charsets.UTF_8))
        return b64(ok.iv) + ":" + b64(data)
    }

    actual suspend fun decrypt(blob: String): String? {
        val parts = blob.split(':')
        if (parts.size != 2) return null
        val key = key(create = false) ?: throw BiometricKeyInvalidated()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        try {
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, unb64(parts[0])))
        } catch (e: KeyPermanentlyInvalidatedException) {
            throw BiometricKeyInvalidated()
        }
        val ok = prompt(cipher, "Odemknout Doklady", "Přiložte prst ke snímači") ?: return null
        return runCatching { String(ok.doFinal(unb64(parts[1])), Charsets.UTF_8) }.getOrNull()
    }

    actual fun reset() {
        runCatching { keyStore().deleteEntry(KEY) }
    }

    private suspend fun prompt(cipher: Cipher, title: String, subtitle: String): Cipher? = suspendCancellableCoroutine { cont ->
        val activity = AndroidPlatform.activity as? FragmentActivity
        if (activity == null) {
            cont.resume(null)
            return@suspendCancellableCoroutine
        }
        val prompt = BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                if (cont.isActive) cont.resume(result.cryptoObject?.cipher)
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                if (cont.isActive) cont.resume(null)
            }
        })
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setNegativeButtonText("Zrušit")
            .setAllowedAuthenticators(BIOMETRIC_STRONG)
            .build()
        prompt.authenticate(info, BiometricPrompt.CryptoObject(cipher))
        cont.invokeOnCancellation { runCatching { prompt.cancelAuthentication() } }
    }

    private fun b64(b: ByteArray) = Base64.encodeToString(b, Base64.NO_WRAP)
    private fun unb64(s: String) = Base64.decode(s, Base64.NO_WRAP)
}

// ---------------------------------------------------------------- přístupové klíče (Credential Manager)

actual object Passkeys {
    actual fun supported(): Boolean = Build.VERSION.SDK_INT >= 28

    actual suspend fun get(optionsJson: String): String {
        val activity = AndroidPlatform.activity ?: throw PasskeyException("Aplikace není připravená. Zkuste to znovu.")
        val manager = CredentialManager.create(activity)
        try {
            val result = manager.getCredential(activity, GetCredentialRequest(listOf(GetPublicKeyCredentialOption(optionsJson))))
            val credential = result.credential
            if (credential is PublicKeyCredential) return credential.authenticationResponseJson
            throw PasskeyException("Telefon nevrátil přístupový klíč.")
        } catch (e: GetCredentialCancellationException) {
            throw PasskeyException("Zrušeno.", cancelled = true)
        } catch (e: NoCredentialException) {
            throw PasskeyException("V telefonu není přístupový klíč pro Doklady. Přihlaste se heslem a klíč si přidejte v Zabezpečení.")
        } catch (e: GetCredentialException) {
            throw PasskeyException("Přihlášení klíčem se nepodařilo: ${e.errorMessage ?: e.type}")
        }
    }

    actual suspend fun create(optionsJson: String): String {
        val activity = AndroidPlatform.activity ?: throw PasskeyException("Aplikace není připravená. Zkuste to znovu.")
        val manager = CredentialManager.create(activity)
        try {
            val result = manager.createCredential(activity, CreatePublicKeyCredentialRequest(optionsJson))
            return (result as? CreatePublicKeyCredentialResponse)?.registrationResponseJson
                ?: throw PasskeyException("Telefon klíč nevytvořil.")
        } catch (e: CreateCredentialCancellationException) {
            throw PasskeyException("Zrušeno.", cancelled = true)
        } catch (e: CreateCredentialException) {
            throw PasskeyException("Klíč se nepodařilo vytvořit: ${e.errorMessage ?: e.type}")
        }
    }

    actual fun deviceName(): String = listOf(Build.MANUFACTURER.replaceFirstChar { it.uppercase() }, Build.MODEL)
        .filter { it.isNotBlank() }.distinct().joinToString(" ").take(80)
}
