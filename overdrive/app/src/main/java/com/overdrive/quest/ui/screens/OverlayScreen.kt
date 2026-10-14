package com.overdrive.quest.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.overdrive.quest.R
import com.overdrive.quest.core.model.HudCorner
import com.overdrive.quest.core.model.HudStyle
import com.overdrive.quest.ui.MainViewModel
import com.overdrive.quest.ui.components.Banner
import com.overdrive.quest.ui.components.ButtonStyle
import com.overdrive.quest.ui.components.ChipGroup
import com.overdrive.quest.ui.components.GlassCard
import com.overdrive.quest.ui.components.IconBadge
import com.overdrive.quest.ui.components.LabeledSlider
import com.overdrive.quest.ui.components.NeonButton
import com.overdrive.quest.ui.components.NeonChip
import com.overdrive.quest.ui.components.Pill
import com.overdrive.quest.ui.components.SectionHeader
import com.overdrive.quest.ui.components.ToggleRow
import com.overdrive.quest.ui.components.caps
import com.overdrive.quest.ui.overlay.HudPanel
import com.overdrive.quest.ui.theme.LocalPalette
import com.overdrive.quest.ui.theme.OD
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@Composable
fun OverlayScreen(vm: MainViewModel) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val metrics by vm.metrics.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()
    val access by vm.access.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val overlay = settings.overlay
    val palette = LocalPalette.current

    // The permission can be granted outside the app, so poll it while this screen is open.
    var canDraw by remember { mutableStateOf(vm.canDrawOverlays()) }
    var ovrInstalled by remember { mutableStateOf(vm.isOvrMetricsInstalled()) }
    LaunchedEffect(Unit) {
        while (true) {
            canDraw = vm.canDrawOverlays()
            ovrInstalled = vm.isOvrMetricsInstalled()
            delay(1_500)
        }
    }

    ScreenColumn {
        CardRow {
            GlassCard(Modifier.weight(1.1f), glow = overlay.enabled) {
                ToggleRow(
                    title = stringResource(R.string.overlay_hud),
                    subtitle = stringResource(R.string.overlay_hud_sub),
                    checked = overlay.enabled,
                    onCheckedChange = { on -> vm.setOverlay { it.copy(enabled = on) } },
                    icon = Icons.Rounded.Layers,
                )
                Spacer(Modifier.height(8.dp))
                if (canDraw) {
                    Pill(stringResource(R.string.overlay_permission_ok), color = OD.Good, icon = Icons.Rounded.CheckCircle)
                } else {
                    Banner(
                        stringResource(R.string.overlay_permission_missing),
                        icon = Icons.Rounded.Lock,
                        action = stringResource(R.string.action_grant),
                        onAction = vm::grantOverlayPermission,
                    )
                }
                Spacer(Modifier.height(18.dp))
                Text(stringResource(R.string.overlay_style).caps(), style = MaterialTheme.typography.labelMedium, color = OD.TextDim)
                Spacer(Modifier.height(10.dp))
                ChipGroup(
                    options = HudStyle.entries,
                    selected = overlay.style,
                    label = {
                        stringResource(
                            when (it) {
                                HudStyle.MINIMAL -> R.string.hud_minimal
                                HudStyle.STANDARD -> R.string.hud_standard
                                HudStyle.PRO -> R.string.hud_pro
                            },
                        )
                    },
                    onSelect = { s -> vm.setOverlay { it.copy(style = s) } },
                )
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.overlay_position).caps(), style = MaterialTheme.typography.labelMedium, color = OD.TextDim)
                Spacer(Modifier.height(10.dp))
                ChipGroup(
                    options = HudCorner.entries,
                    selected = overlay.corner,
                    label = {
                        stringResource(
                            when (it) {
                                HudCorner.TOP_LEFT -> R.string.corner_tl
                                HudCorner.TOP_RIGHT -> R.string.corner_tr
                                HudCorner.BOTTOM_LEFT -> R.string.corner_bl
                                HudCorner.BOTTOM_RIGHT -> R.string.corner_br
                            },
                        )
                    },
                    onSelect = { c -> vm.setOverlay { it.copy(corner = c, x = 40, y = 40) } },
                )
                Spacer(Modifier.height(16.dp))
                LabeledSlider(
                    title = stringResource(R.string.overlay_opacity),
                    valueText = "${(overlay.opacity * 100).roundToInt()}%",
                    value = overlay.opacity,
                    onValueChange = { v -> vm.setOverlay { it.copy(opacity = (v * 20).roundToInt() / 20f) } },
                    valueRange = 0.3f..1f,
                    steps = 13,
                )
                LabeledSlider(
                    title = stringResource(R.string.overlay_scale),
                    valueText = "%.2f×".format(overlay.scale),
                    value = overlay.scale,
                    onValueChange = { v -> vm.setOverlay { it.copy(scale = (v * 20).roundToInt() / 20f) } },
                    valueRange = 0.6f..1.8f,
                    steps = 23,
                )
                ToggleRow(
                    title = stringResource(R.string.overlay_click_through),
                    subtitle = stringResource(R.string.overlay_click_through_sub),
                    checked = overlay.clickThrough,
                    onCheckedChange = { on -> vm.setOverlay { it.copy(clickThrough = on) } },
                    icon = Icons.Rounded.TouchApp,
                )
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.overlay_items).caps(), style = MaterialTheme.typography.labelMedium, color = OD.TextDim)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NeonChip("FPS", overlay.showFps, { vm.setOverlay { it.copy(showFps = !it.showFps) } })
                    NeonChip("CPU", overlay.showCpu, { vm.setOverlay { it.copy(showCpu = !it.showCpu) } })
                    NeonChip("GPU", overlay.showGpu, { vm.setOverlay { it.copy(showGpu = !it.showGpu) } })
                    NeonChip(stringResource(R.string.item_temp), overlay.showTemp, { vm.setOverlay { it.copy(showTemp = !it.showTemp) } })
                    NeonChip(stringResource(R.string.item_battery), overlay.showBattery, { vm.setOverlay { it.copy(showBattery = !it.showBattery) } })
                    NeonChip("RAM", overlay.showRam, { vm.setOverlay { it.copy(showRam = !it.showRam) } })
                }
            }

            // Live preview on a faux game scene
            GlassCard(Modifier.weight(1f)) {
                SectionHeader(stringResource(R.string.overlay_preview), subtitle = stringResource(R.string.overlay_preview_sub))
                Spacer(Modifier.height(14.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(420.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFF0D1B3D), Color(0xFF2A0F3D), Color(0xFF3D1A0F)),
                            ),
                        )
                        .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(18.dp)),
                ) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(
                                Brush.radialGradient(
                                    listOf(palette.primary.copy(alpha = 0.18f), Color.Transparent),
                                ),
                            ),
                    )
                    val align = when (overlay.corner) {
                        HudCorner.TOP_LEFT -> Alignment.TopStart
                        HudCorner.TOP_RIGHT -> Alignment.TopEnd
                        HudCorner.BOTTOM_LEFT -> Alignment.BottomStart
                        HudCorner.BOTTOM_RIGHT -> Alignment.BottomEnd
                    }
                    Box(Modifier.align(align)) {
                        HudPanel(metrics, history, overlay.copy(scale = overlay.scale.coerceAtMost(1.2f)))
                    }
                }
            }
        }

        // In-VR overlay through Meta's OVR Metrics Tool
        GlassCard(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(Icons.Rounded.Visibility, tint = palette.secondary)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.ovr_title).caps(), style = MaterialTheme.typography.titleMedium, color = OD.Text)
                    Text(stringResource(R.string.ovr_sub), style = MaterialTheme.typography.bodySmall, color = OD.TextDim)
                }
                Pill(
                    stringResource(if (ovrInstalled) R.string.ovr_installed else R.string.ovr_missing),
                    color = if (ovrInstalled) OD.Good else OD.Warn,
                )
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                NeonButton(
                    stringResource(R.string.ovr_enable),
                    onClick = { vm.setOvrMetricsOverlay(true) },
                    icon = Icons.Rounded.Visibility,
                    enabled = ovrInstalled && access.ready,
                    loading = MainViewModel.KEY_OVR in busy,
                )
                NeonButton(
                    stringResource(R.string.ovr_disable),
                    onClick = { vm.setOvrMetricsOverlay(false) },
                    icon = Icons.Rounded.VisibilityOff,
                    style = ButtonStyle.GHOST,
                    enabled = ovrInstalled && access.ready,
                )
            }
            Spacer(Modifier.height(12.dp))
            HintText(stringResource(R.string.ovr_hint), icon = Icons.Rounded.Info)
        }

        Banner(stringResource(R.string.overlay_limits), icon = Icons.Rounded.Warning, color = OD.Cool)
    }
}
