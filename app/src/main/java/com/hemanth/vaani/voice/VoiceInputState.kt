package com.hemanth.vaani.voice

sealed class VoiceInputState {
    data object Idle : VoiceInputState()
    data object Listening : VoiceInputState()
    data class Partial(val text: String) : VoiceInputState()
    data class Final(val text: String) : VoiceInputState()
    data class Error(val message: String) : VoiceInputState()
}
