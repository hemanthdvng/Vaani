package com.hemanth.vaani.assistant

sealed class AssistantAction {
    data object WhitelistLastCaller : AssistantAction()
    data object ReportLastCallerAsSpam : AssistantAction()
    data object ShowRecentCalls : AssistantAction()
    data class Chat(val text: String) : AssistantAction()
}
