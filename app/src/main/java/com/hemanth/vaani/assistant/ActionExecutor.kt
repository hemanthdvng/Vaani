package com.hemanth.vaani.assistant

import com.hemanth.vaani.call.SpamScorer
import com.hemanth.vaani.data.AppLanguage
import com.hemanth.vaani.data.ScreeningOutcome
import com.hemanth.vaani.data.VaaniDao
import com.hemanth.vaani.data.WhitelistEntity

/**
 * Runs the side effect for a classified AssistantAction and returns a
 * ready-to-display (and speak) confirmation string in the user's chosen
 * language. Keeping these as fixed, reviewed templates rather than letting
 * the LLM phrase confirmations avoids ambiguity about whether an action
 * (like reporting spam) actually happened.
 */
class ActionExecutor(
    private val dao: VaaniDao,
    private val spamScorer: SpamScorer
) {
    suspend fun execute(action: AssistantAction, language: AppLanguage): String? = when (action) {
        is AssistantAction.WhitelistLastCaller -> whitelistLastCaller(language)
        is AssistantAction.ReportLastCallerAsSpam -> reportLastCallerAsSpam(language)
        is AssistantAction.ShowRecentCalls -> showRecentCalls(language)
        is AssistantAction.Chat -> null // not an action -- caller should route to the LLM
    }

    private suspend fun whitelistLastCaller(language: AppLanguage): String {
        val lastCall = dao.getLastCall() ?: return Strings.noRecentCalls(language)
        dao.upsertWhitelistEntry(
            WhitelistEntity(
                phoneNumber = lastCall.phoneNumber,
                label = "Added via Vaani",
                addedMillis = System.currentTimeMillis()
            )
        )
        return Strings.whitelisted(language, lastCall.phoneNumber)
    }

    private suspend fun reportLastCallerAsSpam(language: AppLanguage): String {
        val lastCall = dao.getLastCall() ?: return Strings.noRecentCalls(language)
        spamScorer.reportAsSpam(lastCall.phoneNumber)
        return Strings.spamReported(language, lastCall.phoneNumber)
    }

    private suspend fun showRecentCalls(language: AppLanguage): String {
        val recent = dao.getRecentCalls(limit = 5)
        if (recent.isEmpty()) return Strings.noRecentCalls(language)

        val lines = recent.joinToString("\n") { entry ->
            "${entry.phoneNumber} -- ${Strings.outcomeLabel(language, entry.outcome)}"
        }
        return "${Strings.recentCallsHeader(language)}\n$lines"
    }
}

private object Strings {
    fun noRecentCalls(language: AppLanguage): String = when (language) {
        AppLanguage.HINDI -> "अभी तक कोई हाल की कॉल नहीं है।"
        AppLanguage.KANNADA -> "ಇನ್ನೂ ಯಾವುದೇ ಇತ್ತೀಚಿನ ಕರೆಗಳಿಲ್ಲ."
        AppLanguage.TELUGU -> "ఇంకా ఇటీవలి కాల్‌లు లేవు."
        AppLanguage.TAMIL -> "இதுவரை சமீபத்திய அழைப்புகள் இல்லை."
        else -> "You don't have any recent calls yet."
    }

    fun whitelisted(language: AppLanguage, number: String): String = when (language) {
        AppLanguage.HINDI -> "हो गया -- मैंने $number को भरोसेमंद सूची में जोड़ दिया है। अब यह हमेशा बजेगी।"
        AppLanguage.KANNADA -> "ಆಯಿತು -- ನಾನು $number ಅನ್ನು ವಿಶ್ವಾಸಾರ್ಹ ಪಟ್ಟಿಗೆ ಸೇರಿಸಿದ್ದೇನೆ. ಇದು ಇನ್ನು ಮುಂದೆ ಯಾವಾಗಲೂ ರಿಂಗ್ ಆಗುತ್ತದೆ."
        AppLanguage.TELUGU -> "అయ్యింది -- నేను $number ని విశ్వసనీయ జాబితాలో చేర్చాను. ఇక నుండి ఇది ఎప్పుడూ మోగుతుంది."
        AppLanguage.TAMIL -> "முடிந்தது -- நான் $number ஐ நம்பகமான பட்டியலில் சேர்த்துவிட்டேன். இனி இது எப்போதும் ஒலிக்கும்."
        else -> "Done -- I've added $number to your trusted list. It'll always ring through now."
    }

