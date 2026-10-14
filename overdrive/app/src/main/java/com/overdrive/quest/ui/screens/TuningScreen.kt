package com.overdrive.quest.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeveloperBoard
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.Undo
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.overdrive.quest.R
import com.overdrive.quest.core.model.DeviceCaps
import com.overdrive.quest.core.model.PerformanceProfile
import com.overdrive.quest.ui.MainViewModel
import com.overdrive.quest.ui.components.Banner
import com.overdrive.quest.ui.components.ButtonStyle
import com.overdrive.quest.ui.components.ChipGroup
import com.overdrive.quest.ui.components.GlassCard
import com.overdrive.quest.ui.components.IconBadge
import com.overdrive.quest.ui.components.LabeledSlider
import com.overdrive.quest.ui.components.LevelSelector
import com.overdrive.quest.ui.components.NeonButton
import com.overdrive.quest.ui.components.Pill
import com.overdrive.quest.ui.components.SectionHeader
import com.overdrive.quest.ui.components.ToggleRow
import com.overdrive.quest.ui.components.caps
import com.overdrive.quest.ui.components.formatClock
import com.overdrive.quest.ui.theme.LocalPalette
import com.overdrive.quest.ui.theme.OD
import kotlin.math.roundToInt

@Composable
fun TuningScreen(vm: MainViewModel) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val caps by vm.caps.collectAsStateWithLifecycle()
    val metrics by vm.metrics.collectAsStateWithLifecycle()
    val cpuClocks by vm.cpuClocks.collectAsStateWithLifecycle()
    val gpuClocks by vm.gpuClocks.collectAsStateWithLifecycle()
    val access by vm.access.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current

    var draft by remember(settings.profile) { mutableStateOf(settings.profile) }
    val dirty = draft != settings.profile

    ScreenColumn {
        SectionHeader(stringResource(R.string.tuning_presets), subtitle = stringResource(R.string.tuning_presets_sub))
        PresetStrip(vm, settings.profile, caps, access.ready)

        if (settings.auto.enabled) {
            Banner(stringResource(R.string.tuning_auto_note), icon = Icons.Rounded.Info, color = OD.Cool)
        }

        CardRow {
            LevelCard(
                title = stringResource(R.string.tuning_cpu),
                subtitle = stringResource(R.string.tuning_cpu_sub),
                icon = Icons.Rounded.Memory,
                value = draft.cpuLevel,
                max = caps.maxCpuLevel,
                clocks = cpuClocks,
                live = metrics.cpuLevel,
                liveClock = metrics.cpuMHz,
                onSelect = { draft = draft.copy(cpuLevel = it) },
                modifier = Modifier.weight(1f),
            )
            LevelCard(
                title = stringResource(R.string.tuning_gpu),
                subtitle = stringResource(R.string.tuning_gpu_sub),
                icon = Icons.Rounded.DeveloperBoard,
                value = draft.gpuLevel,
                max = caps.maxGpuLevel,
                clocks = gpuClocks,
                live = metrics.gpuLevel,
                liveClock = metrics.gpuMHz,
                onSelect = { draft = draft.copy(gpuLevel = it) },
                modifier = Modifier.weight(1f),
            )
        }

        DisplayCard(draft, caps, onChange = { draft = it })

        // Apply bar
        GlassCard(Modifier.fillMaxWidth(), glow = dirty) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        (if (dirty) stringResource(R.string.tuning_pending) else stringResource(R.string.tuning_in_sync)).caps(),
                        style = MaterialTheme.typography.titleMedium,
                        color = if (dirty) LocalPalette.current.primary else OD.Text,
                    )
                    Text(profileSummary(draft), style = MaterialTheme.typography.bodySmall, color = OD.TextDim)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    NeonButton(
                        stringResource(R.string.action_copy_adb),
                        onClick = {
                            clipboard.setText(AnnotatedString(vm.adbScript()))
                            vm.notifyCopied()
                        },
                        icon = Icons.Rounded.ContentCopy,
                        style = ButtonStyle.GHOST,
                    )
                    NeonButton(
                        stringResource(R.string.action_stock),
                        onClick = vm::resetStock,
                        icon = Icons.Rounded.Restore,
                        style = ButtonStyle.GHOST,
                        enabled = access.ready,
                    )
                    AnimatedVisibility(dirty) {
                        NeonButton(
                            stringResource(R.string.action_revert),
                            onClick = { draft = settings.profile },
                            icon = Icons.Rounded.Undo,
                            style = ButtonStyle.GHOST,
                        )
                    }
                    NeonButton(
                        stringResource(R.string.action_apply),
                        onClick = { vm.applyProfile(draft) },
                        icon = Icons.Rounded.Check,
                        enabled = dirty && access.ready,
                        loading = MainViewModel.KEY_APPLY in busy,
                    )
                }
            }
        }
    }
}

