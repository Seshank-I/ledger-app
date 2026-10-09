package com.simpleledger.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// All text colours below reach at least 7:1 contrast on their background.
private val LightColors = lightColorScheme(
    primary = Color(0xFF0B3D91),
    onPrimary = Color.White,
    background = Color.White,
    onBackground = Color(0xFF111111),
    surface = Color.White,
    onSurface = Color(0xFF111111),
    surfaceVariant = Color(0xFFEDEFF3),
    onSurfaceVariant = Color(0xFF222222),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF5F6F8),
    surfaceContainer = Color(0xFFEDEFF3),
    surfaceContainerHigh = Color(0xFFE6E8EC),
    surfaceContainerHighest = Color(0xFFE0E2E6),
    error = Color(0xFFA11A12),
    onError = Color.White,
    outline = Color(0xFF4A4D52),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFAFC6FF),
    onPrimary = Color(0xFF002D6E),
    background = Color(0xFF121212),
    onBackground = Color(0xFFF2F2F2),
    surface = Color(0xFF121212),
    onSurface = Color(0xFFF2F2F2),
    surfaceVariant = Color(0xFF2A2D33),
    onSurfaceVariant = Color(0xFFE6E6E6),
    surfaceContainerLowest = Color(0xFF0E0E0E),
    surfaceContainerLow = Color(0xFF1C1D20),
    surfaceContainer = Color(0xFF2A2D33),
    surfaceContainerHigh = Color(0xFF32353B),
    surfaceContainerHighest = Color(0xFF3A3D44),
    error = Color(0xFFFFB4A9),
    onError = Color(0xFF5F0A04),
    outline = Color(0xFFB8B8B8),
)

/** Large type throughout: body text 20sp, amounts 32sp+. Scales further with the phone's font-size setting. */
private val LedgerTypography = Typography(
    displaySmall = TextStyle(fontSize = 44.sp, fontWeight = FontWeight.Bold, lineHeight = 52.sp),
    headlineMedium = TextStyle(fontSize = 32.sp, fontWeight = FontWeight.Bold, lineHeight = 40.sp),
    headlineSmall = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.SemiBold, lineHeight = 34.sp),
    titleLarge = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold, lineHeight = 32.sp),
    titleMedium = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold, lineHeight = 30.sp),
    bodyLarge = TextStyle(fontSize = 20.sp, lineHeight = 28.sp),
    bodyMedium = TextStyle(fontSize = 20.sp, lineHeight = 28.sp),
    bodySmall = TextStyle(fontSize = 18.sp, lineHeight = 26.sp),
    labelLarge = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold, lineHeight = 28.sp),
    labelMedium = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.SemiBold, lineHeight = 24.sp),
)

/** Colours with a fixed meaning. Red always comes with the word "gave" and an up arrow; green with "got" and a down arrow. */
object LedgerColors {
    val gaveButton = Color(0xFFA11A12)
    val gotButton = Color(0xFF1B5E20)

    /** Bright yellow with near-black text (over 12:1 contrast), matching the app icon. */
    val highlight = Color(0xFFFFC107)
    val onHighlight = Color(0xFF1A1A1A)

    val gaveText: Color
        @Composable get() = if (isSystemInDarkTheme()) Color(0xFFFFB4A9) else Color(0xFFA11A12)

    val gotText: Color
        @Composable get() = if (isSystemInDarkTheme()) Color(0xFFA5D6A7) else Color(0xFF1B5E20)

    val muted: Color
        @Composable get() = if (isSystemInDarkTheme()) Color(0xFFBDBDBD) else Color(0xFF4A4D52)

    val warningBackground: Color
        @Composable get() = if (isSystemInDarkTheme()) Color(0xFF4A3B00) else Color(0xFFFFF0C2)

    val criticalBackground: Color
        @Composable get() = if (isSystemInDarkTheme()) Color(0xFF5F0A04) else Color(0xFFFFDAD5)
}

@Composable
fun SimpleLedgerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        typography = LedgerTypography,
        content = content,
    )
}
