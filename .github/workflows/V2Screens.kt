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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cz.multio.doklady.AppConfig
import cz.multio.doklady.Navigator
import cz.multio.doklady.data.Api
import cz.multio.doklady.data.AppVersionInfo
import cz.multio.doklady.data.Choice
import cz.multio.doklady.data.NotificationSettings
import cz.multio.doklady.platform.KeyValueStore
import cz.multio.doklady.platform.openUrl
import kotlinx.coroutines.launch

/** Stará verze po zásadní změně (2.0): aplikace se zastaví s výzvou k aktualizaci. Data zůstávají na serveru. */
@Composable
fun UpdateRequiredScreen(info: AppVersionInfo) {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxSize().systemBarsPadding().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Surface(shape = RoundedCornerShape(50), color = Brand.AmberSoft, modifier = Modifier.size(84.dp)) {
                Box(contentAlignment = Alignment.Center) { Icon(Icons.Outlined.SystemUpdate, null, tint = Brand.Amber, modifier = Modifier.size(40.dp)) }
            }
            Spacer(Modifier.height(20.dp))
            Text("Aktualizujte aplikaci", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Text("Tahle verze (${AppConfig.VERSION_NAME}) už s portálem nefunguje. Vaše doklady jsou v bezpečí – po aktualizaci je uvidíte.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp, bottom = 24.dp))
            Button(onClick = { openUrl(info.androidUrl) }, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Otevřít Google Play") }
        }
    }
}

/** Nová verze je k dispozici – nabídka jednou za verzi. */
@Composable
fun UpdateDialog(info: AppVersionInfo, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = { KeyValueStore.put("update_dismissed", info.latestCode.toString()); onDismiss() },
        title = { Text("Je k dispozici nová verze ${info.latestName}") },
        text = { Text(info.notes.ifBlank { "Aktualizace přináší opravy a novinky." }) },
        confirmButton = { Button(onClick = { openUrl(info.androidUrl); onDismiss() }) { Text("Aktualizovat") } },
        dismissButton = { TextButton(onClick = { KeyValueStore.put("update_dismissed", info.latestCode.toString()); onDismiss() }) { Text("Později") } },
    )
}

/** Nastavení upozornění v telefonu (2.0). */
@Composable
fun NotificationSettingsScreen(nav: Navigator) {
    var settings by remember { mutableStateOf<NotificationSettings?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { try { settings = Api.notificationSettings() } catch (e: Exception) { error = nav.errorText(e) } }
    SimpleScreen(nav, "Upozornění") {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
            ErrorBanner(error)
            val s = settings
            if (s == null) { if (error == null) Box(Modifier.fillMaxWidth().height(160.dp)) { Loading() }; return@Column }
            AppCard {
                s.categories.forEachIndexed { i, c ->
                    if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(c.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        Switch(checked = c.enabled, onCheckedChange = { on ->
                            scope.launch { try { settings = Api.saveNotificationSettings(c.key, on) } catch (e: Exception) { error = nav.errorText(e) } }
                        })
                    }
                }
            }
            Text("E-maily chodí dál jako dnes. Upozornění v telefonu jsou navíc. Systémové povolení upozornění najdete v nastavení telefonu.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 12.dp))
        }
    }
}

/** „Co se letos změnilo?“ – při každoročním otevření podkladů. */
@Composable
fun ChangesDialog(options: List<Choice>, summary: String, onDismiss: () -> Unit, onOk: (List<String>) -> Unit) {
    val picked = remember { mutableStateListOf<String>() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Co se letos změnilo?") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (summary.isNotBlank()) Text("Loni: $summary. Zaškrtněte, co je jinak.", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp))
                options.forEach { o ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(o.key in picked, { on ->
                            if (on) { if (o.key == "none") picked.clear() else picked.remove("none"); picked.add(o.key) } else picked.remove(o.key)
                        })
                        Text(o.label, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = { Button(enabled = picked.isNotEmpty(), onClick = { onOk(picked.toList()) }) { Text("Pokračovat") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Později") } },
    )
}
