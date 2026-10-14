package com.overdrive.quest.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BatteryAlert
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Eco
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.TrendingDown
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material.icons.rounded.Whatshot
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.overdrive.quest.R
import com.overdrive.quest.core.auto.BoostReason
import com.overdrive.quest.core.auto.EngineEvent
import com.overdrive.quest.core.auto.EngineEventKind
import com.overdrive.quest.core.model.Aggressiveness
import com.overdrive.quest.core.model.BoostMode
import com.overdrive.quest.core.model.ThermalSource
import com.overdrive.quest.ui.MainViewModel
import com.overdrive.quest.ui.components.AnimatedNumber
import com.overdrive.quest.ui.components.ChipGroup
import com.overdrive.quest.ui.components.GlassCard
import com.overdrive.quest.ui.components.IconBadge
import com.overdrive.quest.ui.components.PersistedSlider
import com.overdrive.quest.ui.components.NeonButton
import com.overdrive.quest.ui.components.ButtonStyle
import com.overdrive.quest.ui.components.NeonProgress
import com.overdrive.quest.ui.components.NeonSwitch
import com.overdrive.quest.ui.components.Pill
import com.overdrive.quest.ui.components.SectionHeader
import com.overdrive.quest.ui.components.ToggleRow
import com.overdrive.quest.ui.components.caps
import com.overdrive.quest.ui.components.gradientText
import com.overdrive.quest.ui.components.neonGlow
import com.overdrive.quest.ui.theme.LocalPalette
import com.overdrive.quest.ui.theme.OD
import kotlin.math.roundToInt

