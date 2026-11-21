package cz.multio.doklady.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cz.multio.doklady.AppConfig
import cz.multio.doklady.data.Api
import cz.multio.doklady.data.Session
import cz.multio.doklady.platform.KeyValueStore
import cz.multio.doklady.platform.PasskeyException
import cz.multio.doklady.platform.Passkeys
import cz.multio.doklady.platform.parseActivationCode
import kotlinx.coroutines.launch

/**
 * Přihlášení: uživatelské jméno nebo e-mail a heslo (stejné jako na webu), nebo přístupový klíč.
 * Když měl uživatel zapnuté odemykání otiskem, po přihlášení se nové přihlášení rovnou zašifruje (systémové okno).
 */
@Composable
fun LoginScreen(onLoggedIn: () -> Unit, activationCode: String? = null) {
    var activation by remember { mutableStateOf(activationCode != null) }
    var code by remember { mutableStateOf(activationCode ?: "") }
    var ident by remember { mutableStateOf(KeyValueStore.get("last_login") ?: "") }
    var password by remember { mutableStateOf("") }
    var password2 by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var forgot by remember { mutableStateOf(false) }
    var info by remember { mutableStateOf<String?>(null) }
    // skryté nastavení serveru pro vývoj: 5× klepnout na logo
    var logoTaps by remember { mutableIntStateOf(0) }
    var server by remember { mutableStateOf(Api.baseUrl) }
    val scope = rememberCoroutineScope()

    suspend fun finish() {
        // zapnuté odemykání otiskem: nové přihlášení zašifrovat; když uživatel okno zruší, otisk se vypne
        if (Session.biometricEnabled && !Session.enableBiometric()) Session.disableBiometric()
        onLoggedIn()
    }

    fun submit() {
        busy = true; error = null
        scope.launch {
            try {
                if (server != Api.baseUrl) Api.baseUrl = server
                if (forgot) {
                    info = Api.passwordReset(ident)
                    return@launch
                }
                if (activation) {
                    val c = parseActivationCode(code) ?: throw IllegalArgumentException("Kód má 8 znaků, např. ABCD-EFGH.")
                    if (password != password2) throw IllegalArgumentException("Hesla se neshodují.")
                    Api.activate(c, password)
                } else {
                    Api.login(ident, password)
                    KeyValueStore.put("last_login", ident.trim())
                }
                finish()
            } catch (e: Exception) {
                error = e.message
            } finally {
                busy = false
            }
        }
    }

    fun passkey() {
        busy = true; error = null
        scope.launch {
            try {
                if (server != Api.baseUrl) Api.baseUrl = server
                val challenge = Api.passkeyLoginOptions()
                val response = Passkeys.get(challenge.options.toString())
                Api.passkeyLogin(response, challenge.state)
                finish()
            } catch (e: PasskeyException) {
                if (!e.cancelled) error = e.message
            } catch (e: Exception) {
                error = e.message
            } finally {
                busy = false
            }
        }
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        Column(
            Modifier.fillMaxSize().systemBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 28.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(32.dp))
            BrandLogo(Modifier.width(220.dp).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { logoTaps++ })
            Spacer(Modifier.height(40.dp))
            Column(Modifier.widthIn(max = 420.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Column {
                    Text(if (forgot) "Zapomenuté heslo" else if (activation) "Aktivace portálu" else "Přihlášení",
                        style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                    Text(if (forgot) "Pošleme vám odkaz na nastavení nového hesla."
                         else if (activation) "Nastavte si heslo kódem z pozvánky od účetní."
                         else "Použijte stejné údaje jako do klientského portálu.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (logoTaps >= 5) {
                    Field("Server (jen pro testování)", server, { server = it }, keyboard = KeyboardType.Uri, placeholder = AppConfig.SERVER_URL)
                }
                if (!forgot && !activation && Passkeys.supported()) {
                    Button(enabled = !busy, onClick = ::passkey, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                        Icon(Icons.Outlined.Key, null, Modifier.size(20.dp)); Spacer(Modifier.width(10.dp)); Text("Přihlásit přístupovým klíčem")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        HorizontalDivider(Modifier.weight(1f))
                        Text("nebo heslem", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 12.dp))
                        HorizontalDivider(Modifier.weight(1f))
                    }
                }
                if (forgot) {
                    Field("E-mail", ident, { ident = it; info = null }, keyboard = KeyboardType.Email)
                } else if (activation) {
                    OutlinedTextField(
                        value = code, onValueChange = { code = it.uppercase() },
                        label = { Text("Aktivační kód z pozvánky") }, placeholder = { Text("ABCD-EFGH") },
                        singleLine = true, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    )
                    PasswordField("Nové heslo (alespoň 8 znaků)", password) { password = it }
                    PasswordField("Heslo znovu", password2) { password2 = it }
                } else {
                    Field("Uživatelské jméno nebo e-mail", ident, { ident = it }, keyboard = KeyboardType.Email)
                    PasswordField("Heslo", password) { password = it }
                }
                ErrorBanner(error)
                info?.let { Text(it, color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) }
                val ready = if (forgot) ident.contains("@")
                            else if (activation) code.isNotBlank() && password.length >= 8 && password2.isNotEmpty()
                            else ident.isNotBlank() && password.isNotBlank()
                val passkeyFirst = !forgot && !activation && Passkeys.supported()
                if (passkeyFirst) {
                    OutlinedButton(enabled = !busy && ready, onClick = ::submit, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Přihlásit se") }
                } else {
                    Button(enabled = !busy && ready, onClick = ::submit, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                        if (busy) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                        else Text(if (forgot) "Poslat odkaz" else if (activation) "Aktivovat portál" else "Přihlásit se")
                    }
                }
                if (busy && passkeyFirst) CircularProgressIndicator(Modifier.size(24.dp).align(Alignment.CenterHorizontally), strokeWidth = 2.dp)
                when {
                    forgot -> TextButton(onClick = { forgot = false; error = null; info = null }, modifier = Modifier.fillMaxWidth()) { Text("Zpět na přihlášení") }
                    activation -> TextButton(onClick = { activation = false; error = null }, modifier = Modifier.fillMaxWidth()) { Text("Už mám heslo – přihlásit se") }
                    else -> TextButton(onClick = { forgot = true; error = null; info = null }, modifier = Modifier.fillMaxWidth()) { Text("Zapomněli jste heslo?") }
                }
            }
            Spacer(Modifier.height(24.dp))
            Text("Multio CZ s.r.o. · Poradce v oblasti účetnictví", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun PasswordField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) },
        singleLine = true, visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium,
    )
}
