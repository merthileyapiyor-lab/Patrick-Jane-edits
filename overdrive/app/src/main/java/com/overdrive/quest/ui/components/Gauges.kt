package com.overdrive.quest.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.overdrive.quest.ui.theme.Chakra
import com.overdrive.quest.ui.theme.LocalPalette
import com.overdrive.quest.ui.theme.OD
import kotlin.math.cos
import kotlin.math.sin

/** 270° neon ring with a glowing value arc and tick marks. */
@Composable
fun RingGauge(
    fraction: Float?,
    value: String,
    unit: String,
    label: String,
    modifier: Modifier = Modifier,
    color: Color = LocalPalette.current.primary,
    endColor: Color = LocalPalette.current.secondary,
    caption: String? = null,
    size: Dp = 148.dp,
) {
    val animated by animateFloatAsState(
        targetValue = (fraction ?: 0f).coerceIn(0f, 1f),
        animationSpec = tween(750, easing = FastOutSlowInEasing),
        label = "ring",
    )
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(size), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val stroke = this.size.minDimension * 0.075f
                val inset = stroke * 1.6f
                val arcSize = Size(this.size.width - inset * 2, this.size.height - inset * 2)
                val topLeft = Offset(inset, inset)
                val c = center
                val radius = arcSize.width / 2f
                // ticks
                for (i in 0..27) {
                    val angle = Math.toRadians((135.0 + i * 10.0))
                    val major = i % 9 == 0
                    val r1 = radius + stroke * 0.95f
                    val r2 = r1 + if (major) stroke * 0.7f else stroke * 0.35f
                    val lit = i / 27f <= animated && fraction != null
                    drawLine(
                        color = if (lit) color.copy(alpha = 0.75f) else Color.White.copy(alpha = 0.12f),
                        start = Offset(c.x + (r1 * cos(angle)).toFloat(), c.y + (r1 * sin(angle)).toFloat()),
                        end = Offset(c.x + (r2 * cos(angle)).toFloat(), c.y + (r2 * sin(angle)).toFloat()),
                        strokeWidth = if (major) 2.2f else 1.4f,
                    )
                }
                drawArc(
                    color = Color.White.copy(alpha = 0.07f),
                    startAngle = 135f,
                    sweepAngle = 270f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
                if (fraction != null && animated > 0.001f) {
                    rotate(135f, pivot = c) {
                        val brush = Brush.sweepGradient(0f to color, 0.75f to endColor, 1f to color, center = c)
                        // glow
                        drawArc(
                            brush = brush,
                            startAngle = 0f,
                            sweepAngle = 270f * animated,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            alpha = 0.18f,
                            style = Stroke(stroke * 2.6f, cap = StrokeCap.Round),
                        )
                        drawArc(
                            brush = brush,
                            startAngle = 0f,
                            sweepAngle = 270f * animated,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(stroke, cap = StrokeCap.Round),
                        )
                    }
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    value,
                    fontFamily = Chakra,
                    fontSize = (size.value * 0.2f).sp,
                    color = OD.Text,
                    maxLines = 1,
                )
                Text(unit, style = MaterialTheme.typography.labelSmall, color = OD.TextDim)
            }
        }
        Text(label.caps(), style = MaterialTheme.typography.labelMedium, color = OD.Text, textAlign = TextAlign.Center)
        if (caption != null) {
            Text(caption, style = MaterialTheme.typography.bodySmall, color = OD.TextDim, textAlign = TextAlign.Center, maxLines = 1)
        }
    }
}

/**
 * The CPU/GPU "overclock" selector: one segment per performance level, colored from cool to
 * red-hot, with learned clock speeds underneath when known.
 */
