package com.overdrive.quest.ui

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.overdrive.quest.R
import com.overdrive.quest.core.shell.AccessState
import com.overdrive.quest.ui.components.CyberBackground
import com.overdrive.quest.ui.components.Pill
import com.overdrive.quest.ui.components.caps
import com.overdrive.quest.ui.components.gradientText
import com.overdrive.quest.ui.components.neonGlow
import com.overdrive.quest.ui.screens.AutoBoostScreen
import com.overdrive.quest.ui.screens.DashboardScreen
import com.overdrive.quest.ui.screens.OverlayScreen
import com.overdrive.quest.ui.screens.ProfilesScreen
import com.overdrive.quest.ui.screens.SettingsScreen
import com.overdrive.quest.ui.screens.ToolboxScreen
import com.overdrive.quest.ui.screens.TuningScreen
import com.overdrive.quest.ui.theme.LocalPalette
import com.overdrive.quest.ui.theme.OD
import com.overdrive.quest.ui.theme.OverdriveTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.collectLatest

enum class Screen(val icon: ImageVector, @StringRes val title: Int, @StringRes val subtitle: Int) {
    DASHBOARD(Icons.Rounded.Dashboard, R.string.nav_dashboard, R.string.sub_dashboard),
    TUNING(Icons.Rounded.Tune, R.string.nav_tuning, R.string.sub_tuning),
    AUTO(Icons.Rounded.AutoAwesome, R.string.nav_auto, R.string.sub_auto),
    PROFILES(Icons.Rounded.SportsEsports, R.string.nav_profiles, R.string.sub_profiles),
    OVERLAY(Icons.Rounded.Layers, R.string.nav_overlay, R.string.sub_overlay),
    TOOLBOX(Icons.Rounded.Build, R.string.nav_toolbox, R.string.sub_toolbox),
    SETTINGS(Icons.Rounded.Settings, R.string.nav_settings, R.string.sub_settings),
}

@Composable
fun OverdriveRoot(vm: MainViewModel) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    OverdriveTheme(accent = settings.accent, reduceMotion = settings.reduceMotion) {
        var screen by rememberSaveable { mutableStateOf(Screen.DASHBOARD) }
        val access by vm.access.collectAsStateWithLifecycle()
        Box(Modifier.fillMaxSize().background(OD.Bg0)) {
            CyberBackground()
            Row(Modifier.fillMaxSize()) {
                NavRail(
                    current = screen,
                    access = access,
                    autoOn = settings.auto.enabled,
                    onSelect = { screen = it },
                )
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    TopBar(screen, vm, onAccessClick = { screen = Screen.SETTINGS })
                    AnimatedContent(
                        targetState = screen,
                        transitionSpec = {
                            (fadeIn(tween(280)) + slideInVertically(tween(340)) { it / 30 }) togetherWith fadeOut(tween(140))
                        },
                        label = "screen",
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                    ) { target ->
                        when (target) {
                            Screen.DASHBOARD -> DashboardScreen(vm, onNavigate = { screen = it })
                            Screen.TUNING -> TuningScreen(vm)
                            Screen.AUTO -> AutoBoostScreen(vm)
                            Screen.PROFILES -> ProfilesScreen(vm)
                            Screen.OVERLAY -> OverlayScreen(vm)
                            Screen.TOOLBOX -> ToolboxScreen(vm)
                            Screen.SETTINGS -> SettingsScreen(vm)
                        }
                    }
                }
            }
            ToastHost(vm.messages, Modifier.align(Alignment.BottomCenter).padding(bottom = 28.dp))
        }
    }
}

@Composable
private fun NavRail(
    current: Screen,
    access: AccessState,
    autoOn: Boolean,
    onSelect: (Screen) -> Unit,
) {
    Column(
        Modifier
            .width(112.dp)
            .fillMaxHeight()
            .background(Brush.horizontalGradient(listOf(OD.Bg0.copy(alpha = 0.85f), OD.Bg0.copy(alpha = 0.35f))))
            .padding(vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        OverdriveLogo(Modifier.size(54.dp))
        Spacer(Modifier.height(6.dp))
        Text("OVERDRIVE", style = MaterialTheme.typography.labelSmall, modifier = Modifier.gradientText(LocalPalette.current.horizontal))
        Spacer(Modifier.height(22.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Screen.entries.forEach { s ->
                NavItem(s, s == current, badge = s == Screen.AUTO && autoOn) { onSelect(s) }
            }
        }
        val ok = access.ready
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier
                    .size(10.dp)
                    .neonGlow(if (ok) OD.Good else OD.Hot, 5.dp, 6.dp)
                    .background(if (ok) OD.Good else OD.Hot, RoundedCornerShape(5.dp)),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(if (ok) R.string.access_short_ok else R.string.access_short_none).caps(),
                style = MaterialTheme.typography.labelSmall,
                color = if (ok) OD.Good else OD.Hot,
            )
        }
    }
}

