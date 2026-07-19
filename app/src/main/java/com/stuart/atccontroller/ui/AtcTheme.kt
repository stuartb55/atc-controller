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
    night = Color(0xFF050B0D),
    radar = Color(0xFF071517),
    radarGlow = Color(0xFF0B2425),
    dataLabel = Color(0xD90A1715),
    selectedDataLabel = Color(0xE6143134),
    panel = Color(0xFF0D1B1D),
    panelRaised = Color(0xFF14272A),
    line = Color(0xFF315055),
    green = Color(0xFF72F4B7),
    greenBright = Color(0xFFB4FFD9),
    greenDim = Color(0xFF3EAD7E),
    cyan = Color(0xFF72D9F1),
    amber = Color(0xFFFFCA66),
    red = Color(0xFFFF7A78),
    white = Color(0xFFF0FBF8),
    muted = Color(0xFFA2B8B7),
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

@Suppress("UnusedReceiverParameter")
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
        fontSize = 44.sp,
        lineHeight = 48.sp,
        letterSpacing = (-1.2).sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 29.sp,
        lineHeight = 35.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 23.sp,
        lineHeight = 29.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 25.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 21.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 23.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 17.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 13.sp,
        lineHeight = 17.sp,
        letterSpacing = .5.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = .3.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 15.sp,
        letterSpacing = .35.sp,
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
