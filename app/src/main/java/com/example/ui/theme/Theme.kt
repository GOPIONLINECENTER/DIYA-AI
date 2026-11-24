package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val DiyaDarkColorScheme = darkColorScheme(
    primary = ElectricCyan,
    onPrimary = DeepSpaceBlack,
    primaryContainer = GlassSurfaceElevated,
    onPrimaryContainer = IceHolo,
    secondary = ElectricBlue,
    onSecondary = TextPrimary,
    secondaryContainer = ObsidianPanel,
    onSecondaryContainer = CyanGlow,
    tertiary = ListeningAqua,
    onTertiary = DeepSpaceBlack,
    background = DeepSpaceBlack,
    onBackground = TextPrimary,
    surface = MidnightNavy,
    onSurface = TextPrimary,
    surfaceVariant = GlassSurface,
    onSurfaceVariant = TextSecondary,
    outline = HoloBorderCyan,
    outlineVariant = HoloBorderSubtle,
    error = AlertCoral,
    onError = DeepSpaceBlack
)

val DiyaShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun DiyaAiTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DiyaDarkColorScheme,
        typography = DiyaTypography,
        shapes = DiyaShapes,
        content = content
    )
}
