package com.example.core.remote

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@Keep
@IgnoreExtraProperties
data class DeviceRecord(
    @DocumentId val id: String = "",
    val userId: String = "",
    val deviceType: String = "ANDROID_MOBILE",
    val deviceName: String = "",
    val connectionStatus: String = "STANDBY",
    val pairingStatus: String = "UNPAIRED",
    val allowedActions: List<String> = emptyList(),
    @ServerTimestamp val lastConnectedAt: Timestamp? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class PairingCodeRecord(
    @DocumentId val id: String = "",
    val userId: String = "",
    val code: String = "",
    val mobileDeviceId: String = "",
    val mobileDeviceName: String = "",
    val status: String = "ACTIVE",
    val expiresAt: Timestamp? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class CommandRequestRecord(
    @DocumentId val id: String = "",
    val userId: String = "",
    val sourceDeviceId: String = "",
    val targetDeviceId: String = "",
    val actionType: String = AllowedDesktopAction.WORKSPACE_STATUS_CHECK.id,
    val summaryText: String = "",
    val targetScope: String = "",
    val confirmationRequired: Boolean = true,
    val confirmed: Boolean = false,
    val status: String = "PENDING_CONFIRMATION",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

/**
 * Strictly allowlisted desktop capabilities.
 * Arbitrary shell execution and unrestricted filesystem traversal are prohibited by design.
 */
enum class AllowedDesktopAction(
    val id: String,
    val title: String,
    val description: String,
    val requiresExplicitConfirmation: Boolean,
    val defaultScopeHint: String
) {
    WORKSPACE_STATUS_CHECK(
        id = "WORKSPACE_STATUS_CHECK",
        title = "Workspace Status Check",
        description = "Read safe Windows agent heartbeat & active desktop readiness.",
        requiresExplicitConfirmation = false,
        defaultScopeHint = "telemetry:status"
    ),
    OPEN_BROWSER_URL(
        id = "OPEN_BROWSER_URL",
        title = "Open Safe HTTPS URL",
        description = "Open an explicitly validated HTTPS URL in default browser.",
        requiresExplicitConfirmation = false,
        defaultScopeHint = "https://www.youtube.com"
    ),
    SEARCH_SCOPED_DOCUMENTS(
        id = "SEARCH_SCOPED_DOCUMENTS",
        title = "Search Scoped Documents",
        description = "Search filenames only inside the configured safe workspace folder.",
        requiresExplicitConfirmation = false,
        defaultScopeHint = "scope:documents_workspace"
    ),
    MEDIA_PLAYBACK_CONTROL(
        id = "MEDIA_PLAYBACK_CONTROL",
        title = "Media Playback Control",
        description = "Send safe Play/Pause/Next media key event to paired desktop.",
        requiresExplicitConfirmation = false,
        defaultScopeHint = "media:play_pause"
    ),
    DRAFT_MESSAGE_PREVIEW(
        id = "DRAFT_MESSAGE_PREVIEW",
        title = "Send / Dispatch Message",
        description = "Sensitive messaging action — requires explicit confirmation before queuing.",
        requiresExplicitConfirmation = true,
        defaultScopeHint = "channel:whatsapp_draft"
    ),
    ARCHIVE_SCOPED_FILE(
        id = "ARCHIVE_SCOPED_FILE",
        title = "Modify / Archive Scoped File",
        description = "Sensitive file mutation inside safe workspace — requires confirmation.",
        requiresExplicitConfirmation = true,
        defaultScopeHint = "scope:archive_note.txt"
    );

    companion object {
        fun fromId(raw: String): AllowedDesktopAction =
            entries.firstOrNull { it.id == raw } ?: WORKSPACE_STATUS_CHECK
    }
}

sealed interface RelayConnectionState {
    data object Unconfigured : RelayConnectionState
    data object Disconnected : RelayConnectionState
    data object Connecting : RelayConnectionState
    data class AuthenticatedMobileOnly(val relayHost: String) : RelayConnectionState
    data class PairedAgentOnline(val relayHost: String, val pcDeviceName: String) : RelayConnectionState
    data class Error(val message: String) : RelayConnectionState
}
