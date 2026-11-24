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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.remote.AllowedDesktopAction
import com.example.core.remote.CommandRequestRecord
import com.example.core.remote.RelayConnectionState
import com.example.ui.theme.AlertCoral
import com.example.ui.theme.DeepSpaceBlack
import com.example.ui.theme.DisconnectedSlate
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.GlassSurface
import com.example.ui.theme.GlassSurfaceElevated
import com.example.ui.theme.ListeningAqua
import com.example.ui.theme.ObsidianPanel
import com.example.ui.theme.StandbyAmber
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.holoGlassPanel
import com.example.viewmodel.RemoteBridgeUiState
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ConnectionDashboardScreen(
    remoteState: RemoteBridgeUiState,
    onRelayUrlChanged: (String) -> Unit,
    onSaveAndConnectRelay: () -> Unit,
    onDisconnectRelay: () -> Unit,
    onGeneratePairingCode: () -> Unit,
    onRevokePairingCode: () -> Unit,
    onRequestAction: (AllowedDesktopAction, String) -> Unit,
    onOpenConfirmation: (CommandRequestRecord) -> Unit,
    onConfirmCommand: (CommandRequestRecord) -> Unit,
    onCancelCommand: (CommandRequestRecord) -> Unit,
    onDismissConfirmation: () -> Unit,
    onClearBanner: () -> Unit,
    modifier: Modifier = Modifier
) {
    val clipboard = LocalClipboardManager.current
    val dateFormat = remember { SimpleDateFormat("dd MMM, hh:mm a", Locale.US) }

    val pcOnline = remoteState.relayState is RelayConnectionState.PairedAgentOnline ||
        remoteState.pcDeviceRecord?.connectionStatus == "ONLINE"
    val pcDeviceName = when (val r = remoteState.relayState) {
        is RelayConnectionState.PairedAgentOnline -> r.pcDeviceName
        else -> remoteState.pcDeviceRecord?.deviceName?.takeIf { it.isNotBlank() } ?: "No Windows PC paired yet"
    }
    val lastConnectedText = remoteState.pcDeviceRecord?.lastConnectedAt?.toDate()?.let { dateFormat.format(it) }
        ?: remoteState.mobileDeviceRecord?.lastConnectedAt?.toDate()?.let { "Mobile: ${dateFormat.format(it)} • PC: Never" }
        ?: "PC: Never connected"

    val pairingStatusLabel = when {
        pcOnline -> "PAIRED • PC ONLINE"
        remoteState.pcDeviceRecord != null -> "PAIRED • WAITING FOR PC AGENT"
        remoteState.activePairingCode != null -> "PAIRING CODE ACTIVE (${formatSeconds(remoteState.pairingRemainingSeconds)})"
        else -> "UNPAIRED"
    }

    var customScopeInput by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 820.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            remoteState.statusBannerMessage?.let { msg ->
                BannerNotice(text = msg, color = ElectricCyan, onDismiss = onClearBanner)
            }
            remoteState.errorMessage?.let { err ->
                BannerNotice(text = err, color = AlertCoral, onDismiss = onClearBanner)
            }

            // 1. CONNECTION DASHBOARD (5 Required Telemetry Fields)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .holoGlassPanel(shape = RoundedCornerShape(22.dp), elevated = true)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "CONNECTION DASHBOARD",
                        style = MaterialTheme.typography.titleSmall.copy(
                            color = ElectricCyan,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Text(
                        text = "HTTPS / WSS",
                        style = MaterialTheme.typography.labelSmall.copy(color = ListeningAqua)
                    )
                }

                TelemetryMetricRow(
                    icon = Icons.Outlined.PhoneAndroid,
                    label = "Mobile Connection Status",
                    value = when (val r = remoteState.relayState) {
                        is RelayConnectionState.PairedAgentOnline -> "Authenticated & Relay Connected (${r.relayHost})"
                        is RelayConnectionState.AuthenticatedMobileOnly -> "Authenticated • Relay Connected (${r.relayHost})"
                        is RelayConnectionState.Connecting -> "Connecting over TLS WSS..."
                        is RelayConnectionState.Error -> "Session Active • Relay Error: ${r.message}"
                        RelayConnectionState.Disconnected -> "Authenticated • WSS Disconnected"
                        RelayConnectionState.Unconfigured -> "Authenticated Session • Relay URL Not Set"
                    },
                    accentColor = ElectricCyan
                )

                TelemetryMetricRow(
                    icon = Icons.Outlined.Computer,
                    label = "PC Connection Status",
                    value = if (pcOnline) "ONLINE • Authenticated Outbound Agent" else "OFFLINE • No Authenticated Windows Agent",
                    accentColor = if (pcOnline) ListeningAqua else DisconnectedSlate
                )

                TelemetryMetricRow(
                    icon = Icons.Outlined.CloudSync,
                    label = "Last Connected Time",
                    value = lastConnectedText,
                    accentColor = TextSecondary
                )

                TelemetryMetricRow(
                    icon = Icons.Outlined.Shield,
                    label = "Device Name & Unique IDs",
                    value = "Mobile: ${remoteState.mobileDeviceName} (${remoteState.mobileDeviceId.ifBlank { "initializing..." }})\nPC: $pcDeviceName",
                    accentColor = ElectricCyan
                )

                TelemetryMetricRow(
                    icon = Icons.Outlined.Key,
                    label = "Pairing Status",
                    value = pairingStatusLabel,
                    accentColor = if (pcOnline) ListeningAqua else StandbyAmber
                )
            }

            // 2. SECURE DEVICE PAIRING (Temporary Expiring Code + Outbound WSS Architecture)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .holoGlassPanel(shape = RoundedCornerShape(22.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "SECURE DEVICE PAIRING",
                    style = MaterialTheme.typography.titleSmall.copy(
                        color = ElectricCyan,
                        fontWeight = FontWeight.Bold
                    )
                )
                Text(
                    text = "No Windows ports are exposed to the internet. Generate a 5-minute pairing code below, then start your authenticated Windows desktop agent so it initiates an outbound WSS connection.",
                    style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                )

                val activeCode = remoteState.activePairingCode
                if (activeCode != null && remoteState.pairingRemainingSeconds > 0L) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(GlassSurfaceElevated.copy(alpha = 0.72f))
                            .border(1.dp, ElectricCyan.copy(alpha = 0.55f), RoundedCornerShape(16.dp))
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = activeCode.code,
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    color = ElectricCyan,
                                    letterSpacing = 3.sp
                                ),
                                modifier = Modifier.testTag("active_pairing_code")
                            )
                            Text(
                                text = "Expires in ${formatSeconds(remoteState.pairingRemainingSeconds)}",
                                style = MaterialTheme.typography.labelSmall.copy(color = StandbyAmber)
                            )
                        }
                        Row {
                            IconButton(
                                onClick = { clipboard.setText(AnnotatedString(activeCode.code)) },
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.ContentCopy,
                                    contentDescription = "Copy pairing code",
                                    tint = ElectricCyan
                                )
                            }
                            OutlinedButton(
                                onClick = onRevokePairingCode,
                                modifier = Modifier.testTag("revoke_pairing_code_button")
                            ) {
                                Text("Revoke", color = AlertCoral)
                            }
                        }
                    }
                }

                Button(
                    onClick = onGeneratePairingCode,
                    enabled = !remoteState.isBusy,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ElectricCyan,
                        contentColor = DeepSpaceBlack
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("generate_pairing_code_button")
                ) {
                    Text(
                        text = if (activeCode != null) "Rotate Temporary Pairing Code" else "Generate 5-Min Pairing Code",
                        style = MaterialTheme.typography.labelLarge
                    )
                }

                // Cloud Run HTTPS / WSS Relay Endpoint Config
                OutlinedTextField(
                    value = remoteState.relayHttpsUrlDraft,
                    onValueChange = onRelayUrlChanged,
                    label = { Text("Cloud Run Relay URL (https://...)") },
                    placeholder = { Text("https://diya-relay-xxxxx.run.app") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ElectricCyan,
                        unfocusedBorderColor = ElectricBlue.copy(alpha = 0.4f),
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedLabelColor = ElectricCyan,
                        unfocusedLabelColor = TextSecondary
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("relay_url_input")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onSaveAndConnectRelay,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ElectricBlue,
                            contentColor = TextPrimary
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("connect_relay_button")
                    ) {
                        Text("Connect WSS Relay")
                    }
                    OutlinedButton(
                        onClick = onDisconnectRelay,
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("disconnect_relay_button")
                    ) {
                        Text("Disconnect", color = TextSecondary)
                    }
                }
            }

            // 3. ALLOWLISTED COMMAND REQUEST & CONFIRMATION SYSTEM
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .holoGlassPanel(shape = RoundedCornerShape(22.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "ALLOWLISTED COMMAND GATE",
                        style = MaterialTheme.typography.titleSmall.copy(
                            color = ElectricCyan,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Text(
                        text = "NO SHELL EXEC",
                        style = MaterialTheme.typography.labelSmall.copy(color = ListeningAqua)
                    )
                }

                Text(
                    text = "Only explicitly allowlisted actions can be requested. Sensitive actions (messaging, file mutations) require confirmation before queuing.",
                    style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                )

                OutlinedTextField(
                    value = customScopeInput,
                    onValueChange = { customScopeInput = it },
                    label = { Text("Optional Safe Scope / HTTPS URL") },
                    placeholder = { Text("https://www.youtube.com or scope:workspace") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ElectricCyan,
                        unfocusedBorderColor = ElectricBlue.copy(alpha = 0.35f),
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("command_scope_input")
                )

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AllowedDesktopAction.entries.forEach { action ->
                        val interactionSource = remember { MutableInteractionSource() }
                        val accent = if (action.requiresExplicitConfirmation) StandbyAmber else ElectricCyan
                        Column(
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .clip(RoundedCornerShape(14.dp))
                                .background(GlassSurface.copy(alpha = 0.72f))
                                .border(0.9.dp, accent.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
                                .clickable(
                                    interactionSource = interactionSource,
                                    indication = ripple(bounded = true, color = accent),
                                    onClick = {
                                        onRequestAction(
                                            action,
                                            customScopeInput.ifBlank { action.defaultScopeHint }
                                        )
                                    }
                                )
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                                .testTag("action_chip_${action.id.lowercase()}")
                        ) {
                            Text(
                                text = action.title,
                                style = MaterialTheme.typography.labelLarge.copy(color = TextPrimary)
                            )
                            Text(
                                text = if (action.requiresExplicitConfirmation) "CONFIRMATION REQUIRED" else "ALLOWLISTED",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = accent,
                                    fontSize = 9.5.sp
                                )
                            )
                        }
                    }
                }

                // Recent Command Requests from Firestore
                if (remoteState.commandRequests.isNotEmpty()) {
                    Text(
                        text = "RECENT COMMAND REQUESTS",
                        style = MaterialTheme.typography.labelMedium.copy(color = ElectricCyan)
                    )
                    remoteState.commandRequests.take(8).forEach { cmd ->
                        CommandRequestRow(
                            command = cmd,
                            onConfirm = { onOpenConfirmation(cmd) },
                            onCancel = { onCancelCommand(cmd) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
        }
    }

    remoteState.pendingConfirmationDialog?.let { pending ->
        CommandConfirmationDialog(
            command = pending,
            pcOnline = pcOnline,
            onConfirm = { onConfirmCommand(pending) },
            onCancel = { onCancelCommand(pending) },
            onDismiss = onDismissConfirmation
        )
    }
}

@Composable
private fun TelemetryMetricRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    accentColor: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(ObsidianPanel.copy(alpha = 0.72f))
            .border(0.8.dp, accentColor.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(accentColor.copy(alpha = 0.14f))
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary)
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = TextPrimary,
                    fontWeight = FontWeight.Medium
                )
            )
        }
    }
}

