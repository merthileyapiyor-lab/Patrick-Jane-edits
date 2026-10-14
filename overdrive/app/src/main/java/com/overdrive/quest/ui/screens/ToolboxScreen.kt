package com.overdrive.quest.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Assessment
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.FiberManualRecord
import androidx.compose.material.icons.rounded.GetApp
import androidx.compose.material.icons.rounded.PanTool
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.overdrive.quest.R
import com.overdrive.quest.core.model.CaptureConfig
import com.overdrive.quest.core.session.SessionSummary
import com.overdrive.quest.ui.MainViewModel
import com.overdrive.quest.ui.components.ButtonStyle
import com.overdrive.quest.ui.components.ChipGroup
import com.overdrive.quest.ui.components.GlassCard
import com.overdrive.quest.ui.components.IconBadge
import com.overdrive.quest.ui.components.NeonButton
import com.overdrive.quest.ui.components.Pill
import com.overdrive.quest.ui.components.RingGauge
import com.overdrive.quest.ui.components.SectionHeader
import com.overdrive.quest.ui.components.StatTile
import com.overdrive.quest.ui.components.ToggleRow
import com.overdrive.quest.ui.components.caps
import com.overdrive.quest.ui.theme.LocalPalette
import com.overdrive.quest.ui.theme.OD

private data class CaptureSize(val width: Int?, val height: Int?, val label: String)

@Composable
fun ToolboxScreen(vm: MainViewModel) {
    val metrics by vm.metrics.collectAsStateWithLifecycle()
    val access by vm.access.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val recording by vm.recording.collectAsStateWithLifecycle()
    val sessions by vm.sessions.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val proximityOff by vm.proximityDisabled.collectAsStateWithLifecycle()
    val palette = LocalPalette.current

    ScreenColumn {
        CardRow {
            // RAM booster
            GlassCard(Modifier.weight(1f)) {
                SectionHeader(stringResource(R.string.tool_ram), subtitle = stringResource(R.string.tool_ram_sub))
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val ram = metrics.ram
                    RingGauge(
                        fraction = ram?.usedFraction,
                        value = ram?.let { "%.1f".format(it.usedMb / 1024f) } ?: "--",
                        unit = "GB",
                        label = stringResource(R.string.tool_ram_used),
                        caption = ram?.let { stringResource(R.string.tool_ram_free, "%.1f".format(it.availMb / 1024f)) },
                        size = 136.dp,
                    )
                    Spacer(Modifier.width(18.dp))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.tool_ram_desc), style = MaterialTheme.typography.bodySmall, color = OD.TextDim)
                        Spacer(Modifier.height(14.dp))
                        NeonButton(
                            stringResource(R.string.action_boost_ram),
                            onClick = vm::boostRam,
                            icon = Icons.Rounded.Storage,
                            enabled = access.ready,
                            loading = MainViewModel.KEY_RAM in busy,
                        )
                    }
                }
            }

            // Proximity sensor
            GlassCard(Modifier.weight(1f)) {
                SectionHeader(stringResource(R.string.tool_prox), subtitle = stringResource(R.string.tool_prox_sub))
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(Icons.Rounded.PanTool, tint = if (proximityOff) OD.Warn else palette.primary, size = 52.dp)
                    Spacer(Modifier.width(16.dp))
                    Text(stringResource(R.string.tool_prox_desc), style = MaterialTheme.typography.bodySmall, color = OD.TextDim, modifier = Modifier.weight(1f))
                }
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    NeonButton(
                        stringResource(R.string.tool_prox_disable),
                        onClick = { vm.setProximity(true) },
                        enabled = access.ready,
                        loading = MainViewModel.KEY_PROX in busy,
                    )
                    NeonButton(
                        stringResource(R.string.tool_prox_restore),
                        onClick = { vm.setProximity(false) },
                        style = ButtonStyle.GHOST,
                        enabled = access.ready,
                    )
                }
            }
        }

        // Performance lab
        GlassCard(Modifier.fillMaxWidth(), glow = recording != null, glowColor = OD.Hot) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(Icons.Rounded.Assessment, tint = palette.secondary)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.lab_title).caps(), style = MaterialTheme.typography.titleMedium, color = OD.Text)
                    Text(stringResource(R.string.lab_sub), style = MaterialTheme.typography.bodySmall, color = OD.TextDim)
                }
                val rec = recording
                if (rec == null) {
                    NeonButton(
                        stringResource(R.string.lab_start),
                        onClick = vm::startRecording,
                        icon = Icons.Rounded.FiberManualRecord,
                        style = ButtonStyle.DANGER,
                    )
                } else {
                    Pill(formatDuration(((metrics.timestamp - rec.startedAt) / 1000).coerceAtLeast(0)), color = OD.Hot, pulse = true)
                    Spacer(Modifier.width(12.dp))
                    NeonButton(
                        stringResource(R.string.lab_stop),
                        onClick = vm::stopRecording,
                        icon = Icons.Rounded.Stop,
                        loading = MainViewModel.KEY_RECORD in busy,
                    )
                }
            }
            recording?.let { rec ->
                Spacer(Modifier.height(16.dp))
                SummaryStats(rec.live)
            }
            Spacer(Modifier.height(16.dp))
            if (sessions.isEmpty()) {
                Text(stringResource(R.string.lab_empty), style = MaterialTheme.typography.bodySmall, color = OD.TextDim)
            }
            sessions.forEach { s -> SessionRow(s, vm) }
        }

        CaptureCard(vm, settings.capture, access.ready, MainViewModel.KEY_CAPTURE in busy)
    }
}

