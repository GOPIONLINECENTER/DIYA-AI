package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.DiyaUiState
import com.example.model.ShortcutItem
import com.example.model.VoiceState
import com.example.ui.components.AiCoreVisualizer
import com.example.ui.components.ChatConversationPanel
import com.example.ui.components.CommandHistoryPanel
import com.example.ui.components.HudStatusBar
import com.example.ui.components.QuickShortcutsPanel
import com.example.ui.components.VoiceControlSection
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.GlassSurface
import com.example.ui.theme.ObsidianPanel
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.holoGlassPanel

@Composable
fun DashboardScreen(
    uiState: DiyaUiState,
    onMicClicked: () -> Unit,
    onVoiceStateSelected: (VoiceState) -> Unit,
    onShortcutClicked: (ShortcutItem) -> Unit,
    onOpenChatTab: () -> Unit,
    onOpenHistoryTab: () -> Unit,
    onDraftChanged: (String) -> Unit,
    onSendMessage: (String?) -> Unit,
    onHistoryFilterChanged: (String) -> Unit,
    onClearHistory: () -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isWideCommandCenter = maxWidth >= 840.dp

        if (isWideCommandCenter) {
            // Tablet / Foldable / Windows Desktop Multi-Pane Command Center
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Column(
                    modifier = Modifier
                        .weight(1.15f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    HudStatusBar(
                        currentTimeText = uiState.currentTimeText,
                        currentDateText = uiState.currentDateText,
                        mobileUiReady = uiState.mobileUiReady,
                        pcAgentStatus = uiState.pcAgentStatus,
                        geminiLiveStatus = uiState.geminiLiveStatus,
                        isWideLayout = true
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .holoGlassPanel(shape = RoundedCornerShape(24.dp))
                            .padding(vertical = 20.dp, horizontal = 16.dp)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            AiCoreVisualizer(
                                voiceState = uiState.voiceState,
                                waveformBars = uiState.waveformBars,
                                glowIntensity = uiState.settings.holographicGlowIntensity,
                                reducedMotion = uiState.settings.reducedMotion,
                                coreDiameter = 238.dp
                            )
                            VoiceControlSection(
                                voiceState = uiState.voiceState,
                                language = uiState.settings.primaryLanguage,
                                reducedMotion = uiState.settings.reducedMotion,
                                onMicClicked = onMicClicked,
                                onStateChipSelected = onVoiceStateSelected
                            )
                        }
                    }

                    QuickShortcutsPanel(
                        shortcuts = uiState.shortcuts,
                        onShortcutClick = onShortcutClicked
                    )
                }

                // Right Command Center Panel (Tabbed Chat + Command History)
                DesktopSideTelemetryPane(
                    uiState = uiState,
                    onDraftChanged = onDraftChanged,
                    onSendMessage = onSendMessage,
                    onHistoryFilterChanged = onHistoryFilterChanged,
                    onClearHistory = onClearHistory,
                    modifier = Modifier
                        .weight(0.95f)
                        .fillMaxHeight()
                )
            }
        } else {
            // Mobile Portrait HUD Layout
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 600.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    HudStatusBar(
                        currentTimeText = uiState.currentTimeText,
                        currentDateText = uiState.currentDateText,
                        mobileUiReady = uiState.mobileUiReady,
                        pcAgentStatus = uiState.pcAgentStatus,
                        geminiLiveStatus = uiState.geminiLiveStatus,
                        isWideLayout = false
                    )

                    // Center Holographic AI Core + Waveform + Mic Card
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .holoGlassPanel(shape = RoundedCornerShape(24.dp))
                            .padding(vertical = 18.dp, horizontal = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        AiCoreVisualizer(
                            voiceState = uiState.voiceState,
                            waveformBars = uiState.waveformBars,
                            glowIntensity = uiState.settings.holographicGlowIntensity,
                            reducedMotion = uiState.settings.reducedMotion,
                            coreDiameter = 212.dp
                        )

                        VoiceControlSection(
                            voiceState = uiState.voiceState,
                            language = uiState.settings.primaryLanguage,
                            reducedMotion = uiState.settings.reducedMotion,
                            onMicClicked = onMicClicked,
                            onStateChipSelected = onVoiceStateSelected
                        )
                    }

                    // Compact Latest Chat Preview Card
                    val latestDiyaMsg = uiState.messages.lastOrNull()
                    CompactHudPreviewCard(
                        icon = Icons.Outlined.AutoAwesome,
                        title = "CHAT CONVERSATION",
                        badge = "${uiState.messages.size} MSGS",
                        previewText = latestDiyaMsg?.text ?: "Tap to open DIYA chat preview",
                        onClick = onOpenChatTab,
                        testTag = "dashboard_chat_preview_card"
                    )

                    // Quick Application Shortcuts
                    QuickShortcutsPanel(
                        shortcuts = uiState.shortcuts,
                        onShortcutClick = onShortcutClicked
                    )

                    // Compact Command History Preview Card
                    val latestCmd = uiState.commandHistory.firstOrNull()
                    CompactHudPreviewCard(
                        icon = Icons.Outlined.History,
                        title = "COMMAND HISTORY",
                        badge = "${uiState.commandHistory.size} LOGS",
                        previewText = latestCmd?.let { "${it.languageTag}: ${it.commandText}" }
                            ?: "No commands logged yet",
                        onClick = onOpenHistoryTab,
                        testTag = "dashboard_history_preview_card"
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun DesktopSideTelemetryPane(
    uiState: DiyaUiState,
    onDraftChanged: (String) -> Unit,
    onSendMessage: (String?) -> Unit,
    onHistoryFilterChanged: (String) -> Unit,
    onClearHistory: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedPane by remember { mutableIntStateOf(0) }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .holoGlassPanel(shape = RoundedCornerShape(16.dp))
                .padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PaneTabButton(
                label = "Conversation",
                selected = selectedPane == 0,
                onClick = { selectedPane = 0 },
                modifier = Modifier.weight(1f)
            )
            PaneTabButton(
                label = "Command History (${uiState.commandHistory.size})",
                selected = selectedPane == 1,
                onClick = { selectedPane = 1 },
                modifier = Modifier.weight(1f)
            )
        }

        if (selectedPane == 0) {
            ChatConversationPanel(
                messages = uiState.messages,
                draftText = uiState.chatDraft,
                language = uiState.settings.primaryLanguage,
                onDraftChanged = onDraftChanged,
                onSendMessage = onSendMessage,
                showHeader = false,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )
        } else {
            CommandHistoryPanel(
                entries = uiState.commandHistory,
                selectedFilter = uiState.historyFilterTag,
                onFilterChanged = onHistoryFilterChanged,
                onClearHistory = onClearHistory,
                showHeader = false,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )
        }
    }
}