@Composable
fun AutoBoostScreen(vm: MainViewModel) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val metrics by vm.metrics.collectAsStateWithLifecycle()
    val caps by vm.caps.collectAsStateWithLifecycle()
    val events by vm.events.collectAsStateWithLifecycle()
    val auto = settings.auto
    val palette = LocalPalette.current

    ScreenColumn {
        // Hero
        GlassCard(Modifier.fillMaxWidth(), glow = auto.enabled) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.auto_title).caps(),
                            style = MaterialTheme.typography.headlineMedium,
                            modifier = Modifier.gradientText(palette.horizontal),
                        )
                        Spacer(Modifier.width(14.dp))
                        if (auto.enabled) {
                            Pill(stringResource(R.string.auto_engaged), color = OD.Good, pulse = true)
                        } else {
                            Pill(stringResource(R.string.auto_standby), color = OD.TextDim)
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(stringResource(R.string.auto_desc), style = MaterialTheme.typography.bodyMedium, color = OD.TextDim)
                }
                Spacer(Modifier.width(20.dp))
                NeonSwitch(auto.enabled, { on -> vm.setAuto { it.copy(enabled = on) } }, Modifier.size(width = 58.dp, height = 32.dp))
            }
            Spacer(Modifier.height(22.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                LevelMeter("CPU", metrics.cpuLevel, caps.maxCpuLevel)
                LevelMeter("GPU", metrics.gpuLevel, caps.maxGpuLevel)
                Column {
                    Text(stringResource(R.string.auto_live_fps).caps(), style = MaterialTheme.typography.labelSmall, color = OD.TextDim)
                    Row(verticalAlignment = Alignment.Bottom) {
                        AnimatedNumber(metrics.fps, fontSize = 56)
                        Text(
                            " / ${metrics.targetFps ?: "--"}",
                            style = MaterialTheme.typography.titleLarge,
                            color = OD.TextDim,
                            modifier = Modifier.padding(bottom = 10.dp),
                        )
                    }
                }
                Column(Modifier.weight(1f)) {
                    val temp = if (auto.thermalSource == ThermalSource.SOC) metrics.socTempC ?: metrics.battery.tempC else metrics.battery.tempC
                    val limit = auto.limitC.toFloat()
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.auto_thermal_headroom).caps(), style = MaterialTheme.typography.labelSmall, color = OD.TextDim, modifier = Modifier.weight(1f))
                        Text("${formatTemp(temp)}° / ${auto.limitC}°", style = MaterialTheme.typography.titleSmall, color = OD.Text)
                    }
                    Spacer(Modifier.height(8.dp))
                    val f = temp?.let { it / limit } ?: 0f
                    NeonProgress(f, color = palette.heat((f - 0.5f) * 2f), height = 10.dp)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        if (metrics.vrActive) stringResource(R.string.auto_state_game) else stringResource(R.string.auto_state_idle),
                        style = MaterialTheme.typography.bodySmall,
                        color = OD.TextDim,
                    )
                }
            }
        }

        // Modes
        SectionHeader(stringResource(R.string.auto_mode), subtitle = stringResource(R.string.auto_mode_sub))
        CardRow {
            ModeCard(BoostMode.ADAPTIVE, auto.mode, Icons.Rounded.AutoAwesome, R.string.mode_adaptive, R.string.mode_adaptive_desc, palette.primary, Modifier.weight(1f)) {
                vm.setAuto { it.copy(mode = BoostMode.ADAPTIVE) }
            }
            ModeCard(BoostMode.MAX_SUSTAIN, auto.mode, Icons.Rounded.FlashOn, R.string.mode_max, R.string.mode_max_desc, OD.Hot, Modifier.weight(1f)) {
                vm.setAuto { it.copy(mode = BoostMode.MAX_SUSTAIN) }
            }
            ModeCard(BoostMode.ECO_SMART, auto.mode, Icons.Rounded.Eco, R.string.mode_eco, R.string.mode_eco_desc, OD.Good, Modifier.weight(1f)) {
                vm.setAuto { it.copy(mode = BoostMode.ECO_SMART) }
            }
        }

        CardRow {
            // Thermal guard
            GlassCard(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(Icons.Rounded.Whatshot, tint = OD.Hot)
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(stringResource(R.string.auto_thermal).caps(), style = MaterialTheme.typography.titleMedium, color = OD.Text)
                        Text(stringResource(R.string.auto_thermal_sub), style = MaterialTheme.typography.bodySmall, color = OD.TextDim)
                    }
                }
                Spacer(Modifier.height(16.dp))
                ChipGroup(
                    options = ThermalSource.entries,
                    selected = auto.thermalSource,
                    label = { stringResource(if (it == ThermalSource.SOC) R.string.auto_source_soc else R.string.auto_source_battery) },
                    onSelect = { src -> vm.setAuto { it.copy(thermalSource = src) } },
                )
                Spacer(Modifier.height(14.dp))
                if (auto.thermalSource == ThermalSource.SOC) {
                    PersistedSlider(
                        title = stringResource(R.string.auto_limit),
                        valueText = { "${it.roundToInt()}°C" },
                        value = auto.socLimitC.toFloat(),
                        onCommit = { v -> vm.setAuto { it.copy(socLimitC = v.roundToInt()) } },
                        valueRange = 65f..95f,
                        steps = 29,
                        accent = palette.heat((auto.socLimitC - 65f) / 30f),
                    )
                } else {
                    PersistedSlider(
                        title = stringResource(R.string.auto_limit),
                        valueText = { "${it.roundToInt()}°C" },
                        value = auto.batteryLimitC.toFloat(),
                        onCommit = { v -> vm.setAuto { it.copy(batteryLimitC = v.roundToInt()) } },
                        valueRange = 36f..50f,
                        steps = 13,
                        accent = palette.heat((auto.batteryLimitC - 36f) / 14f),
                    )
                }
            }

            // Battery + behaviour
            GlassCard(Modifier.weight(1f)) {
                ToggleRow(
                    title = stringResource(R.string.auto_battery_guard),
                    subtitle = stringResource(R.string.auto_battery_guard_sub, auto.batteryGuardPct),
                    checked = auto.batteryGuard,
                    onCheckedChange = { on -> vm.setAuto { it.copy(batteryGuard = on) } },
                    icon = Icons.Rounded.BatteryAlert,
                )
                if (auto.batteryGuard) {
                    PersistedSlider(
                        title = stringResource(R.string.auto_battery_threshold),
                        valueText = { "${(it / 5f).roundToInt() * 5}%" },
                        value = auto.batteryGuardPct.toFloat(),
                        onCommit = { v -> vm.setAuto { it.copy(batteryGuardPct = (v / 5f).roundToInt() * 5) } },
                        valueRange = 5f..50f,
                        steps = 8,
                    )
                }
                ToggleRow(
                    title = stringResource(R.string.auto_idle_eco),
                    subtitle = stringResource(R.string.auto_idle_eco_sub),
                    checked = auto.idleEco,
                    onCheckedChange = { on -> vm.setAuto { it.copy(idleEco = on) } },
                    icon = Icons.Rounded.AcUnit,
                )
                Spacer(Modifier.height(10.dp))
                Text(stringResource(R.string.auto_aggressiveness).caps(), style = MaterialTheme.typography.labelMedium, color = OD.TextDim)
                Spacer(Modifier.height(10.dp))
                ChipGroup(
                    options = Aggressiveness.entries,
                    selected = auto.aggressiveness,
                    label = {
                        stringResource(
                            when (it) {
                                Aggressiveness.GENTLE -> R.string.aggr_gentle
                                Aggressiveness.NORMAL -> R.string.aggr_normal
                                Aggressiveness.AGGRESSIVE -> R.string.aggr_aggressive
                            },
                        )
                    },
                    onSelect = { a -> vm.setAuto { it.copy(aggressiveness = a) } },
                )
            }
        }

        // Range
        GlassCard(Modifier.fillMaxWidth()) {
            SectionHeader(stringResource(R.string.auto_range), subtitle = stringResource(R.string.auto_range_sub))
            Spacer(Modifier.height(14.dp))
            val maxLevel = minOf(caps.maxCpuLevel, caps.maxGpuLevel)
            Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.auto_floor).caps(), style = MaterialTheme.typography.labelMedium, color = OD.TextDim)
                    Spacer(Modifier.height(10.dp))
                    ChipGroup(
                        options = (0..maxLevel).toList(),
                        selected = auto.floorLevel,
                        label = { "L$it" },
                        onSelect = { l -> vm.setAuto { it.copy(floorLevel = l, ceilingLevel = it.ceilingLevel?.coerceAtLeast(l)) } },
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.auto_ceiling).caps(), style = MaterialTheme.typography.labelMedium, color = OD.TextDim)
                    Spacer(Modifier.height(10.dp))
                    val maxLabel = stringResource(R.string.auto_ceiling_max)
                    ChipGroup(
                        options = listOf<Int?>(null) + (auto.floorLevel..maxLevel).toList(),
                        selected = auto.ceilingLevel,
                        label = { it?.let { l -> "L$l" } ?: maxLabel },
                        onSelect = { l -> vm.setAuto { it.copy(ceilingLevel = l) } },
                    )
                }
            }
        }

        // Live log
        GlassCard(Modifier.fillMaxWidth()) {
            SectionHeader(
                stringResource(R.string.auto_log),
                subtitle = stringResource(R.string.auto_log_sub),
                trailing = {
                    NeonButton(
                        stringResource(R.string.action_clear),
                        onClick = vm::clearEvents,
                        icon = Icons.Rounded.DeleteSweep,
                        style = ButtonStyle.GHOST,
                        height = 40.dp,
                    )
                },
            )
            Spacer(Modifier.height(12.dp))
            if (events.isEmpty()) {
                Text(stringResource(R.string.auto_log_empty), style = MaterialTheme.typography.bodySmall, color = OD.TextDim)
            }
            events.take(30).forEach { EventRow(it, vm) }
        }
    }
}