    fun spamReported(language: AppLanguage, number: String): String = when (language) {
        AppLanguage.HINDI -> "समझ गया -- मैंने $number को स्पैम के रूप में चिह्नित कर दिया है। अगली बार वाणी इसे चुपचाप रोक देगी।"
        AppLanguage.KANNADA -> "ಸರಿ -- ನಾನು $number ಅನ್ನು ಸ್ಪ್ಯಾಮ್ ಎಂದು ಗುರುತಿಸಿದ್ದೇನೆ. ಮುಂದಿನ ಬಾರಿ ವಾಣಿ ಅದನ್ನು ಮೌನವಾಗಿ ನಿರ್ಬಂಧಿಸುತ್ತದೆ."
        AppLanguage.TELUGU -> "సరే -- నేను $number ని స్పామ్‌గా గుర్తించాను. తదుపరిసారి వాణి దాన్ని నిశ్శబ్దంగా బ్లాక్ చేస్తుంది."
        AppLanguage.TAMIL -> "சரி -- நான் $number ஐ ஸ்பேமாக குறித்துவிட்டேன். அடுத்த முறை வாணி அதை அமைதியாக தடுக்கும்."
        else -> "Got it -- I've marked $number as spam. Vaani will silently block it next time."
    }

    fun recentCallsHeader(language: AppLanguage): String = when (language) {
        AppLanguage.HINDI -> "आपकी हाल की कॉलें यह हैं:"
        AppLanguage.KANNADA -> "ನಿಮ್ಮ ಇತ್ತೀಚಿನ ಕರೆಗಳು ಇಲ್ಲಿವೆ:"
        AppLanguage.TELUGU -> "మీ ఇటీవలి కాల్‌లు ఇవి:"
        AppLanguage.TAMIL -> "உங்கள் சமீபத்திய அழைப்புகள் இதோ:"
        else -> "Here are your recent calls:"
    }

    fun outcomeLabel(language: AppLanguage, outcome: ScreeningOutcome): String = when (outcome) {
        ScreeningOutcome.ALLOWED -> when (language) {
            AppLanguage.HINDI -> "बजी"
            AppLanguage.KANNADA -> "ರಿಂಗ್ ಆಯಿತು"
            AppLanguage.TELUGU -> "మోగింది"
            AppLanguage.TAMIL -> "ஒலித்தது"
            else -> "Rang"
        }
        ScreeningOutcome.WHITELISTED -> when (language) {
            AppLanguage.HINDI -> "भरोसेमंद"
            AppLanguage.KANNADA -> "ವಿಶ್ವಾಸಾರ್ಹ"
            AppLanguage.TELUGU -> "విశ్వసనీయమైనది"
            AppLanguage.TAMIL -> "நம்பகமானது"
            else -> "Trusted"
        }
        ScreeningOutcome.SILENCED_SPAM -> when (language) {
            AppLanguage.HINDI -> "स्पैम -- चुप कराया"
            AppLanguage.KANNADA -> "ಸ್ಪ್ಯಾಮ್ -- ಮೌನಗೊಳಿಸಲಾಗಿದೆ"
            AppLanguage.TELUGU -> "స్పామ్ -- నిశ్శబ్దం చేయబడింది"
            AppLanguage.TAMIL -> "ஸ்பேம் -- அமைதியாக்கப்பட்டது"
            else -> "Spam -- silenced"
        }
    }
}
