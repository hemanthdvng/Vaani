package com.hemanth.vaani.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "vaani_prefs")

enum class AppLanguage(val displayName: String, val instructionName: String) {
    AUTO("Match my language", "the same language the user writes in"),
    ENGLISH("English", "English"),
    HINDI("Hindi", "Hindi (Devanagari script)"),
    KANNADA("Kannada", "Kannada (Kannada script)"),
    TELUGU("Telugu", "Telugu (Telugu script)"),
    TAMIL("Tamil", "Tamil (Tamil script)")
}

/**
 * Default model: Gemma 4 E2B via litert-community (Apache-2.0, NOT gated --
 * unlike the official google/gemma-3n-* repos, no HF login/token needed).
 * Swap modelDownloadUrl in settings to point at gemma-4-E4B for better
 * quality (bigger download) since the OnePlus 15 can handle it.
 */
object VaaniDefaults {
    const val MODEL_DOWNLOAD_URL =
        "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm"
    const val MODEL_FILE_NAME = "gemma-4-E2B-it.litertlm"
}

class VaaniPreferences(private val context: Context) {

    private object Keys {
        val MODEL_URL = stringPreferencesKey("model_download_url")
        val HF_TOKEN = stringPreferencesKey("hf_token") // only needed for gated repos
        val REPLY_LANGUAGE = stringPreferencesKey("reply_language")
        val BACKEND = stringPreferencesKey("backend") // "GPU" or "CPU"
    }

    val modelDownloadUrl: Flow<String> = context.dataStore.data.map {
        it[Keys.MODEL_URL] ?: VaaniDefaults.MODEL_DOWNLOAD_URL
    }

    val hfToken: Flow<String?> = context.dataStore.data.map { it[Keys.HF_TOKEN] }

    val replyLanguage: Flow<AppLanguage> = context.dataStore.data.map {
        AppLanguage.entries.find { lang -> lang.name == it[Keys.REPLY_LANGUAGE] } ?: AppLanguage.AUTO
    }

    val backend: Flow<String> = context.dataStore.data.map { it[Keys.BACKEND] ?: "GPU" }

    suspend fun setModelDownloadUrl(url: String) {
        context.dataStore.edit { it[Keys.MODEL_URL] = url }
    }

    suspend fun setHfToken(token: String?) {
        context.dataStore.edit {
            if (token.isNullOrBlank()) it.remove(Keys.HF_TOKEN) else it[Keys.HF_TOKEN] = token
        }
    }

    suspend fun setReplyLanguage(language: AppLanguage) {
        context.dataStore.edit { it[Keys.REPLY_LANGUAGE] = language.name }
    }

    suspend fun setBackend(backend: String) {
        context.dataStore.edit { it[Keys.BACKEND] = backend }
    }
}