@Composable
fun LevelSelector(
    value: Int?,
    max: Int,
    onSelect: (Int?) -> Unit,
    stockLabel: String,
    modifier: Modifier = Modifier,
    clocks: Map<Int, Int> = emptyMap(),
    live: Int? = null,
    enabled: Boolean = true,
) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        LevelSegment(
            title = stockLabel,
            subtitle = null,
            selected = value == null,
            heat = null,
            barFraction = 0f,
            live = false,
            enabled = enabled,
            onClick = { onSelect(null) },
            modifier = Modifier.weight(1.4f),
        )
        for (level in 0..max) {
            val f = if (max == 0) 1f else level / max.toFloat()
            LevelSegment(
                title = level.toString(),
                subtitle = clocks[level]?.let { formatClock(it) },
                selected = value == level,
                heat = f,
                barFraction = 0.25f + 0.75f * f,
                live = live == level,
                enabled = enabled,
                onClick = { onSelect(level) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

fun formatClock(mhz: Int): String = if (mhz >= 1000) "%.2fG".format(mhz / 1000f) else "${mhz}M"

@Composable
private fun LevelSegment(
    title: String,
    subtitle: String?,
    selected: Boolean,
    heat: Float?,
    barFraction: Float,
    live: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalPalette.current
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val accent = heat?.let { palette.heat(it) } ?: palette.primary
    val bg by animateColorAsState(
        when {
            selected -> accent.copy(alpha = 0.24f)
            hovered -> Color.White.copy(alpha = 0.09f)
            else -> Color.White.copy(alpha = 0.035f)
        },
        label = "segBg",
    )
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier
            .graphicsLayer { alpha = if (enabled) 1f else 0.45f }
            .neonGlow(accent, 14.dp, 10.dp, if (selected) 1f else 0f)
            .clip(shape)
            .background(bg)
            .border(
                width = if (selected) 1.5.dp else 1.dp,
                brush = if (selected) Brush.verticalGradient(listOf(accent, accent.copy(alpha = 0.3f))) else SolidColor(Color.White.copy(alpha = 0.1f)),
                shape = shape,
            )
            .hoverable(interaction, enabled)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.height(26.dp).fillMaxWidth(0.5f), contentAlignment = Alignment.BottomCenter) {
            if (heat != null) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(26.dp * barFraction)
                        .clip(RoundedCornerShape(3.dp))
                        .background(
                            Brush.verticalGradient(
                                listOf(accent, accent.copy(alpha = if (selected) 0.5f else 0.15f)),
                            ),
                        )
                        .graphicsLayer { alpha = if (selected || live) 1f else 0.45f },
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            title.caps(),
            style = if (heat == null) MaterialTheme.typography.labelMedium else MaterialTheme.typography.titleLarge,
            color = if (selected) OD.Text else OD.TextDim,
            maxLines = 1,
        )
        Text(
            when {
                live -> "● LIVE"
                subtitle != null -> subtitle
                else -> " "
            },
            style = MaterialTheme.typography.labelSmall,
            color = if (live) OD.Good else OD.TextFaint,
            maxLines = 1,
        )
    }
}

@Composable
fun AnimatedNumber(
    value: Int?,
    modifier: Modifier = Modifier,
    fontSize: Int = 72,
    color: Color = OD.Text,
    placeholder: String = "--",
) {
    val animated by animateIntAsState(value ?: 0, tween(450), label = "num")
    Text(
        if (value == null) placeholder else animated.toString(),
        modifier = modifier,
        fontFamily = Chakra,
        fontSize = fontSize.sp,
        color = color,
        maxLines = 1,
        lineHeight = (fontSize * 1.05f).sp,
    )
}

/** Small stat block: icon, big value and label. */
@Composable
fun StatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    unit: String? = null,
    color: Color = OD.Text,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
            IconBadge(icon, size = 36.dp, tint = if (color == OD.Text) null else color)
            Spacer(Modifier.size(12.dp))
        }
        Column {
            Text(label.caps(), style = MaterialTheme.typography.labelSmall, color = OD.TextDim)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(value, style = MaterialTheme.typography.headlineSmall, color = color, maxLines = 1)
                if (unit != null) {
                    Text(
                        " $unit",
                        style = MaterialTheme.typography.labelMedium,
                        color = OD.TextDim,
                        modifier = Modifier.padding(bottom = 3.dp),
                    )
                }
            }
        }
    }
}
