package com.overdrive.quest.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.overdrive.quest.R
import com.overdrive.quest.core.model.Preset
import com.overdrive.quest.core.monitor.Metrics
import com.overdrive.quest.ui.MainViewModel
import com.overdrive.quest.ui.Screen
import com.overdrive.quest.ui.components.AnimatedNumber
import com.overdrive.quest.ui.components.Banner
import com.overdrive.quest.ui.components.ButtonStyle
import com.overdrive.quest.ui.components.ChartLegend
import com.overdrive.quest.ui.components.ChartSeries
import com.overdrive.quest.ui.components.GlassCard
import com.overdrive.quest.ui.components.LineChart
import com.overdrive.quest.ui.components.NeonButton
import com.overdrive.quest.ui.components.NeonProgress
import com.overdrive.quest.ui.components.Pill
import com.overdrive.quest.ui.components.RingGauge
import com.overdrive.quest.ui.components.SectionHeader
import com.overdrive.quest.ui.components.ToggleRow
import com.overdrive.quest.ui.components.caps
import com.overdrive.quest.ui.components.gradientText
import com.overdrive.quest.ui.theme.LocalPalette
import com.overdrive.quest.ui.theme.OD

@Composable
fun DashboardScreen(vm: MainViewModel, onNavigate: (Screen) -> Unit) {
    val metrics by vm.metrics.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val caps by vm.caps.collectAsStateWithLifecycle()
    val access by vm.access.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val appProfiles by vm.appProfiles.collectAsStateWithLifecycle()
    val palette = LocalPalette.current

    ScreenColumn {
        if (!access.ready) {
            Banner(
                text = stringResource(R.string.banner_no_access),
                icon = Icons.Rounded.Link,
                action = stringResource(R.string.action_connect),
                onAction = { onNavigate(Screen.SETTINGS) },
            )
        }

        CardRow {
            // Hero: live FPS and one-tap boost
            GlassCard(Modifier.weight(1.35f), glow = metrics.vrActive) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (metrics.vrActive) {
                        Pill(stringResource(R.string.dash_live), color = OD.Good, pulse = true)
                        Spacer(Modifier.width(10.dp))
                        metrics.foreground?.let { Pill(vm.label(it), color = OD.TextDim) }
                    } else {
                        Pill(stringResource(R.string.dash_idle), color = OD.TextDim)
                    }
                    Spacer(Modifier.weight(1f))
                    val preset = Preset.detect(settings.profile, caps)
                    Text(
                        (preset?.let { stringResource(MainViewModel.presetName(it)) } ?: profileSummary(settings.profile)).caps(),
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.gradientText(palette.horizontal),
                    )
                }
                Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 8.dp)) {
                    AnimatedNumber(metrics.fps, fontSize = 104, modifier = Modifier.gradientText(palette.gradient))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.padding(bottom = 18.dp)) {
                        Text(
                            "/ ${metrics.targetFps ?: "--"}",
                            style = MaterialTheme.typography.headlineMedium,
                            color = OD.TextDim,
                        )
                        Text("FPS", style = MaterialTheme.typography.labelLarge, color = OD.TextFaint)
                    }
                    Spacer(Modifier.weight(1f))
                    Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(bottom = 18.dp)) {
                        frameStat(stringResource(R.string.dash_stale), metrics.vr?.stale?.toString())
                        frameStat(stringResource(R.string.dash_gpu_time), metrics.vr?.appGpuMs?.let { "%.1f ms".format(it) })
                        frameStat(stringResource(R.string.dash_latency), metrics.vr?.predictionMs?.let { "$it ms" })
                    }
                }
                Text(
                    if (metrics.vrActive) stringResource(R.string.dash_hint_live) else stringResource(R.string.dash_hint_idle),
                    style = MaterialTheme.typography.bodySmall,
                    color = OD.TextDim,
                )
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    NeonButton(
                        stringResource(R.string.action_boost_now),
                        onClick = { vm.applyPreset(Preset.OVERDRIVE) },
                        icon = Icons.Rounded.FlashOn,
                        loading = MainViewModel.KEY_APPLY in busy,
                        enabled = access.ready,
                        modifier = Modifier.weight(1f),
                    )
                    NeonButton(
                        stringResource(R.string.action_stock),
                        onClick = vm::resetStock,
                        icon = Icons.Rounded.Restore,
                        style = ButtonStyle.GHOST,
                        enabled = access.ready,
                    )
                }
            }

            // Engine quick switches
            GlassCard(Modifier.weight(1f)) {
                SectionHeader(stringResource(R.string.dash_engine), subtitle = stringResource(R.string.dash_engine_sub))
                Spacer(Modifier.height(10.dp))
                ToggleRow(
                    title = stringResource(R.string.auto_title),
                    subtitle = stringResource(R.string.dash_auto_sub),
                    checked = settings.auto.enabled,
                    onCheckedChange = { on -> vm.setAuto { it.copy(enabled = on) } },
                    icon = Icons.Rounded.AutoAwesome,
                )
                ToggleRow(
                    title = stringResource(R.string.profiles_auto_switch),
                    subtitle = stringResource(R.string.dash_profiles_sub, appProfiles.size),
                    checked = settings.perAppProfiles,
                    onCheckedChange = { on -> vm.updateSettings { it.copy(perAppProfiles = on) } },
                    icon = Icons.Rounded.SportsEsports,
                )
                ToggleRow(
                    title = stringResource(R.string.overlay_hud),
                    subtitle = stringResource(R.string.dash_overlay_sub),
                    checked = settings.overlay.enabled,
                    onCheckedChange = { on -> vm.setOverlay { it.copy(enabled = on) } },
                    icon = Icons.Rounded.Layers,
                )
            }
        }

        // Gauges
        GlassCard(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                CpuGauge(metrics)
                GpuGauge(metrics)
                TempGauge(metrics)
                BatteryGauge(metrics)
            }
        }

        CardRow {
            TelemetryCard(history, metrics, Modifier.weight(1.6f))
            CoresCard(metrics, Modifier.weight(1f))
        }

        SectionHeader(stringResource(R.string.dash_presets), subtitle = stringResource(R.string.dash_presets_sub))
        PresetStrip(vm, settings.profile, caps, access.ready)
    }
}

