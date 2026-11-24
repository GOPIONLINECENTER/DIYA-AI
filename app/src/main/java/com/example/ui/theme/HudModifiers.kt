package com.example.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

fun Modifier.holoGlassPanel(
    shape: Shape = RoundedCornerShape(20.dp),
    glowColor: Color = ElectricCyan,
    elevated: Boolean = false
): Modifier = this
    .clip(shape)
    .background(
        brush = Brush.linearGradient(
            colors = listOf(
                (if (elevated) GlassSurfaceElevated else GlassSurface).copy(alpha = 0.78f),
                ObsidianPanel.copy(alpha = 0.68f),
                MidnightNavy.copy(alpha = 0.82f)
            ),
            start = Offset(0f, 0f),
            end = Offset(900f, 900f)
        )
    )
    .border(
        border = BorderStroke(
            width = 1.dp,
            brush = Brush.linearGradient(
                colors = listOf(
                    glowColor.copy(alpha = 0.42f),
                    ElectricBlue.copy(alpha = 0.16f),
                    glowColor.copy(alpha = 0.25f)
                )
            )
        ),
        shape = shape
    )

fun Modifier.diyaHudBackground(glowIntensity: Float = 1f): Modifier = this.drawBehind {
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(
                DeepSpaceBlack,
                MidnightNavy,
                Color(0xFF040A18)
            )
        )
    )

    val safeGlow = glowIntensity.coerceIn(0.2f, 1.4f)
    // Ambient top-center electric cyan aura
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                ElectricCyan.copy(alpha = 0.11f * safeGlow),
                ElectricBlue.copy(alpha = 0.05f * safeGlow),
                Color.Transparent
            ),
            center = Offset(size.width * 0.5f, size.height * 0.28f),
            radius = size.maxDimension * 0.55f
        ),
        radius = size.maxDimension * 0.55f,
        center = Offset(size.width * 0.5f, size.height * 0.28f)
    )

    // Subtle secondary lower cobalt glow
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                ElectricBlue.copy(alpha = 0.09f * safeGlow),
                Color.Transparent
            ),
            center = Offset(size.width * 0.85f, size.height * 0.85f),
            radius = size.maxDimension * 0.42f
        ),
        radius = size.maxDimension * 0.42f,
        center = Offset(size.width * 0.85f, size.height * 0.85f)
    )

    // Ultra-subtle JARVIS HUD grid lines
    val step = 44.dp.toPx()
    val lineColor = ElectricCyan.copy(alpha = 0.032f)
    var x = 0f
    while (x <= size.width) {
        drawLine(
            color = lineColor,
            start = Offset(x, 0f),
            end = Offset(x, size.height),
            strokeWidth = 1f
        )
        x += step
    }
    var y = 0f
    while (y <= size.height) {
        drawLine(
            color = lineColor,
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = 1f
        )
        y += step
    }
}

fun hudCornerRadius(dp: Dp = 20.dp): RoundedCornerShape = RoundedCornerShape(dp)
