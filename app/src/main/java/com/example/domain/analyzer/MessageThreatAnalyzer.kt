package com.example.domain.analyzer

import java.util.regex.Pattern

object MessageThreatAnalyzer {

    // Regex patterns for URL detection within text
    private val URL_PATTERN = Pattern.compile(
        "https?://[\\w\\.-]+(?:\\.[a-zA-Z]{2,})+(?:/[\\w\\.-]*)*(?:\\?[\\w\\.%&=-]*)?",
        Pattern.CASE_INSENSITIVE
    )

    // Suspicious Shortener and redirect domains
    private val SHORTENER_DOMAINS = listOf(
        "bit.ly", "tinyurl.com", "t.co", "is.gd", "buff.ly", "ow.ly", "cutt.ly", "rb.gy"
    )

    // Targeted high-value brands commonly impersonated
    private val IMPERSONATED_BRANDS = mapOf(
        "apple" to "Apple Security / Apple ID",
        "paypal" to "PayPal Payments",
        "amazon" to "Amazon Customer Support",
        "netflix" to "Netflix Membership",
        "usps" to "US Postal Service / Package Alert",
        "fedex" to "FedEx Delivery",
        "dhl" to "DHL Express Logistics",
        "chase" to "Chase Bank Alert",
        "wells fargo" to "Wells Fargo Fraud Dept",
        "bank of america" to "Bank of America Fraud Alert",
        "irs" to "Internal Revenue Service (IRS)",
        "coinbase" to "Coinbase Crypto Exchange",
        "metamask" to "MetaMask Crypto Wallet",
        "geek squad" to "Geek Squad Tech Support",
        "microsoft" to "Microsoft Security Account"
    )

    // Urgency indicators
    private val URGENCY_TRIGGERS = listOf(
        "immediate action required", "urgent", "act immediately", "account suspended",
        "suspended within 24 hours", "permanently closed", "unauthorized login",
        "unauthorized transaction", "final warning", "arrest warrant", "legal action",
        "terminate your account", "action required immediately", "limited time"
    )

    // Credential harvesting cues
    private val HARVESTING_CUES = listOf(
        "seed phrase", "secret phrase", "recovery key", "verify your account",
        "confirm password", "enter your pin", "provide otp", "one-time password",
        "social security number", "ssn", "cvv", "credit card details",
        "update billing", "re-authenticate", "login to secure", "confirm credentials"
    )

    // Advance-fee / lottery / refund fraud
    private val SCAM_CUES = listOf(
        "claim your refund", "lottery winner", "congratulations you won",
        "crypto giveaway", "free bitcoin", "inheritance payment", "wire transfer",
        "overdue tax", "compensation fund", "guaranteed returns", "gift card payment"
    )

