package cz.multio.doklady.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cz.multio.doklady.AppConfig
import cz.multio.doklady.data.Api
import cz.multio.doklady.platform.parseActivationCode
import kotlinx.coroutines.launch

/**
 * Přihlášení do klientského portálu. Adresa serveru je v aplikaci pevně – klient zadává jen e-mail a heslo.
 * Nový klient zvolí „Mám pozvánku“ (nebo klepne na odkaz z e-mailu) a nastaví si heslo kódem.
 * [activationCode] = kód z odkazu v pozvánce → rovnou se otevře aktivace.
 */
@Composable
fun LoginScreen(onLoggedIn: () -> Unit, activationCode: String? = null) {
    var activation by remember { mutableStateOf(activationCode != null) }
    var code by remember { mutableStateOf(activationCode ?: "") }
    var email by remember { mutableStateOf(Api.cachedMe()?.user?.email ?: "") }
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

    fun submit() {
        busy = true; error = null
        scope.launch {
            try {
                if (server != Api.baseUrl) Api.baseUrl = server
                if (forgot) {
                    info = Api.passwordReset(email)
                    return@launch
                }
                if (activation) {
                    val c = parseActivationCode(code) ?: throw IllegalArgumentException("Kód má 8 znaků, např. ABCD-EFGH.")
                    if (password != password2) throw IllegalArgumentException("Hesla se neshodují.")
                    Api.activate(c, password)
                } else {
                    Api.login(email, password)
                }
                onLoggedIn()
            } catch (e: Exception) {
                error = e.message
            } finally {
                busy = false
            }
        }
    }

    Column(
        Modifier.fillMaxSize().systemBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Filled.DocumentScanner, null, tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(56.dp).clickable(
                interactionSource = remember { MutableInteractionSource() }, indication = null,
            ) { logoTaps++ })
        Text("Klientský portál", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 8.dp))
        Text(if (forgot) "Zapomenuté heslo" else if (activation) "Aktivace z pozvánky od účetní" else "Doklady, faktury a přehledy vaší firmy",
            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Spacer(Modifier.height(28.dp))

        Column(Modifier.widthIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (logoTaps >= 5) {
                Field("Server (jen pro testování)", server, { server = it }, keyboard = KeyboardType.Uri,
                    placeholder = AppConfig.SERVER_URL)
            }
            if (forgot) {
                Text("Zadejte e-mail, který máte u účtu. Pošleme vám odkaz, ve kterém si nastavíte nové heslo. Když e-mail nepřijde, požádejte účetní – odkaz vám vytvoří v portálu.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Field("E-mail", email, { email = it; info = null }, keyboard = KeyboardType.Email)
            } else if (activation) {
                OutlinedTextField(
                    value = code, onValueChange = { code = it.uppercase() },
                    label = { Text("Aktivační kód z pozvánky") }, placeholder = { Text("ABCD-EFGH") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                )
                PasswordField("Nové heslo (alespoň 8 znaků)", password) { password = it }
                PasswordField("Heslo znovu", password2) { password2 = it }
            } else {
                Field("E-mail", email, { email = it }, keyboard = KeyboardType.Email)
                PasswordField("Heslo", password) { password = it }
            }
            ErrorBanner(error)
            info?.let { Text(it, color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) }
            val ready = if (forgot) email.contains("@")
                        else if (activation) code.isNotBlank() && password.length >= 8 && password2.isNotEmpty()
                        else email.isNotBlank() && password.isNotBlank()
            Button(enabled = !busy && ready, onClick = ::submit, modifier = Modifier.fillMaxWidth().height(50.dp)) {
                if (busy) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                else Text(if (forgot) "Poslat odkaz" else if (activation) "Aktivovat portál" else "Přihlásit")
            }
            if (forgot) {
                TextButton(onClick = { forgot = false; error = null; info = null }, modifier = Modifier.fillMaxWidth()) {
                    Text("← Zpět na přihlášení")
                }
            } else if (activation) {
                TextButton(onClick = { activation = false; error = null }, modifier = Modifier.fillMaxWidth()) {
                    Text("Už mám heslo – přihlásit se")
                }
            } else {
                TextButton(onClick = { forgot = true; error = null; info = null }, modifier = Modifier.fillMaxWidth()) {
                    Text("Zapomněli jste heslo?")
                }
                Text("Přístup do portálu vám založí vaše účetní kancelář. V pozvánce e-mailem si nastavíte heslo a pak se tu přihlásíte.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun PasswordField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) },
        singleLine = true, visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier.fillMaxWidth(),
    )
}
