package com.overdrive.quest.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.overdrive.quest.core.monitor.History
import com.overdrive.quest.ui.theme.OD

@Immutable
data class ChartSeries(
    val label: String,
    val values: List<Float?>,
    val color: Color,
    val min: Float,
    val max: Float,
    val fill: Boolean = false,
)

/** Real-time telemetry chart: glowing lines, gradient fills, gaps where data is missing. */
@Composable
fun LineChart(
    series: List<ChartSeries>,
    modifier: Modifier = Modifier,
    capacity: Int = History.CAPACITY,
    gridLines: Int = 4,
) {
    Canvas(modifier) {
        val dash = PathEffect.dashPathEffect(floatArrayOf(6f, 8f), 0f)
        if (gridLines > 0) {
            for (i in 0..gridLines) {
                val y = size.height * i / gridLines
                drawLine(Color.White.copy(alpha = 0.06f), Offset(0f, y), Offset(size.width, y), 1f, pathEffect = dash)
            }
        }
        series.forEach { drawSeries(it, capacity) }
    }
}

private fun DrawScope.drawSeries(s: ChartSeries, capacity: Int) {
    if (s.values.isEmpty()) return
    val range = (s.max - s.min).takeIf { it > 0f } ?: 1f
    val offset = capacity - s.values.size
    val step = size.width / (capacity - 1).coerceAtLeast(1)
    fun x(i: Int) = (i + offset) * step
    fun y(v: Float) = size.height - ((v - s.min) / range).coerceIn(0f, 1f) * size.height

    val line = Path()
    val fill = Path()
    var segmentStart: Int? = null
    var last: Offset? = null
    s.values.forEachIndexed { i, v ->
        if (v == null) {
            if (segmentStart != null && last != null) closeFill(fill, x(segmentStart!!), last!!.x)
            segmentStart = null
            return@forEachIndexed
        }
        val p = Offset(x(i), y(v))
        if (segmentStart == null) {
            line.moveTo(p.x, p.y)
            fill.moveTo(p.x, size.height)
            fill.lineTo(p.x, p.y)
            segmentStart = i
        } else {
            line.lineTo(p.x, p.y)
            fill.lineTo(p.x, p.y)
        }
        last = p
    }
    if (segmentStart != null && last != null) closeFill(fill, x(segmentStart!!), last!!.x)

    if (s.fill) {
        drawPath(fill, Brush.verticalGradient(listOf(s.color.copy(alpha = 0.28f), Color.Transparent)))
    }
    drawPath(line, s.color.copy(alpha = 0.16f), style = Stroke(9f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    drawPath(line, s.color, style = Stroke(3f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    val end = s.values.lastOrNull()
    if (end != null) {
        val p = Offset(x(s.values.lastIndex), y(end))
        drawCircle(s.color.copy(alpha = 0.25f), radius = 10f, center = p)
        drawCircle(s.color, radius = 4.5f, center = p)
    }
}

private fun DrawScope.closeFill(fill: Path, startX: Float, endX: Float) {
    fill.lineTo(endX, size.height)
    fill.lineTo(startX, size.height)
    fill.close()
}

@Composable
fun ChartLegend(series: List<ChartSeries>, valueText: (ChartSeries) -> String, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
        series.forEach { s ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(10.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(s.color),
                )
                Spacer(Modifier.width(8.dp))
                Text(s.label.caps(), style = MaterialTheme.typography.labelSmall, color = OD.TextDim)
                Spacer(Modifier.width(6.dp))
                Text(valueText(s), style = MaterialTheme.typography.labelMedium, color = OD.Text)
            }
        }
    }
}

/** Compact line for the overlay HUD. */
@Composable
fun Sparkline(values: List<Float?>, color: Color, min: Float, max: Float, modifier: Modifier = Modifier) {
    LineChart(
        series = listOf(ChartSeries("", values, color, min, max, fill = true)),
        modifier = modifier,
        capacity = values.size.coerceAtLeast(2),
        gridLines = 0,
    )
}
