package com.example.ui.components

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.CommandLogEntry
import com.example.model.CommandPreviewStatus
import com.example.model.CommandTarget
import com.example.ui.theme.DisconnectedSlate
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.GlassSurface
import com.example.ui.theme.ObsidianPanel
import com.example.ui.theme.StandbyAmber
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.holoGlassPanel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CommandHistoryPanel(
    entries: List<CommandLogEntry>,
    selectedFilter: String,
    onFilterChanged: (String) -> Unit,
    onClearHistory: () -> Unit,
    modifier: Modifier = Modifier,
    showHeader: Boolean = true
) {
    val filteredEntries = remember(entries, selectedFilter) {
        if (selectedFilter == "ALL") entries else entries.filter { it.languageTag == selectedFilter }
    }

    Column(
        modifier = modifier
            .holoGlassPanel(shape = RoundedCornerShape(22.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (showHeader) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.History,
                        contentDescription = null,
                        tint = ElectricCyan,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "COMMAND HISTORY",
                        style = MaterialTheme.typography.titleSmall.copy(
                            color = ElectricCyan,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }

                IconButton(
                    onClick = onClearHistory,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("clear_history_button")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.DeleteOutline,
                        contentDescription = "Clear command history",
                        tint = TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("ALL", "BN", "EN", "HI").forEach { tag ->
                val active = selectedFilter == tag
                val interactionSource = remember { MutableInteractionSource() }
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .clip(RoundedCornerShape(50))
                        .background(
                            if (active) ElectricCyan.copy(alpha = 0.2f)
                            else ObsidianPanel.copy(alpha = 0.6f)
                        )
                        .border(
                            1.dp,
                            if (active) ElectricCyan else ElectricBlue.copy(alpha = 0.25f),
                            RoundedCornerShape(50)
                        )
                        .clickable(
                            interactionSource = interactionSource,
                            indication = ripple(bounded = true, color = ElectricCyan),
                            onClick = { onFilterChanged(tag) }
                        )
                        .padding(horizontal = 14.dp, vertical = 7.dp)
                        .testTag("history_filter_${tag.lowercase()}")
                ) {
                    Text(
                        text = tag,
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = if (active) ElectricCyan else TextSecondary
                        )
                    )
                }
            }
        }

        if (filteredEntries.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f, fill = true)
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.History,
                        contentDescription = null,
                        tint = TextMuted,
                        modifier = Modifier.size(36.dp)
                    )
                    Text(
                        text = "No command logs for '$selectedFilter' yet.\nTry a voice preview or send a message.",
                        style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f, fill = true)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredEntries, key = { it.id }) { entry ->
                    CommandHistoryRow(entry = entry)
                }
            }
        }
    }
}

@Composable
private fun CommandHistoryRow(entry: CommandLogEntry) {
    val statusColor = when (entry.status) {
        CommandPreviewStatus.UI_PREVIEW -> ElectricCyan
        CommandPreviewStatus.NOT_CONNECTED -> StandbyAmber
    }
    val targetLabel = when (entry.target) {
        CommandTarget.MOBILE_CORE -> "MOBILE UI"
        CommandTarget.WINDOWS_AGENT -> "PC AGENT"
        CommandTarget.GEMINI_LIVE -> "GEMINI LIVE"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(GlassSurface.copy(alpha = 0.68f))
            .border(0.8.dp, ElectricCyan.copy(alpha = 0.22f), RoundedCornerShape(14.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TagPill(text = entry.languageTag, color = ElectricCyan)
                TagPill(text = targetLabel, color = DisconnectedSlate)
            }
            Text(
                text = entry.timestamp,
                style = MaterialTheme.typography.labelSmall.copy(color = TextMuted)
            )
        }

        Text(
            text = entry.commandText,
            style = MaterialTheme.typography.bodyMedium.copy(
                color = TextPrimary,
                fontWeight = FontWeight.Medium
            ),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = entry.note,
                style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (entry.status == CommandPreviewStatus.UI_PREVIEW) "PREVIEW" else "OFFLINE",
                style = MaterialTheme.typography.labelSmall.copy(
                    color = statusColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 9.5.sp
                )
            )
        }
    }
}

@Composable
private fun TagPill(
    text: String,
    color: androidx.compose.ui.graphics.Color
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.14f))
            .border(0.6.dp, color.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
            .padding(horizontal = 7.dp, vertical = 2.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(
                color = color,
                fontWeight = FontWeight.Bold,
                fontSize = 9.5.sp
            )
        )
    }
}

@Composable
fun HistoryScreen(
    entries: List<CommandLogEntry>,
    selectedFilter: String,
    onFilterChanged: (String) -> Unit,
    onClearHistory: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        CommandHistoryPanel(
            entries = entries,
            selectedFilter = selectedFilter,
            onFilterChanged = onFilterChanged,
            onClearHistory = onClearHistory,
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 820.dp)
                .align(Alignment.CenterHorizontally)
        )
    }
}
