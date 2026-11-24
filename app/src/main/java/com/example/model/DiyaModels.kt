package com.example.model

import androidx.compose.ui.graphics.Color
import com.example.core.architecture.LiveConnectionStatus
import com.example.core.architecture.PcAgentStatus
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.ListeningAqua
import com.example.ui.theme.SpeakingCyan
import com.example.ui.theme.ThinkingIndigo

enum class VoiceState {
    IDLE,
    LISTENING,
    THINKING,
    SPEAKING;

    fun accentColor(): Color = when (this) {
        IDLE -> ElectricCyan
        LISTENING -> ListeningAqua
        THINKING -> ThinkingIndigo
        SPEAKING -> SpeakingCyan
    }

    fun secondaryColor(): Color = when (this) {
        IDLE -> ElectricBlue
        LISTENING -> ElectricCyan
        THINKING -> ElectricBlue
        SPEAKING -> ElectricCyan
    }
}

enum class AppLanguage(
    val code: String,
    val nativeLabel: String,
    val shortTag: String
) {
    BENGALI("bn-IN", "বাংলা (Primary)", "BN"),
    ENGLISH("en-US", "English", "EN"),
    HINDI("hi-IN", "हिन्दी", "HI")
}

enum class SenderRole {
    USER,
    DIYA,
    SYSTEM_NOTE
}

data class ChatMessage(
    val id: String,
    val role: SenderRole,
    val text: String,
    val languageTag: String,
    val timestamp: String,
    val isPreviewTag: Boolean = false
)

enum class CommandTarget {
    MOBILE_CORE,
    WINDOWS_AGENT,
    GEMINI_LIVE
}

enum class CommandPreviewStatus {
    UI_PREVIEW,
    NOT_CONNECTED
}

data class CommandLogEntry(
    val id: String,
    val commandText: String,
    val languageTag: String,
    val target: CommandTarget,
    val status: CommandPreviewStatus,
    val timestamp: String,
    val note: String
)

enum class ShortcutKind {
    WHATSAPP,
    YOUTUBE,
    CHROME,
    FILES,
    SETTINGS
}

data class ShortcutItem(
    val kind: ShortcutKind,
    val title: String,
    val subtitle: String,
    val bridgeTarget: String,
    val stageNote: String,
    val isSettings: Boolean = false
)

data class DiyaSettings(
    val primaryLanguage: AppLanguage = AppLanguage.BENGALI,
    val wakeWordPreviewEnabled: Boolean = true,
    val holographicGlowIntensity: Float = 1.0f,
    val voiceResponseCadence: Float = 0.55f,
    val reducedMotion: Boolean = false,
    val compactWaveformDensity: Boolean = false
)

data class DiyaUiState(
    val currentTimeText: String = "00:00:00",
    val currentDateText: String = "SYSTEM STANDBY",
    val voiceState: VoiceState = VoiceState.IDLE,
    val mobileUiReady: Boolean = true,
    val pcAgentStatus: PcAgentStatus = PcAgentStatus.DISCONNECTED,
    val geminiLiveStatus: LiveConnectionStatus = LiveConnectionStatus.NOT_CONNECTED,
    val waveformBars: List<Float> = List(32) { 0.14f },
    val messages: List<ChatMessage> = emptyList(),
    val commandHistory: List<CommandLogEntry> = emptyList(),
    val historyFilterTag: String = "ALL",
    val shortcuts: List<ShortcutItem> = emptyList(),
    val selectedShortcutSheet: ShortcutItem? = null,
    val chatDraft: String = "",
    val settings: DiyaSettings = DiyaSettings()
)

object DiyaCopy {
    fun voiceLabel(state: VoiceState, language: AppLanguage): String = when (language) {
        AppLanguage.BENGALI -> when (state) {
            VoiceState.IDLE -> "প্রস্তুত • STANDBY"
            VoiceState.LISTENING -> "শুনছি (UI প্রিভিউ) • LISTENING"
            VoiceState.THINKING -> "বিশ্লেষণ করছি • THINKING"
            VoiceState.SPEAKING -> "উত্তর দিচ্ছি (প্রিভিউ) • SPEAKING"
        }
        AppLanguage.ENGLISH -> when (state) {
            VoiceState.IDLE -> "CORE STANDBY"
            VoiceState.LISTENING -> "LISTENING (UI PREVIEW)"
            VoiceState.THINKING -> "THINKING (UI PREVIEW)"
            VoiceState.SPEAKING -> "SPEAKING (UI PREVIEW)"
        }
        AppLanguage.HINDI -> when (state) {
            VoiceState.IDLE -> "तैयार • STANDBY"
            VoiceState.LISTENING -> "सुन रही हूँ (प्रीव्यू) • LISTENING"
            VoiceState.THINKING -> "सोच रही हूँ • THINKING"
            VoiceState.SPEAKING -> "बोल रही हूँ (प्रीव्यू) • SPEAKING"
        }
    }