@Composable
private fun NavItem(screen: Screen, selected: Boolean, badge: Boolean, onClick: () -> Unit) {
    val palette = LocalPalette.current
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val bg by animateColorAsState(
        when {
            selected -> palette.primary.copy(alpha = 0.16f)
            hovered -> Color.White.copy(alpha = 0.07f)
            else -> Color.Transparent
        },
        label = "navBg",
    )
    val tint by animateColorAsState(if (selected) palette.primary else if (hovered) OD.Text else OD.TextDim, label = "navTint")
    val indicator by animateFloatAsState(if (selected) 1f else 0f, label = "navInd")
    Box(
        Modifier
            .width(92.dp)
            .neonGlow(palette.primary, 18.dp, 10.dp, indicator * 0.6f)
            .clip(RoundedCornerShape(18.dp))
            .background(bg)
            .border(1.dp, palette.primary.copy(alpha = 0.45f * indicator), RoundedCornerShape(18.dp))
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(vertical = 11.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(screen.icon, contentDescription = null, tint = tint, modifier = Modifier.size(26.dp))
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(screen.title).caps(),
                style = MaterialTheme.typography.labelSmall,
                color = tint,
                maxLines = 1,
            )
        }
        if (badge) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 2.dp, end = 14.dp)
                    .size(8.dp)
                    .neonGlow(OD.Good, 4.dp, 5.dp)
                    .background(OD.Good, RoundedCornerShape(4.dp)),
            )
        }
    }
}

/** Gauge ring with a lightning bolt, drawn in the accent gradient. */
@Composable
fun OverdriveLogo(modifier: Modifier = Modifier) {
    val palette = LocalPalette.current
    Canvas(modifier.neonGlow(palette.primary, 27.dp, 10.dp, 0.7f)) {
        val stroke = size.minDimension * 0.09f
        drawArc(
            brush = Brush.linearGradient(listOf(palette.primary, palette.secondary)),
            startAngle = 135f,
            sweepAngle = 270f,
            useCenter = false,
            topLeft = Offset(stroke, stroke),
            size = Size(size.width - stroke * 2, size.height - stroke * 2),
            style = Stroke(stroke, cap = StrokeCap.Round),
        )
        val w = size.width
        val h = size.height
        val bolt = Path().apply {
            moveTo(w * 0.56f, h * 0.18f)
            lineTo(w * 0.33f, h * 0.55f)
            lineTo(w * 0.49f, h * 0.55f)
            lineTo(w * 0.43f, h * 0.84f)
            lineTo(w * 0.68f, h * 0.45f)
            lineTo(w * 0.52f, h * 0.45f)
            close()
        }
        drawPath(bolt, Brush.verticalGradient(listOf(Color.White, palette.primary)))
    }
}

@Composable
private fun TopBar(screen: Screen, vm: MainViewModel, onAccessClick: () -> Unit) {
    val palette = LocalPalette.current
    val metrics by vm.metrics.collectAsStateWithLifecycle()
    val access by vm.access.collectAsStateWithLifecycle()
    val caps by vm.caps.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val recording by vm.recording.collectAsStateWithLifecycle()
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 28.dp, end = 28.dp, top = 22.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(screen.title).caps(),
                style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.gradientText(palette.horizontal),
            )
            Text(stringResource(screen.subtitle), style = MaterialTheme.typography.bodyMedium, color = OD.TextDim)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (recording != null) {
                Pill(stringResource(R.string.pill_rec), color = OD.Hot, pulse = true)
            }
            if (settings.auto.enabled) {
                Pill(stringResource(R.string.pill_auto), color = OD.Good, pulse = true)
            }
            if (metrics.vrActive) {
                Pill("${metrics.fps ?: "--"} FPS", color = palette.primary, pulse = true)
            }
            Pill(caps.model.caps(), color = OD.TextDim)
            Pill(
                text = stringResource(accessLabel(access)).caps(),
                color = if (access.ready) OD.Good else OD.Hot,
                onClick = onAccessClick,
            )
        }
    }
}

@StringRes
fun accessLabel(state: AccessState): Int = when (state) {
    AccessState.CHECKING -> R.string.access_checking
    AccessState.NOT_INSTALLED -> R.string.access_not_installed
    AccessState.NOT_RUNNING -> R.string.access_not_running
    AccessState.NO_PERMISSION -> R.string.access_no_permission
    AccessState.OUTDATED -> R.string.access_outdated
    AccessState.READY_SHIZUKU -> R.string.access_ready_shizuku
    AccessState.READY_ROOT -> R.string.access_ready_root
}

@Composable
private fun ToastHost(messages: SharedFlow<UiMessage>, modifier: Modifier = Modifier) {
    var current by remember { mutableStateOf<UiMessage?>(null) }
    var shown by remember { mutableStateOf<UiMessage?>(null) }
    LaunchedEffect(messages) {
        messages.collectLatest { msg ->
            current = msg
            shown = msg
            delay(3_000)
            current = null
        }
    }
    AnimatedVisibility(
        visible = current != null,
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut(),
        modifier = modifier,
    ) {
        val msg = shown ?: return@AnimatedVisibility
        val color = when (msg.kind) {
            MessageKind.SUCCESS -> OD.Good
            MessageKind.ERROR -> OD.Hot
            MessageKind.INFO -> LocalPalette.current.primary
        }
        val icon = when (msg.kind) {
            MessageKind.SUCCESS -> Icons.Rounded.CheckCircle
            MessageKind.ERROR -> Icons.Rounded.Error
            MessageKind.INFO -> Icons.Rounded.Info
        }
        Row(
            Modifier
                .neonGlow(color, 18.dp, 14.dp, 0.8f)
                .clip(RoundedCornerShape(18.dp))
                .background(OD.Bg1.copy(alpha = 0.96f))
                .border(1.dp, color.copy(alpha = 0.55f), RoundedCornerShape(18.dp))
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, null, tint = color, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(12.dp))
            Text(msg.text, style = MaterialTheme.typography.bodyLarge, color = OD.Text)
        }
    }
}
