package com.stuart.atccontroller.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

@Immutable
data class AtcPalette(
    val night: Color,
    val radar: Color,
    val radarGlow: Color,
    val dataLabel: Color,
    val selectedDataLabel: Color,
    val panel: Color,
    val panelRaised: Color,
    val line: Color,
    val green: Color,
    val greenBright: Color,
    val greenDim: Color,
    val cyan: Color,
    val amber: Color,
    val red: Color,
    val white: Color,
    val muted: Color,
)

private val StandardAtcPalette = AtcPalette(
    night = Color(0xFF030908),
    radar = Color(0xFF061311),
    radarGlow = Color(0xFF0C2520),
    dataLabel = Color(0xD90A1715),
    selectedDataLabel = Color(0xE6143134),
    panel = Color(0xFF0B1917),
    panelRaised = Color(0xFF10231F),
    line = Color(0xFF26443E),
    green = Color(0xFF60F6B2),
    greenBright = Color(0xFFA2FFD2),
    greenDim = Color(0xFF2F9B70),
    cyan = Color(0xFF62D8F4),
    amber = Color(0xFFFFC857),
    red = Color(0xFFFF6B6B),
    white = Color(0xFFE9FFF6),
    muted = Color(0xFF8EA9A1),
)

private val HighContrastAtcPalette = AtcPalette(
    night = Color.Black,
    radar = Color(0xFF00100C),
    radarGlow = Color(0xFF063B2C),
    dataLabel = Color(0xFA020B08),
    selectedDataLabel = Color(0xFA0A2929),
    panel = Color(0xFF07130F),
    panelRaised = Color(0xFF102A22),
    line = Color(0xFF83AFA3),
    green = Color(0xFF73FFBB),
    greenBright = Color(0xFFC5FFE1),
    greenDim = Color(0xFF67DCA9),
    cyan = Color(0xFF88E9FF),
    amber = Color(0xFFFFD166),
    red = Color(0xFFFF8A8A),
    white = Color.White,
    muted = Color(0xFFD0E4DD),
)

private val LocalAtcPalette = staticCompositionLocalOf { StandardAtcPalette }

val MaterialTheme.atcColors: AtcPalette
    @Composable
    @ReadOnlyComposable
    get() = LocalAtcPalette.current

private val AtcColorScheme = darkColorScheme(
    primary = StandardAtcPalette.green,
    onPrimary = StandardAtcPalette.night,
    primaryContainer = Color(0xFF173B30),
    onPrimaryContainer = StandardAtcPalette.greenBright,
    secondary = StandardAtcPalette.cyan,
    onSecondary = StandardAtcPalette.night,
    secondaryContainer = Color(0xFF12323A),
    onSecondaryContainer = Color(0xFFB9F2FF),
    tertiary = StandardAtcPalette.amber,
    onTertiary = StandardAtcPalette.night,
    error = StandardAtcPalette.red,
    onError = StandardAtcPalette.night,
    background = StandardAtcPalette.night,
    onBackground = StandardAtcPalette.white,
    surface = StandardAtcPalette.panel,
    onSurface = StandardAtcPalette.white,
    surfaceVariant = StandardAtcPalette.panelRaised,
    onSurfaceVariant = StandardAtcPalette.muted,
    outline = StandardAtcPalette.line,
)

private val AtcHighContrastScheme = AtcColorScheme.copy(
    primary = HighContrastAtcPalette.green,
    onPrimary = HighContrastAtcPalette.night,
    onPrimaryContainer = HighContrastAtcPalette.greenBright,
    secondary = HighContrastAtcPalette.cyan,
    tertiary = HighContrastAtcPalette.amber,
    error = HighContrastAtcPalette.red,
    background = HighContrastAtcPalette.night,
    onBackground = HighContrastAtcPalette.white,
    surface = HighContrastAtcPalette.panel,
    onSurface = HighContrastAtcPalette.white,
    surfaceVariant = HighContrastAtcPalette.panelRaised,
    onSurfaceVariant = HighContrastAtcPalette.muted,
    outline = HighContrastAtcPalette.line,
)

private val AtcTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Black,
        fontSize = 45.sp,
        lineHeight = 48.sp,
        letterSpacing = (-1.2).sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 30.sp,
        lineHeight = 34.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 26.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        lineHeight = 22.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 19.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 21.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = .5.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = .3.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Medium,
        fontSize = 9.sp,
        lineHeight = 12.sp,
        letterSpacing = .5.sp,
    ),
)

@Composable
fun AtcControllerTheme(
    highContrast: Boolean = false,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalAtcPalette provides if (highContrast) HighContrastAtcPalette else StandardAtcPalette,
    ) {
        MaterialTheme(
            colorScheme = if (highContrast) AtcHighContrastScheme else AtcColorScheme,
            typography = AtcTypography,
            shapes = Shapes(
                extraSmall = RoundedCornerShape(6),
                small = RoundedCornerShape(9),
                medium = RoundedCornerShape(14),
                large = RoundedCornerShape(22),
                extraLarge = RoundedCornerShape(30),
            ),
            content = content,
        )
    }
}
