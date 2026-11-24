package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.architecture.DesktopAgentBridge
import com.example.core.architecture.GeminiLiveBridge
import com.example.core.architecture.LiveConnectionStatus
import com.example.core.architecture.PcAgentStatus
import com.example.core.architecture.StageOneDesktopAgentBridge
import com.example.core.architecture.StageOneGeminiLiveBridge
import com.example.core.remote.AllowedDesktopAction
import com.example.core.remote.CommandRequestRecord
import com.example.core.remote.DeviceIdentityStore
import com.example.core.remote.DeviceRecord
import com.example.core.remote.DiyaRemoteRepository
import com.example.core.remote.PairingCodeRecord
import com.example.core.remote.RelayConnectionState
import com.example.core.remote.SecureWssRelayClient
import com.example.model.AppLanguage
import com.example.model.ChatMessage
import com.example.model.CommandLogEntry
import com.example.model.CommandPreviewStatus
import com.example.model.CommandTarget
import com.example.model.DiyaCopy
import com.example.model.DiyaSettings
import com.example.model.DiyaUiState
import com.example.model.SenderRole
import com.example.model.ShortcutItem
import com.example.model.ShortcutKind
import com.example.model.VoiceState
import com.google.firebase.firestore.FirebaseFirestoreException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

data class RemoteBridgeUiState(
    val userId: String = "",
    val userEmail: String = "",
    val mobileDeviceId: String = "",
    val mobileDeviceName: String = "Android Mobile",
    val relayHttpsUrlDraft: String = "",
    val configuredRelayUrl: String = "",
    val relayState: RelayConnectionState = RelayConnectionState.Unconfigured,
    val mobileDeviceRecord: DeviceRecord? = null,
    val pcDeviceRecord: DeviceRecord? = null,
    val activePairingCode: PairingCodeRecord? = null,
    val pairingRemainingSeconds: Long = 0L,
    val commandRequests: List<CommandRequestRecord> = emptyList(),
    val pendingConfirmationDialog: CommandRequestRecord? = null,
    val statusBannerMessage: String? = null,
    val errorMessage: String? = null,
    val isBusy: Boolean = false
)