@Composable
private fun frameStat(label: String, value: String?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label.caps(), style = MaterialTheme.typography.labelSmall, color = OD.TextFaint)
        Spacer(Modifier.width(8.dp))
        Text(value ?: "--", style = MaterialTheme.typography.titleSmall, color = OD.Text)
    }
}

@Composable
private fun CpuGauge(m: Metrics) {
    val palette = LocalPalette.current
    val mhz = m.cpuMHz
    val max = m.cpuMaxMHz ?: 2_400
    RingGauge(
        fraction = mhz?.let { it.toFloat() / max },
        value = mhz?.let { if (it >= 1000) "%.2f".format(it / 1000f) else "$it" } ?: "--",
        unit = if ((mhz ?: 0) >= 1000) "GHz" else "MHz",
        label = "CPU",
        caption = stringResource(R.string.level_caption, m.cpuLevel?.toString() ?: "--"),
        color = palette.primary,
        endColor = palette.secondary,
    )
}

@Composable
private fun GpuGauge(m: Metrics) {
    val palette = LocalPalette.current
    val mhz = m.gpuMHz
    val util = m.gpuUtil?.let { " · ${(it * 100).toInt()}%" } ?: ""
    RingGauge(
        fraction = mhz?.let { it.toFloat() / (m.gpuMaxMHz ?: 700) },
        value = mhz?.toString() ?: "--",
        unit = "MHz",
        label = "GPU",
        caption = stringResource(R.string.level_caption, m.gpuLevel?.toString() ?: "--") + util,
        color = palette.secondary,
        endColor = palette.tertiary,
    )
}

@Composable
private fun TempGauge(m: Metrics) {
    val palette = LocalPalette.current
    val soc = m.socTempC
    val shown = soc ?: m.battery.tempC
    RingGauge(
        fraction = shown?.let { (it - 20f) / 80f },
        value = formatTemp(shown),
        unit = "°C",
        label = if (soc != null) stringResource(R.string.gauge_soc) else stringResource(R.string.gauge_battery_temp),
        caption = m.snapshot?.skinTempC?.let { stringResource(R.string.gauge_skin, formatTemp(it)) },
        color = palette.heat(((shown ?: 30f) - 25f) / 65f),
        endColor = OD.Hot,
    )
}

