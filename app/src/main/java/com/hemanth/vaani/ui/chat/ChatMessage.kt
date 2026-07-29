package com.hemanth.vaani.ui.chat

data class ChatMessage(
    val text: String,
    val isFromUser: Boolean,
    val timestampMillis: Long = System.currentTimeMillis()
)
