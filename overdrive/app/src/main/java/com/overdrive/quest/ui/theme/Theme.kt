package com.overdrive.quest.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.overdrive.quest.R
import com.overdrive.quest.core.model.AccentTheme

object OD {
    val Bg0 = Color(0xFF05070D)
    val Bg1 = Color(0xFF0A0F1C)
    val Bg2 = Color(0xFF121A2B)
    val Text = Color(0xFFEAF2FF)
    val TextDim = Color(0xFF8E9AB3)
    val TextFaint = Color(0xFF56627C)
    val Stroke = Color(0x1FFFFFFF)
    val Good = Color(0xFF2BFF88)
    val Warn = Color(0xFFFFC23D)
    val Hot = Color(0xFFFF4D6D)
    val Cool = Color(0xFF3DA9FF)
}

@Immutable
data class Palette(val primary: Color, val secondary: Color, val tertiary: Color) {
    val gradient: Brush get() = Brush.linearGradient(listOf(primary, secondary))
    val horizontal: Brush get() = Brush.horizontalGradient(listOf(primary, secondary))

    /** Cool blue at 0, accent in the middle, red-hot at 1: used for levels and temperatures. */
    fun heat(f: Float): Color {
        val x = f.coerceIn(0f, 1f)
        return if (x < 0.5f) lerp(OD.Cool, primary, x * 2f) else lerp(primary, OD.Hot, (x - 0.5f) * 2f)
    }
}

fun paletteFor(accent: AccentTheme): Palette = when (accent) {
    AccentTheme.NEON -> Palette(Color(0xFF00F0FF), Color(0xFFB14CFF), Color(0xFFFF2BD6))
    AccentTheme.PLASMA -> Palette(Color(0xFFFF2BD6), Color(0xFFFF8A00), Color(0xFFFFD000))
    AccentTheme.TOXIC -> Palette(Color(0xFFA3FF12), Color(0xFF00FFA3), Color(0xFF00C2FF))
    AccentTheme.SOLAR -> Palette(Color(0xFFFFC400), Color(0xFFFF3D3D), Color(0xFFFF7A00))
    AccentTheme.ICE -> Palette(Color(0xFF7DF9FF), Color(0xFF3D7BFF), Color(0xFFC6F0FF))
}

val LocalPalette = staticCompositionLocalOf { paletteFor(AccentTheme.NEON) }
val LocalReduceMotion = staticCompositionLocalOf { false }

val Chakra = FontFamily(
    Font(R.font.chakrapetch_regular, FontWeight.Normal),
    Font(R.font.chakrapetch_medium, FontWeight.Medium),
    Font(R.font.chakrapetch_semibold, FontWeight.SemiBold),
    Font(R.font.chakrapetch_bold, FontWeight.Bold),
)

private fun chakra(size: Int, weight: FontWeight, spacing: Float = 0f) =
    TextStyle(fontFamily = Chakra, fontWeight = weight, fontSize = size.sp, letterSpacing = spacing.sp)

private fun body(size: Int, weight: FontWeight = FontWeight.Normal, line: Int = size + 6) =
    TextStyle(fontWeight = weight, fontSize = size.sp, lineHeight = line.sp)

val OverdriveTypography = Typography(
    displayLarge = chakra(72, FontWeight.Bold, -1.5f),
    displayMedium = chakra(52, FontWeight.Bold, -1f),
    displaySmall = chakra(38, FontWeight.Bold, -0.5f),
    headlineLarge = chakra(30, FontWeight.Bold),
    headlineMedium = chakra(25, FontWeight.SemiBold),
    headlineSmall = chakra(21, FontWeight.SemiBold),
    titleLarge = chakra(20, FontWeight.SemiBold, 0.3f),
    titleMedium = chakra(17, FontWeight.SemiBold, 0.4f),
    titleSmall = chakra(15, FontWeight.Medium, 0.4f),
    bodyLarge = body(16),
    bodyMedium = body(14),
    bodySmall = body(12, line = 17),
    labelLarge = chakra(15, FontWeight.SemiBold, 1.2f),
    labelMedium = chakra(13, FontWeight.Medium, 1.1f),
    labelSmall = chakra(11, FontWeight.Medium, 1.3f),
)

@Composable
fun OverdriveTheme(
    accent: AccentTheme,
    reduceMotion: Boolean = false,
    content: @Composable () -> Unit,
) {
    val target = paletteFor(accent)
    val primary by animateColorAsState(target.primary, tween(700), label = "primary")
    val secondary by animateColorAsState(target.secondary, tween(700), label = "secondary")
    val tertiary by animateColorAsState(target.tertiary, tween(700), label = "tertiary")
    val palette = Palette(primary, secondary, tertiary)
    val scheme = darkColorScheme(
        primary = primary,
        onPrimary = OD.Bg0,
        secondary = secondary,
        onSecondary = OD.Bg0,
        tertiary = tertiary,
        background = OD.Bg0,
        onBackground = OD.Text,
        surface = OD.Bg1,
        onSurface = OD.Text,
        surfaceVariant = OD.Bg2,
        onSurfaceVariant = OD.TextDim,
        outline = OD.Stroke,
        error = OD.Hot,
    )
    CompositionLocalProvider(LocalPalette provides palette, LocalReduceMotion provides reduceMotion) {
        MaterialTheme(colorScheme = scheme, typography = OverdriveTypography, content = content)
    }
}