@Composable
private fun BatteryGauge(m: Metrics) {
    val level = m.battery.level
    val caption = buildString {
        if (m.battery.charging) append(stringResource(R.string.gauge_charging)) else m.battery.currentMa?.let { append("${kotlin.math.abs(it)} mA") }
        m.battery.tempC?.let { append(" · ${formatTemp(it)}°C") }
    }
    RingGauge(
        fraction = level?.let { it / 100f },
        value = level?.toString() ?: "--",
        unit = "%",
        label = stringResource(R.string.gauge_battery),
        caption = caption.ifBlank { null },
        color = if ((level ?: 100) <= 20) OD.Hot else OD.Good,
        endColor = if ((level ?: 100) <= 20) OD.Warn else LocalPalette.current.primary,
    )
}

@Composable
private fun TelemetryCard(history: com.overdrive.quest.core.monitor.History, m: Metrics, modifier: Modifier) {
    val palette = LocalPalette.current
    val series = listOf(
        ChartSeries(stringResource(R.string.chart_fps), history.fps, palette.primary, 0f, ((m.targetFps ?: 90) + 10).toFloat(), fill = true),
        ChartSeries(stringResource(R.string.chart_gpu), history.gpuUtil, palette.secondary, 0f, 1f),
        ChartSeries(stringResource(R.string.chart_temp), history.socTemp, OD.Hot, 20f, 100f),
    )
    GlassCard(modifier) {
        SectionHeader(stringResource(R.string.dash_telemetry), subtitle = stringResource(R.string.dash_telemetry_sub))
        Spacer(Modifier.height(12.dp))
        ChartLegend(series, valueText = { s ->
            when (s.values.lastOrNull()) {
                null -> "--"
                else -> when (s.color) {
                    palette.primary -> "${m.fps ?: "--"}"
                    palette.secondary -> m.gpuUtil?.let { "${(it * 100).toInt()}%" } ?: "--"
                    else -> "${formatTemp(m.socTempC)}°"
                }
            }
        })
        Spacer(Modifier.height(12.dp))
        LineChart(series, Modifier.fillMaxWidth().height(190.dp))
    }
}

@Composable
private fun CoresCard(m: Metrics, modifier: Modifier) {
    val palette = LocalPalette.current
    GlassCard(modifier) {
        SectionHeader(stringResource(R.string.dash_cores), subtitle = stringResource(R.string.dash_cores_sub))
        Spacer(Modifier.height(12.dp))
        val cores = m.snapshot?.cores.orEmpty()
        if (cores.isEmpty()) {
            Text(stringResource(R.string.needs_access_data), style = MaterialTheme.typography.bodySmall, color = OD.TextDim)
        }
        cores.forEach { core ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 3.dp)) {
                Text("C${core.index}", style = MaterialTheme.typography.labelMedium, color = OD.TextDim, modifier = Modifier.width(34.dp))
                val f = if (core.online && core.curMHz != null && core.maxMHz != null && core.maxMHz > 0) core.curMHz.toFloat() / core.maxMHz else 0f
                Box(Modifier.weight(1f)) {
                    NeonProgress(f, color = if (core.online) palette.heat(f) else OD.TextFaint, height = 7.dp)
                }
                Text(
                    if (core.online) core.curMHz?.toString() ?: "--" else "OFF",
                    style = MaterialTheme.typography.labelMedium,
                    color = OD.Text,
                    modifier = Modifier.width(56.dp).padding(start = 10.dp),
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        val ram = m.ram
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(palette.primary.copy(alpha = 0.12f))
                    .padding(6.dp),
            ) {
                androidx.compose.material3.Icon(Icons.Rounded.Storage, null, tint = palette.primary)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.dash_ram).caps(), style = MaterialTheme.typography.labelSmall, color = OD.TextDim)
                Text(
                    ram?.let { "%.1f / %.1f GB".format(it.usedMb / 1024f, it.totalMb / 1024f) } ?: "--",
                    style = MaterialTheme.typography.titleSmall,
                    color = OD.Text,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        NeonProgress(ram?.usedFraction ?: 0f)
    }
}
