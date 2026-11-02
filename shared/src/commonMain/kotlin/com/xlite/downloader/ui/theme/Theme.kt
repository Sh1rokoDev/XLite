package com.xlite.downloader.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private data class StatusColors(
    val success: Color,
    val warning: Color,
    val danger: Color,
    val neutral: Color,
)

private val LightStatus = StatusColors(
    success = Color(0xFF08734C),
    warning = Color(0xFF926000),
    danger = Color(0xFFB42332),
    neutral = Color(0xFF5E6675),
)

private val DarkStatus = StatusColors(
    success = Color(0xFF79D8AA),
    warning = Color(0xFFF2C46E),
    danger = Color(0xFFFF9BA4),
    neutral = Color(0xFFAFB8C8),
)

private val LocalStatusColors = staticCompositionLocalOf { LightStatus }

object XLiteColors {
    val BrandBlue = Color(0xFF315AE8)
    val BrandCyan: Color @Composable get() = MaterialTheme.colorScheme.primary
    val Success: Color @Composable get() = LocalStatusColors.current.success
    val Warning: Color @Composable get() = LocalStatusColors.current.warning
    val Danger: Color @Composable get() = LocalStatusColors.current.danger
    val Neutral: Color @Composable get() = LocalStatusColors.current.neutral
}

private val LightColors = lightColorScheme(
    primary = XLiteColors.BrandBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE8EDFF),
    onPrimaryContainer = Color(0xFF213C9A),
    secondary = Color(0xFF53627D),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFEDF0F6),
    onSecondaryContainer = Color(0xFF263348),
    tertiary = Color(0xFF53627D),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFEDF0F6),
    onTertiaryContainer = Color(0xFF263348),
    background = Color(0xFFF6F7F9),
    onBackground = Color(0xFF191E29),
    surface = Color.White,
    onSurface = Color(0xFF191E29),
    surfaceVariant = Color(0xFFF0F2F6),
    onSurfaceVariant = Color(0xFF5E6675),
    // Menus and dialogs use these elevated surface roles rather than surface.
    surfaceTint = XLiteColors.BrandBlue,
    surfaceDim = Color(0xFFE1E5EC),
    surfaceBright = Color.White,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFAFBFC),
    surfaceContainer = Color(0xFFF6F7F9),
    surfaceContainerHigh = Color(0xFFF0F2F6),
    surfaceContainerHighest = Color(0xFFE8ECF2),
    inverseSurface = Color(0xFF29313F),
    inverseOnSurface = Color(0xFFF1F3F8),
    inversePrimary = Color(0xFFAAB9FF),
    scrim = Color.Black,
    outline = Color(0xFF929BAA),
    outlineVariant = Color(0xFFE1E5EC),
    error = LightStatus.danger,
    onError = Color.White,
    errorContainer = Color(0xFFFFEAEC),
    onErrorContainer = Color(0xFF7F1722),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFAAB9FF),
    onPrimary = Color(0xFF10266C),
    primaryContainer = Color(0xFF263B7B),
    onPrimaryContainer = Color(0xFFE1E6FF),
    secondary = Color(0xFFBCC7DE),
    onSecondary = Color(0xFF263348),
    secondaryContainer = Color(0xFF293447),
    onSecondaryContainer = Color(0xFFDBE3F3),
    tertiary = Color(0xFFBCC7DE),
    onTertiary = Color(0xFF263348),
    tertiaryContainer = Color(0xFF293447),
    onTertiaryContainer = Color(0xFFDBE3F3),
    background = Color(0xFF10131A),
    onBackground = Color(0xFFE8EBF2),
    surface = Color(0xFF1A1F29),
    onSurface = Color(0xFFE8EBF2),
    surfaceVariant = Color(0xFF262C38),
    onSurfaceVariant = Color(0xFFAFB8C8),
    surfaceTint = Color(0xFFAAB9FF),
    surfaceDim = Color(0xFF10131A),
    surfaceBright = Color(0xFF343C4A),
    surfaceContainerLowest = Color(0xFF0C0F15),
    surfaceContainerLow = Color(0xFF171C25),
    surfaceContainer = Color(0xFF1A1F29),
    surfaceContainerHigh = Color(0xFF232A36),
    surfaceContainerHighest = Color(0xFF2D3543),
    inverseSurface = Color(0xFFE8EBF2),
    inverseOnSurface = Color(0xFF29313F),
    inversePrimary = XLiteColors.BrandBlue,
    scrim = Color.Black,
    outline = Color(0xFF8590A2),
    outlineVariant = Color(0xFF343C4A),
    error = DarkStatus.danger,
    onError = Color(0xFF60101C),
    errorContainer = Color(0xFF61232D),
    onErrorContainer = Color(0xFFFFDADD),
)

private val XLiteTypography = Typography().let { base ->
    base.copy(
        headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.SemiBold, fontSize = 26.sp, lineHeight = 34.sp),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold, fontSize = 20.sp),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        titleSmall = base.titleSmall.copy(fontWeight = FontWeight.SemiBold),
        bodyLarge = base.bodyLarge.copy(lineHeight = 24.sp),
        bodyMedium = base.bodyMedium.copy(lineHeight = 21.sp),
        labelLarge = base.labelLarge.copy(fontWeight = FontWeight.Medium),
    )
}

private val XLiteShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(9.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(20.dp),
)

@Composable
fun XLiteTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalStatusColors provides if (darkTheme) DarkStatus else LightStatus) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = XLiteTypography,
            shapes = XLiteShapes,
            content = content,
        )
    }
}
