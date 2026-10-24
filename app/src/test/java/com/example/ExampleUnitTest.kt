package com.example

import com.example.domain.analyzer.MessageThreatAnalyzer
import com.example.domain.analyzer.ThreatLevel
import com.example.domain.analyzer.UrlThreatAnalyzer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun testPhishingMessageDetection() {
        val phishingText = "CHASE ALERT: Unauthorized wire transfer of $2,450.00 detected. Act immediately or account suspended: https://bit.ly/chase-verify"
        val result = MessageThreatAnalyzer.analyze(phishingText)

        assertTrue(result.riskScore >= 45)
        assertTrue(result.threatLevel == ThreatLevel.HIGH || result.threatLevel == ThreatLevel.CRITICAL)
        assertTrue(result.indicators.isNotEmpty())
    }

    @Test
    fun testCleanMessageDetection() {
        val cleanText = "Hi Team, reminder that our design sprint kickoff starts tomorrow at 10:00 AM."
        val result = MessageThreatAnalyzer.analyze(cleanText)

        assertEquals(ThreatLevel.SAFE, result.threatLevel)
        assertEquals(0, result.riskScore)
    }

    @Test
    fun testRawIpUrlDetection() {
        val ipUrl = "http://185.220.101.50:8080/paypal-login.php"
        val result = UrlThreatAnalyzer.analyze(ipUrl)

        assertTrue(result.riskScore >= 60)
        assertTrue(result.threatLevel == ThreatLevel.HIGH || result.threatLevel == ThreatLevel.CRITICAL)
        assertTrue(result.indicators.any { it.title.contains("Raw IP") || it.title.contains("Port") })
    }

    @Test
    fun testCleanUrlDetection() {
        val cleanUrl = "https://www.google.com/search?q=cybersecurity"
        val result = UrlThreatAnalyzer.analyze(cleanUrl)

        assertEquals(ThreatLevel.SAFE, result.threatLevel)
        assertTrue(result.riskScore < 20)
    }
}