@Composable
private fun SummaryStats(s: SessionSummary) {
    val palette = LocalPalette.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        StatTile(stringResource(R.string.lab_avg_fps), s.avgFps?.let { "%.1f".format(it) } ?: "--", color = palette.primary)
        StatTile(stringResource(R.string.lab_low_fps), s.onePercentLowFps?.let { "%.0f".format(it) } ?: "--")
        StatTile(stringResource(R.string.lab_smooth), s.smoothnessPct?.let { "%.0f".format(it) } ?: "--", unit = "%")
        StatTile(stringResource(R.string.lab_max_temp), formatTemp(s.maxSocC ?: s.maxBatteryC), unit = "°C", color = OD.Hot)
        StatTile(stringResource(R.string.lab_drain), s.drainPerHourPct?.let { "%.0f".format(it) } ?: "--", unit = "%/h")
    }
}

@Composable
private fun SessionRow(s: SessionSummary, vm: MainViewModel) {
    val busy by vm.busy.collectAsStateWithLifecycle()
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.035f))
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(s.app?.let { vm.label(it) } ?: stringResource(R.string.lab_unknown_app), style = MaterialTheme.typography.titleSmall, color = OD.Text)
                Text(
                    "${formatDate(s.startedAt)} · ${formatDuration(s.durationSec)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = OD.TextDim,
                )
            }
            NeonButton(
                stringResource(R.string.action_export),
                onClick = { vm.exportSession(s.id) },
                icon = Icons.Rounded.GetApp,
                style = ButtonStyle.GHOST,
                height = 40.dp,
                loading = (MainViewModel.KEY_EXPORT + s.id) in busy,
            )
            Spacer(Modifier.width(8.dp))
            NeonButton("", onClick = { vm.deleteSession(s.id) }, icon = Icons.Rounded.Delete, style = ButtonStyle.GHOST, height = 40.dp)
        }
        Spacer(Modifier.height(12.dp))
        SummaryStats(s)
    }
}

@Composable
private fun CaptureCard(vm: MainViewModel, current: CaptureConfig, enabled: Boolean, loading: Boolean) {
    var draft by remember(current) { mutableStateOf(current) }
    val stock = stringResource(R.string.level_stock)
    val sizes = listOf(
        CaptureSize(null, null, stock),
        CaptureSize(1024, 1024, "1024²"),
        CaptureSize(1280, 720, "720p"),
        CaptureSize(1920, 1080, "1080p"),
        CaptureSize(1600, 1600, "1600²"),
        CaptureSize(2560, 1440, "1440p"),
    )
    GlassCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(Icons.Rounded.Videocam)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.capture_title).caps(), style = MaterialTheme.typography.titleMedium, color = OD.Text)
                Text(stringResource(R.string.capture_sub), style = MaterialTheme.typography.bodySmall, color = OD.TextDim)
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.capture_resolution).caps(), style = MaterialTheme.typography.labelMedium, color = OD.TextDim)
                Spacer(Modifier.height(10.dp))
                ChipGroup(
                    options = sizes,
                    selected = sizes.firstOrNull { it.width == draft.width && it.height == draft.height },
                    label = { it.label },
                    onSelect = { draft = draft.copy(width = it.width, height = it.height) },
                )
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.capture_fps).caps(), style = MaterialTheme.typography.labelMedium, color = OD.TextDim)
                Spacer(Modifier.height(10.dp))
                ChipGroup(
                    options = listOf<Int?>(null, 30, 60, 72),
                    selected = draft.fps,
                    label = { it?.let { f -> "$f fps" } ?: stock },
                    onSelect = { draft = draft.copy(fps = it) },
                )
            }
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.capture_bitrate).caps(), style = MaterialTheme.typography.labelMedium, color = OD.TextDim)
                Spacer(Modifier.height(10.dp))
                ChipGroup(
                    options = listOf<Int?>(null, 10, 20, 40, 60),
                    selected = draft.bitrateMbps,
                    label = { it?.let { b -> "$b Mbps" } ?: stock },
                    onSelect = { draft = draft.copy(bitrateMbps = it) },
                )
                Spacer(Modifier.height(8.dp))
                ToggleRow(
                    title = stringResource(R.string.capture_full_rate),
                    subtitle = stringResource(R.string.capture_full_rate_sub),
                    checked = draft.fullRate == true,
                    onCheckedChange = { on -> draft = draft.copy(fullRate = if (on) true else null) },
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Spacer(Modifier.weight(1f))
            NeonButton(
                stringResource(R.string.action_stock),
                onClick = { vm.applyCapture(CaptureConfig()) },
                icon = Icons.Rounded.Restore,
                style = ButtonStyle.GHOST,
                enabled = enabled,
            )
            NeonButton(
                stringResource(R.string.action_apply),
                onClick = { vm.applyCapture(draft) },
                icon = Icons.Rounded.Check,
                enabled = enabled && draft != current,
                loading = loading,
            )
        }
    }
}