@Composable
private fun LevelCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: Int?,
    max: Int,
    clocks: Map<Int, Int>,
    live: Int?,
    liveClock: Int?,
    onSelect: (Int?) -> Unit,
    modifier: Modifier,
) {
    val palette = LocalPalette.current
    val heat = value?.let { if (max == 0) 1f else it / max.toFloat() }
    GlassCard(modifier, glow = value == max, glowColor = OD.Hot) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(icon, tint = heat?.let { palette.heat(it) })
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title.caps(), style = MaterialTheme.typography.titleMedium, color = OD.Text)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = OD.TextDim)
            }
            liveClock?.let { Pill(formatClock(it), color = palette.primary, pulse = true) }
        }
        Spacer(Modifier.height(16.dp))
        LevelSelector(
            value = value,
            max = max,
            onSelect = onSelect,
            stockLabel = stringResource(R.string.level_stock),
            clocks = clocks,
            live = live,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            when {
                value == null -> stringResource(R.string.level_desc_stock)
                value == max -> stringResource(R.string.level_desc_max)
                value >= max - 1 -> stringResource(R.string.level_desc_high)
                value <= 1 -> stringResource(R.string.level_desc_low)
                else -> stringResource(R.string.level_desc_mid)
            },
            style = MaterialTheme.typography.bodySmall,
            color = if (value == max) OD.Warn else OD.TextDim,
        )
    }
}

@Composable
private fun DisplayCard(draft: PerformanceProfile, caps: DeviceCaps, onChange: (PerformanceProfile) -> Unit) {
    GlassCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(Icons.Rounded.Tv)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.tuning_display).caps(), style = MaterialTheme.typography.titleMedium, color = OD.Text)
                Text(stringResource(R.string.tuning_display_sub), style = MaterialTheme.typography.bodySmall, color = OD.TextDim)
            }
            Pill(stringResource(R.string.tuning_relaunch), color = OD.Warn, icon = Icons.Rounded.Warning)
        }
        Spacer(Modifier.height(18.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.tuning_refresh).caps(), style = MaterialTheme.typography.labelMedium, color = OD.TextDim)
                Spacer(Modifier.height(10.dp))
                val stock = stringResource(R.string.level_stock)
                ChipGroup(
                    options = listOf<Int?>(null) + caps.refreshRates,
                    selected = draft.refreshRate,
                    label = { it?.let { hz -> "$hz Hz" } ?: stock },
                    onSelect = { onChange(draft.copy(refreshRate = it)) },
                )
                Spacer(Modifier.height(20.dp))
                Text(stringResource(R.string.tuning_ffr).caps(), style = MaterialTheme.typography.labelMedium, color = OD.TextDim)
                Spacer(Modifier.height(10.dp))
                val ffrLabels = listOf(
                    stringResource(R.string.ffr_off),
                    stringResource(R.string.ffr_low),
                    stringResource(R.string.ffr_medium),
                    stringResource(R.string.ffr_high),
                    stringResource(R.string.ffr_max),
                )
                ChipGroup(
                    options = listOf<Int?>(null, 0, 1, 2, 3, 4),
                    selected = draft.foveationLevel,
                    label = { it?.let { l -> ffrLabels[l] } ?: stock },
                    onSelect = { onChange(draft.copy(foveationLevel = it)) },
                )
                Spacer(Modifier.height(12.dp))
                val dynOptions = listOf<Boolean?>(null, true, false)
                val dynLabels = listOf(stock, stringResource(R.string.ffr_dynamic_on), stringResource(R.string.ffr_dynamic_off))
                ChipGroup(
                    options = dynOptions,
                    selected = draft.dynamicFoveation,
                    label = { dynLabels[dynOptions.indexOf(it)] },
                    onSelect = { onChange(draft.copy(dynamicFoveation = it)) },
                )
            }
            Column(Modifier.weight(1f)) {
                ToggleRow(
                    title = stringResource(R.string.tuning_resolution),
                    subtitle = stringResource(R.string.tuning_resolution_sub, caps.baseEyeWidth, caps.baseEyeHeight),
                    checked = draft.resolutionScale != null,
                    onCheckedChange = { on -> onChange(draft.copy(resolutionScale = if (on) 1.2f else null)) },
                )
                val scale = draft.resolutionScale
                if (scale != null) {
                    val (w, h) = caps.eyeSize(scale)
                    val pixels = ((scale * scale - 1f) * 100f).roundToInt()
                    Spacer(Modifier.height(8.dp))
                    LabeledSlider(
                        title = "%.2f×".format(scale),
                        valueText = "$w × $h",
                        value = scale,
                        onValueChange = { v -> onChange(draft.copy(resolutionScale = (v * 20f).roundToInt() / 20f)) },
                        valueRange = DeviceCaps.MIN_SCALE..DeviceCaps.MAX_SCALE,
                        steps = 21,
                        accent = LocalPalette.current.heat(((scale - 0.6f) / 1.1f)),
                    )
                    Text(
                        stringResource(
                            if (pixels >= 0) R.string.tuning_pixels_more else R.string.tuning_pixels_less,
                            kotlin.math.abs(pixels),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (scale > 1.35f) OD.Warn else OD.TextDim,
                    )
                } else {
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.tuning_resolution_stock), style = MaterialTheme.typography.bodySmall, color = OD.TextDim)
                }
            }
        }
    }
}
