package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.ScanRecord
import com.example.data.local.SentinelDatabase
import com.example.data.remote.GeminiClient
import com.example.data.repository.ScanRepository
import com.example.domain.analyzer.MessageThreatAnalyzer
import com.example.domain.analyzer.ThreatAnalysisResult
import com.example.domain.analyzer.ThreatLevel
import com.example.domain.analyzer.UrlThreatAnalyzer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONArray

enum class AppTab(val label: String) {
    HOME("Home"),
    SCAN("Scan"),
    ASSISTANT("Assistant"),
    HISTORY("History"),
    SETTINGS("Settings")
}

enum class ScanSubTab {
    MESSAGE, URL
}

data class ChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val role: String, // "user" or "assistant"
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class SampleThreatPreset(
    val title: String,
    val description: String,
    val content: String,
    val isUrl: Boolean = false
)

class SentinelViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: ScanRepository

    init {
        val db = SentinelDatabase.getInstance(application)
        repository = ScanRepository(db.scanDao())
    }

    // App Navigation
    private val _currentTab = MutableStateFlow(AppTab.HOME)
    val currentTab: StateFlow<AppTab> = _currentTab.asStateFlow()

    private val _activeScanSubTab = MutableStateFlow(ScanSubTab.MESSAGE)
    val activeScanSubTab: StateFlow<ScanSubTab> = _activeScanSubTab.asStateFlow()

    fun setTab(tab: AppTab) {
        _currentTab.value = tab
    }

    fun setScanSubTab(subTab: ScanSubTab) {
        _activeScanSubTab.value = subTab
    }

    // Settings
    private val _sensitivity = MutableStateFlow("STANDARD") // STANDARD, HIGH, PARANOID
    val sensitivity: StateFlow<String> = _sensitivity.asStateFlow()

    private val _aiIntelEnabled = MutableStateFlow(true)
    val aiIntelEnabled: StateFlow<Boolean> = _aiIntelEnabled.asStateFlow()

    private val _themeMode = MutableStateFlow("DARK") // SYSTEM, DARK, LIGHT
    val themeMode: StateFlow<String> = _themeMode.asStateFlow()

    fun setSensitivity(level: String) {
        _sensitivity.value = level
    }

    fun setAiIntelEnabled(enabled: Boolean) {
        _aiIntelEnabled.value = enabled
    }

    fun setThemeMode(mode: String) {
        _themeMode.value = mode
    }

    // Scan Stats
    val totalScansCount: StateFlow<Int> = repository.totalCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val threatsBlockedCount: StateFlow<Int> = repository.threatCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val safeScansCount: StateFlow<Int> = repository.safeCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    // Message Scan State
    private val _messageInput = MutableStateFlow("")
    val messageInput: StateFlow<String> = _messageInput.asStateFlow()

    private val _isAnalyzingMessage = MutableStateFlow(false)
    val isAnalyzingMessage: StateFlow<Boolean> = _isAnalyzingMessage.asStateFlow()

    private val _messageResult = MutableStateFlow<ThreatAnalysisResult?>(null)
    val messageResult: StateFlow<ThreatAnalysisResult?> = _messageResult.asStateFlow()

    fun updateMessageInput(text: String) {
        _messageInput.value = text
    }

    fun clearMessageInput() {
        _messageInput.value = ""
        _messageResult.value = null
    }

    // URL Scan State
    private val _urlInput = MutableStateFlow("")
    val urlInput: StateFlow<String> = _urlInput.asStateFlow()

    private val _isAnalyzingUrl = MutableStateFlow(false)
    val isAnalyzingUrl: StateFlow<Boolean> = _isAnalyzingUrl.asStateFlow()

    private val _urlResult = MutableStateFlow<ThreatAnalysisResult?>(null)
    val urlResult: StateFlow<ThreatAnalysisResult?> = _urlResult.asStateFlow()

    fun updateUrlInput(text: String) {
        _urlInput.value = text
    }

    fun clearUrlInput() {
        _urlInput.value = ""
        _urlResult.value = null
    }

    // Analysis Logic
    fun analyzeMessage() {
        val text = _messageInput.value.trim()
        if (text.isEmpty()) return

        viewModelScope.launch {
            _isAnalyzingMessage.value = true
            try {
                val baseResult = MessageThreatAnalyzer.analyze(text, _sensitivity.value)

                var finalResult = baseResult
                if (_aiIntelEnabled.value) {
                    val aiReport = GeminiClient.generateCyberExplanation(
                        targetType = "Message / Text Content",
                        targetContent = text,
                        heuristicsSummary = baseResult.summary
                    )
                    finalResult = baseResult.copy(aiExplanation = aiReport)
                }

                _messageResult.value = finalResult

                // Save to Room DB
                saveResultToHistory(finalResult)
            } finally {
                _isAnalyzingMessage.value = false
            }
        }
    }

    fun analyzeUrl() {
        val raw = _urlInput.value.trim()
        if (raw.isEmpty()) return

        viewModelScope.launch {
            _isAnalyzingUrl.value = true
            try {
                val baseResult = UrlThreatAnalyzer.analyze(raw, _sensitivity.value)

                var finalResult = baseResult
                if (_aiIntelEnabled.value) {
                    val aiReport = GeminiClient.generateCyberExplanation(
                        targetType = "URL / Web Domain",
                        targetContent = baseResult.target,
                        heuristicsSummary = baseResult.summary
                    )
                    finalResult = baseResult.copy(aiExplanation = aiReport)
                }

                _urlResult.value = finalResult

                // Save to Room DB
                saveResultToHistory(finalResult)
            } finally {
                _isAnalyzingUrl.value = false
            }
        }
    }

    private suspend fun saveResultToHistory(result: ThreatAnalysisResult) {
        val indicatorsArray = JSONArray()
        result.indicators.forEach { ind ->
            indicatorsArray.put("${ind.severity.label}: ${ind.title} - ${ind.description}")
        }

        val recsArray = JSONArray()
        result.recommendations.forEach { recsArray.put(it) }

        val record = ScanRecord(
            type = result.type,
            target = result.target,
            riskScore = result.riskScore,
            threatLevel = result.threatLevel.name,
            threatCategory = result.category,
            indicatorsJson = indicatorsArray.toString(),
            aiExplanation = result.aiExplanation,
            recommendationsJson = recsArray.toString(),
            timestamp = result.timestamp
        )
        repository.insertScan(record)
    }

    // History and Filtering
    private val _historyFilter = MutableStateFlow("ALL") // ALL, THREATS, SUSPICIOUS, SAFE, MESSAGE, URL
    val historyFilter: StateFlow<String> = _historyFilter.asStateFlow()

    private val _historySearchQuery = MutableStateFlow("")
    val historySearchQuery: StateFlow<String> = _historySearchQuery.asStateFlow()

    private val _selectedScanDetail = MutableStateFlow<ScanRecord?>(null)
    val selectedScanDetail: StateFlow<ScanRecord?> = _selectedScanDetail.asStateFlow()

    fun setHistoryFilter(filter: String) {
        _historyFilter.value = filter
    }

    fun setHistorySearchQuery(query: String) {
        _historySearchQuery.value = query
    }

    fun selectScanDetail(record: ScanRecord?) {
        _selectedScanDetail.value = record
    }

    fun deleteScan(id: Long) {
        viewModelScope.launch {
            repository.deleteScan(id)
            if (_selectedScanDetail.value?.id == id) {
                _selectedScanDetail.value = null
            }
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            repository.clearAll()
            _selectedScanDetail.value = null
        }
    }

    val filteredHistory: StateFlow<List<ScanRecord>> = combine(
        repository.allScans,
        _historyFilter,
        _historySearchQuery
    ) { scans, filter, query ->
        scans.filter { scan ->
            val matchesFilter = when (filter) {
                "THREATS" -> scan.threatLevel == "HIGH" || scan.threatLevel == "CRITICAL"
                "SUSPICIOUS" -> scan.threatLevel == "MODERATE"
                "SAFE" -> scan.threatLevel == "SAFE" || scan.threatLevel == "LOW"
                "MESSAGE" -> scan.type == "MESSAGE"
                "URL" -> scan.type == "URL"
                else -> true
            }
            val matchesQuery = if (query.isBlank()) true else {
                scan.target.contains(query, ignoreCase = true) ||
                        scan.threatCategory.contains(query, ignoreCase = true)
            }
            matchesFilter && matchesQuery
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Assistant Chat State
    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(
        listOf(
            ChatMessage(
                role = "assistant",
                content = "Greetings. I am **Sentinel AI**, your dedicated cybersecurity advisor. " +
                        "I can help you identify phishing scams, assess suspicious links, explain social engineering tactics, or guide you through incident response. How can I assist your defense today?"
            )
        )
    )
    val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages.asStateFlow()

    private val _isAssistantThinking = MutableStateFlow(false)
    val isAssistantThinking: StateFlow<Boolean> = _isAssistantThinking.asStateFlow()

    fun sendMessageToAssistant(prompt: String) {
        val trimmed = prompt.trim()
        if (trimmed.isEmpty()) return

        val userMsg = ChatMessage(role = "user", content = trimmed)
        val updatedList = _chatMessages.value + userMsg
        _chatMessages.value = updatedList

        viewModelScope.launch {
            _isAssistantThinking.value = true
            try {
                val historyTuples = updatedList.map { it.role to it.content }
                val aiResponse = GeminiClient.chatWithAdvisor(historyTuples, trimmed)

                val assistantMsg = ChatMessage(role = "assistant", content = aiResponse)
                _chatMessages.value = _chatMessages.value + assistantMsg
            } finally {
                _isAssistantThinking.value = false
            }
        }
    }

    fun clearAssistantChat() {
        _chatMessages.value = listOf(
            ChatMessage(
                role = "assistant",
                content = "Chat session cleared. Ready for your cybersecurity inquiries or threat triage."
            )
        )
    }

    // Sample Presets for Testing
    val sampleMessagePresets = listOf(
        SampleThreatPreset(
            title = "Chase Bank Urgent Fraud SMS",
            description = "Smishing with artificial urgency and credential harvesting link",
            content = "CHASE ALERT: Unauthorized wire transfer of $2,450.00 detected on your checking account. If not recognized, act immediately to prevent permanent account suspension: https://bit.ly/chase-fraud-verify-auth"
        ),
        SampleThreatPreset(
            title = "MetaMask Crypto Recovery Phish",
            description = "High-criticality secret seed phrase harvesting attempt",
            content = "[MetaMask Notification] Critical security protocol update required. Please verify your wallet and submit your 12-word secret recovery phrase within 24 hours to avoid wallet freeze."
        ),
        SampleThreatPreset(
            title = "USPS Package Delivery Fee Scam",
            description = "Smishing targeting package tracking and credit card details",
            content = "USPS: Your delivery package #US-893041 has an incomplete address and $1.99 redelivery fee. Update billing and confirm address at: https://usps.package-redelivery.xyz/track"
        ),
        SampleThreatPreset(
            title = "Legitimate Team Sync Invite",
            description = "Clean routine business calendar invite",
            content = "Hi Alex, please find the agenda for our quarterly product roadmap sync attached. We will review sprint metrics and launch timelines on Tuesday at 10:00 AM PST. Thanks!"
        )
    )

    val sampleUrlPresets = listOf(
        SampleThreatPreset(
            title = "Raw IP Host Authentication",
            description = "Direct IP login portal without domain registration",
            content = "http://185.220.101.45:8080/secure/paypal-login.php",
            isUrl = true
        ),
        SampleThreatPreset(
            title = "Spoofed Subdomain & Risky TLD",
            description = "Brand spoofing on high-abuse .xyz TLD",
            content = "https://appleid.apple.com.security-verify.xyz/login?token=89234",
            isUrl = true
        ),
        SampleThreatPreset(
            title = "Obfuscated Link Shortener",
            description = "Shortened redirector masking endpoint",
            content = "https://tinyurl.com/bank-security-login-now",
            isUrl = true
        ),
        SampleThreatPreset(
            title = "Legitimate Corporate Domain",
            description = "Clean HTTPS trusted enterprise endpoint",
            content = "https://www.google.com/search?q=cybersecurity+best+practices",
            isUrl = true
        )
    )

    fun loadSamplePreset(preset: SampleThreatPreset) {
        if (preset.isUrl) {
            _urlInput.value = preset.content
            _currentTab.value = AppTab.SCAN
            _activeScanSubTab.value = ScanSubTab.URL
        } else {
            _messageInput.value = preset.content
            _currentTab.value = AppTab.SCAN
            _activeScanSubTab.value = ScanSubTab.MESSAGE
        }
    }
}
