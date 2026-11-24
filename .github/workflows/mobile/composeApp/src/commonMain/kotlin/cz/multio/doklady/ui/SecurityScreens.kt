package cz.multio.doklady.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cz.multio.doklady.Navigator
import cz.multio.doklady.data.Api
import cz.multio.doklady.data.PasskeyInfo
import cz.multio.doklady.data.Session
import cz.multio.doklady.platform.BiometricKeyInvalidated
import cz.multio.doklady.platform.Biometrics
import cz.multio.doklady.platform.PasskeyException
import cz.multio.doklady.platform.Passkeys
import kotlinx.coroutines.launch

/**
 * Zabezpečení (1.5): klient si vybere odemykání otiskem / obličejem, přístupový klíč, obojí, nebo nic.
 * Přístupové klíče jsou společné s webem (klíč přidaný tady funguje i v prohlížeči a naopak).
 */
@Composable
fun SecurityScreen(nav: Navigator) {
    var bio by remember { mutableStateOf(Session.biometricEnabled) }
    var keys by remember { mutableStateOf<List<PasskeyInfo>?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var info by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf<PasskeyInfo?>(null) }
    val scope = rememberCoroutineScope()
    val bioAvailable = remember { Biometrics.available() }

    suspend fun loadKeys() {
        runCatching { Api.passkeys() }.onSuccess { keys = it }.onFailure { error = nav.errorText(it) }
    }
    LaunchedEffect(Unit) { loadKeys() }

    SimpleScreen(nav, "Zabezpečení") {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
            SectionTitle("Přihlášení do aplikace", Modifier.padding(top = 0.dp))
            AppCard {
                ListRow(
                    "Otisk prstu nebo obličej",
                    if (bioAvailable) "Odemknutí aplikace při každém otevření" else "V telefonu není nastavený otisk prstu (Nastavení telefonu → Zabezpečení).",
                    Icons.Outlined.Fingerprint, if (bio) Tone.Primary else Tone.Neutral,
                    trailing = {
                        Switch(checked = bio, enabled = bioAvailable && !busy, onCheckedChange = { on ->
                            error = null; info = null
                            if (on) {
                                busy = true
                                scope.launch {
                                    bio = Session.enableBiometric()
                                    if (bio) info = "Odemykání otiskem je zapnuté. Aplikace se zamkne po zavření nebo po minutě na pozadí."
                                    busy = false
                                }
                            } else {
                                Session.disableBiometric(); bio = false
                            }
                        })
                    },
                )
                ListRow(
                    "Přístupový klíč",
                    if (Passkeys.supported()) "Přihlášení bez hesla – klíč v telefonu nebo bezpečnostní klíč (USB / NFC)" else "Na tomto zařízení zatím není k dispozici.",
                    Icons.Outlined.Key, if (keys?.isNotEmpty() == true) Tone.Primary else Tone.Neutral, divider = true,
                )
            }
            SectionTitle("Uložené přístupové klíče")
            AppCard {
                val list = keys
                when {
                    list == null -> Box(Modifier.fillMaxWidth().height(80.dp)) { Loading() }
                    list.isEmpty() -> ListRow("Zatím žádný klíč", "Přidejte klíč tlačítkem níže – pak se přihlásíte bez hesla.", Icons.Outlined.Key)
                    else -> list.forEachIndexed { i, k ->
                        ListRow(
                            k.name,
                            listOfNotNull("přidáno ${czDate(k.createdAt)}", k.lastUsedAt?.let { "použito ${czDate(it)}" }).joinToString(" · "),
                            Icons.Outlined.Key, divider = i > 0,
                            trailing = { IconButton(onClick = { confirmDelete = k }) { Icon(Icons.Outlined.Close, "Odebrat klíč") } },
                        )
                    }
                }
            }
            if (Passkeys.supported()) {
                Spacer(Modifier.height(14.dp))
                OutlinedButton(enabled = !busy, modifier = Modifier.fillMaxWidth().height(50.dp), onClick = {
                    busy = true; error = null; info = null
                    scope.launch {
                        try {
                            val challenge = Api.passkeyRegisterOptions()
                            val response = Passkeys.create(challenge.options.toString())
                            Api.passkeyRegister(response, challenge.state, Passkeys.deviceName())
                            info = "Klíč je uložený. Příště se můžete přihlásit bez hesla."
                            loadKeys()
                        } catch (e: PasskeyException) {
                            if (!e.cancelled) error = e.message
                        } catch (e: Exception) {
                            error = nav.errorText(e)
                        }
                        busy = false
                    }
                }) {
                    if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    else { Icon(Icons.Outlined.Key, null, Modifier.size(20.dp)); Spacer(Modifier.width(10.dp)); Text("Přidat přístupový klíč") }
                }
            }
            info?.let { Spacer(Modifier.height(14.dp)); AppCard { ListRow(it, null, Icons.Outlined.Lock, Tone.Primary) } }
            error?.let { Spacer(Modifier.height(14.dp)); ErrorBanner(it) }
            Spacer(Modifier.height(16.dp))
            Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium) {
                Row(Modifier.padding(14.dp)) {
                    Icon(Icons.Outlined.Lock, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(10.dp))
                    Text("Klíče jsou společné s webovým portálem – klíč přidaný tady funguje i na webu a naopak. Otisk prstu ani obličej neopustí telefon.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }

    confirmDelete?.let { k ->
        ConfirmDialog("Odebrat klíč?", "Klíč „${k.name}“ už nepůjde použít k přihlášení (ani na webu).", "Odebrat", onConfirm = {
            scope.launch { runCatching { Api.deletePasskey(k.id) }.onFailure { error = nav.errorText(it) }; loadKeys() }
        }, onDismiss = { confirmDelete = null })
    }
}

/** Zamčená aplikace: odemknutí otiskem, nebo přihlášení heslem. */
@Composable
fun LockScreen(onUnlocked: () -> Unit, onUsePassword: () -> Unit) {
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun unlock() {
        busy = true; error = null
        scope.launch {
            try {
                if (Session.unlock()) onUnlocked()
            } catch (e: BiometricKeyInvalidated) {
                error = e.message
            } catch (e: Exception) {
                error = e.message
            }
            busy = false
        }
    }
    LaunchedEffect(Unit) { unlock() }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).systemBarsPadding().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        BrandLogo(Modifier.width(200.dp))
        Spacer(Modifier.height(48.dp))
        Box(Modifier.size(84.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.Fingerprint, null, Modifier.size(44.dp), tint = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(20.dp))
        Text("Aplikace je zamčená", style = MaterialTheme.typography.titleLarge)
        Text("Odemkněte ji otiskem prstu.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 12.dp)) }
        Spacer(Modifier.height(28.dp))
        Button(onClick = ::unlock, enabled = !busy, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Odemknout") }
        TextButton(onClick = onUsePassword, modifier = Modifier.padding(top = 6.dp)) { Text("Přihlásit se heslem") }
    }
}
