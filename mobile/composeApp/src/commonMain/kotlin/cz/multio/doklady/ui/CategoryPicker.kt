package cz.multio.doklady.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.ElectricBolt
import androidx.compose.material.icons.outlined.Handyman
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.LocalCafe
import androidx.compose.material.icons.outlined.LocalGasStation
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material.icons.outlined.Warehouse
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cz.multio.doklady.data.Api
import cz.multio.doklady.data.CategoriesInfo
import cz.multio.doklady.data.CategoryOption

/** Ikony druhů (klíče nastavuje kancelář v portálu: Správa → Druhy pro klienty). */
fun categoryIcon(key: String): ImageVector = when (key) {
    "box" -> Icons.Outlined.Inventory2
    "fuel" -> Icons.Outlined.LocalGasStation
    "tool" -> Icons.Outlined.Handyman
    "bolt" -> Icons.Outlined.ElectricBolt
    "phone" -> Icons.Outlined.Smartphone
    "home" -> Icons.Outlined.Home
    "car" -> Icons.Outlined.DirectionsCar
    "laptop" -> Icons.Outlined.Computer
    "cup" -> Icons.Outlined.LocalCafe
    "wrench" -> Icons.Outlined.Build
    "shield" -> Icons.Outlined.Shield
    "receipt" -> Icons.Outlined.Receipt
    "asset" -> Icons.Outlined.Warehouse
    "person" -> Icons.Outlined.Person
    "partner" -> Icons.Outlined.People
    else -> Icons.Outlined.Category
}

/** Načte nabídku druhů (null = načítá se). */
@Composable
fun rememberCategories(): CategoriesInfo? {
    var info by remember { mutableStateOf<CategoriesInfo?>(null) }
    LaunchedEffect(Api.companyId) { info = runCatching { Api.categories() }.getOrNull() ?: CategoriesInfo() }
    return info
}

