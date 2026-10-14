package com.overdrive.quest.ui.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.overdrive.quest.core.model.HudStyle
import com.overdrive.quest.core.model.OverlayConfig
import com.overdrive.quest.core.monitor.History
import com.overdrive.quest.core.monitor.Metrics
import com.overdrive.quest.ui.components.PulsingDot
import com.overdrive.quest.ui.components.Sparkline
import com.overdrive.quest.ui.components.formatClock
import com.overdrive.quest.ui.components.neonGlow
import com.overdrive.quest.ui.theme.Chakra
import com.overdrive.quest.ui.theme.LocalPalette
import com.overdrive.quest.ui.theme.OD

/** The floating performance HUD, used by the overlay window and the in-app preview. */
@Composable
fun HudPanel(
    metrics: Metrics,
    history: History,
    config: OverlayConfig,
    modifier: Modifier = Modifier,
    onDrag: ((Float, Float) -> Unit)? = null,
    onDragEnd: (() -> Unit)? = null,
) {
    val density = LocalDensity.current
    val drag by rememberUpdatedState(onDrag)
    val dragEnd by rememberUpdatedState(onDragEnd)
    CompositionLocalProvider(
        LocalDensity provides Density(density.density * config.scale, density.fontScale),
    ) {
        val dragModifier = if (onDrag != null && !config.clickThrough) {
            Modifier.pointerInput(Unit) {
                detectDragGestures(onDragEnd = { dragEnd?.invoke() }) { change, amount ->
                    change.consume()
                    drag?.invoke(amount.x, amount.y)
                }
            }
        } else {
            Modifier
        }
        when (config.style) {
            HudStyle.MINIMAL -> MinimalHud(metrics, modifier.then(dragModifier))
            HudStyle.STANDARD -> StandardHud(metrics, history, config, pro = false, modifier = modifier.then(dragModifier))
            HudStyle.PRO -> StandardHud(metrics, history, config, pro = true, modifier = modifier.then(dragModifier))
        }
    }
}

@Composable
private fun hudShell(modifier: Modifier): Modifier {
    val palette = LocalPalette.current
    val shape = RoundedCornerShape(16.dp)
    return modifier
        .padding(14.dp)
        .neonGlow(palette.primary, 16.dp, 10.dp, 0.5f)
        .clip(shape)
        .background(Brush.verticalGradient(listOf(OD.Bg1.copy(alpha = 0.92f), OD.Bg0.copy(alpha = 0.92f))))
        .border(1.dp, Brush.linearGradient(listOf(palette.primary.copy(alpha = 0.7f), palette.secondary.copy(alpha = 0.3f))), shape)
}

@Composable
private fun MinimalHud(m: Metrics, modifier: Modifier) {
    val palette = LocalPalette.current
    Row(
        hudShell(modifier).padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(m.fps?.toString() ?: "--", fontFamily = Chakra, fontSize = 26.sp, color = fpsColor(m))
        Text(" FPS", fontFamily = Chakra, fontSize = 11.sp, color = OD.TextDim)
        Spacer(Modifier.width(12.dp))
        val t = m.socTempC ?: m.battery.tempC
        Text(t?.let { "%.0f°".format(it) } ?: "--°", fontFamily = Chakra, fontSize = 18.sp, color = palette.heat(((t ?: 30f) - 30f) / 60f))
    }
}

@Composable
private fun StandardHud(m: Metrics, history: History, config: OverlayConfig, pro: Boolean, modifier: Modifier) {
    val palette = LocalPalette.current
    Column(hudShell(modifier).width(if (pro) 250.dp else 210.dp).padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.FlashOn, null, tint = palette.primary, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("OVERDRIVE", fontFamily = Chakra, fontSize = 11.sp, letterSpacing = 1.5.sp, color = OD.TextDim, modifier = Modifier.weight(1f))
            PulsingDot(if (m.vrActive) OD.Good else OD.TextFaint, size = 6.dp)
        }
        Spacer(Modifier.height(8.dp))
        if (config.showFps) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(m.fps?.toString() ?: "--", fontFamily = Chakra, fontSize = 40.sp, lineHeight = 40.sp, color = fpsColor(m))
                Text(" / ${m.targetFps ?: "--"} FPS", fontFamily = Chakra, fontSize = 13.sp, color = OD.TextDim, modifier = Modifier.padding(bottom = 6.dp))
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            if (config.showCpu) {
                HudRow("CPU", "L${m.cpuLevel ?: "-"}", m.cpuMHz?.let { formatClock(it) }, m.cpuUtil?.let { "${(it * 100).toInt()}%" })
            }
            if (config.showGpu) {
                HudRow("GPU", "L${m.gpuLevel ?: "-"}", m.gpuMHz?.let { formatClock(it) }, m.gpuUtil?.let { "${(it * 100).toInt()}%" })
            }
            if (config.showTemp) {
                val soc = m.socTempC
                HudRow("TEMP", soc?.let { "%.0f°C".format(it) } ?: "--", m.battery.tempC?.let { "BAT %.0f°".format(it) }, null, palette.heat(((soc ?: 40f) - 40f) / 50f))
            }
            if (config.showBattery) {
                HudRow("BAT", m.battery.level?.let { "$it%" } ?: "--", if (m.battery.charging) "⚡" else m.battery.currentMa?.let { "${kotlin.math.abs(it)}mA" }, null)
            }
            if (config.showRam) {
                HudRow("RAM", m.ram?.let { "%.1fG".format(it.usedMb / 1024f) } ?: "--", m.ram?.let { "/ %.0fG".format(it.totalMb / 1024f) }, null)
            }
        }
        if (pro) {
            Spacer(Modifier.height(10.dp))
            Sparkline(history.fps.takeLast(60), palette.primary, 0f, ((m.targetFps ?: 90) + 10).toFloat(), Modifier.height(38.dp).width(222.dp))
            Spacer(Modifier.height(4.dp))
            Sparkline(history.socTemp.takeLast(60), OD.Hot, 20f, 100f, Modifier.height(24.dp).width(222.dp))
        }
    }
}

@Composable
private fun HudRow(label: String, value: String, extra: String?, tail: String?, color: Color = OD.Text) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontFamily = Chakra, fontSize = 11.sp, color = OD.TextFaint, modifier = Modifier.width(42.dp))
        Text(value, fontFamily = Chakra, fontSize = 15.sp, color = color, modifier = Modifier.width(62.dp))
        Text(extra ?: "", fontFamily = Chakra, fontSize = 12.sp, color = OD.TextDim, modifier = Modifier.weight(1f))
        if (tail != null) Text(tail, fontFamily = Chakra, fontSize = 12.sp, color = LocalPalette.current.primary)
    }
}

@Composable
private fun fpsColor(m: Metrics): Color {
    val fps = m.fps ?: return OD.Text
    val target = m.targetFps ?: return OD.Text
    val ratio = fps.toFloat() / target
    return when {
        ratio >= 0.97f -> OD.Good
        ratio >= 0.85f -> OD.Warn
        else -> OD.Hot
    }
}