@Composable
private fun CommandRequestRow(
    command: CommandRequestRecord,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    val statusColor = when (command.status) {
        "PENDING_CONFIRMATION" -> StandbyAmber
        "APPROVED_QUEUED", "EXECUTED" -> ListeningAqua
        "CANCELLED", "REJECTED" -> AlertCoral
        else -> DisconnectedSlate
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(ObsidianPanel.copy(alpha = 0.72f))
            .border(0.8.dp, statusColor.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = command.actionType,
                style = MaterialTheme.typography.labelMedium.copy(color = ElectricCyan),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = command.status,
                style = MaterialTheme.typography.labelSmall.copy(
                    color = statusColor,
                    fontWeight = FontWeight.Bold
                )
            )
        }
        Text(
            text = "${command.summaryText} • ${command.targetScope}",
            style = MaterialTheme.typography.bodySmall.copy(color = TextPrimary),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        if (command.status == "PENDING_CONFIRMATION") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onConfirm,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ElectricCyan,
                        contentColor = DeepSpaceBlack
                    ),
                    modifier = Modifier.height(40.dp)
                ) {
                    Icon(Icons.Outlined.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Review & Confirm")
                }
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.height(40.dp)
                ) {
                    Text("Cancel", color = AlertCoral)
                }
            }
        }
    }
}

