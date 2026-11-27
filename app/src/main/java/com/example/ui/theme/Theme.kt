package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val StudioDarkColorScheme = darkColorScheme(
    primary = CoralPrimary,
    onPrimary = Color.White,
    primaryContainer = CoralContainer,
    onPrimaryContainer = Color(0xFFFFDAD2),
    secondary = ElectricViolet,
    onSecondary = Color.White,
    secondaryContainer = ElectricVioletContainer,
    onSecondaryContainer = Color(0xFFE4DFFF),
    tertiary = CyanSpark,
    onTertiary = Color(0xFF00363E),
    tertiaryContainer = CyanContainer,
    onTertiaryContainer = Color(0xFFB5F4FF),
    background = StudioMidnight,
    onBackground = Color(0xFFF4F2FF),
    surface = StudioSurface,
    onSurface = Color(0xFFF4F2FF),
    surfaceVariant = StudioSurfaceVariant,
    onSurfaceVariant = Color(0xFFC7C2E4),
    surfaceContainerHigh = StudioSurfaceHigh,
    outline = StudioOutline,
    error = CrimsonError,
    onError = Color.White
)

private val StudioLightColorScheme = lightColorScheme(
    primary = CoralPrimaryLight,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDBD3),
    onPrimaryContainer = Color(0xFF410E05),
    secondary = VioletSecondaryLight,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE5E0FF),
    onSecondaryContainer = Color(0xFF1B1056),
    tertiary = CyanTertiaryLight,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFCFF7FF),
    onTertiaryContainer = Color(0xFF003641),
    background = LightCanvas,
    onBackground = LightInk,
    surface = LightSurface,
    onSurface = LightInk,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightMutedInk,
    outline = LightOutline,
    error = Color(0xFFDC2626),
    onError = Color.White
)

val CartoonShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun CartoonMasterTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) StudioDarkColorScheme else StudioLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = CartoonTypography,
        shapes = CartoonShapes,
        content = content
    )
}
