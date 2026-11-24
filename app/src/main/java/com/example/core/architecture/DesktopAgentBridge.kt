package com.example.core.architecture

import kotlinx.coroutines.flow.StateFlow

/**
 * Contract for a future secure Windows Desktop Agent (mTLS / encrypted WebSocket pairing).
 * Stage 1 explicitly exposes disconnected status and provides zero fake PC controls.
 */
interface DesktopAgentBridge {
    val agentStatus: StateFlow<PcAgentStatus>
    val securityProfile: String

    suspend fun inspectShortcutCapability(targetId: String): BridgeActionResult
}

enum class PcAgentStatus(val label: String, val isConnected: Boolean) {
    DISCONNECTED("PC Disconnected", false),
    PAIRING_REQUIRED("Pairing Required", false),
    CONNECTED("PC Connected", true)
}

class StageOneDesktopAgentBridge : DesktopAgentBridge {
    private val _agentStatus = kotlinx.coroutines.flow.MutableStateFlow(PcAgentStatus.DISCONNECTED)
    override val agentStatus: StateFlow<PcAgentStatus> = _agentStatus

    override val securityProfile: String = "Zero-Trust Device Pairing • mTLS / Encrypted Relay (Stage 3)"

    override suspend fun inspectShortcutCapability(targetId: String): BridgeActionResult {
        return BridgeActionResult(
            executed = false,
            statusLabel = "Not Connected",
            message = "Target '$targetId' requires device integration or Windows Desktop Agent pairing in a later stage."
        )
    }
}