@Composable
private fun CommandConfirmationDialog(
    command: CommandRequestRecord,
    pcOnline: Boolean,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ObsidianPanel,
        icon = {
            Icon(
                imageVector = Icons.Outlined.WarningAmber,
                contentDescription = null,
                tint = StandbyAmber,
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Text(
                text = "Confirm Sensitive Action",
                style = MaterialTheme.typography.titleLarge.copy(color = TextPrimary)
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Action: ${command.actionType}",
                    style = MaterialTheme.typography.labelMedium.copy(color = ElectricCyan)
                )
                Text(
                    text = "Scope: ${command.targetScope}",
                    style = MaterialTheme.typography.bodySmall.copy(color = TextPrimary)
                )
                Text(
                    text = if (pcOnline) {
                        "Your paired Windows agent is ONLINE. Confirming will dispatch this allowlisted action over TLS WSS."
                    } else {
                        "Your Windows PC agent is currently OFFLINE. Confirming will record approval safely as PC_OFFLINE_NOT_SENT without executing."
                    },
                    style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = ElectricCyan,
                    contentColor = DeepSpaceBlack
                ),
                modifier = Modifier.testTag("confirm_command_button")
            ) {
                Icon(Icons.Outlined.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Approve Action")
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.testTag("cancel_command_button")
            ) {
                Text("Cancel Request", color = AlertCoral)
            }
        }
    )
}

@Composable
private fun BannerNotice(
    text: String,
    color: Color,
    onDismiss: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(color.copy(alpha = 0.14f))
            .border(1.dp, color.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall.copy(color = TextPrimary),
            modifier = Modifier.weight(1f)
        )
        IconButton(
            onClick = onDismiss,
            modifier = Modifier.minimumInteractiveComponentSize()
        ) {
            Icon(
                imageVector = Icons.Outlined.Close,
                contentDescription = "Dismiss notice",
                tint = TextSecondary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

private fun formatSeconds(totalSeconds: Long): String {
    val clamped = totalSeconds.coerceAtLeast(0L)
    val mins = clamped / 60L
    val secs = clamped % 60L
    return "%02d:%02d".format(mins, secs)
}
