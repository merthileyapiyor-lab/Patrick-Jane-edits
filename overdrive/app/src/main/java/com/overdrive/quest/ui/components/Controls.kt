package com.overdrive.quest.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.overdrive.quest.ui.theme.LocalPalette
import com.overdrive.quest.ui.theme.LocalReduceMotion
import com.overdrive.quest.ui.theme.OD
import java.util.Locale

fun String.caps(): String = uppercase(Locale.getDefault())

/** Paints text (or any content) with a gradient. */
fun Modifier.gradientText(brush: Brush): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        drawRect(brush, blendMode = BlendMode.SrcAtop)
    }

/** Soft neon halo drawn outside the element's bounds. */
fun Modifier.neonGlow(
    color: Color,
    cornerRadius: Dp = 22.dp,
    spread: Dp = 14.dp,
    intensity: Float = 1f,
): Modifier = drawBehind {
    if (intensity <= 0f) return@drawBehind
    val layers = 7
    val spreadPx = spread.toPx()
    val radius = cornerRadius.toPx()
    for (i in layers downTo 1) {
        val grow = spreadPx * i / layers
        val alpha = (0.055f * intensity * (1f - (i - 1f) / layers)).coerceIn(0f, 1f)
        drawRoundRect(
            color = color.copy(alpha = alpha),
            topLeft = Offset(-grow, -grow),
            size = Size(size.width + grow * 2, size.height + grow * 2),
            cornerRadius = CornerRadius(radius + grow),
        )
    }
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    glow: Boolean = false,
    glowColor: Color? = null,
    contentPadding: Dp = 20.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val palette = LocalPalette.current
    val shape = RoundedCornerShape(24.dp)
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val accent = glowColor ?: palette.primary
    val border = if (glow || hovered) {
        Brush.linearGradient(listOf(accent.copy(alpha = 0.75f), palette.secondary.copy(alpha = 0.35f), Color.White.copy(alpha = 0.05f)))
    } else {
        Brush.linearGradient(listOf(Color.White.copy(alpha = 0.16f), Color.White.copy(alpha = 0.04f)))
    }
    Column(
        modifier
            .neonGlow(accent, 24.dp, 16.dp, if (glow) 1f else if (hovered && onClick != null) 0.6f else 0f)
            .clip(shape)
            .background(OD.Bg1.copy(alpha = 0.62f))
            .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.075f), Color.White.copy(alpha = 0.02f))))
            .border(1.dp, border, shape)
            .hoverable(interaction)
            .then(
                if (onClick != null) {
                    Modifier.clickable(interactionSource = interaction, indication = null, onClick = onClick)
                } else {
                    Modifier
                },
            )
            .padding(contentPadding),
        content = content,
    )
}

enum class ButtonStyle { PRIMARY, GHOST, DANGER }

@Composable
fun NeonButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    style: ButtonStyle = ButtonStyle.PRIMARY,
    enabled: Boolean = true,
    loading: Boolean = false,
    height: Dp = 50.dp,
) {
    val palette = LocalPalette.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val hovered by interaction.collectIsHoveredAsState()
    val scale by animateFloatAsState(
        targetValue = when {
            pressed -> 0.95f
            hovered && enabled -> 1.03f
            else -> 1f
        },
        animationSpec = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow),
        label = "btnScale",
    )
    val shape = RoundedCornerShape(15.dp)
    val background: Brush = when (style) {
        ButtonStyle.PRIMARY -> palette.horizontal
        ButtonStyle.DANGER -> Brush.horizontalGradient(listOf(OD.Hot, Color(0xFFFF7A45)))
        ButtonStyle.GHOST -> SolidColor(Color.White.copy(alpha = if (hovered) 0.11f else 0.05f))
    }
    val content = when (style) {
        ButtonStyle.PRIMARY -> OD.Bg0
        ButtonStyle.DANGER -> Color.White
        ButtonStyle.GHOST -> OD.Text
    }
    val glowColor = if (style == ButtonStyle.DANGER) OD.Hot else palette.primary
    Row(
        modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = if (enabled) 1f else 0.4f
            }
            .neonGlow(glowColor, 15.dp, 12.dp, if (style == ButtonStyle.GHOST || !enabled) 0f else if (hovered) 1.5f else 0.8f)
            .clip(shape)
            .background(background)
            .border(1.dp, Color.White.copy(alpha = if (style == ButtonStyle.GHOST) 0.14f else 0.3f), shape)
            .hoverable(interaction, enabled)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled && !loading,
                role = Role.Button,
                onClick = onClick,
            )
            .height(height)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(Modifier.size(18.dp), color = content, strokeWidth = 2.dp)
            Spacer(Modifier.width(10.dp))
        } else if (icon != null) {
            Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
        }
        Text(text.caps(), style = MaterialTheme.typography.labelLarge, color = content, maxLines = 1)
    }
}