class DiyaViewModel(
    private val currentUserId: String = "",
    private val currentUserEmail: String = "",
    private val remoteRepository: DiyaRemoteRepository? = null,
    private val identityStore: DeviceIdentityStore? = null,
    private val relayClient: SecureWssRelayClient? = null,
    private val geminiLiveBridge: GeminiLiveBridge = StageOneGeminiLiveBridge(),
    private val desktopAgentBridge: DesktopAgentBridge = StageOneDesktopAgentBridge()
) : ViewModel() {

    private val timeFormatter = SimpleDateFormat("HH:mm:ss", Locale.US)
    private val shortTimeFormatter = SimpleDateFormat("hh:mm a", Locale.US)
    private val dateFormatter = SimpleDateFormat("EEE, dd MMM yyyy", Locale.US)

    private val _uiState = MutableStateFlow(buildInitialState())
    val uiState: StateFlow<DiyaUiState> = _uiState.asStateFlow()

    private val _remoteState = MutableStateFlow(
        RemoteBridgeUiState(
            userId = currentUserId,
            userEmail = currentUserEmail
        )
    )
    val remoteState: StateFlow<RemoteBridgeUiState> = _remoteState.asStateFlow()

    private var voiceCycleJob: Job? = null

    init {
        startLiveClock()
        startWaveformAnimator()
        observeBridges()
        if (currentUserId.isNotBlank() && remoteRepository != null && identityStore != null) {
            initializeAuthenticatedRemoteBridge()
        }
    }

    private fun buildInitialState(): DiyaUiState {
        val now = Date()
        val lang = AppLanguage.BENGALI
        return DiyaUiState(
            currentTimeText = timeFormatter.format(now),
            currentDateText = dateFormatter.format(now).uppercase(Locale.US),
            shortcuts = defaultShortcuts(),
            messages = listOf(
                ChatMessage(
                    id = "msg_welcome_1",
                    role = SenderRole.DIYA,
                    text = DiyaCopy.initialGreeting(lang),
                    languageTag = lang.shortTag,
                    timestamp = shortTimeFormatter.format(now),
                    isPreviewTag = true
                ),
                ChatMessage(
                    id = "msg_system_note",
                    role = SenderRole.SYSTEM_NOTE,
                    text = if (currentUserId.isNotBlank()) {
                        "Authenticated session active • Secure pairing & allowlisted command bridge ready. Windows PC remains OFFLINE until an authenticated outbound agent connects."
                    } else {
                        "Stage 1 Architecture Active • Gemini Live API & Windows Desktop Agent are in standby (not connected)."
                    },
                    languageTag = "SYS",
                    timestamp = shortTimeFormatter.format(now),
                    isPreviewTag = false
                )
            ),
            commandHistory = listOf(
                CommandLogEntry(
                    id = "cmd_1",
                    commandText = "সুপ্রভাত দিয়া, সিস্টেম স্ট্যাটাস দেখাও",
                    languageTag = "BN",
                    target = CommandTarget.MOBILE_CORE,
                    status = CommandPreviewStatus.UI_PREVIEW,
                    timestamp = "09:12 AM",
                    note = "Multilingual UI telemetry check (Preview)"
                ),
                CommandLogEntry(
                    id = "cmd_2",
                    commandText = "Check Windows Desktop Agent handshake",
                    languageTag = "EN",
                    target = CommandTarget.WINDOWS_AGENT,
                    status = CommandPreviewStatus.NOT_CONNECTED,
                    timestamp = "09:05 AM",
                    note = "Waiting for authenticated outbound PC agent"
                )
            )
        )
    }

    private fun initializeAuthenticatedRemoteBridge() {
        val repo = remoteRepository ?: return
        val store = identityStore ?: return

        viewModelScope.launch {
            val identity = store.getOrCreateIdentity()
            _remoteState.update {
                it.copy(
                    mobileDeviceId = identity.mobileDeviceId,
                    mobileDeviceName = identity.mobileDeviceName,
                    relayHttpsUrlDraft = identity.relayHttpsBaseUrl,
                    configuredRelayUrl = identity.relayHttpsBaseUrl
                )
            }
            repo.upsertMobileDeviceSession(
                userId = currentUserId,
                mobileDeviceId = identity.mobileDeviceId,
                mobileDeviceName = identity.mobileDeviceName,
                pairingStatus = "UNPAIRED"
            )
        }

        // Observe Firestore Devices with two-tier CancellationException unwrapping
        viewModelScope.launch {
            repo.observeDevices(currentUserId)
                .catch { error ->
                    val fsErr = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fsErr == null && error is CancellationException) throw error
                    val root = fsErr ?: error
                    _remoteState.update { it.copy(errorMessage = root.message ?: "Failed to observe devices.") }
                }
                .collect { devices ->
                    val mobile = devices.firstOrNull { it.deviceType == "ANDROID_MOBILE" }
                    val pc = devices.firstOrNull { it.deviceType == "WINDOWS_DESKTOP" }
                    val pcIsTrulyOnline = pc?.connectionStatus == "ONLINE"
                    _remoteState.update {
                        it.copy(
                            mobileDeviceRecord = mobile,
                            pcDeviceRecord = pc
                        )
                    }
                    _uiState.update {
                        it.copy(
                            pcAgentStatus = if (pcIsTrulyOnline) PcAgentStatus.CONNECTED else PcAgentStatus.DISCONNECTED
                        )
                    }
                }
        }

        // Observe Firestore Pairing Codes
        viewModelScope.launch {
            repo.observePairingCodes(currentUserId)
                .catch { error ->
                    val fsErr = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fsErr == null && error is CancellationException) throw error
                    val root = fsErr ?: error
                    _remoteState.update { it.copy(errorMessage = root.message ?: "Failed to observe pairing codes.") }
                }
                .collect { codes ->
                    val nowMs = System.currentTimeMillis()
                    val active = codes.firstOrNull {
                        it.status == "ACTIVE" && (it.expiresAt?.toDate()?.time ?: 0L) > nowMs
                    }
                    _remoteState.update { it.copy(activePairingCode = active) }
                }
        }

        // Observe Firestore Command Requests
        viewModelScope.launch {
            repo.observeCommandRequests(currentUserId)
                .catch { error ->
                    val fsErr = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fsErr == null && error is CancellationException) throw error
                    val root = fsErr ?: error
                    _remoteState.update { it.copy(errorMessage = root.message ?: "Failed to observe command requests.") }
                }
                .collect { commands ->
                    _remoteState.update { it.copy(commandRequests = commands) }
                }
        }

        // Observe WSS relay state
        relayClient?.let { client ->
            viewModelScope.launch {
                client.connectionState.collect { relayState ->
                    _remoteState.update { it.copy(relayState = relayState) }
                    if (relayState is RelayConnectionState.PairedAgentOnline) {
                        _uiState.update { it.copy(pcAgentStatus = PcAgentStatus.CONNECTED) }
                    } else if (_remoteState.value.pcDeviceRecord?.connectionStatus != "ONLINE") {
                        _uiState.update { it.copy(pcAgentStatus = PcAgentStatus.DISCONNECTED) }
                    }
                }
            }
        }

        // Pairing TTL countdown ticker
        viewModelScope.launch {
            while (true) {
                val active = _remoteState.value.activePairingCode
                val expiresMs = active?.expiresAt?.toDate()?.time ?: 0L
                val remaining = ((expiresMs - System.currentTimeMillis()) / 1000L).coerceAtLeast(0L)
                _remoteState.update { it.copy(pairingRemainingSeconds = remaining) }
                delay(1000L)
            }
        }
    }

    fun onRelayUrlDraftChanged(url: String) {
        _remoteState.update { it.copy(relayHttpsUrlDraft = url, errorMessage = null) }
    }

    fun saveAndConnectRelay() {
        val store = identityStore ?: return
        val client = relayClient ?: return
        val state = _remoteState.value
        viewModelScope.launch {
            _remoteState.update { it.copy(isBusy = true, errorMessage = null) }
            val saved = store.updateRelayUrl(state.relayHttpsUrlDraft)
            saved.fold(
                onSuccess = { url ->
                    _remoteState.update {
                        it.copy(
                            configuredRelayUrl = url,
                            isBusy = false,
                            statusBannerMessage = if (url.isEmpty()) "Relay URL cleared." else "Connecting over TLS WSS..."
                        )
                    }
                    if (url.isNotEmpty()) {
                        client.connect(url, state.mobileDeviceId, state.mobileDeviceName)
                    } else {
                        client.disconnect()
                    }
                },
                onFailure = { err ->
                    _remoteState.update {
                        it.copy(isBusy = false, errorMessage = err.message ?: "Invalid HTTPS relay URL.")
                    }
                }
            )
        }
    }

    fun disconnectRelay() {
        relayClient?.disconnect()
        _remoteState.update { it.copy(statusBannerMessage = "WSS relay disconnected.") }
    }

    fun updateMobileDeviceName(newName: String) {
        val store = identityStore ?: return
        val repo = remoteRepository ?: return
        if (currentUserId.isBlank()) return
        viewModelScope.launch {
            store.updateDeviceName(newName).onSuccess {
                val clean = newName.trim().take(80)
                _remoteState.update { it.copy(mobileDeviceName = clean) }
                repo.upsertMobileDeviceSession(
                    userId = currentUserId,
                    mobileDeviceId = _remoteState.value.mobileDeviceId,
                    mobileDeviceName = clean,
                    pairingStatus = _remoteState.value.mobileDeviceRecord?.pairingStatus ?: "UNPAIRED"
                )
            }.onFailure { err ->
                _remoteState.update { it.copy(errorMessage = err.message) }
            }
        }
    }

    fun generatePairingCode() {
        val repo = remoteRepository ?: return
        if (currentUserId.isBlank()) return
        val state = _remoteState.value
        viewModelScope.launch {
            _remoteState.update { it.copy(isBusy = true, errorMessage = null) }
            val result = repo.generateTemporaryPairingCode(
                userId = currentUserId,
                mobileDeviceId = state.mobileDeviceId,
                mobileDeviceName = state.mobileDeviceName
            )
            result.fold(
                onSuccess = { record ->
                    _remoteState.update {
                        it.copy(
                            isBusy = false,
                            activePairingCode = record,
                            statusBannerMessage = "Temporary 5-minute pairing code generated. Enter it in your Windows agent."
                        )
                    }
                },
                onFailure = { err ->
                    _remoteState.update {
                        it.copy(isBusy = false, errorMessage = err.localizedMessage ?: "Failed to generate pairing code.")
                    }
                }
            )
        }
    }

    fun revokeActivePairingCode() {
        val repo = remoteRepository ?: return
        val active = _remoteState.value.activePairingCode ?: return
        if (currentUserId.isBlank() || active.id.isBlank()) return
        viewModelScope.launch {
            repo.revokePairingCode(currentUserId, active.id)
                .onSuccess {
                    _remoteState.update { it.copy(statusBannerMessage = "Pairing code revoked.") }
                }
                .onFailure { err ->
                    _remoteState.update { it.copy(errorMessage = err.localizedMessage ?: "Failed to revoke code.") }
                }
        }
    }

    fun requestDesktopAction(
        action: AllowedDesktopAction,
        customScope: String = action.defaultScopeHint
    ) {
        val repo = remoteRepository ?: return
        if (currentUserId.isBlank()) return
        val state = _remoteState.value
        val pcOnline = state.relayState is RelayConnectionState.PairedAgentOnline ||
            state.pcDeviceRecord?.connectionStatus == "ONLINE"
        val targetPcId = state.pcDeviceRecord?.id?.takeIf { it.isNotBlank() }

        viewModelScope.launch {
            _remoteState.update { it.copy(isBusy = true, errorMessage = null) }
            val res = repo.submitCommandRequest(
                userId = currentUserId,
                sourceDeviceId = state.mobileDeviceId,
                targetDeviceId = targetPcId,
                action = action,
                summaryText = action.title,
                rawScope = customScope,
                isPcOnlineOverWss = pcOnline
            )
            res.fold(
                onSuccess = { record ->
                    _remoteState.update {
                        it.copy(
                            isBusy = false,
                            pendingConfirmationDialog = if (record.confirmationRequired && !record.confirmed) record else null,
                            statusBannerMessage = when (record.status) {
                                "PENDING_CONFIRMATION" -> "Explicit confirmation required before dispatching '${action.title}'."
                                "APPROVED_QUEUED" -> "Allowlisted action queued to authenticated PC agent."
                                else -> "PC agent is offline — command logged as PC_OFFLINE_NOT_SENT (not executed)."
                            }
                        )
                    }
                    if (record.status == "APPROVED_QUEUED") {
                        relayClient?.sendApprovedCommand(record)
                    }
                },
                onFailure = { err ->
                    _remoteState.update {
                        it.copy(isBusy = false, errorMessage = err.localizedMessage ?: "Command validation failed.")
                    }
                }
            )
        }
    }

    fun openConfirmationDialog(command: CommandRequestRecord) {
        _remoteState.update { it.copy(pendingConfirmationDialog = command) }
    }

    fun dismissConfirmationDialog() {
        _remoteState.update { it.copy(pendingConfirmationDialog = null) }
    }

    fun confirmPendingCommand(command: CommandRequestRecord) {
        val repo = remoteRepository ?: return
        if (currentUserId.isBlank() || command.id.isBlank()) return
        val state = _remoteState.value
        val pcOnline = state.relayState is RelayConnectionState.PairedAgentOnline ||
            state.pcDeviceRecord?.connectionStatus == "ONLINE"
        val targetPcId = state.pcDeviceRecord?.id?.takeIf { it.isNotBlank() }

        viewModelScope.launch {
            val res = repo.confirmCommandRequest(
                userId = currentUserId,
                commandId = command.id,
                targetDeviceId = targetPcId,
                isPcOnlineOverWss = pcOnline
            )
            res.fold(
                onSuccess = { nextStatus ->
                    if (nextStatus == "APPROVED_QUEUED") {
                        relayClient?.sendApprovedCommand(command.copy(confirmed = true, status = nextStatus))
                    }
                    _remoteState.update {
                        it.copy(
                            pendingConfirmationDialog = null,
                            statusBannerMessage = if (nextStatus == "APPROVED_QUEUED") {
                                "Confirmed and dispatched via encrypted WSS."
                            } else {
                                "Confirmed, but Windows PC is offline — marked PC_OFFLINE_NOT_SENT."
                            }
                        )
                    }
                },
                onFailure = { err ->
                    _remoteState.update {
                        it.copy(
                            pendingConfirmationDialog = null,
                            errorMessage = err.localizedMessage ?: "Confirmation failed."
                        )
                    }
                }
            )
        }
    }

    fun cancelPendingCommand(command: CommandRequestRecord) {
        val repo = remoteRepository ?: return
        if (currentUserId.isBlank() || command.id.isBlank()) return
        viewModelScope.launch {
            repo.cancelCommandRequest(currentUserId, command.id)
                .onSuccess {
                    _remoteState.update {
                        it.copy(
                            pendingConfirmationDialog = null,
                            statusBannerMessage = "Command cancelled safely."
                        )
                    }
                }
                .onFailure { err ->
                    _remoteState.update {
                        it.copy(errorMessage = err.localizedMessage ?: "Failed to cancel command.")
                    }
                }
        }
    }

    fun clearRemoteBanner() {
        _remoteState.update { it.copy(statusBannerMessage = null, errorMessage = null) }
    }

    private fun defaultShortcuts(): List<ShortcutItem> = listOf(
        ShortcutItem(
            kind = ShortcutKind.WHATSAPP,
            title = "WhatsApp",
            subtitle = "Messaging Bridge",
            bridgeTarget = "Confirmation-Gated Draft / Share",
            stageNote = "Sensitive messaging requires explicit user confirmation and an authenticated paired agent."
        ),
        ShortcutItem(
            kind = ShortcutKind.YOUTUBE,
            title = "YouTube",
            subtitle = "Media Stream",
            bridgeTarget = "Allowlisted HTTPS Browser Action",
            stageNote = "Opens only verified HTTPS URLs via the paired outbound Windows agent when online."
        ),
        ShortcutItem(
            kind = ShortcutKind.CHROME,
            title = "Chrome",
            subtitle = "Web Workspace",
            bridgeTarget = "Outbound WSS Desktop Agent",
            stageNote = "Requires an authenticated Windows desktop agent connected outbound over TLS WSS."
        ),
        ShortcutItem(
            kind = ShortcutKind.FILES,
            title = "Files",
            subtitle = "Scoped Vault",
            bridgeTarget = "Scoped Workspace Index Only",
            stageNote = "Unrestricted filesystem access is prohibited. Only user-approved scoped workspace queries are allowed."
        ),
        ShortcutItem(
            kind = ShortcutKind.SETTINGS,
            title = "Settings",
            subtitle = "Core Config",
            bridgeTarget = "Local & Session Config",
            stageNote = "Configure language, HUD glow, voice cadence, and manage your authenticated session.",
            isSettings = true
        )
    )

    private fun startLiveClock() {
        viewModelScope.launch {
            while (true) {
                val now = Date()
                _uiState.update {
                    it.copy(
                        currentTimeText = timeFormatter.format(now),
                        currentDateText = dateFormatter.format(now).uppercase(Locale.US)
                    )
                }
                delay(1000L)
            }
        }
    }

    private fun observeBridges() {
        viewModelScope.launch {
            geminiLiveBridge.connectionStatus.collect { status ->
                _uiState.update { it.copy(geminiLiveStatus = status) }
            }
        }
        viewModelScope.launch {
            desktopAgentBridge.agentStatus.collect { status ->
                if (currentUserId.isBlank()) {
                    _uiState.update { it.copy(pcAgentStatus = status) }
                }
            }
        }
    }

    private fun startWaveformAnimator() {
        viewModelScope.launch {
            var phase = 0f
            while (true) {
                val state = _uiState.value
                val barCount = if (state.settings.compactWaveformDensity) 24 else 36
                val reduced = state.settings.reducedMotion

                val bars = List(barCount) { idx ->
                    val norm = idx.toFloat() / barCount.coerceAtLeast(1)
                    val envelope = sin(norm * PI).toFloat().coerceAtLeast(0.15f)
                    when (state.voiceState) {
                        VoiceState.IDLE -> {
                            val idleWave = if (reduced) 0.16f else 0.14f + 0.09f * abs(sin(phase * 0.6f + idx * 0.35f))
                            (idleWave * envelope).coerceIn(0.08f, 0.35f)
                        }
                        VoiceState.LISTENING -> {
                            val amp = 0.32f + 0.58f * abs(sin(phase * 1.9f + idx * 0.45f) * sin(phase * 0.8f + idx * 0.2f))
                            (amp * envelope).coerceIn(0.14f, 0.96f)
                        }
                        VoiceState.THINKING -> {
                            val sweep = abs(sin(phase * 1.3f - idx * 0.28f))
                            (0.22f + 0.48f * sweep * envelope).coerceIn(0.14f, 0.78f)
                        }
                        VoiceState.SPEAKING -> {
                            val cadence = 0.45f + state.settings.voiceResponseCadence * 0.45f
                            val wave = abs(sin(phase * 2.5f + idx * 0.55f)) * 0.65f +
                                abs(sin(phase * 1.1f - idx * 0.3f)) * 0.35f
                            (wave * cadence * envelope).coerceIn(0.16f, 1.0f)
                        }
                    }
                }
                _uiState.update { it.copy(waveformBars = bars) }
                phase += if (reduced) 0.07f else 0.18f
                delay(if (reduced) 110L else 55L)
            }
        }
    }

    fun onMicButtonTapped() {
        val current = _uiState.value.voiceState
        if (current == VoiceState.IDLE) {
            runVoicePreviewSequence()
        } else {
            voiceCycleJob?.cancel()
            _uiState.update { it.copy(voiceState = VoiceState.IDLE) }
        }
    }

    fun setVoiceStatePreview(targetState: VoiceState) {
        voiceCycleJob?.cancel()
        _uiState.update { it.copy(voiceState = targetState) }
    }

    private fun runVoicePreviewSequence() {
        voiceCycleJob?.cancel()
        voiceCycleJob = viewModelScope.launch {
            _uiState.update { it.copy(voiceState = VoiceState.LISTENING) }
            delay(2400L)
            _uiState.update { it.copy(voiceState = VoiceState.THINKING) }
            delay(1600L)
            _uiState.update { it.copy(voiceState = VoiceState.SPEAKING) }
            val result = geminiLiveBridge.requestSessionPreview(_uiState.value.settings.primaryLanguage.code)
            appendSystemPreviewNotice(result.message)
            delay(2600L)
            _uiState.update { it.copy(voiceState = VoiceState.IDLE) }
        }
    }

    private fun appendSystemPreviewNotice(note: String) {
        val now = Date()
        val entry = CommandLogEntry(
            id = "cmd_${System.currentTimeMillis()}",
            commandText = "Voice UI Preview Cycle",
            languageTag = _uiState.value.settings.primaryLanguage.shortTag,
            target = CommandTarget.GEMINI_LIVE,
            status = CommandPreviewStatus.NOT_CONNECTED,
            timestamp = shortTimeFormatter.format(now),
            note = note
        )
        _uiState.update { state ->
            state.copy(commandHistory = listOf(entry) + state.commandHistory.take(24))
        }
    }

    fun onChatDraftChanged(newDraft: String) {
        _uiState.update { it.copy(chatDraft = newDraft) }
    }

    fun sendPreviewMessage(overrideText: String? = null) {
        val raw = (overrideText ?: _uiState.value.chatDraft).trim()
        if (raw.isEmpty()) return

        val now = Date()
        val timeStr = shortTimeFormatter.format(now)
        val lang = _uiState.value.settings.primaryLanguage
        val detectedTag = detectLanguageTag(raw, lang)

        val userMsg = ChatMessage(
            id = "msg_u_${System.currentTimeMillis()}",
            role = SenderRole.USER,
            text = raw,
            languageTag = detectedTag,
            timestamp = timeStr
        )

        val logEntry = CommandLogEntry(
            id = "cmd_${System.currentTimeMillis()}",
            commandText = raw,
            languageTag = detectedTag,
            target = CommandTarget.MOBILE_CORE,
            status = CommandPreviewStatus.UI_PREVIEW,
            timestamp = timeStr,
            note = "Chat preview • Gemini API calls stay server-side in Cloud Run"
        )

        _uiState.update {
            it.copy(
                chatDraft = if (overrideText == null) "" else it.chatDraft,
                messages = it.messages + userMsg,
                commandHistory = listOf(logEntry) + it.commandHistory,
                voiceState = VoiceState.THINKING
            )
        }

        viewModelScope.launch {
            delay(650L)
            val replyText = DiyaCopy.previewReplyForInput(raw, lang)
            val replyMsg = ChatMessage(
                id = "msg_d_${System.currentTimeMillis()}",
                role = SenderRole.DIYA,
                text = replyText,
                languageTag = detectedTag,
                timestamp = shortTimeFormatter.format(Date()),
                isPreviewTag = true
            )
            _uiState.update {
                it.copy(
                    messages = it.messages + replyMsg,
                    voiceState = VoiceState.SPEAKING
                )
            }
            delay(1300L)
            _uiState.update {
                if (it.voiceState == VoiceState.SPEAKING) it.copy(voiceState = VoiceState.IDLE) else it
            }
        }
    }

    private fun detectLanguageTag(text: String, fallback: AppLanguage): String = when {
        text.any { it in '\u0980'..'\u09FF' } -> "BN"
        text.any { it in '\u0900'..'\u097F' } -> "HI"
        text.all { it.code < 128 } -> "EN"
        else -> fallback.shortTag
    }

    fun onHistoryFilterChanged(tag: String) {
        _uiState.update { it.copy(historyFilterTag = tag) }
    }

    fun clearCommandHistory() {
        _uiState.update { it.copy(commandHistory = emptyList()) }
    }

    fun openShortcutPreview(item: ShortcutItem) {
        viewModelScope.launch {
            val result = desktopAgentBridge.inspectShortcutCapability(item.title)
            val nowStr = shortTimeFormatter.format(Date())
            val logEntry = CommandLogEntry(
                id = "cmd_${System.currentTimeMillis()}",
                commandText = "Inspect ${item.title} shortcut",
                languageTag = _uiState.value.settings.primaryLanguage.shortTag,
                target = if (item.kind == ShortcutKind.CHROME || item.kind == ShortcutKind.FILES) {
                    CommandTarget.WINDOWS_AGENT
                } else {
                    CommandTarget.MOBILE_CORE
                },
                status = CommandPreviewStatus.NOT_CONNECTED,
                timestamp = nowStr,
                note = result.message
            )
            _uiState.update {
                it.copy(
                    selectedShortcutSheet = item,
                    commandHistory = listOf(logEntry) + it.commandHistory.take(24)
                )
            }
        }
    }

    fun dismissShortcutSheet() {
        _uiState.update { it.copy(selectedShortcutSheet = null) }
    }

    fun updateLanguage(language: AppLanguage) {
        _uiState.update { state ->
            val updatedGreeting = ChatMessage(
                id = "msg_lang_${System.currentTimeMillis()}",
                role = SenderRole.DIYA,
                text = DiyaCopy.initialGreeting(language),
                languageTag = language.shortTag,
                timestamp = shortTimeFormatter.format(Date()),
                isPreviewTag = true
            )
            state.copy(
                settings = state.settings.copy(primaryLanguage = language),
                messages = state.messages + updatedGreeting
            )
        }
    }

    fun updateSettings(transform: (DiyaSettings) -> DiyaSettings) {
        _uiState.update { it.copy(settings = transform(it.settings)) }
    }

    fun getGeminiProtocolSpec(): String =
        "Server-Side Gemini API via Cloud Run Relay (GEMINI_API_KEY stays strictly on server)"

    fun getDesktopSecurityProfile(): String =
        "Outbound-Only TLS WSS Windows Agent • Zero Public PC Ports • Allowlisted Actions Only"

    override fun onCleared() {
        super.onCleared()
        relayClient?.disconnect()
    }
}
