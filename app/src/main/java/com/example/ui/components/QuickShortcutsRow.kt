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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.PlayCircleOutline
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.ShortcutItem
import com.example.model.ShortcutKind
import com.example.ui.theme.DeepSpaceBlack
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.GlassSurfaceElevated
import com.example.ui.theme.ListeningAqua
import com.example.ui.theme.ObsidianPanel
import com.example.ui.theme.StandbyAmber
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.holoGlassPanel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QuickShortcutsPanel(
    shortcuts: List<ShortcutItem>,
    onShortcutClick: (ShortcutItem) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .holoGlassPanel(shape = RoundedCornerShape(20.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "QUICK APPLICATION SHORTCUTS",
                style = MaterialTheme.typography.titleSmall.copy(
                    color = ElectricCyan,
                    fontWeight = FontWeight.Bold
                )
            )
            Text(
                text = "STAGE 1 READY",
                style = MaterialTheme.typography.labelSmall.copy(
                    color = TextSecondary
                )
            )
        }

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            shortcuts.forEach { item ->
                ShortcutTile(
                    item = item,
                    onClick = { onShortcutClick(item) },
                    modifier = Modifier.weight(1f, fill = false)
                )
            }
        }
    }
}

@Composable
private fun ShortcutTile(
    item: ShortcutItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = shortcutAccentColor(item.kind)
    val icon = shortcutIcon(item.kind)
    val interactionSource = remember { MutableInteractionSource() }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(16.dp))
            .background(GlassSurfaceElevated.copy(alpha = 0.55f))
            .border(
                width = 1.dp,
                color = accent.copy(alpha = 0.35f),
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(bounded = true, color = accent),
                onClick = onClick
            )
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .testTag("shortcut_${item.kind.name.lowercase()}")
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(accent.copy(alpha = 0.16f))
                .border(1.dp, accent.copy(alpha = 0.45f), CircleShape)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = item.title,
                tint = accent,
                modifier = Modifier.size(22.dp)
            )
        }
        Text(
            text = item.title,
            style = MaterialTheme.typography.labelLarge.copy(color = TextPrimary),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = if (item.isSettings) "CONFIG" else "PREVIEW",
            style = MaterialTheme.typography.labelSmall.copy(
                color = if (item.isSettings) ElectricCyan else StandbyAmber
            )
        )
    }
}

@Composable
fun ShortcutPreviewDialog(
    shortcut: ShortcutItem,
    onDismiss: () -> Unit
) {
    val accent = shortcutAccentColor(shortcut.kind)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ObsidianPanel,
        titleContentColor = TextPrimary,
        textContentColor = TextSecondary,
        icon = {
            Icon(
                imageVector = shortcutIcon(shortcut.kind),
                contentDescription = shortcut.title,
                tint = accent,
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Text(
                text = "${shortcut.title} • Bridge Preview",
                style = MaterialTheme.typography.titleLarge.copy(color = TextPrimary)
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(StandbyAmber.copy(alpha = 0.12f))
                        .border(1.dp, StandbyAmber.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                        .padding(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Shield,
                        contentDescription = null,
                        tint = StandbyAmber,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Not Connected Yet (Stage 1 UI)",
                        style = MaterialTheme.typography.labelMedium.copy(color = StandbyAmber)
                    )
                }

                Text(
                    text = "Planned Bridge: ${shortcut.bridgeTarget}",
                    style = MaterialTheme.typography.labelMedium.copy(color = ElectricCyan)
                )
                Text(
                    text = shortcut.stageNote,
                    style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary)
                )
                Spacer(modifier = Modifier.height(2.dp))
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(
                    containerColor = ElectricCyan,
                    contentColor = DeepSpaceBlack
                ),
                modifier = Modifier.testTag("dismiss_shortcut_dialog")
            ) {
                Text("Understood", style = MaterialTheme.typography.labelLarge)
            }
        }
    )
}

private fun shortcutIcon(kind: ShortcutKind): ImageVector = when (kind) {
    ShortcutKind.WHATSAPP -> Icons.AutoMirrored.Outlined.Chat
    ShortcutKind.YOUTUBE -> Icons.Outlined.PlayCircleOutline
    ShortcutKind.CHROME -> Icons.Outlined.Language
    ShortcutKind.FILES -> Icons.Outlined.FolderOpen
    ShortcutKind.SETTINGS -> Icons.Outlined.Settings
}

private fun shortcutAccentColor(kind: ShortcutKind): Color = when (kind) {
    ShortcutKind.WHATSAPP -> ListeningAqua
    ShortcutKind.YOUTUBE -> Color(0xFFFF6B8B)
    ShortcutKind.CHROME -> ElectricCyan
    ShortcutKind.FILES -> ElectricBlue
    ShortcutKind.SETTINGS -> Color(0xFF84FFFF)
}
