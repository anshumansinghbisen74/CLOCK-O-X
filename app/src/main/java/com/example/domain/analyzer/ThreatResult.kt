package com.example.domain.analyzer

enum class ThreatLevel(val label: String, val colorHex: Long) {
    SAFE("SAFE", 0xFF10B981),
    LOW("LOW RISK", 0xFF3B82F6),
    MODERATE("SUSPICIOUS", 0xFFF59E0B),
    HIGH("HIGH RISK", 0xFFF97316),
    CRITICAL("CRITICAL THREAT", 0xFFEF4444)
}

data class SecurityIndicator(
    val title: String,
    val description: String,
    val severity: ThreatLevel,
    val matchedText: String? = null
)

data class ThreatAnalysisResult(
    val type: String, // "MESSAGE" or "URL"
    val target: String,
    val riskScore: Int, // 0 to 100
    val threatLevel: ThreatLevel,
    val category: String, // e.g. "Phishing Attempt", "Credential Harvesting", "Smishing Lure"
    val summary: String,
    val indicators: List<SecurityIndicator>,
    val aiExplanation: String,
    val recommendations: List<String>,
    val timestamp: Long = System.currentTimeMillis()
)
