package com.overdrive.quest.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.SettingsBackupRestore
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Usb
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.overdrive.quest.BuildConfig
import com.overdrive.quest.R
import com.overdrive.quest.core.model.AccentTheme
import com.overdrive.quest.core.shell.AccessState
import com.overdrive.quest.core.tweaks.QuestCommands
import com.overdrive.quest.ui.MainViewModel
import com.overdrive.quest.ui.accessLabel
import com.overdrive.quest.ui.components.ButtonStyle
import com.overdrive.quest.ui.components.ChipGroup
import com.overdrive.quest.ui.components.GlassCard
import com.overdrive.quest.ui.components.IconBadge
import com.overdrive.quest.ui.components.NeonButton
import com.overdrive.quest.ui.components.Pill
import com.overdrive.quest.ui.components.SectionHeader
import com.overdrive.quest.ui.components.ToggleRow
import com.overdrive.quest.ui.components.caps
import com.overdrive.quest.ui.components.neonGlow
import com.overdrive.quest.ui.theme.LocalPalette
import com.overdrive.quest.ui.theme.OD
import com.overdrive.quest.ui.theme.paletteFor

@Composable
fun SettingsScreen(vm: MainViewModel) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val access by vm.access.collectAsStateWithLifecycle()
    val caps by vm.caps.collectAsStateWithLifecycle()
    val log by vm.log.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current
    val copy: (String) -> Unit = {
        clipboard.setText(AnnotatedString(it))
        vm.notifyCopied()
    }

    ScreenColumn {
        // Access & setup
        GlassCard(Modifier.fillMaxWidth(), glow = !access.ready, glowColor = OD.Hot) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(Icons.Rounded.Security, tint = if (access.ready) OD.Good else OD.Hot, size = 52.dp)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.access_title).caps(), style = MaterialTheme.typography.titleLarge, color = OD.Text)
                    Text(stringResource(accessLabel(access)), style = MaterialTheme.typography.bodyMedium, color = if (access.ready) OD.Good else OD.Warn)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    NeonButton(stringResource(R.string.action_recheck), onClick = vm::refreshAccess, icon = Icons.Rounded.Refresh, style = ButtonStyle.GHOST)
                    if (access == AccessState.NO_PERMISSION) {
                        NeonButton(stringResource(R.string.action_allow), onClick = vm::requestShizuku, icon = Icons.Rounded.Lock)
                    }
                    if (access.ready) {
                        NeonButton(
                            stringResource(R.string.action_one_tap),
                            onClick = vm::oneTapSetup,
                            icon = Icons.Rounded.Bolt,
                            loading = MainViewModel.KEY_SETUP in busy,
                        )
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
            Text(stringResource(R.string.access_why), style = MaterialTheme.typography.bodySmall, color = OD.TextDim)
            Spacer(Modifier.height(14.dp))
            SetupStep(1, stringResource(R.string.setup_step1), Icons.Rounded.Usb)
            SetupStep(2, stringResource(R.string.setup_step2), Icons.Rounded.Link)
            CommandBox("adb shell ${QuestCommands.SHIZUKU_START}", copy)
            SetupStep(3, stringResource(R.string.setup_step3), Icons.Rounded.Lock)
            SetupStep(4, stringResource(R.string.setup_step4), Icons.Rounded.Bolt)
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.access_root), style = MaterialTheme.typography.bodySmall, color = OD.TextDim, modifier = Modifier.weight(1f))
                if (access == AccessState.READY_ROOT) {
                    NeonButton(stringResource(R.string.action_disable_root), onClick = vm::disableRoot, style = ButtonStyle.GHOST, height = 40.dp)
                } else {
                    NeonButton(
                        stringResource(R.string.action_use_root),
                        onClick = vm::enableRoot,
                        style = ButtonStyle.GHOST,
                        height = 40.dp,
                        loading = MainViewModel.KEY_ROOT in busy,
                    )
                }
            }
        }

        CardRow {
            // Appearance
            GlassCard(Modifier.weight(1f)) {
                SectionHeader(stringResource(R.string.settings_look))
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    AccentTheme.entries.forEach { accent ->
                        AccentSwatch(accent, settings.accent == accent) {
                            vm.updateSettings { it.copy(accent = accent) }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                ToggleRow(
                    title = stringResource(R.string.settings_reduce_motion),
                    subtitle = stringResource(R.string.settings_reduce_motion_sub),
                    checked = settings.reduceMotion,
                    onCheckedChange = { on -> vm.updateSettings { it.copy(reduceMotion = on) } },
                    icon = Icons.Rounded.Palette,
                )
            }

            // Engine
            GlassCard(Modifier.weight(1f)) {
                SectionHeader(stringResource(R.string.settings_engine))
                Spacer(Modifier.height(8.dp))
                ToggleRow(
                    title = stringResource(R.string.settings_apply_connect),
                    subtitle = stringResource(R.string.settings_apply_connect_sub),
                    checked = settings.applyOnConnect,
                    onCheckedChange = { on -> vm.updateSettings { it.copy(applyOnConnect = on) } },
                    icon = Icons.Rounded.SettingsBackupRestore,
                )
                ToggleRow(
                    title = stringResource(R.string.settings_extended),
                    subtitle = stringResource(R.string.settings_extended_sub, caps.maxCpuLevel, caps.maxGpuLevel),
                    checked = settings.extendedRange,
                    onCheckedChange = { on -> vm.updateSettings { it.copy(extendedRange = on) } },
                    icon = Icons.Rounded.Speed,
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Timer, null, tint = OD.TextDim, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.settings_interval).caps(), style = MaterialTheme.typography.labelMedium, color = OD.TextDim)
                }
                Spacer(Modifier.height(10.dp))
                ChipGroup(
                    options = listOf(500L, 1000L, 2000L),
                    selected = settings.sampleIntervalMs,
                    label = { if (it < 1000L) "0.5 s" else "${it / 1000} s" },
                    onSelect = { ms -> vm.updateSettings { it.copy(sampleIntervalMs = ms) } },
                )
            }
        }

        // ADB export
        GlassCard(Modifier.fillMaxWidth()) {
            SectionHeader(
                stringResource(R.string.settings_adb),
                subtitle = stringResource(R.string.settings_adb_sub),
                trailing = {
                    NeonButton(stringResource(R.string.action_copy), onClick = { copy(vm.adbScript()) }, icon = Icons.Rounded.ContentCopy, style = ButtonStyle.GHOST, height = 40.dp)
                },
            )
            Spacer(Modifier.height(12.dp))
            CodeBlock(vm.adbScript())
        }

        // Command log
        GlassCard(Modifier.fillMaxWidth()) {
            SectionHeader(
                stringResource(R.string.settings_log),
                subtitle = stringResource(R.string.settings_log_sub),
                trailing = {
                    NeonButton(stringResource(R.string.action_clear), onClick = vm::clearLog, icon = Icons.Rounded.DeleteSweep, style = ButtonStyle.GHOST, height = 40.dp)
                },
            )
            Spacer(Modifier.height(12.dp))
            if (log.isEmpty()) {
                Text(stringResource(R.string.settings_log_empty), style = MaterialTheme.typography.bodySmall, color = OD.TextDim)
            }
            log.take(40).forEach { entry ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.Black.copy(alpha = 0.3f))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(formatTime(entry.timeMs), fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = OD.TextFaint)
                        Spacer(Modifier.width(10.dp))
                        Text(entry.label ?: "shell", style = MaterialTheme.typography.labelMedium, color = OD.Text, modifier = Modifier.weight(1f))
                        Pill(
                            if (entry.result.ok) "OK" else "EXIT ${entry.result.code}",
                            color = if (entry.result.ok) OD.Good else OD.Hot,
                        )
                    }
                    Text(
                        "$ " + entry.command,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = LocalPalette.current.primary.copy(alpha = 0.85f),
                        maxLines = 2,
                    )
                    val out = (entry.result.err.ifBlank { entry.result.out }).trim()
                    if (out.isNotEmpty()) {
                        Text(out.take(300), fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = OD.TextDim, maxLines = 4)
                    }
                }
            }
        }

        // About
        GlassCard(Modifier.fillMaxWidth()) {
            SectionHeader(stringResource(R.string.settings_about))
            Spacer(Modifier.height(10.dp))
            KeyValue(stringResource(R.string.about_version), BuildConfig.VERSION_NAME)
            KeyValue(stringResource(R.string.about_device), "${caps.model} (${caps.codename})")
            KeyValue(stringResource(R.string.about_levels), "CPU 0–${caps.maxCpuLevel} · GPU 0–${caps.maxGpuLevel}")
            KeyValue(stringResource(R.string.about_eye), "${caps.baseEyeWidth} × ${caps.baseEyeHeight}")
            KeyValue(stringResource(R.string.about_refresh), caps.refreshRates.joinToString(" / ") { "$it Hz" })
            Spacer(Modifier.height(12.dp))
            HintText(stringResource(R.string.about_disclaimer), icon = Icons.Rounded.Info)
        }
    }
}

