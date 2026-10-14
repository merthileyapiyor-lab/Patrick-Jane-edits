package com.overdrive.quest.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Eco
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.overdrive.quest.R
import com.overdrive.quest.core.model.DeviceCaps
import com.overdrive.quest.core.model.PerformanceProfile
import com.overdrive.quest.core.model.Preset
import com.overdrive.quest.ui.MainViewModel
import com.overdrive.quest.ui.components.GlassCard
import com.overdrive.quest.ui.components.IconBadge
import com.overdrive.quest.ui.components.PulsingDot
import com.overdrive.quest.ui.components.caps
import com.overdrive.quest.ui.theme.LocalPalette
import com.overdrive.quest.ui.theme.OD
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Scrollable page body shared by every screen. */
@Composable
fun ScreenColumn(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 28.dp, end = 28.dp, top = 12.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        content = content,
    )
}

@Composable
fun CardRow(content: @Composable RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp), content = content)
}

fun presetIcon(p: Preset): ImageVector = when (p) {
    Preset.ECO -> Icons.Rounded.Eco
    Preset.BALANCED -> Icons.Rounded.Equalizer
    Preset.PERFORMANCE -> Icons.Rounded.Speed
    Preset.OVERDRIVE -> Icons.Rounded.FlashOn
    Preset.CLARITY -> Icons.Rounded.HighQuality
}

@Composable
fun presetColor(p: Preset): Color {
    val palette = LocalPalette.current
    return when (p) {
        Preset.ECO -> OD.Good
        Preset.BALANCED -> OD.Cool
        Preset.PERFORMANCE -> palette.primary
        Preset.OVERDRIVE -> OD.Hot
        Preset.CLARITY -> palette.tertiary
    }
}

@Composable
fun PresetCard(
    preset: Preset,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val color = presetColor(preset)
    GlassCard(
        modifier = modifier,
        glow = active,
        glowColor = color,
        contentPadding = 16.dp,
        onClick = if (enabled) onClick else null,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(presetIcon(preset), tint = color, size = 38.dp)
            Spacer(Modifier.weight(1f))
            if (active) PulsingDot(color)
        }
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(MainViewModel.presetName(preset)).caps(),
            style = MaterialTheme.typography.titleMedium,
            color = if (active) color else OD.Text,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(MainViewModel.presetDescription(preset)),
            style = MaterialTheme.typography.bodySmall,
            color = OD.TextDim,
            minLines = 2,
            maxLines = 3,
        )
    }
}

@Composable
fun PresetStrip(vm: MainViewModel, current: PerformanceProfile, caps: DeviceCaps, enabled: Boolean) {
    val active = Preset.detect(current, caps)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Preset.entries.forEach { p ->
            PresetCard(p, active == p, { vm.applyPreset(p) }, Modifier.weight(1f), enabled)
        }
    }
}

/** One-line description of a profile, e.g. "CPU 5 · GPU 5 · 120 Hz · 1.30×". */
@Composable
fun profileSummary(p: PerformanceProfile): String {
    if (p.isStock) return stringResource(R.string.profile_stock)
    val parts = buildList {
        p.cpuLevel?.let { add("CPU $it") }
        p.gpuLevel?.let { add("GPU $it") }
        p.refreshRate?.let { add("$it Hz") }
        p.resolutionScale?.let { add("%.2f×".format(it)) }
        p.foveationLevel?.let { add("FFR $it") }
    }
    return parts.joinToString(" · ").ifEmpty { stringResource(R.string.profile_custom) }
}

fun formatTemp(c: Float?): String = c?.let { "%.0f".format(it) } ?: "--"

fun formatTime(ms: Long): String = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(ms))

fun formatDate(ms: Long): String = SimpleDateFormat("d MMM · HH:mm", Locale.getDefault()).format(Date(ms))

fun formatDuration(sec: Long): String {
    val h = sec / 3600
    val m = (sec % 3600) / 60
    val s = sec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}

@Composable
fun KeyValue(label: String, value: String, modifier: Modifier = Modifier, color: Color = OD.Text) {
    Row(modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = OD.TextDim, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.titleSmall, color = color)
    }
}

@Composable
fun HintText(text: String, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    Row(modifier, verticalAlignment = Alignment.Top) {
        if (icon != null) {
            Icon(icon, null, tint = OD.TextFaint, modifier = Modifier.padding(top = 1.dp, end = 8.dp).size(16.dp))
        }
        Text(text, style = MaterialTheme.typography.bodySmall, color = OD.TextDim)
    }
}
