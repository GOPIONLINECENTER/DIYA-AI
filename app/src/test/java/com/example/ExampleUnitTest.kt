package com.example

import com.example.core.architecture.LiveConnectionStatus
import com.example.core.architecture.PcAgentStatus
import com.example.core.architecture.StageOneDesktopAgentBridge
import com.example.core.architecture.StageOneGeminiLiveBridge
import com.example.model.AppLanguage
import com.example.model.DiyaCopy
import com.example.model.VoiceState
import com.example.viewmodel.DiyaViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ExampleUnitTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `stage one bridges honestly report not connected`() = runTest {
        val geminiBridge = StageOneGeminiLiveBridge()
        val pcBridge = StageOneDesktopAgentBridge()

        assertEquals(LiveConnectionStatus.NOT_CONNECTED, geminiBridge.connectionStatus.value)
        assertFalse(geminiBridge.connectionStatus.value.isConnected)

        val liveResult = geminiBridge.requestSessionPreview("bn-IN")
        assertFalse(liveResult.executed)

        assertEquals(PcAgentStatus.DISCONNECTED, pcBridge.agentStatus.value)
        assertFalse(pcBridge.agentStatus.value.isConnected)

        val pcResult = pcBridge.inspectShortcutCapability("Chrome")
        assertFalse(pcResult.executed)
    }

    @Test
    fun `diya defaults to bengali primary language and five shortcuts`() {
        val viewModel = DiyaViewModel()
        val state = viewModel.uiState.value

        assertEquals(AppLanguage.BENGALI, state.settings.primaryLanguage)
        assertEquals(5, state.shortcuts.size)
        assertEquals(VoiceState.IDLE, state.voiceState)
        assertTrue(state.messages.isNotEmpty())
        assertTrue(state.commandHistory.isNotEmpty())
    }

    @Test
    fun `multilingual preview replies support bengali english and hindi`() {
        val bnReply = DiyaCopy.previewReplyForInput("সুপ্রভাত দিয়া", AppLanguage.BENGALI)
        val hiReply = DiyaCopy.previewReplyForInput("नमस्ते दिया", AppLanguage.HINDI)
        val enReply = DiyaCopy.previewReplyForInput("Status report", AppLanguage.ENGLISH)

        assertTrue(bnReply.contains("স্টেজ-১"))
        assertTrue(hiReply.contains("स्टेज-1"))
        assertTrue(enReply.contains("Stage 1 UI Preview"))
    }
}