@Composable
private fun SetupStep(n: Int, text: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    val palette = LocalPalette.current
    Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(palette.gradient),
            contentAlignment = Alignment.Center,
        ) {
            Text("$n", style = MaterialTheme.typography.labelLarge, color = OD.Bg0)
        }
        Spacer(Modifier.width(14.dp))
        Icon(icon, null, tint = OD.TextDim, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = OD.Text)
    }
}

@Composable
private fun CommandBox(command: String, copy: (String) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 44.dp, top = 4.dp, bottom = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black.copy(alpha = 0.45f))
            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
            .clickable { copy(command) }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            command,
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            color = LocalPalette.current.primary,
            modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
            maxLines = 1,
        )
        Spacer(Modifier.width(10.dp))
        Icon(Icons.Rounded.ContentCopy, null, tint = OD.TextDim, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun CodeBlock(code: String) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color.Black.copy(alpha = 0.45f))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(14.dp))
            .padding(16.dp),
    ) {
        Row {
            Icon(Icons.Rounded.Code, null, tint = OD.TextFaint, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Text(code, fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = OD.Text, lineHeight = 18.sp)
        }
    }
}

@Composable
private fun AccentSwatch(accent: AccentTheme, selected: Boolean, onClick: () -> Unit) {
    val p = paletteFor(accent)
    Box(
        Modifier
            .size(52.dp)
            .neonGlow(p.primary, 26.dp, 10.dp, if (selected) 1.2f else 0f)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(p.primary, p.secondary)))
            .border(if (selected) 3.dp else 1.dp, if (selected) Color.White else Color.White.copy(alpha = 0.2f), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) Icon(Icons.Rounded.Check, null, tint = OD.Bg0)
    }
}
