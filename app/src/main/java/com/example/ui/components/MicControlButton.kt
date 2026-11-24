package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.model.AppLanguage
import com.example.model.DiyaCopy
import com.example.model.VoiceState
import com.example.ui.theme.DeepSpaceBlack
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.GlassSurface
import com.example.ui.theme.IceHolo
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VoiceControlSection(
    voiceState: VoiceState,
    language: AppLanguage,
    reducedMotion: Boolean,
    onMicClicked: () -> Unit,
    onStateChipSelected: (VoiceState) -> Unit,
    modifier: Modifier = Modifier
) {
    val activeColor by animateColorAsState(
        targetValue = voiceState.accentColor(),
        animationSpec = tween(300),
        label = "micAccent"
    )
    val isActive = voiceState != VoiceState.IDLE

    val infiniteTransition = rememberInfiniteTransition(label = "micSonar")
    val haloScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = if (isActive && !reducedMotion) 1.28f else 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (isActive) 950 else 2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "micHaloScale"
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Active Voice State Label Pill
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(activeColor.copy(alpha = 0.14f))
                .border(1.dp, activeColor.copy(alpha = 0.55f), RoundedCornerShape(50))
                .padding(horizontal = 14.dp, vertical = 6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(activeColor)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = DiyaCopy.voiceLabel(voiceState, language),
                style = MaterialTheme.typography.labelMedium.copy(
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            )
        }

        // Large Futuristic Microphone Button
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(112.dp)
        ) {
            // Outer pulsing sonar ring
            Box(
                modifier = Modifier
                    .size(92.dp)
                    .scale(haloScale)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                activeColor.copy(alpha = if (isActive) 0.34f else 0.14f),
                                Color.Transparent
                            )
                        )
                    )
                    .border(
                        width = 1.2.dp,
                        color = activeColor.copy(alpha = if (isActive) 0.6f else 0.25f),
                        shape = CircleShape
                    )
            )

            // Core clickable Mic button
            val interactionSource = remember { MutableInteractionSource() }
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(78.dp)
                    .minimumInteractiveComponentSize()
                    .clip(CircleShape)
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                activeColor,
                                ElectricBlue
                            )
                        )
                    )
                    .border(2.dp, IceHolo.copy(alpha = 0.85f), CircleShape)
                    .clickable(
                        interactionSource = interactionSource,
                        indication = ripple(bounded = true, color = IceHolo),
                        onClick = onMicClicked
                    )
                    .testTag("mic_preview_button")
            ) {
                Icon(
                    imageVector = when (voiceState) {
                        VoiceState.IDLE -> Icons.Filled.Mic
                        VoiceState.LISTENING -> Icons.Filled.Stop
                        VoiceState.THINKING, VoiceState.SPEAKING -> Icons.Filled.GraphicEq
                    },
                    contentDescription = stringResource(R.string.cd_mic_button),
                    tint = DeepSpaceBlack,
                    modifier = Modifier.size(34.dp)
                )
            }
        }

        // Voice Listening / Thinking / Speaking Status Selector Chips (Stage 1 UI Preview)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            VoiceStateChip(
                label = "Standby",
                selected = voiceState == VoiceState.IDLE,
                accentColor = ElectricCyan,
                onClick = { onStateChipSelected(VoiceState.IDLE) },
                tag = "state_chip_idle"
            )
            VoiceStateChip(
                label = "Listening",
                selected = voiceState == VoiceState.LISTENING,
                accentColor = VoiceState.LISTENING.accentColor(),
                onClick = { onStateChipSelected(VoiceState.LISTENING) },
                tag = "state_chip_listening"
            )
            VoiceStateChip(
                label = "Thinking",
                selected = voiceState == VoiceState.THINKING,
                accentColor = VoiceState.THINKING.accentColor(),
                onClick = { onStateChipSelected(VoiceState.THINKING) },
                tag = "state_chip_thinking"
            )
            VoiceStateChip(
                label = "Speaking",
                selected = voiceState == VoiceState.SPEAKING,
                accentColor = VoiceState.SPEAKING.accentColor(),
                onClick = { onStateChipSelected(VoiceState.SPEAKING) },
                tag = "state_chip_speaking"
            )
        }

        Text(
            text = DiyaCopy.voicePreviewNotice(language),
            style = MaterialTheme.typography.bodySmall.copy(
                color = TextSecondary,
                fontSize = 11.5.sp
            ),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }
}

@Composable
private fun VoiceStateChip(
    label: String,
    selected: Boolean,
    accentColor: Color,
    onClick: () -> Unit,
    tag: String
) {
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(50))
            .background(
                if (selected) accentColor.copy(alpha = 0.2f)
                else GlassSurface.copy(alpha = 0.55f)
            )
            .border(
                width = 1.dp,
                color = if (selected) accentColor else ElectricCyan.copy(alpha = 0.2f),
                shape = RoundedCornerShape(50)
            )
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(bounded = true, color = accentColor),
                onClick = onClick
            )
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .testTag(tag)
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(if (selected) accentColor else TextSecondary.copy(alpha = 0.45f))
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(
                color = if (selected) TextPrimary else TextSecondary
            )
        )
    }
}
