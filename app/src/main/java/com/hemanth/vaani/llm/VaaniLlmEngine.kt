package com.hemanth.vaani.llm

import android.content.Context
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig
import com.hemanth.vaani.data.AppLanguage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import com.google.ai.edge.litertlm.Conversation as LiteRtConversation

/**
 * Thin wrapper around the LiteRT-LM Kotlin API.
 *
 * initialize() tries GPU first (best throughput on the OnePlus 15's Adreno
 * GPU), and transparently falls back to CPU if GPU init throws -- some
 * devices/driver combos don't support every backend for every model.
 *
 * engine.initialize() can take several seconds to load a multi-GB model,
 * so this always runs on Dispatchers.IO, never the main thread.
 */
class VaaniLlmEngine(private val context: Context) {

    private var engine: Engine? = null
    private var conversation: LiteRtConversation? = null

    suspend fun initialize(modelPath: String, preferGpu: Boolean, language: AppLanguage): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val backend = if (preferGpu) Backend.GPU() else Backend.CPU()
                val config = buildEngineConfig(modelPath, backend)
                var activeEngine = Engine(config)
                try {
                    activeEngine.initialize()
                } catch (gpuFailure: Exception) {
                    if (!preferGpu) throw gpuFailure
                    // Retry once on CPU before giving up entirely.
                    activeEngine.close()
                    val cpuEngine = Engine(buildEngineConfig(modelPath, Backend.CPU()))
                    cpuEngine.initialize()
                    activeEngine = cpuEngine
                }
                engine = activeEngine
                conversation = activeEngine.createConversation(buildConversationConfig(language))
            }
        }

    private fun buildEngineConfig(modelPath: String, backend: Backend) = EngineConfig(
        modelPath = modelPath,
        backend = backend,
        cacheDir = context.cacheDir.path // speeds up subsequent loads
    )

    private fun buildConversationConfig(language: AppLanguage) = ConversationConfig(
        systemInstruction = Contents.of(systemPrompt(language)),
        samplerConfig = SamplerConfig(topK = 40, topP = 0.95, temperature = 0.7)
    )

    private fun systemPrompt(language: AppLanguage): String =
        "You are Vaani, a helpful on-device voice assistant for an Android phone. " +
            "You understand English, Hindi, Kannada, Telugu, and Tamil. " +
            "Reply in ${language.instructionName}. " +
            "Keep answers short and conversational, suitable for being read aloud."

    /** Streams response tokens as they're generated. */
    fun sendMessage(text: String): Flow<String> {
        val activeConversation = conversation
            ?: return flow { throw IllegalStateException("Engine not initialized") }

        return activeConversation.sendMessageAsync(text)
            .map { it.toString() }
            .flowOn(Dispatchers.IO)
    }

    suspend fun updateLanguage(language: AppLanguage) = withContext(Dispatchers.IO) {
        val currentEngine = engine ?: return@withContext
        conversation?.close()
        conversation = currentEngine.createConversation(buildConversationConfig(language))
    }

    fun close() {
        conversation?.close()
        engine?.close()
        conversation = null
        engine = null
    }

    fun isReady(): Boolean = engine != null && conversation != null
}