@Composable
fun NeonSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val palette = LocalPalette.current
    val progress by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.62f, stiffness = 520f),
        label = "switch",
    )
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier
            .graphicsLayer { alpha = if (enabled) 1f else 0.4f }
            .size(width = 58.dp, height = 32.dp)
            .neonGlow(palette.primary, 16.dp, 9.dp, progress)
            .clip(CircleShape)
            .background(lerp(Color.White.copy(alpha = 0.09f), palette.primary, progress))
            .border(1.dp, Color.White.copy(alpha = 0.18f), CircleShape)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Switch,
            ) { onCheckedChange(!checked) },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .offset(x = 4.dp + 26.dp * progress)
                .size(24.dp)
                .background(lerp(Color.White.copy(alpha = 0.85f), OD.Bg0, progress), CircleShape),
        )
    }
}

@Composable
fun ToggleRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            IconBadge(icon, active = checked)
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = OD.Text)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = OD.TextDim)
            }
        }
        Spacer(Modifier.width(12.dp))
        NeonSwitch(checked, onCheckedChange, enabled = enabled)
    }
}

@Composable
fun IconBadge(icon: ImageVector, modifier: Modifier = Modifier, active: Boolean = true, tint: Color? = null, size: Dp = 40.dp) {
    val palette = LocalPalette.current
    val color by animateColorAsState(tint ?: if (active) palette.primary else OD.TextDim, label = "badge")
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(12.dp))
            .background(color.copy(alpha = 0.13f))
            .border(1.dp, color.copy(alpha = 0.32f), RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(size * 0.52f))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> ChipGroup(
    options: List<T>,
    selected: T?,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    FlowRow(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        options.forEach { option ->
            NeonChip(label(option), option == selected, { onSelect(option) }, enabled = enabled)
        }
    }
}

@Composable
fun NeonChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    val palette = LocalPalette.current
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val shape = RoundedCornerShape(12.dp)
    val bg by animateColorAsState(
        when {
            selected -> palette.primary.copy(alpha = 0.18f)
            hovered -> Color.White.copy(alpha = 0.09f)
            else -> Color.White.copy(alpha = 0.04f)
        },
        label = "chipBg",
    )
    Row(
        modifier
            .graphicsLayer { alpha = if (enabled) 1f else 0.45f }
            .neonGlow(palette.primary, 12.dp, 8.dp, if (selected) 0.7f else 0f)
            .clip(shape)
            .background(bg)
            .border(
                1.dp,
                if (selected) palette.gradient else SolidColor(Color.White.copy(alpha = 0.12f)),
                shape,
            )
            .hoverable(interaction, enabled)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = if (selected) palette.primary else OD.TextDim, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) OD.Text else OD.TextDim,
            maxLines = 1,
        )
    }
}

