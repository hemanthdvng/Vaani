package com.hemanth.vaani.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.hemanth.vaani.data.AppLanguage
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.util.Locale
import java.util.UUID

class VoiceOutputManager(private val context: Context) {

    private var tts: TextToSpeech? = null
    private var isReady = false
    private var pendingLanguage: AppLanguage? = null

    fun initialize(onReady: (Boolean) -> Unit = {}) {
        if (tts != null) return
        tts = TextToSpeech(context) { status ->
            isReady = status == TextToSpeech.SUCCESS
            if (isReady) {
                pendingLanguage?.let { applyLanguage(it) }
            }
            onReady(isReady)
        }
    }

    fun setLanguage(language: AppLanguage) {
        if (!isReady) {
            pendingLanguage = language
            return
        }
        applyLanguage(language)
    }

    private fun applyLanguage(language: AppLanguage) {
        val locale = VoiceLanguageMapper.ttsLocale(language)
        val result = tts?.setLanguage(locale)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            // Fall back silently to whatever the engine already had (usually
            // English) rather than crashing -- not every OEM TTS engine ships
            // Kannada/Telugu/Tamil voice data out of the box.
            tts?.setLanguage(Locale.ENGLISH)
        }
    }

    /** Speaks text, emitting true on completion, false on error/skip. */
    fun speak(text: String): Flow<Boolean> = callbackFlow {
        if (!isReady || text.isBlank()) {
            trySend(false)
            close()
            return@callbackFlow
        }

        val utteranceId = UUID.randomUUID().toString()
        val listener = object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit
            override fun onDone(utteranceId: String?) {
                trySend(true)
                close()
            }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                trySend(false)
                close()
            }
        }
        tts?.setOnUtteranceProgressListener(listener)
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)

        awaitClose { /* Don't stop() here -- let in-flight speech finish naturally. */ }
    }

    fun stopSpeaking() {
        tts?.stop()
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isReady = false
    }
}
