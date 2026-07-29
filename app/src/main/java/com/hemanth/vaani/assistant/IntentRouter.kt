package com.hemanth.vaani.assistant

/**
 * Deterministic, offline intent classification for the small set of concrete
 * actions Vaani can take (whitelist/block last caller, show recent calls).
 *
 * This is intentionally NOT routed through the LLM: on a 2-4B on-device
 * model, asking it to reliably emit structured function-call JSON is a
 * common source of flakiness. For a fixed, small action set, keyword
 * matching is more predictable -- everything else falls through to normal
 * chat with the LLM.
 *
 * Keywords are bag-of-words per language rather than full phrases, since
 * speech recognition output phrasing varies a lot. This is a v1 heuristic:
 * expand the keyword lists here as you find phrasings it misses.
 */
object IntentRouter {

    private val actionWords = setOf(
        // English
        "whitelist", "allow", "trust", "block", "spam", "report",
        // Hindi
        "अनुमति", "भरोसा", "व्हाइटलिस्ट", "ब्लॉक", "स्पैम", "रिपोर्ट",
        // Kannada
        "ಅನುಮತಿ", "ವಿಶ್ವಾಸ", "ಬ್ಲಾಕ್", "ಸ್ಪ್ಯಾಮ್",
        // Telugu
        "అనుమతి", "నమ్మకం", "బ్లాక్", "స్పామ్",
        // Tamil
        "அனுமதி", "நம்பிக்கை", "தடு", "ஸ்பேம்"
    )

    private val blockWords = setOf(
        "block", "spam", "report", "ब्लॉक", "स्पैम", "रिपोर्ट",
        "ಬ್ಲಾಕ್", "ಸ್ಪ್ಯಾಮ್", "బ్లాక్", "స్పామ్", "தடு", "ஸ்பேம்"
    )

    private val callerReferenceWords = setOf(
        // English
        "last", "that", "caller", "call", "number",
        // Hindi
        "पिछला", "पिछली", "वह", "कॉल", "नंबर",
        // Kannada
        "ಕೊನೆಯ", "ಆ", "ಕರೆ", "ಸಂಖ್ಯೆ",
        // Telugu
        "చివరి", "ఆ", "కాల్", "నంబర్",
        // Tamil
        "கடைசி", "அந்த", "அழைப்பு", "எண்"
    )

    private val recentCallsWords = setOf(
        // English
        "recent call", "call log", "who called", "call history",
        // Hindi
        "हाल की कॉल", "कॉल लॉग", "किसने कॉल किया", "कॉल इतिहास",
        // Kannada
        "ಇತ್ತೀಚಿನ ಕರೆ", "ಕರೆ ಪಟ್ಟಿ", "ಯಾರು ಕರೆ ಮಾಡಿದ್ದಾರೆ",
        // Telugu
        "ఇటీవలి కాల్", "కాల్ లాగ్", "ఎవరు కాల్ చేశారు",
        // Tamil
        "சமீபத்திய அழைப்பு", "அழைப்பு பதிவு", "யார் அழைத்தார்கள்"
    )

    fun classify(rawText: String): AssistantAction {
        val text = rawText.trim()
        if (text.isBlank()) return AssistantAction.Chat(rawText)

        if (recentCallsWords.any { text.contains(it, ignoreCase = true) }) {
            return AssistantAction.ShowRecentCalls
        }

        val hasActionWord = actionWords.any { text.contains(it, ignoreCase = true) }
        val hasCallerReference = callerReferenceWords.any { text.contains(it, ignoreCase = true) }

        if (hasActionWord && hasCallerReference) {
            val isBlock = blockWords.any { text.contains(it, ignoreCase = true) }
            return if (isBlock) AssistantAction.ReportLastCallerAsSpam else AssistantAction.WhitelistLastCaller
        }

        return AssistantAction.Chat(rawText)
    }
}
