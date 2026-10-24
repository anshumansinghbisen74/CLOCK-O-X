package com.example.domain.analyzer

import java.net.URI
import java.util.regex.Pattern

object UrlThreatAnalyzer {

    // Common High Risk / Spam TLDs
    private val HIGH_RISK_TLDS = setOf(
        "xyz", "top", "buzz", "cc", "work", "icu", "gq", "tk", "ml", "cf",
        "fit", "live", "rest", "ru", "su", "cn", "link", "click", "download",
        "review", "country", "stream", "racing", "kim", "party", "zip", "mov"
    )

    // Known URL shorteners
    private val SHORTENER_DOMAINS = setOf(
        "bit.ly", "tinyurl.com", "t.co", "is.gd", "buff.ly", "ow.ly", "cutt.ly",
        "rb.gy", "goo.gl", "rebrand.ly", "shorturl.at"
    )

    // Brands frequently spoofed in deceptive hostnames/subdomains
    private val TARGET_BRANDS = listOf(
        "paypal", "apple", "amazon", "netflix", "microsoft", "google", "facebook",
        "instagram", "whatsapp", "chase", "wellsfargo", "bankofamerica", "coinbase",
        "binance", "metamask", "usps", "fedex", "dhl", "dropbox"
    )

    // IPv4 Address Pattern
    private val IP_PATTERN = Pattern.compile(
        "^(\\d{1,3}\\.){3}\\d{1,3}$"
    )

    // Suspicious keywords in paths or query strings
    private val SUSPICIOUS_PATH_KEYWORDS = listOf(
        "login", "signin", "verify", "secure", "account", "update", "banking",
        "wallet", "password", "recovery", "auth", "credential", "confirm",
        "wp-admin", "session", "validation"
    )

    fun normalizeUrl(raw: String): String {
        var trimmed = raw.trim()
        if (trimmed.isEmpty()) return ""
        if (!trimmed.startsWith("http://", ignoreCase = true) &&
            !trimmed.startsWith("https://", ignoreCase = true) &&
            !trimmed.startsWith("ftp://", ignoreCase = true)
        ) {
            trimmed = "https://$trimmed"
        }
        return trimmed
    }