@Composable
private fun PaneTabButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (selected) ElectricCyan.copy(alpha = 0.2f)
                else ObsidianPanel.copy(alpha = 0.45f)
            )
            .border(
                1.dp,
                if (selected) ElectricCyan else ElectricBlue.copy(alpha = 0.2f),
                RoundedCornerShape(12.dp)
            )
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(bounded = true, color = ElectricCyan),
                onClick = onClick
            )
            .padding(vertical = 10.dp, horizontal = 12.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge.copy(
                color = if (selected) ElectricCyan else TextSecondary
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun CompactHudPreviewCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    badge: String,
    previewText: String,
    onClick: () -> Unit,
    testTag: String
) {
    val interactionSource = remember { MutableInteractionSource() }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
            .holoGlassPanel(shape = RoundedCornerShape(18.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(bounded = true, color = ElectricCyan),
                onClick = onClick
            )
            .padding(14.dp)
            .testTag(testTag),
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
                    tint = ElectricCyan,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall.copy(
                        color = ElectricCyan,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(GlassSurface)
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = badge,
                        style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary)
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
                    contentDescription = "Open $title",
                    tint = ElectricCyan,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
        Text(
            text = previewText,
            style = MaterialTheme.typography.bodySmall.copy(color = TextPrimary),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}