/**
 * „Za co to je?“ – jeden řádek štítků (nejčastější druhy) a „Jiné…“ se všemi druhy. Nepovinné; návrh portálu
 * je přerušovaně orámovaný. selected = null → nechat na účetní.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CategoryChips(info: CategoriesInfo, selected: Int?, suggestion: CategoryOption? = null, enabled: Boolean = true, onSelect: (Int?) -> Unit) {
    var sheet by remember { mutableStateOf(false) }
    val byId = info.options.associateBy { it.id }
    val shown = buildList {
        selected?.let { byId[it] }?.let { add(it) }
        suggestion?.let { s -> byId[s.id] }?.takeIf { s -> none { it.id == s.id } }?.let { add(it) }
        info.frequent.mapNotNull { byId[it] }.forEach { c -> if (size < 5 && none { it.id == c.id }) add(c) }
    }
    Column {
        Row(Modifier.fillMaxWidth().padding(bottom = 9.dp)) {
            Text("Za co to je?", style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp), modifier = Modifier.weight(1f))
            Text("nepovinné", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            shown.forEach { c ->
                Chip(c.name, categoryIcon(c.icon), selected = c.id == selected, suggested = selected == null && c.id == suggestion?.id, enabled = enabled) {
                    onSelect(if (c.id == selected) null else c.id)
                }
            }
            Chip("Jiné…", Icons.Outlined.MoreHoriz, selected = false, suggested = false, enabled = enabled) { sheet = true }
        }
        val hint = when {
            selected != null -> byId[selected]?.explanation
            suggestion != null -> "Přerušovaný rámeček = návrh ${suggestion.reason ?: ""}. Klepnutím ho potvrdíte."
            else -> "Když nevyberete nic, zařadí to účetní."
        }
        hint?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 9.dp)) }
    }
    if (sheet) CategorySheet(info, selected, onDismiss = { sheet = false }) { onSelect(it); sheet = false }
}

@Composable
private fun Chip(text: String, icon: ImageVector, selected: Boolean, suggested: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    val bg = when {
        selected -> primary
        suggested -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = .35f)
        else -> MaterialTheme.colorScheme.surface
    }
    val fg = when {
        selected -> MaterialTheme.colorScheme.onPrimary
        suggested -> primary
        else -> MaterialTheme.colorScheme.onSurface
    }
    val shape = RoundedCornerShape(19.dp)
    Surface(
        onClick = onClick, enabled = enabled, shape = shape, color = bg,
        border = if (selected || suggested) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .6f)),
        modifier = Modifier.height(38.dp).then(
            if (suggested) Modifier.drawBehind {
                drawRoundRect(primary, cornerRadius = CornerRadius(19.dp.toPx()),
                    style = Stroke(width = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))))
            } else Modifier
        ),
    ) {
        Row(Modifier.padding(horizontal = 13.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(17.dp), tint = fg)
            Spacer(Modifier.width(7.dp))
            Text(text, color = fg, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        }
    }
}

/** Všechny druhy jako dlaždice + hledání + „Nevím – nechat na účetní“. */
@Composable
fun CategorySheet(info: CategoriesInfo, selected: Int?, onDismiss: () -> Unit, onSelect: (Int?) -> Unit) {
    var query by remember { mutableStateOf("") }
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state, containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(horizontal = 20.dp).navigationBarsPadding().padding(bottom = 20.dp)) {
            Text("Za co to je?", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 12.dp))
            if (info.options.size > 8) {
                OutlinedTextField(query, { query = it }, placeholder = { Text("Hledat druh") }, leadingIcon = { Icon(Icons.Outlined.Search, null) },
                    singleLine = true, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp))
            }
            val q = query.trim().lowercase()
            val list = info.options.filter { q.isEmpty() || it.name.lowercase().contains(q) }
            LazyVerticalGrid(GridCells.Fixed(2), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.heightIn(max = 520.dp)) {
                items(list, key = { it.id }) { c -> Tile(c.name, categoryIcon(c.icon), c.id == selected, warn = c.icon == "person" || c.icon == "partner") { onSelect(c.id) } }
                item(key = "none") { Tile("Nevím – nechat na účetní", Icons.Outlined.MoreHoriz, selected == null, warn = false) { onSelect(null) } }
            }
        }
    }
}

@Composable
private fun Tile(text: String, icon: ImageVector, selected: Boolean, warn: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick, shape = MaterialTheme.shapes.large,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = .45f) else MaterialTheme.colorScheme.surface,
        border = BorderStroke(if (selected) 1.5.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(Modifier.fillMaxWidth().heightIn(min = 60.dp).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBadge(icon, if (selected) Tone.Primary else if (warn) Tone.Amber else Tone.Neutral)
            Spacer(Modifier.width(10.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
        }
    }
}

/** Řádek „Druh“ v detailu dokladu: vybraný druh / návrh / zamčeno po kontrole účetní. */
@Composable
fun CategoryRow(info: CategoriesInfo, selected: CategoryOption?, suggestion: CategoryOption?, editable: Boolean, busy: Boolean, onSelect: (Int?) -> Unit) {
    var sheet by remember { mutableStateOf(false) }
    val shown = selected ?: suggestion
    Row(
        Modifier.fillMaxWidth().then(if (editable && !busy) Modifier.clickable { sheet = true } else Modifier).padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Druh", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        if (shown != null) {
            IconBadge(categoryIcon(shown.icon), if (selected != null) Tone.Primary else Tone.Neutral)
            Spacer(Modifier.width(10.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(shown.name, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyLarge)
                if (selected == null) Text("návrh – klepnutím potvrdíte", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
        } else {
            Text(if (editable) "Vybrat" else "zařadí účetní", color = if (editable) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold)
        }
        if (editable) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.outline)
    }
    if (sheet) CategorySheet(info, selected?.id, onDismiss = { sheet = false }) { onSelect(it); sheet = false }
}
