package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.model.VoiceState
import com.example.ui.theme.DeepSpaceBlack
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.IceHolo
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun AiCoreVisualizer(
    voiceState: VoiceState,
    waveformBars: List<Float>,
    glowIntensity: Float,
    reducedMotion: Boolean,
    coreDiameter: Dp = 228.dp,
    modifier: Modifier = Modifier
) {
    val coreDescription = stringResource(R.string.cd_diya_core)
    val primaryAccent by animateColorAsState(
        targetValue = voiceState.accentColor(),
        animationSpec = tween(350),
        label = "corePrimaryAccent"
    )
    val secondaryAccent by animateColorAsState(
        targetValue = voiceState.secondaryColor(),
        animationSpec = tween(350),
        label = "coreSecondaryAccent"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "diyaCoreTransition")
    val outerAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (reducedMotion) 28000 else 14000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "outerRingRotation"
    )
    val innerAngle by infiniteTransition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (reducedMotion) 22000 else 8500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "innerRingRotation"
    )
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.07f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (voiceState) {
                    VoiceState.IDLE -> 2200
                    VoiceState.LISTENING -> 1100
                    VoiceState.THINKING -> 900
                    VoiceState.SPEAKING -> 750
                },
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "corePulse"
    )

    val activeMultiplier by animateFloatAsState(
        targetValue = when (voiceState) {
            VoiceState.IDLE -> 0.7f
            VoiceState.LISTENING -> 1.15f
            VoiceState.THINKING -> 1.05f
            VoiceState.SPEAKING -> 1.25f
        },
        label = "activeGlowMultiplier"
    )

    val effectivePulse = if (reducedMotion) 1f else pulseScale
    val glow = (glowIntensity * activeMultiplier).coerceIn(0.3f, 1.5f)

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(
            modifier = Modifier
                .size(coreDiameter)
                .semantics { contentDescription = coreDescription },
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val maxR = size.minDimension / 2f

                // Outer holographic aura
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            primaryAccent.copy(alpha = 0.24f * glow),
                            secondaryAccent.copy(alpha = 0.09f * glow),
                            Color.Transparent
                        ),
                        center = center,
                        radius = maxR
                    ),
                    radius = maxR,
                    center = center
                )

                // Outer radial audio-reactive tick ring
                val tickCount = 48
                val tickBaseR = maxR * 0.84f
                for (i in 0 until tickCount) {
                    val rad = (i.toFloat() / tickCount) * 2f * PI.toFloat()
                    val barVal = if (waveformBars.isNotEmpty()) {
                        waveformBars[i % waveformBars.size]
                    } else 0.15f
                    val tickLen = maxR * (0.04f + barVal * 0.11f)
                    val start = Offset(
                        x = center.x + cos(rad) * tickBaseR,
                        y = center.y + sin(rad) * tickBaseR
                    )
                    val end = Offset(
                        x = center.x + cos(rad) * (tickBaseR + tickLen),
                        y = center.y + sin(rad) * (tickBaseR + tickLen)
                    )
                    drawLine(
                        color = primaryAccent.copy(alpha = 0.35f + barVal * 0.55f),
                        start = start,
                        end = end,
                        strokeWidth = 2.2.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }

                // Rotating dashed orbital telemetry ring
                rotate(degrees = outerAngle, pivot = center) {
                    val r1 = maxR * 0.77f
                    drawCircle(
                        color = primaryAccent.copy(alpha = 0.45f),
                        radius = r1,
                        center = center,
                        style = Stroke(
                            width = 1.5.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f), 0f)
                        )
                    )
                    // 3 Orbital arc segments
                    val arcRadius = maxR * 0.70f
                    val arcTopLeft = Offset(center.x - arcRadius, center.y - arcRadius)
                    val arcSize = Size(arcRadius * 2f, arcRadius * 2f)
                    listOf(0f, 120f, 240f).forEach { startDeg ->
                        drawArc(
                            color = primaryAccent.copy(alpha = 0.82f),
                            startAngle = startDeg,
                            sweepAngle = 72f,
                            useCenter = false,
                            topLeft = arcTopLeft,
                            size = arcSize,
                            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }
                }

                // Counter-rotating inner precision arcs
                rotate(degrees = innerAngle, pivot = center) {
                    val arcRadius = maxR * 0.56f
                    val arcTopLeft = Offset(center.x - arcRadius, center.y - arcRadius)
                    val arcSize = Size(arcRadius * 2f, arcRadius * 2f)
                    listOf(25f, 205f).forEach { startDeg ->
                        drawArc(
                            color = secondaryAccent.copy(alpha = 0.9f),
                            startAngle = startDeg,
                            sweepAngle = 125f,
                            useCenter = false,
                            topLeft = arcTopLeft,
                            size = arcSize,
                            style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }
                }

                // Pulsing inner core halo + dark obsidian lens
                val coreRadius = maxR * 0.41f * effectivePulse
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            IceHolo.copy(alpha = 0.35f * glow),
                            primaryAccent.copy(alpha = 0.25f * glow),
                            DeepSpaceBlack.copy(alpha = 0.94f)
                        ),
                        center = center,
                        radius = coreRadius * 1.2f
                    ),
                    radius = coreRadius,
                    center = center
                )
                drawCircle(
                    color = primaryAccent.copy(alpha = 0.75f),
                    radius = coreRadius,
                    center = center,
                    style = Stroke(width = 2.dp.toPx())
                )
                drawCircle(
                    color = IceHolo.copy(alpha = 0.3f),
                    radius = coreRadius * 0.72f,
                    center = center,
                    style = Stroke(width = 1.dp.toPx())
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "DIYA",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        color = IceHolo,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 4.sp,
                        fontSize = 20.sp
                    )
                )
                Text(
                    text = "CORE v1.0",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = primaryAccent.copy(alpha = 0.9f),
                        letterSpacing = 1.8.sp
                    )
                )
            }
        }

        GlowingAudioWaveform(
            bars = waveformBars,
            primaryColor = primaryAccent,
            secondaryColor = secondaryAccent,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .height(54.dp)
        )
    }
}

