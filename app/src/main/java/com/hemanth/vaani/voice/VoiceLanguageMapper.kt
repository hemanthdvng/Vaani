package com.hemanth.vaani.voice

import com.hemanth.vaani.data.AppLanguage
import java.util.Locale

object VoiceLanguageMapper {

    /** BCP-47 tag for SpeechRecognizer's EXTRA_LANGUAGE. */
    fun recognizerTag(language: AppLanguage): String = when (language) {
        AppLanguage.ENGLISH -> "en-IN"
        AppLanguage.HINDI -> "hi-IN"
        AppLanguage.KANNADA -> "kn-IN"
        AppLanguage.TELUGU -> "te-IN"
        AppLanguage.TAMIL -> "ta-IN"
        // SpeechRecognizer has no true "auto-detect" mode -- default to the
        // device's own locale, which is usually what the user actually speaks.
        AppLanguage.AUTO -> Locale.getDefault().toLanguageTag().ifBlank { "en-IN" }
    }

    /** Locale for TextToSpeech.setLanguage(). */
    fun ttsLocale(language: AppLanguage): Locale = when (language) {
        AppLanguage.ENGLISH -> Locale("en", "IN")
        AppLanguage.HINDI -> Locale("hi", "IN")
        AppLanguage.KANNADA -> Locale("kn", "IN")
        AppLanguage.TELUGU -> Locale("te", "IN")
        AppLanguage.TAMIL -> Locale("ta", "IN")
        AppLanguage.AUTO -> Locale.getDefault()
    }
}
