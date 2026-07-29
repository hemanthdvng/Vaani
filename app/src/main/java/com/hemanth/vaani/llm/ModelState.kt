package com.hemanth.vaani.llm

sealed class ModelState {
    data object NotDownloaded : ModelState()
    data class Downloading(val bytesDownloaded: Long, val totalBytes: Long) : ModelState() {
        val progress: Float get() = if (totalBytes > 0) bytesDownloaded.toFloat() / totalBytes else 0f
    }
    data object Verifying : ModelState()
    data object Downloaded : ModelState()
    data object InitializingEngine : ModelState()
    data object Ready : ModelState()
    data class Error(val message: String) : ModelState()
}
