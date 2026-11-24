package com.example.core.architecture

import kotlinx.coroutines.flow.StateFlow

/**
 * Contract for Stage 2+ Gemini Live API bidirectional audio & multimodal integration.
 * Stage 1 provides a standby preview implementation without claiming active audio streaming.
 */
interface GeminiLiveBridge {
    val connectionStatus: StateFlow<LiveConnectionStatus>
    val protocolSpec: String

    suspend fun requestSessionPreview(languageCode: String): BridgeActionResult
    suspend fun closeSession()
}

enum class LiveConnectionStatus(val badgeText: String, val isConnected: Boolean) {
    NOT_CONNECTED("Standby • Not Connected", false),
    HANDSHAKE_READY("Architecture Ready", false),
    CONNECTED("Live Stream Active", true)
}

data class BridgeActionResult(
    val executed: Boolean,
    val statusLabel: String,
    val message: String
)

/**
 * Stage 1 architectural placeholder. Never fakes live Gemini API calls or microphone streaming.
 */
class StageOneGeminiLiveBridge : GeminiLiveBridge {
    private val _status = kotlinx.coroutines.flow.MutableStateFlow(LiveConnectionStatus.NOT_CONNECTED)
    override val connectionStatus: StateFlow<LiveConnectionStatus> = _status

    override val protocolSpec: String = "Gemini Live Multimodal Audio • Stage 2 Hook Ready"

    override suspend fun requestSessionPreview(languageCode: String): BridgeActionResult {
        return BridgeActionResult(
            executed = false,
            statusLabel = "UI Preview Only",
            message = "Gemini Live API and microphone streaming are not connected in Stage 1 ($languageCode)."
        )
    }

    override suspend fun closeSession() {
        _status.value = LiveConnectionStatus.NOT_CONNECTED
    }
}