    fun analyze(rawUrl: String, sensitivity: String = "STANDARD"): ThreatAnalysisResult {
        val trimmed = rawUrl.trim()
        if (trimmed.isEmpty()) {
            return ThreatAnalysisResult(
                type = "URL",
                target = rawUrl,
                riskScore = 0,
                threatLevel = ThreatLevel.SAFE,
                category = "Empty Input",
                summary = "No URL provided for analysis.",
                indicators = emptyList(),
                aiExplanation = "Input URL was empty. Enter a link to inspect.",
                recommendations = listOf("Paste or enter a full URL (e.g., https://example.com).")
            )
        }

        val normalized = normalizeUrl(trimmed)
        val indicators = mutableListOf<SecurityIndicator>()
        var score = 0

        val uri: URI? = try {
            URI.create(normalized)
        } catch (e: Exception) {
            null
        }

        if (uri == null || uri.host.isNullOrEmpty()) {
            return ThreatAnalysisResult(
                type = "URL",
                target = trimmed,
                riskScore = 80,
                threatLevel = ThreatLevel.CRITICAL,
                category = "Malformed URL",
                summary = "Invalid or malformed URI structure that may attempt parser evasion.",
                indicators = listOf(
                    SecurityIndicator(
                        title = "Malformed URI Syntax",
                        description = "The target cannot be safely parsed as a valid URI. Attackers frequently format malformed links to trick mobile app webviews.",
                        severity = ThreatLevel.CRITICAL,
                        matchedText = trimmed
                    )
                ),
                aiExplanation = "The provided string cannot be resolved into a valid URL host/scheme. Do not interact with unparseable links.",
                recommendations = listOf("Do not click this link.", "Verify the source of the message containing this string.")
            )
        }

        val scheme = uri.scheme?.lowercase() ?: ""
        val host = uri.host.lowercase()
        val path = uri.path?.lowercase() ?: ""
        val query = uri.query?.lowercase() ?: ""
        val port = uri.port

        // 1. Check Dangerous / Insecure Schemes
        if (scheme == "http") {
            score += 15
            indicators.add(
                SecurityIndicator(
                    title = "Unencrypted Protocol (HTTP)",
                    description = "Connection lacks TLS encryption. Credentials sent over HTTP are exposed in plaintext to network eavesdroppers.",
                    severity = ThreatLevel.MODERATE,
                    matchedText = "http://"
                )
            )
        } else if (scheme !in listOf("https", "http")) {
            score += 45
            indicators.add(
                SecurityIndicator(
                    title = "Non-Standard / Potentially Malicious Scheme",
                    description = "Schemes like 'data:', 'javascript:', or custom intent schemes can trigger unauthorized code execution or local app hijacks.",
                    severity = ThreatLevel.CRITICAL,
                    matchedText = "$scheme://"
                )
            )
        }

        // 2. Check Raw IP Address in Hostname
        if (IP_PATTERN.matcher(host).matches()) {
            score += 45
            indicators.add(
                SecurityIndicator(
                    title = "Raw IP Address Hostname",
                    description = "Legitimate services use registered domain names. Using raw IP addresses bypasses domain reputational checks and DNS monitoring.",
                    severity = ThreatLevel.CRITICAL,
                    matchedText = host
                )
            )
        }

        // 3. Check Punycode / Homograph (IDN) Attacks
        if (host.startsWith("xn--") || host.contains(".xn--")) {
            score += 35
            indicators.add(
                SecurityIndicator(
                    title = "Punycode (IDN) Homograph Attack Risk",
                    description = "The domain uses internationalized punycode encoding ('xn--'), often used by threat actors to spoof characters that look identical to genuine Latin letters.",
                    severity = ThreatLevel.HIGH,
                    matchedText = host
                )
            )
        }

        // 4. Check High-Risk / Abused TLDs
        val domainParts = host.split(".")
        val tld = domainParts.lastOrNull() ?: ""
        if (HIGH_RISK_TLDS.contains(tld)) {
            score += 25
            indicators.add(
                SecurityIndicator(
                    title = "High-Risk Top Level Domain (.$tld)",
                    description = "Top level domain '.$tld' has a disproportionate prevalence of phishing, botnets, and malware campaigns.",
                    severity = ThreatLevel.HIGH,
                    matchedText = ".$tld"
                )
            )
        }

        // 5. Check URL Shorteners
        if (SHORTENER_DOMAINS.contains(host)) {
            score += 20
            indicators.add(
                SecurityIndicator(
                    title = "URL Shortener / Redirection Proxy",
                    description = "Shortened URL obscures the actual target destination server and may dynamically redirect to exploit kits or phishing pages.",
                    severity = ThreatLevel.MODERATE,
                    matchedText = host
                )
            )
        }

        // 6. Check Deceptive Subdomains & Brand Spoofing in Hostname
        for (brand in TARGET_BRANDS) {
            if (host.contains(brand)) {
                // If it contains the brand name, but the root domain is NOT the official brand domain
                val isLegitimateRoot = host.endsWith(".$brand.com") || host == "$brand.com" ||
                        host.endsWith(".$brand.org") || host == "$brand.org" ||
                        host.endsWith(".$brand.net") || host == "$brand.net"
                if (!isLegitimateRoot) {
                    score += 40
                    indicators.add(
                        SecurityIndicator(
                            title = "Brand Spoofing in Domain Structure",
                            description = "The brand '$brand' appears in a subdomain or deceptive root domain that does not appear to belong to the official owner.",
                            severity = ThreatLevel.CRITICAL,
                            matchedText = host
                        )
                    )
                    break
                }
            }
        }

        // 7. Excessive Subdomain Nesting (e.g. 4+ segments)
        if (domainParts.size >= 4 && !IP_PATTERN.matcher(host).matches()) {
            score += 20
            indicators.add(
                SecurityIndicator(
                    title = "Excessive Subdomain Depth",
                    description = "Deep subdomain structures (e.g., login.security.verify.domain.com) are used by phishing kits to mimic legitimate URL hierarchies.",
                    severity = ThreatLevel.MODERATE,
                    matchedText = host
                )
            )
        }

        // 8. Suspicious Path & Query Keywords
        val matchedKeywords = SUSPICIOUS_PATH_KEYWORDS.filter { path.contains(it) || query.contains(it) }
        if (matchedKeywords.isNotEmpty()) {
            val weight = if (matchedKeywords.size >= 2) 20 else 10
            score += weight
            indicators.add(
                SecurityIndicator(
                    title = "Sensitive Endpoint Keywords",
                    description = "Path/query contains authentication or account verification keywords: ${matchedKeywords.joinToString()}.",
                    severity = if (score > 40) ThreatLevel.HIGH else ThreatLevel.LOW,
                    matchedText = matchedKeywords.joinToString(", ")
                )
            )
        }

        // 9. Non-Standard Port
        if (port != -1 && port != 80 && port != 443) {
            score += 20
            indicators.add(
                SecurityIndicator(
                    title = "Non-Standard Port (:$port)",
                    description = "Service runs on uncommon port :$port, often utilized by rogue web servers, proxy relays, or command-and-control interfaces.",
                    severity = ThreatLevel.HIGH,
                    matchedText = ":$port"
                )
            )
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

        val category = when {
            indicators.any { it.title.contains("Brand Spoofing") } -> "Brand Impersonation Phish"
            indicators.any { it.title.contains("Raw IP") } -> "Direct IP Infrastructure Host"
            indicators.any { it.title.contains("Punycode") } -> "Homograph Lookalike Attack"
            indicators.any { it.title.contains("Shortener") } -> "Obfuscated Redirector"
            score >= 50 -> "Deceptive / Malicious Web Resource"
            score >= 25 -> "Suspicious URL Parameterization"
            else -> "Clean / Low Risk URL"
        }

        val summary = when (level) {
            ThreatLevel.CRITICAL -> "CRITICAL RISK: Highly deceptive domain characteristics observed. Strong likelihood of phishing, credential harvesting, or exploit payload."
            ThreatLevel.HIGH -> "HIGH RISK: URL contains multiple high-risk indicators such as suspicious TLDs, non-standard configurations, or brand deception."
            ThreatLevel.MODERATE -> "SUSPICIOUS: Caution advised. Link presents anomalies or masks the actual endpoint."
            ThreatLevel.LOW -> "LOW RISK: Minor non-standard traits, but predominantly normal structure."
            ThreatLevel.SAFE -> "CLEAN: Standard TLS and domain structure with no prominent threat markers."
        }

        val recs = mutableListOf<String>()
        if (level == ThreatLevel.CRITICAL || level == ThreatLevel.HIGH) {
            recs.add("DO NOT navigate to this website or enter any credentials.")
            recs.add("Do not download files or allow browser notification permissions from this host.")
            recs.add("If you already submitted sensitive details, immediately rotate passwords on the genuine service.")
        } else if (level == ThreatLevel.MODERATE) {
            recs.add("Verify the root domain address carefully before entering sensitive data.")
            recs.add("Avoid clicking if this was received via an unsolicited message.")
        } else {
            recs.add("Always ensure HTTPS padlock is active when submitting confidential information.")
        }

        val baselineExplanation = buildString {
            append("Automated URL Intelligence Report:\n")
            append("Target: $normalized\n")
            append("Domain Host: $host | Scheme: ${scheme.uppercase()} | TLD: .$tld\n")
            append("Calculated Threat Score: $score/100 ($level)\n\n")
            if (indicators.isNotEmpty()) {
                append("Identified Vulnerability Flags:\n")
                indicators.forEach { ind ->
                    append("• [${ind.severity.label}] ${ind.title}: ${ind.description}\n")
                }
            } else {
                append("No anomalous host signatures, IP hostnames, or deceptive redirects identified.\n")
            }
        }

        return ThreatAnalysisResult(
            type = "URL",
            target = normalized,
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