    fun voicePreviewNotice(language: AppLanguage): String = when (language) {
        AppLanguage.BENGALI -> "UI প্রিভিউ মোড • মাইক্রোফোন ও Gemini Live এখনো সংযুক্ত নয়"
        AppLanguage.ENGLISH -> "UI Preview Mode • Live mic & Gemini Live stream not connected yet"
        AppLanguage.HINDI -> "UI प्रीव्यू मोड • माइक्रोफ़ोन और Gemini Live अभी कनेक्ट नहीं हैं"
    }

    fun initialGreeting(language: AppLanguage): String = when (language) {
        AppLanguage.BENGALI -> "নমস্কার! আমি দিয়া (DIYA AI), আপনার ব্যক্তিগত বুদ্ধিমান সহকারী। বাংলা, ইংরেজি ও হিন্দি — তিন ভাষাতেই আমি আপনাকে সাহায্য করতে প্রস্তুত। এটি স্টেজ-১ ফ্রন্টএন্ড প্রিভিউ; লাইভ ভয়েস ও পিসি এজেন্ট পরবর্তী ধাপে সংযুক্ত হবে।"
        AppLanguage.ENGLISH -> "Greetings. I am DIYA AI, your personal intelligent assistant. I understand Bengali, English, and Hindi. You are viewing the Stage 1 HUD preview—live voice and Windows PC automation bridges are architected and awaiting connection."
        AppLanguage.HINDI -> "नमस्ते! मैं दिया (DIYA AI) हूँ, आपकी पर्सनल इंटेलिजेंट असिस्टेंट। मैं बंगाली, अंग्रेज़ी और हिन्दी समझती हूँ। यह स्टेज-1 फ्रंटएंड प्रीव्यू है; लाइव वॉइस और पीसी एजेंट अगले चरण में जुड़ेंगे।"
    }

    fun previewReplyForInput(input: String, language: AppLanguage): String {
        val lower = input.lowercase()
        val looksHindi = input.any { it in '\u0900'..'\u097F' } ||
            listOf("mera", "kya", "hindi", "namaste", "karo").any { it in lower }
        val looksBengali = input.any { it in '\u0980'..'\u09FF' } ||
            listOf("amar", "kemon", "bangla", "diya", "koro").any { it in lower }

        return when {
            looksBengali || language == AppLanguage.BENGALI && !looksHindi && !input.all { it.code < 128 } ->
                "বার্তাটি গ্রহণ করা হয়েছে: \"$input\"। [স্টেজ-১ UI প্রিভিউ] লাইভ Gemini API সংযুক্ত হলে আমি রিয়েল-টাইমে এই কাজের সম্পূর্ণ উত্তর ও নির্দেশনা দেব।"
            looksHindi ->
                "आपका संदेश प्राप्त हुआ: \"$input\"। [स्टेज-1 UI प्रीव्यू] लाइव Gemini API कनेक्ट होने पर मैं इस अनुरोध का वास्तविक उत्तर दूँगी।"
            language == AppLanguage.HINDI ->
                "संदेश प्राप्त हुआ: \"$input\"। [स्टेज-1 UI प्रीव्यू] लाइव Gemini API जुड़ने के बाद वास्तविक प्रतिक्रिया सक्रिय होगी।"
            else ->
                "Acknowledged: \"$input\". [Stage 1 UI Preview] Live Gemini reasoning and device automation are not connected yet, but your command interface is ready."
        }
    }

    fun quickPrompts(language: AppLanguage): List<Pair<String, String>> = when (language) {
        AppLanguage.BENGALI -> listOf(
            "BN" to "সুপ্রভাত দিয়া, আজকের কাজের তালিকা দেখাও",
            "EN" to "Check system bridge readiness",
            "HI" to "दिया, आज का स्टेटस बताओ"
        )
        AppLanguage.ENGLISH -> listOf(
            "BN" to "সুপ্রভাত দিয়া, কেমন আছো?",
            "EN" to "Inspect Gemini Live & PC bridge status",
            "HI" to "मेरा वर्कस्पेस स्टेटस दिखाओ"
        )
        AppLanguage.HINDI -> listOf(
            "HI" to "नमस्ते दिया, आज की प्राथमिकताएँ दिखाओ",
            "BN" to "দিয়া, বাংলা মোড পরীক্ষা করো",
            "EN" to "Show Windows agent architecture"
        )
    }
}