@Composable
fun LabeledSlider(
    title: String,
    valueText: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    steps: Int = 0,
    enabled: Boolean = true,
    onValueChangeFinished: (() -> Unit)? = null,
    accent: Color? = null,
) {
    val palette = LocalPalette.current
    val color = accent ?: palette.primary
    Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = OD.Text, modifier = Modifier.weight(1f))
            Text(valueText, style = MaterialTheme.typography.titleMedium, color = color)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            enabled = enabled,
            onValueChangeFinished = onValueChangeFinished,
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = color,
                inactiveTrackColor = Color.White.copy(alpha = 0.10f),
                activeTickColor = OD.Bg0.copy(alpha = 0.5f),
                inactiveTickColor = Color.White.copy(alpha = 0.18f),
            ),
        )
    }
}

/**
 * Slider for a saved setting: follows the pointer locally and saves once on release,
 * so a drag does not write settings to disk on every frame.
 */
@Composable
fun PersistedSlider(
    title: String,
    value: Float,
    valueText: (Float) -> String,
    onCommit: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    steps: Int = 0,
    enabled: Boolean = true,
    accent: Color? = null,
) {
    var dragging by remember { mutableStateOf<Float?>(null) }
    val shown = dragging ?: value
    LabeledSlider(
        title = title,
        valueText = valueText(shown),
        value = shown,
        onValueChange = { dragging = it },
        valueRange = valueRange,
        modifier = modifier,
        steps = steps,
        enabled = enabled,
        onValueChangeFinished = {
            dragging?.let(onCommit)
            dragging = null
        },
        accent = accent,
    )
}

@Composable
fun Pill(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = OD.TextDim,
    icon: ImageVector? = null,
    pulse: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(50)
    Row(
        modifier
            .clip(shape)
            .background(color.copy(alpha = 0.12f))
            .border(1.dp, color.copy(alpha = 0.35f), shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (pulse) {
            PulsingDot(color)
            Spacer(Modifier.width(8.dp))
        } else if (icon != null) {
            Icon(icon, null, tint = color, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, style = MaterialTheme.typography.labelSmall, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun PulsingDot(color: Color, modifier: Modifier = Modifier, size: Dp = 8.dp) {
    val reduceMotion = LocalReduceMotion.current
    val transition = rememberInfiniteTransition(label = "dot")
    val pulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1100), RepeatMode.Restart),
        label = "pulse",
    )
    Box(
        modifier
            .size(size)
            .drawBehind {
                val p = if (reduceMotion) 0f else pulse
                drawCircle(color.copy(alpha = 0.45f * (1f - p)), radius = this.size.minDimension / 2f * (1f + p * 1.6f))
                drawCircle(color)
            },
    )
}

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    val palette = LocalPalette.current
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(width = 4.dp, height = if (subtitle != null) 36.dp else 20.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(palette.gradient),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title.caps(), style = MaterialTheme.typography.titleMedium, color = OD.Text)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = OD.TextDim)
        }
        trailing?.invoke()
    }
}

@Composable
fun NeonProgress(
    fraction: Float,
    modifier: Modifier = Modifier,
    color: Color? = null,
    height: Dp = 8.dp,
) {
    val palette = LocalPalette.current
    val animated by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(600), label = "progress")
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.08f)),
    ) {
        Box(
            Modifier
                .fillMaxWidth(animated)
                .height(height)
                .clip(CircleShape)
                .background(if (color != null) SolidColor(color) else palette.horizontal),
        )
    }
}

@Composable
fun Banner(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = OD.Warn,
    icon: ImageVector? = null,
    action: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(color.copy(alpha = 0.10f))
            .border(1.dp, color.copy(alpha = 0.35f), shape)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = color, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(12.dp))
        }
        Text(text, style = MaterialTheme.typography.bodyMedium, color = OD.Text, modifier = Modifier.weight(1f))
        if (action != null && onAction != null) {
            Spacer(Modifier.width(12.dp))
            NeonButton(action, onAction, height = 40.dp, style = ButtonStyle.GHOST)
        }
    }
}
