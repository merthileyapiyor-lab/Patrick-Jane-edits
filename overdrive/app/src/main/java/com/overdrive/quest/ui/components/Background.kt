package com.overdrive.quest.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.runtime.State
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.overdrive.quest.ui.theme.LocalPalette
import com.overdrive.quest.ui.theme.LocalReduceMotion
import com.overdrive.quest.ui.theme.OD
import kotlin.math.max

/** Deep-space backdrop: drifting neon nebulas over a synthwave perspective grid. */
@Composable
fun CyberBackground(modifier: Modifier = Modifier) {
    val palette = LocalPalette.current
    val reduceMotion = LocalReduceMotion.current
    val transition = rememberInfiniteTransition(label = "bg")
    val drift: State<Float> = if (reduceMotion) {
        remember { mutableFloatStateOf(0.5f) }
    } else {
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(16_000, easing = LinearEasing), RepeatMode.Reverse),
            label = "drift",
        )
    }
    val scroll: State<Float> = if (reduceMotion) {
        remember { mutableFloatStateOf(0f) }
    } else {
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(3_200, easing = LinearEasing), RepeatMode.Restart),
            label = "scroll",
        )
    }

    Canvas(modifier.fillMaxSize()) {
        val t = drift.value
        drawRect(Brush.verticalGradient(listOf(OD.Bg0, OD.Bg1, OD.Bg0)))
        nebula(palette.primary.copy(alpha = 0.20f), Offset(size.width * (0.12f + 0.12f * t), size.height * (0.18f + 0.08f * t)), size.width * 0.42f)
        nebula(palette.secondary.copy(alpha = 0.17f), Offset(size.width * (0.88f - 0.10f * t), size.height * (0.78f - 0.06f * t)), size.width * 0.48f)
        nebula(palette.tertiary.copy(alpha = 0.07f), Offset(size.width * 0.55f, size.height * (0.05f + 0.12f * t)), size.width * 0.35f)
        grid(palette.primary, scroll.value)
        drawRect(
            Brush.radialGradient(
                listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f)),
                center = Offset(size.width / 2f, size.height / 2f),
                radius = max(size.width, size.height) * 0.78f,
            ),
        )
    }
}

private fun DrawScope.nebula(color: Color, center: Offset, radius: Float) {
    drawCircle(
        brush = Brush.radialGradient(listOf(color, Color.Transparent), center = center, radius = radius),
        radius = radius,
        center = center,
    )
}

private fun DrawScope.grid(color: Color, phase: Float) {
    val horizon = size.height * 0.64f
    val line = color.copy(alpha = 0.08f)
    val depth = size.height - horizon
    // Horizontal lines bunch up toward the horizon and scroll toward the viewer.
    for (i in 0 until 16) {
        val z = (i + phase) / 16f
        val y = horizon + depth * z * z
        drawLine(line.copy(alpha = 0.03f + 0.09f * z), Offset(0f, y), Offset(size.width, y), strokeWidth = 1.2f)
    }
    val cx = size.width / 2f
    for (i in -14..14) {
        drawLine(
            line,
            Offset(cx + i * size.width / 70f, horizon),
            Offset(cx + i * size.width / 9f, size.height),
            strokeWidth = 1.1f,
        )
    }
    drawRect(
        Brush.verticalGradient(listOf(OD.Bg0, Color.Transparent), startY = horizon, endY = horizon + depth * 0.35f),
        topLeft = Offset(0f, horizon - 1f),
        size = Size(size.width, depth * 0.35f + 1f),
    )
}
