package com.hemanth.vaani.ui.chat

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hemanth.vaani.assistant.ActionExecutor
import com.hemanth.vaani.assistant.AssistantAction
import com.hemanth.vaani.assistant.IntentRouter
import com.hemanth.vaani.call.SpamScorer
import com.hemanth.vaani.data.AppLanguage
import com.hemanth.vaani.data.VaaniDatabase
import com.hemanth.vaani.data.VaaniDefaults
import com.hemanth.vaani.data.VaaniPreferences
import com.hemanth.vaani.llm.ModelDownloadManager
import com.hemanth.vaani.llm.ModelState
import com.hemanth.vaani.llm.VaaniLlmEngine
import com.hemanth.vaani.voice.VoiceInputManager
import com.hemanth.vaani.voice.VoiceInputState
import com.hemanth.vaani.voice.VoiceOutputManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val preferences = VaaniPreferences(application)
    private val downloadManager = ModelDownloadManager(application)
    private val engine = VaaniLlmEngine(application)

    private val dao = VaaniDatabase.getInstance(application).vaaniDao()
    private val spamScorer = SpamScorer(application, dao)
    private val actionExecutor = ActionExecutor(dao, spamScorer)

    private val voiceInput = VoiceInputManager(application)
    private val voiceOutput = VoiceOutputManager(application)
    private var listeningJob: Job? = null

    private val _modelState = MutableStateFlow<ModelState>(ModelState.NotDownloaded)
    val modelState: StateFlow<ModelState> = _modelState.asStateFlow()

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _language = MutableStateFlow(AppLanguage.AUTO)
    val language: StateFlow<AppLanguage> = _language.asStateFlow()

    private val _preferGpu = MutableStateFlow(true)
    val preferGpu: StateFlow<Boolean> = _preferGpu.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private val _voiceInputState = MutableStateFlow<VoiceInputState>(VoiceInputState.Idle)
    val voiceInputState: StateFlow<VoiceInputState> = _voiceInputState.asStateFlow()

    private val _speakRepliesEnabled = MutableStateFlow(true)
    val speakRepliesEnabled: StateFlow<Boolean> = _speakRepliesEnabled.asStateFlow()

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _continuousModeEnabled = MutableStateFlow(false)
    val continuousModeEnabled: StateFlow<Boolean> = _continuousModeEnabled.asStateFlow()

    init {
        voiceOutput.initialize { voiceOutput.setLanguage(_language.value) }
        viewModelScope.launch {
            _language.value = preferences.replyLanguage.first()
            _preferGpu.value = preferences.backend.first() == "GPU"
            voiceOutput.setLanguage(_language.value)
        }
        checkExistingModel()
    }

    fun setSpeakRepliesEnabled(enabled: Boolean) {
        _speakRepliesEnabled.value = enabled
        if (!enabled) voiceOutput.stopSpeaking()
    }

    /**
     * Hands-free mode: after Vaani finishes speaking a reply, the mic
     * automatically re-arms for the next thing you say -- no need to tap
     * the mic button each turn. Turning this off returns to tap-to-talk.
     */
    fun setContinuousModeEnabled(enabled: Boolean) {
        _continuousModeEnabled.value = enabled
        if (!enabled) stopVoiceInput()
    }

    /** Starts listening; auto-sends the final transcript as a message. */
    fun startVoiceInput() {
        if (listeningJob?.isActive == true || _isSpeaking.value) return
        listeningJob = viewModelScope.launch {
            voiceInput.startListening(_language.value).collect { state ->
                _voiceInputState.value = state
                if (state is VoiceInputState.Final) {
                    if (state.text.isNotBlank()) sendMessage(state.text)
                    _voiceInputState.value = VoiceInputState.Idle
                }
            }
        }
    }

    fun stopVoiceInput() {
        listeningJob?.cancel()
        listeningJob = null
        _voiceInputState.value = VoiceInputState.Idle
    }

    fun setPreferGpu(useGpu: Boolean) {
        _preferGpu.value = useGpu
        viewModelScope.launch { preferences.setBackend(if (useGpu) "GPU" else "CPU") }
        // Backend choice only takes effect on the next engine init, so if the
        // model is already loaded, re-initialize with the new backend.
        if (_modelState.value is ModelState.Ready) {
            viewModelScope.launch { initializeEngine() }
        }
    }

    private fun checkExistingModel() {
        if (downloadManager.isDownloaded(VaaniDefaults.MODEL_FILE_NAME)) {
            _modelState.value = ModelState.Downloaded
            viewModelScope.launch { initializeEngine() }
        }
    }

    /** Call when the user taps "Download & start" on the setup card. */
    fun downloadAndInitialize() {
        viewModelScope.launch {
            val url = preferences.modelDownloadUrl.first()
            val token = preferences.hfToken.first()

            if (!downloadManager.isDownloaded(VaaniDefaults.MODEL_FILE_NAME)) {
                downloadManager.download(url, VaaniDefaults.MODEL_FILE_NAME, token)
                    .collect { state ->
                        _modelState.value = state
                        if (state is ModelState.Error) return@collect
                    }
            }

            if (_modelState.value is ModelState.Error) return@launch

            initializeEngine()
        }
    }

    private suspend fun initializeEngine() {
        _modelState.value = ModelState.InitializingEngine
        val modelFile = downloadManager.modelFile(VaaniDefaults.MODEL_FILE_NAME)

        val result = engine.initialize(
            modelPath = modelFile.absolutePath,
            preferGpu = _preferGpu.value,
            language = _language.value
        )

        _modelState.value = result.fold(
            onSuccess = { ModelState.Ready },
            onFailure = { ModelState.Error(it.message ?: "Engine failed to initialize") }
        )
    }

    fun setLanguage(newLanguage: AppLanguage) {
        _language.value = newLanguage
        voiceOutput.setLanguage(newLanguage)
        viewModelScope.launch {
            preferences.setReplyLanguage(newLanguage)
            if (engine.isReady()) {
                engine.updateLanguage(newLanguage)
            }
        }
    }

    fun sendMessage(text: String) {
        if (text.isBlank() || _isGenerating.value) return

        _messages.value = _messages.value + ChatMessage(text, isFromUser = true)

        val action = IntentRouter.classify(text)
        if (action !is AssistantAction.Chat) {
            _isGenerating.value = true
            viewModelScope.launch {
                val confirmation = actionExecutor.execute(action, _language.value)
                    ?: "Done."
                _messages.value = _messages.value + ChatMessage(confirmation, isFromUser = false)
                _isGenerating.value = false
                speakThenMaybeRelisten(confirmation)
            }
            return
        }

        if (!engine.isReady()) return

        _isGenerating.value = true
        val responseIndex = _messages.value.size
        _messages.value = _messages.value + ChatMessage("", isFromUser = false)

        viewModelScope.launch {
            val builder = StringBuilder()
            engine.sendMessage(text)
                .catch { e ->
                    builder.append("\n[Error: ${e.message}]")
                    updateMessageAt(responseIndex, builder.toString())
                }
                .collect { chunk ->
                    builder.append(chunk)
                    updateMessageAt(responseIndex, builder.toString())
                }
            _isGenerating.value = false
            speakThenMaybeRelisten(builder.toString())
        }
    }

    /**
     * Speaks the reply (if enabled), waits for it to fully finish, adds a
     * short decay buffer for any lingering speaker echo, THEN -- and only
     * then -- re-arms the mic if continuous mode is on. This ordering is
     * the actual mechanism that stops Vaani from hearing its own voice:
     * listening and speaking never overlap in time, by construction.
     */
    private suspend fun speakThenMaybeRelisten(text: String) {
        if (_speakRepliesEnabled.value && text.isNotBlank()) {
            _isSpeaking.value = true
            voiceOutput.speak(text).collect {}
            delay(400) // let any trailing speaker echo in the room decay
            _isSpeaking.value = false
        }
        if (_continuousModeEnabled.value) {
            startVoiceInput()
        }
    }

    private fun updateMessageAt(index: Int, text: String) {
        val current = _messages.value.toMutableList()
        if (index in current.indices) {
            current[index] = current[index].copy(text = text)
            _messages.value = current
        }
    }

    override fun onCleared() {
        super.onCleared()
        engine.close()
        voiceOutput.shutdown()
        listeningJob?.cancel()
    }
}