    fun analyze(message: String, sensitivity: String = "STANDARD"): ThreatAnalysisResult {
        val trimmed = message.trim()
        if (trimmed.isEmpty()) {
            return ThreatAnalysisResult(
                type = "MESSAGE",
                target = message,
                riskScore = 0,
                threatLevel = ThreatLevel.SAFE,
                category = "Empty Input",
                summary = "No content was provided for inspection.",
                indicators = emptyList(),
                aiExplanation = "The input message was empty. Provide text or an SMS snippet to inspect.",
                recommendations = listOf("Input a message, email, or SMS to perform threat scanning.")
            )
        }

        val lower = trimmed.lowercase()
        val indicators = mutableListOf<SecurityIndicator>()
        var score = 0

        // 1. Check Urgency / Fear Cues
        val matchedUrgency = URGENCY_TRIGGERS.filter { lower.contains(it) }
        if (matchedUrgency.isNotEmpty()) {
            val weight = if (matchedUrgency.size >= 2) 30 else 20
            score += weight
            indicators.add(
                SecurityIndicator(
                    title = "Artificial Urgency & Fear Tactics",
                    description = "Attackers manufacture urgency to induce panic and bypass logical scrutiny.",
                    severity = if (matchedUrgency.size >= 2) ThreatLevel.HIGH else ThreatLevel.MODERATE,
                    matchedText = matchedUrgency.joinToString(", ")
                )
            )
        }

        // 2. Check Credential & Financial Harvesting
        val matchedHarvesting = HARVESTING_CUES.filter { lower.contains(it) }
        if (matchedHarvesting.isNotEmpty()) {
            val weight = if (matchedHarvesting.size >= 2) 35 else 25
            score += weight
            indicators.add(
                SecurityIndicator(
                    title = "Credential Harvesting Intent",
                    description = "Requesting passwords, PINs, OTPs, seed phrases, or billing updates is a hallmark of phishing.",
                    severity = ThreatLevel.CRITICAL,
                    matchedText = matchedHarvesting.joinToString(", ")
                )
            )
        }

        // 3. Check Advance-Fee & Giveaway Scam Cues
        val matchedScam = SCAM_CUES.filter { lower.contains(it) }
        if (matchedScam.isNotEmpty()) {
            score += 25
            indicators.add(
                SecurityIndicator(
                    title = "Financial / Advance-Fee Lure",
                    description = "Unsolicited claims of winnings, crypto distributions, refunds, or gift card requests are typical fraud patterns.",
                    severity = ThreatLevel.HIGH,
                    matchedText = matchedScam.joinToString(", ")
                )
            )
        }

        // 4. Check Brand Impersonation
        val impersonated = IMPERSONATED_BRANDS.entries.filter { lower.contains(it.key) }
        if (impersonated.isNotEmpty()) {
            val brands = impersonated.joinToString(", ") { it.value }
            if (matchedUrgency.isNotEmpty() || matchedHarvesting.isNotEmpty() || matchedScam.isNotEmpty()) {
                score += 25
                indicators.add(
                    SecurityIndicator(
                        title = "Corporate Brand Impersonation",
                        description = "Brand names detected alongside security warnings or verification requests indicate brand spoofing.",
                        severity = ThreatLevel.HIGH,
                        matchedText = brands
                    )
                )
            }
        }

        // 5. Check Links in Message
        val matcher = URL_PATTERN.matcher(trimmed)
        val detectedUrls = mutableListOf<String>()
        while (matcher.find()) {
            detectedUrls.add(matcher.group())
        }

        if (detectedUrls.isNotEmpty()) {
            var suspiciousLinkScore = 15
            val hasShortener = detectedUrls.any { url ->
                SHORTENER_DOMAINS.any { url.contains(it, ignoreCase = true) }
            }
            if (hasShortener) {
                suspiciousLinkScore += 15
                indicators.add(
                    SecurityIndicator(
                        title = "Obfuscated / Shortened Link",
                        description = "Shortened links (e.g. bit.ly, tinyurl) mask the true destination domain to bypass email/SMS filters.",
                        severity = ThreatLevel.HIGH,
                        matchedText = detectedUrls.filter { url -> SHORTENER_DOMAINS.any { url.contains(it, ignoreCase = true) } }.joinToString()
                    )
                )
            } else {
                indicators.add(
                    SecurityIndicator(
                        title = "External Hyperlink Embedded",
                        description = "Directing the user to an external link is typical of smishing/phishing delivery vectors.",
                        severity = ThreatLevel.MODERATE,
                        matchedText = detectedUrls.take(2).joinToString(", ")
                    )
                )
            }
            score += suspiciousLinkScore
        }

        // Apply sensitivity multipliers
        when (sensitivity) {
            "HIGH" -> score = (score * 1.15f).toInt()
            "PARANOID" -> score = (score * 1.30f).toInt()
        }
        score = score.coerceIn(0, 100)

        // Determine Level
        val level = when {
            score >= 70 -> ThreatLevel.CRITICAL
            score >= 45 -> ThreatLevel.HIGH
            score >= 25 -> ThreatLevel.MODERATE
            score >= 10 -> ThreatLevel.LOW
            else -> ThreatLevel.SAFE
        }

        // Threat Category
        val category = when {
            matchedHarvesting.isNotEmpty() && impersonated.isNotEmpty() -> "Brand Phishing / Credential Theft"
            matchedHarvesting.isNotEmpty() -> "Credential Harvesting Attempt"
            matchedUrgency.isNotEmpty() && detectedUrls.isNotEmpty() -> "Smishing / Urgent Attack Vector"
            matchedScam.isNotEmpty() -> "Advance-Fee / Prize Scam"
            impersonated.isNotEmpty() -> "Suspected Brand Impersonation"
            score >= 25 -> "Suspicious Solicitations"
            else -> "Legitimate / Clean"
        }

        // Summary
        val summary = when (level) {
            ThreatLevel.CRITICAL -> "CRITICAL THREAT: Highly coercive message exhibiting multiple indicators of targeted credential theft or phishing."
            ThreatLevel.HIGH -> "HIGH RISK: Message contains suspicious pressure tactics, brand mentions, or suspicious embedded links."
            ThreatLevel.MODERATE -> "SUSPICIOUS: Moderate anomalies detected. Treat with caution and do not disclose sensitive details."
            ThreatLevel.LOW -> "LOW RISK: Few minor anomaly cues, but generally benign content."
            ThreatLevel.SAFE -> "CLEAN: No recognized phishing patterns, coercive triggers, or credential harvesting indicators detected."
        }

        // Recommendations
        val recs = mutableListOf<String>()
        if (level == ThreatLevel.CRITICAL || level == ThreatLevel.HIGH) {
            recs.add("Do NOT tap any embedded links or call phone numbers in this message.")
            recs.add("Never share OTPs, passwords, PINs, or crypto seed recovery phrases.")
            recs.add("If impersonating an organization (bank, postal, tech company), verify independently via their official app or website.")
            recs.add("Forward the SMS to carrier spam reporting (e.g., 7726 in US/UK) and block the sender.")
        } else if (level == ThreatLevel.MODERATE) {
            recs.add("Cross-reference the sender email/number against official contacts.")
            recs.add("Inspect any referenced domain carefully before clicking.")
        } else {
            recs.add("Standard hygiene: Never share passwords or 2FA codes with anyone.")
        }

        // Baseline Heuristic explanation
        val baselineExplanation = buildString {
            append("Automated Threat Engine Evaluation:\n")
            append("Calculated Risk Score: $score/100 ($level).\n\n")
            if (indicators.isNotEmpty()) {
                append("Primary Red Flags:\n")
                indicators.forEach { ind ->
                    append("• [${ind.severity.label}] ${ind.title}: ${ind.description}")
                    if (!ind.matchedText.isNullOrEmpty()) {
                        append(" (Matched: \"${ind.matchedText}\")")
                    }
                    append("\n")
                }
            } else {
                append("The message text did not trigger security heuristics for known social engineering vectors.\n")
            }
        }

        return ThreatAnalysisResult(
            type = "MESSAGE",
            target = trimmed,
            riskScore = score,
            threatLevel = level,
            category = category,
            summary = summary,
            indicators = indicators,
            aiExplanation = baselineExplanation,
            recommendations = recs
        )
    }
}
