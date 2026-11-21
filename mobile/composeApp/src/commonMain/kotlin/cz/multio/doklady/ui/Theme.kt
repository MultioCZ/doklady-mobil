package cz.multio.doklady.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Barvy aplikace podle portálu: tmavě zelená z ikony, neutrální šedé, barvy jen pro stav (chybí / hotovo / problém). */
object Brand {
    val Primary = Color(0xFF1D6B4F)
    val PrimarySoft = Color(0xFFE9F3EE)
    val Ink = Color(0xFF0F1B2D)
    val Ink2 = Color(0xFF334155)
    val Muted = Color(0xFF64748B)
    val Line = Color(0xFFE5E9F0)
    val Background = Color(0xFFF5F7FA)
    val Amber = Color(0xFF92590B)
    val AmberSoft = Color(0xFFFDF3E1)
    val Red = Color(0xFFB42318)
    val RedSoft = Color(0xFFFDECEA)
    val Neutral = Color(0xFFF1F4F8)
}

private val Light = lightColorScheme(
    primary = Brand.Primary, onPrimary = Color.White, primaryContainer = Brand.PrimarySoft, onPrimaryContainer = Color(0xFF0E3B2B),
    secondary = Brand.Ink2, background = Brand.Background, onBackground = Brand.Ink, surface = Color.White, onSurface = Brand.Ink,
    surfaceVariant = Brand.Neutral, onSurfaceVariant = Brand.Muted, outline = Color(0xFFCBD5E1), outlineVariant = Brand.Line,
    error = Brand.Red, errorContainer = Brand.RedSoft, onErrorContainer = Color(0xFF7A1A12),
    surfaceContainer = Color.White, surfaceContainerLow = Color.White, surfaceContainerHigh = Color.White,
)

private val Dark = darkColorScheme(
    primary = Color(0xFF7FD1AE), onPrimary = Color(0xFF00382A), primaryContainer = Color(0xFF184D3B), onPrimaryContainer = Color(0xFFC9F0DE),
    background = Color(0xFF0E141B), surface = Color(0xFF151C24), onSurface = Color(0xFFE6EAF0), surfaceVariant = Color(0xFF1F2833),
    onSurfaceVariant = Color(0xFF9AA7B8), outlineVariant = Color(0xFF2A3440), surfaceContainer = Color(0xFF151C24),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp), small = RoundedCornerShape(8.dp), medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp), extraLarge = RoundedCornerShape(22.dp),
)

private fun appTypography(): Typography {
    val base = Typography()
    return base.copy(
        headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.SemiBold, fontSize = 24.sp, letterSpacing = (-0.3).sp),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold, fontSize = 20.sp),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
        bodyLarge = base.bodyLarge.copy(fontSize = 15.sp),
        labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
    )
}

@Composable
fun DokladyTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, shapes = AppShapes, typography = appTypography(), content = content)
}

private val dark @Composable get() = isSystemInDarkTheme()

/** Bílá karta s tenkým okrajem (jako na webu). */
@Composable
fun AppCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(content = content)
    }
}

/** Nadpis sekce: malý šedý text, vpravo volitelný odkaz. */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(top = 22.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        if (action != null && onAction != null) {
            Text(action, style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp), color = MaterialTheme.colorScheme.primary, modifier = Modifier.clickable(onClick = onAction))
        }
    }
}

enum class Tone { Neutral, Primary, Amber, Red }

private fun Tone.colors(dark: Boolean): Pair<Color, Color> = when (this) {
    Tone.Neutral -> if (dark) Color(0xFF26303C) to Color(0xFFB8C2CF) else Brand.Neutral to Brand.Ink2
    Tone.Primary -> if (dark) Color(0xFF184D3B) to Color(0xFF9FE0C0) else Brand.PrimarySoft to Brand.Primary
    Tone.Amber -> if (dark) Color(0xFF4A3510) to Color(0xFFF2C46D) else Brand.AmberSoft to Brand.Amber
    Tone.Red -> if (dark) Color(0xFF4D1A15) to Color(0xFFF4A39B) else Brand.RedSoft to Brand.Red
}

/** Ikona v zaobleném čtverci (barva podle stavu). */
@Composable
fun IconBadge(icon: ImageVector, tone: Tone = Tone.Neutral) {
    val (bg, fg) = tone.colors(dark)
    Box(Modifier.size(38.dp).background(bg, RoundedCornerShape(11.dp)), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = fg, modifier = Modifier.size(20.dp))
    }
}

/** Štítek stavu („Chybí doklad“, „Zkontrolováno“). */
@Composable
fun Tag(text: String, tone: Tone = Tone.Neutral) {
    val (bg, fg) = tone.colors(dark)
    Text(text, color = fg, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold, fontSize = 12.sp),
        modifier = Modifier.background(bg, RoundedCornerShape(7.dp)).padding(horizontal = 9.dp, vertical = 3.dp))
}

/** Řádek seznamu v kartě: ikona, titulek, podtitulek, vpravo hodnota nebo šipka. */
@Composable
fun ListRow(
    title: String,
    subtitle: String? = null,
    icon: ImageVector? = null,
    tone: Tone = Tone.Neutral,
    divider: Boolean = false,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    if (divider) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    Row(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) { IconBadge(icon, tone); Spacer(Modifier.width(14.dp)) }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!subtitle.isNullOrBlank()) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp), color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        if (trailing != null) { Spacer(Modifier.width(10.dp)); trailing() }
        else if (onClick != null) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.outline)
    }
}

/** Dvojice popisek – hodnota v detailu (datum, VS, protiúčet). */
@Composable
fun KeyValue(label: String, value: String?, divider: Boolean = true) {
    if (divider) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 11.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value?.takeIf { it.isNotBlank() } ?: "—", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

val AmountStyle: TextStyle @Composable get() = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum")
