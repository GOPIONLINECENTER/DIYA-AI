package com.example.ui.screens

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.CloudQueue
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.AppLanguage
import com.example.model.DiyaSettings
import com.example.ui.theme.AlertCoral
import com.example.ui.theme.DeepSpaceBlack
import com.example.ui.theme.DisconnectedSlate
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.GlassSurface
import com.example.ui.theme.ObsidianPanel
import com.example.ui.theme.StandbyAmber
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.holoGlassPanel
import com.example.viewmodel.RemoteBridgeUiState

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    settings: DiyaSettings,
    remoteState: RemoteBridgeUiState,
    geminiProtocolSpec: String,
    desktopSecurityProfile: String,
    onLanguageSelected: (AppLanguage) -> Unit,
    onSettingsUpdated: ((DiyaSettings) -> DiyaSettings) -> Unit,
    onSignOutClicked: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 760.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Section 0: Authenticated Session & Device Identity
            SettingsCard(
                icon = Icons.Outlined.VerifiedUser,
                title = "AUTHENTICATED SESSION",
                subtitle = "Google Sign-In verified • Isolated per-user Firestore & WSS"
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(ObsidianPanel.copy(alpha = 0.7f))
                        .border(0.8.dp, ElectricCyan.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Account: ${remoteState.userEmail.ifBlank { remoteState.userId }}",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = TextPrimary,
                            fontWeight = FontWeight.Medium
                        )
                    )
                    Text(
                        text = "Mobile Device ID: ${remoteState.mobileDeviceId.ifBlank { "initializing..." }}",
                        style = MaterialTheme.typography.labelSmall.copy(color = ElectricCyan)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedButton(
                        onClick = onSignOutClicked,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("sign_out_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.Logout,
                            contentDescription = "Sign out",
                            tint = AlertCoral,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Sign Out", color = AlertCoral)
                    }
                }
            }

            // Section 1: Multilingual Core (Bengali Primary + English + Hindi)
            SettingsCard(
                icon = Icons.Outlined.Language,
                title = "LANGUAGE & PERSONALITY CORE",
                subtitle = "Primary: Bengali • Understands English & Hindi"
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    AppLanguage.entries.forEach { lang ->
                        val selected = settings.primaryLanguage == lang
                        val interactionSource = remember { MutableInteractionSource() }
                        Column(
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    if (selected) ElectricCyan.copy(alpha = 0.18f)
                                    else ObsidianPanel.copy(alpha = 0.7f)
                                )
                                .border(
                                    1.dp,
                                    if (selected) ElectricCyan else ElectricBlue.copy(alpha = 0.3f),
                                    RoundedCornerShape(14.dp)
                                )
                                .clickable(
                                    interactionSource = interactionSource,
                                    indication = ripple(bounded = true, color = ElectricCyan),
                                    onClick = { onLanguageSelected(lang) }
                                )
                                .padding(horizontal = 16.dp, vertical = 10.dp)
                                .testTag("lang_option_${lang.shortTag.lowercase()}")
                        ) {
                            Text(
                                text = lang.nativeLabel,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    color = if (selected) ElectricCyan else TextPrimary
                                )
                            )
                            Text(
                                text = lang.code,
                                style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary)
                            )
                        }
                    }
                }
            }

            // Section 2: Hologram & Voice UI Tuning
            SettingsCard(
                icon = Icons.Outlined.Tune,
                title = "HUD VISUALS & VOICE CADENCE",
                subtitle = "Customize interface lighting and animation behavior"
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Holographic Glow Intensity",
                                style = MaterialTheme.typography.bodyMedium.copy(color = TextPrimary)
                            )
                            Text(
                                text = "${(settings.holographicGlowIntensity * 100).toInt()}%",
                                style = MaterialTheme.typography.labelMedium.copy(color = ElectricCyan)
                            )
                        }
                        Slider(
                            value = settings.holographicGlowIntensity,
                            onValueChange = { v ->
                                onSettingsUpdated { it.copy(holographicGlowIntensity = v) }
                            },
                            valueRange = 0.35f..1.35f,
                            colors = SliderDefaults.colors(
                                thumbColor = ElectricCyan,
                                activeTrackColor = ElectricCyan,
                                inactiveTrackColor = GlassSurface
                            ),
                            modifier = Modifier.testTag("glow_intensity_slider")
                        )
                    }

                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Waveform Cadence Preview",
                                style = MaterialTheme.typography.bodyMedium.copy(color = TextPrimary)
                            )
                            Text(
                                text = "${(settings.voiceResponseCadence * 100).toInt()}%",
                                style = MaterialTheme.typography.labelMedium.copy(color = ElectricCyan)
                            )
                        }
                        Slider(
                            value = settings.voiceResponseCadence,
                            onValueChange = { v ->
                                onSettingsUpdated { it.copy(voiceResponseCadence = v) }
                            },
                            valueRange = 0.2f..1.0f,
                            colors = SliderDefaults.colors(
                                thumbColor = ElectricCyan,
                                activeTrackColor = ElectricBlue,
                                inactiveTrackColor = GlassSurface
                            ),
                            modifier = Modifier.testTag("cadence_slider")
                        )
                    }

                    SettingsToggleRow(
                        icon = Icons.Outlined.GraphicEq,
                        title = "Wake-Word HUD Indicator (\"Hey DIYA\")",
                        description = "Visual badge only — continuous mic capture is not active",
                        checked = settings.wakeWordPreviewEnabled,
                        onCheckedChange = { checked ->
                            onSettingsUpdated { it.copy(wakeWordPreviewEnabled = checked) }
                        },
                        testTag = "wake_word_toggle"
                    )

                    SettingsToggleRow(
                        icon = Icons.Outlined.Tune,
                        title = "Reduced Motion Mode",
                        description = "Calms orbital ring speed and waveform frequency",
                        checked = settings.reducedMotion,
                        onCheckedChange = { checked ->
                            onSettingsUpdated { it.copy(reducedMotion = checked) }
                        },
                        testTag = "reduced_motion_toggle"
                    )
                }
            }

            // Section 3: Architecture Readiness
            SettingsCard(
                icon = Icons.Outlined.Security,
                title = "CLOUD RUN & AGENT SECURITY ARCHITECTURE",
                subtitle = "Server-side Gemini API • Zero exposed Windows ports"
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    BridgeReadinessTile(
                        icon = Icons.Outlined.CloudQueue,
                        title = "Server-Side Gemini API Relay",
                        statusLabel = "SERVER-SIDE ONLY",
                        statusColor = StandbyAmber,
                        detail = geminiProtocolSpec
                    )
                    BridgeReadinessTile(
                        icon = Icons.Outlined.Computer,
                        title = "Windows Desktop Agent",
                        statusLabel = if (remoteState.pcDeviceRecord?.connectionStatus == "ONLINE") "ONLINE" else "OFFLINE",
                        statusColor = DisconnectedSlate,
                        detail = desktopSecurityProfile
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun SettingsCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .holoGlassPanel(shape = RoundedCornerShape(22.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(ElectricCyan.copy(alpha = 0.14f))
                    .border(0.8.dp, ElectricCyan.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = ElectricCyan,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall.copy(
                        color = ElectricCyan,
                        fontWeight = FontWeight.Bold
                    )
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                )
            }
        }
        content()
    }
}

@Composable
private fun SettingsToggleRow(
    icon: ImageVector,
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(ObsidianPanel.copy(alpha = 0.65f))
            .border(0.8.dp, ElectricBlue.copy(alpha = 0.25f), RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = ElectricCyan,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = TextPrimary,
                        fontWeight = FontWeight.Medium
                    )
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                )
            }
        }
        Spacer(modifier = Modifier.width(10.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = DeepSpaceBlack,
                checkedTrackColor = ElectricCyan,
                uncheckedThumbColor = TextSecondary,
                uncheckedTrackColor = GlassSurface
            ),
            modifier = Modifier.testTag(testTag)
        )
    }
}

@Composable
private fun BridgeReadinessTile(
    icon: ImageVector,
    title: String,
    statusLabel: String,
    statusColor: Color,
    detail: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(ObsidianPanel.copy(alpha = 0.7f))
            .border(0.8.dp, statusColor.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = statusColor,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(color = TextPrimary)
                )
            }
            Text(
                text = statusLabel,
                style = MaterialTheme.typography.labelSmall.copy(
                    color = statusColor,
                    fontWeight = FontWeight.Bold
                )
            )
        }
        Text(
            text = detail,
            style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
        )
    }
}