@Composable
private fun LevelMeter(label: String, level: Int?, max: Int) {
    val palette = LocalPalette.current
    val animated by animateFloatAsState(((level ?: 0).toFloat() / max.coerceAtLeast(1)), label = "meter")
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(width = 46.dp, height = 110.dp)
                .neonGlow(palette.heat(animated), 12.dp, 10.dp, if (level != null) 0.6f else 0f),
        ) {
            Canvas(Modifier.size(width = 46.dp, height = 110.dp)) {
                val segments = max + 1
                val gap = 5f
                val segH = (size.height - gap * (segments - 1)) / segments
                for (i in 0 until segments) {
                    val y = size.height - (i + 1) * segH - i * gap
                    val lit = level != null && i <= level
                    drawRoundRect(
                        color = if (lit) palette.heat(i / max.coerceAtLeast(1).toFloat()) else Color.White.copy(alpha = 0.07f),
                        topLeft = Offset(0f, y),
                        size = Size(size.width, segH),
                        cornerRadius = CornerRadius(6f),
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text("$label ${level ?: "-"}", style = MaterialTheme.typography.labelLarge, color = OD.Text)
    }
}

@Composable
private fun ModeCard(
    mode: BoostMode,
    selected: BoostMode,
    icon: ImageVector,
    title: Int,
    desc: Int,
    color: Color,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val active = mode == selected
    GlassCard(modifier, glow = active, glowColor = color, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(icon, tint = color)
            Spacer(Modifier.weight(1f))
            if (active) Pill(stringResource(R.string.selected), color = color)
        }
        Spacer(Modifier.height(14.dp))
        Text(stringResource(title).caps(), style = MaterialTheme.typography.titleMedium, color = if (active) color else OD.Text)
        Spacer(Modifier.height(4.dp))
        Text(stringResource(desc), style = MaterialTheme.typography.bodySmall, color = OD.TextDim, minLines = 3)
    }
}

@Composable
private fun EventRow(e: EngineEvent, vm: MainViewModel) {
    val palette = LocalPalette.current
    val d = e.decision
    val (icon, color) = when {
        e.kind == EngineEventKind.APP_PROFILE || e.kind == EngineEventKind.APP_RESTORE -> Icons.Rounded.SportsEsports to palette.tertiary
        e.kind == EngineEventKind.ENGAGED -> Icons.Rounded.AutoAwesome to OD.Good
        e.kind == EngineEventKind.DISENGAGED -> Icons.Rounded.AutoAwesome to OD.TextDim
        d?.reason == BoostReason.THERMAL -> Icons.Rounded.Whatshot to OD.Hot
        d?.reason == BoostReason.BATTERY_GUARD -> Icons.Rounded.BatteryAlert to OD.Warn
        d?.reason == BoostReason.IDLE || d?.reason == BoostReason.HEADROOM -> Icons.Rounded.TrendingDown to OD.Cool
        else -> Icons.Rounded.TrendingUp to palette.primary
    }
    val title = when (e.kind) {
        EngineEventKind.APP_PROFILE -> stringResource(R.string.event_app_profile, e.appLabel ?: "")
        EngineEventKind.APP_RESTORE -> stringResource(R.string.event_app_restore)
        EngineEventKind.ENGAGED -> stringResource(R.string.event_engaged)
        EngineEventKind.DISENGAGED -> stringResource(R.string.event_disengaged)
        EngineEventKind.BOOST -> stringResource(reasonLabel(d?.reason))
    }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.03f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(formatTime(e.timeMs), style = MaterialTheme.typography.labelMedium, color = OD.TextFaint, modifier = Modifier.width(78.dp))
        Text(title, style = MaterialTheme.typography.titleSmall, color = OD.Text, modifier = Modifier.weight(1f))
        if (d != null) {
            val changes = buildList {
                if (d.cpu != d.fromCpu) add("CPU ${d.fromCpu}→${d.cpu}")
                if (d.gpu != d.fromGpu) add("GPU ${d.fromGpu}→${d.gpu}")
            }.joinToString("  ")
            Text(changes, style = MaterialTheme.typography.labelLarge, color = color)
            val context = listOfNotNull(
                d.fps?.let { f -> "${f}/${d.targetFps ?: "--"} fps" },
                d.tempC?.let { "${formatTemp(it)}°C" },
            ).joinToString(" · ")
            if (context.isNotEmpty()) {
                Spacer(Modifier.width(14.dp))
                Text(context, style = MaterialTheme.typography.bodySmall, color = OD.TextDim)
            }
        }
    }
}

private fun reasonLabel(reason: BoostReason?): Int = when (reason) {
    BoostReason.FPS_DROP -> R.string.reason_fps_drop
    BoostReason.HEADROOM -> R.string.reason_headroom
    BoostReason.THERMAL -> R.string.reason_thermal
    BoostReason.THERMAL_RECOVER -> R.string.reason_thermal_recover
    BoostReason.BATTERY_GUARD -> R.string.reason_battery
    BoostReason.IDLE -> R.string.reason_idle
    BoostReason.VR_START -> R.string.reason_vr_start
    BoostReason.SUSTAIN -> R.string.reason_sustain
    null -> R.string.reason_fps_drop
}
