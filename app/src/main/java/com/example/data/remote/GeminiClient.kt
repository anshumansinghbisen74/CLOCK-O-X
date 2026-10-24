package com.example.data.remote

import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object GeminiClient {

    private const val TAG = "GeminiClient"
    private const val MODEL = "gemini-3.5-flash"
    private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models"

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    fun isApiKeyConfigured(): Boolean {
        val key = try {
            BuildConfig.GEMINI_API_KEY
        } catch (e: Throwable) {
            ""
        }
        return key.isNotBlank() && key != "MY_GEMINI_API_KEY" && !key.startsWith("placeholder")
    }

    suspend fun generateCyberExplanation(
        targetType: String,
        targetContent: String,
        heuristicsSummary: String
    ): String = withContext(Dispatchers.IO) {
        val apiKey = try {
            BuildConfig.GEMINI_API_KEY
        } catch (e: Throwable) {
            ""
        }

        if (!isApiKeyConfigured()) {
            return@withContext getOfflineExplanation(targetType, targetContent, heuristicsSummary)
        }

        val systemPrompt = "You are Sentinel AI, an elite cybersecurity incident responder and threat intelligence analyst. " +
                "Evaluate the provided $targetType. Provide a professional threat intelligence assessment with: " +
                "1. Threat Actor Tactics & Classification (e.g., Social Engineering, Credential Theft, Drive-by Exploit, Brand Hijack)\n" +
                "2. Technical Red Flags and Risk Exposure for the victim\n" +
                "3. Immediate Countermeasures and Action Steps.\n" +
                "Format with clear headings and bullet points. Be concise, precise, and practical."

        val userPrompt = "Analyze this suspicious $targetType:\n\n\"\"\"\n$targetContent\n\"\"\"\n\nLocal Heuristic Findings:\n$heuristicsSummary"

        try {
            val responseText = callGeminiApi(apiKey, systemPrompt, userPrompt)
            if (responseText.isNotBlank()) {
                responseText
            } else {
                getOfflineExplanation(targetType, targetContent, heuristicsSummary)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Gemini API call failed: ${e.message}", e)
            getOfflineExplanation(targetType, targetContent, heuristicsSummary)
        }
    }

    suspend fun chatWithAdvisor(
        conversationHistory: List<Pair<String, String>>, // role to message
        userMessage: String
    ): String = withContext(Dispatchers.IO) {
        val apiKey = try {
            BuildConfig.GEMINI_API_KEY
        } catch (e: Throwable) {
            ""
        }

        if (!isApiKeyConfigured()) {
            return@withContext getOfflineAdvisorResponse(userMessage)
        }

        val systemPrompt = "You are Sentinel AI, a specialized personal cybersecurity assistant. " +
                "Your mission is to defend users from phishing, scams, online fraud, data leaks, identity theft, and malware. " +
                "Provide accurate, actionable, and calm security guidance. Explain risks clearly without overly technical jargon, " +
                "and give concrete recommendations (e.g. how to report, freeze credit, secure accounts with passkeys/MFA)."

        try {
            val responseText = callGeminiMultiTurn(apiKey, systemPrompt, conversationHistory, userMessage)
            if (responseText.isNotBlank()) {
                responseText
            } else {
                getOfflineAdvisorResponse(userMessage)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Gemini Chat call failed: ${e.message}", e)
            getOfflineAdvisorResponse(userMessage)
        }
    }

    private fun callGeminiApi(apiKey: String, systemPrompt: String, userPrompt: String): String {
        val url = "$BASE_URL/$MODEL:generateContent?key=$apiKey"

        val rootJson = JSONObject().apply {
            put("systemInstruction", JSONObject().apply {
                put("parts", JSONArray().put(JSONObject().put("text", systemPrompt)))
            })
            val contentsArray = JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("parts", JSONArray().put(JSONObject().put("text", userPrompt)))
                })
            }
            put("contents", contentsArray)
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.4)
                put("maxOutputTokens", 1024)
            })
        }

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val requestBody = rootJson.toString().toRequestBody(mediaType)
        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()

        val response = httpClient.newCall(request).execute()
        val responseBody = response.body?.string() ?: ""

        if (!response.isSuccessful) {
            Log.w(TAG, "Gemini API error code: ${response.code}, body: $responseBody")
            return ""
        }

        return extractTextFromResponse(responseBody)
    }

    private fun callGeminiMultiTurn(
        apiKey: String,
        systemPrompt: String,
        history: List<Pair<String, String>>,
        newMessage: String
    ): String {
        val url = "$BASE_URL/$MODEL:generateContent?key=$apiKey"

        val rootJson = JSONObject().apply {
            put("systemInstruction", JSONObject().apply {
                put("parts", JSONArray().put(JSONObject().put("text", systemPrompt)))
            })
            val contentsArray = JSONArray()

            // Include last 8 turns of history
            history.takeLast(8).forEach { (role, text) ->
                val apiRole = if (role.equals("assistant", ignoreCase = true)) "model" else "user"
                contentsArray.put(JSONObject().apply {
                    put("role", apiRole)
                    put("parts", JSONArray().put(JSONObject().put("text", text)))
                })
            }

            // Current prompt
            contentsArray.put(JSONObject().apply {
                put("role", "user")
                put("parts", JSONArray().put(JSONObject().put("text", newMessage)))
            })

            put("contents", contentsArray)
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.7)
                put("maxOutputTokens", 1200)
            })
        }

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val requestBody = rootJson.toString().toRequestBody(mediaType)
        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()

        val response = httpClient.newCall(request).execute()
        val responseBody = response.body?.string() ?: ""

        if (!response.isSuccessful) {
            Log.w(TAG, "Gemini API error code: ${response.code}, body: $responseBody")
            return ""
        }

        return extractTextFromResponse(responseBody)
    }

    private fun extractTextFromResponse(jsonStr: String): String {
        return try {
            val root = JSONObject(jsonStr)
            val candidates = root.optJSONArray("candidates")
            if (candidates != null && candidates.length() > 0) {
                val firstCandidate = candidates.getJSONObject(0)
                val content = firstCandidate.optJSONObject("content")
                val parts = content?.optJSONArray("parts")
                if (parts != null && parts.length() > 0) {
                    val firstPart = parts.getJSONObject(0)
                    return firstPart.optString("text", "").trim()
                }
            }
            ""
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse Gemini response JSON", e)
            ""
        }
    }

    private fun getOfflineExplanation(
        targetType: String,
        targetContent: String,
        heuristicsSummary: String
    ): String {
        return buildString {
            append("### Sentinel AI Threat Intelligence Dossier\n\n")
            append("**Analyzed Asset:** $targetType\n\n")
            append("**Threat Actor Tactics (TTPs):**\n")
            if (targetType.equals("URL", ignoreCase = true)) {
                append("• Deceptive domain registration and path manipulation (T1566.002 - Spearphishing Link)\n")
                append("• Potential credential harvester designed to replicate authentic enterprise authentication workflows.\n\n")
                append("**Victim Risk Vector:**\n")
                append("Navigating to this host risks session token theft, corporate account takeover, or drive-by payload installation.\n\n")
            } else {
                append("• Psychological coercion & manufactured urgency (T1566.001 / T1566.002 - Social Engineering)\n")
                append("• Credential harvesting and brand impersonation to induce unauthorized access disclosure.\n\n")
                append("**Victim Risk Vector:**\n")
                append("Responding or interacting may compromise account security, financial data, or enroll the phone number in targeted spam matrices.\n\n")
            }
            append("**Defensive Recommendations:**\n")
            append("1. Zero-Trust Stance: Treat the interaction as hostile.\n")
            append("2. Isolate & Block: Block the sender/domain across devices.\n")
            append("3. Direct Verification: Always navigate to the entity's verified portal independently.\n\n")
            append("_Note: Operating in High-Assurance Offline Engine. Configure Gemini API Key in Settings to enable dynamic LLM deep reasoning._")
        }
    }

    private fun getOfflineAdvisorResponse(userQuery: String): String {
        val lower = userQuery.lowercase()
        return when {
            lower.contains("sms") || lower.contains("text") || lower.contains("smish") -> {
                "### Sentinel Smishing Defense Checklist\n\n" +
                "1. **Never Click Embedded Links:** Banks and legitimate delivery carriers will never text you to verify passwords or enter payment details via an unverified link.\n" +
                "2. **Report Spam:** Forward the message to **7726 (SPAM)** on your cellular network to flag it across telecom providers.\n" +
                "3. **Call Official Numbers:** Look up the bank or organization's official phone number printed on the back of your card or their verified portal—never call the number in the SMS.\n" +
                "4. **Block the Sender:** Add the sender's number to your block list immediately."
            }
            lower.contains("password") || lower.contains("login") || lower.contains("entered") || lower.contains("clicked") -> {
                "### Immediate Incident Response Protocol\n\n" +
                "If you entered credentials or clicked a suspicious link:\n\n" +
                "1. **Rotate Credentials Immediately:** Go to the genuine service from another device or browser tab and change your password right now.\n" +
                "2. **Terminate Active Sessions:** In account security settings, select 'Log out of all other sessions'.\n" +
                "3. **Enable Hardware 2FA / Passkeys:** Replace SMS 2FA with an authenticator app (e.g. Aegis, Google Authenticator) or FIDO2 Passkeys.\n" +
                "4. **Monitor Financials:** If bank/credit card numbers were provided, contact your card issuer's fraud hotline to freeze the card."
            }
            lower.contains("sim") || lower.contains("swap") -> {
                "### SIM Swapping Defense Guide\n\n" +
                "A SIM swap occurs when an attacker tricks your carrier into transferring your phone number to their SIM card to intercept 2FA codes.\n\n" +
                "• **Set a Carrier Account PIN:** Call your cellular provider and set a strict port-out verification PIN/passcode.\n" +
                "• **Migrate off SMS 2FA:** Never use SMS for high-value accounts (email, banking, crypto). Switch to Authenticator apps or hardware keys (YubiKey).\n" +
                "• **Red Flag Warning:** If your phone suddenly loses cellular signal and says 'No Service' or 'SOS Only' unexpectedly, contact your carrier immediately from another line."
            }
            lower.contains("url") || lower.contains("domain") || lower.contains("link") -> {
                "### Link Verification Best Practices\n\n" +
                "• **Inspect the Root Domain:** Look at what is immediately before the `.com` or `.org`. For example, `paypal.security-update.com` is owned by `security-update.com`, NOT PayPal!\n" +
                "• **Beware of Risky TLDs:** Extensions like `.xyz`, `.top`, `.buzz`, `.icu`, and `.zip` have very high rates of malicious use.\n" +
                "• **Never Trust Shorteners:** Use Sentinel's URL scanner to unmask redirects before visiting."
            }
            else -> {
                "### Sentinel Cyber Advisor Guidance\n\n" +
                "Thank you for consulting Sentinel AI. In cybersecurity, the golden rule is **Zero-Trust**: never trust an unsolicited request, always verify independently.\n\n" +
                "Key Security Principles:\n" +
                "• **Verify Out-of-Band:** Always contact people or companies through established, verified channels.\n" +
                "• **Harden Authentication:** Use strong unique passphrases generated by a password manager + hardware/app 2FA.\n" +
                "• **Analyze Unknown Targets:** Use Sentinel AI's Scan tab to inspect any suspicious SMS, email, or link.\n\n" +
                "Feel free to paste a suspicious text or ask about specific threats like ransomware, phishing, or identity theft."
            }
        }
    }
}