@Composable
fun GlowingAudioWaveform(
    bars: List<Float>,
    primaryColor: Color = ElectricCyan,
    secondaryColor: Color = ElectricBlue,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        if (bars.isEmpty()) return@Canvas
        val count = bars.size
        val midY = size.height / 2f
        val slotWidth = size.width / count
        val barWidth = (slotWidth * 0.56f).coerceIn(3f, 12f)

        // Subtle horizon baseline
        drawLine(
            color = primaryColor.copy(alpha = 0.22f),
            start = Offset(0f, midY),
            end = Offset(size.width, midY),
            strokeWidth = 1.dp.toPx()
        )

        bars.forEachIndexed { index, amplitude ->
            val clamped = amplitude.coerceIn(0.08f, 1f)
            val halfHeight = (size.height * 0.44f) * clamped
            val x = index * slotWidth + (slotWidth - barWidth) / 2f

            // Glow halo behind each bar
            drawRoundRect(
                color = primaryColor.copy(alpha = 0.22f * clamped),
                topLeft = Offset(x - 1.5f, midY - halfHeight - 2f),
                size = Size(barWidth + 3f, (halfHeight * 2f) + 4f),
                cornerRadius = CornerRadius(barWidth, barWidth)
            )

            // Main luminous bar
            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        IceHolo.copy(alpha = 0.95f),
                        primaryColor,
                        secondaryColor
                    ),
                    startY = midY - halfHeight,
                    endY = midY + halfHeight
                ),
                topLeft = Offset(x, midY - halfHeight),
                size = Size(barWidth, halfHeight * 2f),
                cornerRadius = CornerRadius(barWidth, barWidth)
            )
        }
    }
}
