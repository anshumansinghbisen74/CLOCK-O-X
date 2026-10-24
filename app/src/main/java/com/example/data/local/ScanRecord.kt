package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "scan_records")
data class ScanRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val type: String, // "MESSAGE" or "URL"
    val target: String,
    val riskScore: Int, // 0 - 100
    val threatLevel: String, // "SAFE", "LOW", "MODERATE", "HIGH", "CRITICAL"
    val threatCategory: String, // e.g., "Phishing", "Credential Harvesting", "Malicious Link"
    val indicatorsJson: String, // JSON-encoded list of indicator strings
    val aiExplanation: String, // AI threat intel summary
    val recommendationsJson: String, // JSON-encoded list of recommended user actions
    val timestamp: Long = System.currentTimeMillis()
)
