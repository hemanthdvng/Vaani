package com.hemanth.vaani.llm

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL

/**
 * Downloads a .litertlm model file to context.filesDir/models/.
 * Resumable: on interruption (network drop, app killed), re-calling
 * download() picks up from the .part file's current size via an HTTP
 * Range request, rather than restarting a 3-4 GB download from zero.
 */
class ModelDownloadManager(private val context: Context) {

    private val modelsDir: File by lazy {
        File(context.filesDir, "models").apply { mkdirs() }
    }

    fun modelFile(fileName: String): File = File(modelsDir, fileName)

    private fun partFile(fileName: String): File = File(modelsDir, "$fileName.part")

    fun isDownloaded(fileName: String, expectedMinBytes: Long = 100_000_000L): Boolean {
        val file = modelFile(fileName)
        return file.exists() && file.length() > expectedMinBytes
    }

    /**
     * Emits ModelState.Downloading progress updates, then ModelState.Downloaded
     * on success, or ModelState.Error on failure. Safe to retry after an error --
     * it will resume from wherever the .part file left off.
     */
    fun download(url: String, fileName: String, hfToken: String?): Flow<ModelState> = flow {
        val destination = modelFile(fileName)
        if (destination.exists() && destination.length() > 0) {
            emit(ModelState.Downloaded)
            return@flow
        }

        val part = partFile(fileName)
        var existingBytes = if (part.exists()) part.length() else 0L

        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            if (!hfToken.isNullOrBlank()) {
                setRequestProperty("Authorization", "Bearer $hfToken")
            }
            if (existingBytes > 0) {
                setRequestProperty("Range", "bytes=$existingBytes-")
            }
            instanceFollowRedirects = true
        }

        connection.connect()

        val responseCode = connection.responseCode
        val supportsResume = responseCode == HttpURLConnection.HTTP_PARTIAL
        if (responseCode !in listOf(HttpURLConnection.HTTP_OK, HttpURLConnection.HTTP_PARTIAL)) {
            emit(ModelState.Error("Download failed: HTTP $responseCode. Check the URL and (if the repo is gated) your HF token."))
            connection.disconnect()
            return@flow
        }

        if (!supportsResume) {
            // Server ignored our Range request (some CDNs do on redirect) --
            // start over cleanly to avoid corrupting the file.
            existingBytes = 0L
            part.delete()
        }

        val contentLengthThisRequest = connection.contentLengthLong
        val totalBytes = if (contentLengthThisRequest > 0) existingBytes + contentLengthThisRequest else -1L

        connection.inputStream.use { input ->
            RandomAccessFile(part, "rw").use { output ->
                output.seek(existingBytes)
                val buffer = ByteArray(64 * 1024)
                var downloaded = existingBytes
                var lastEmitTime = 0L

                while (true) {
                    val read = input.read(buffer)
                    if (read == -1) break
                    output.write(buffer, 0, read)
                    downloaded += read

                    val now = System.currentTimeMillis()
                    if (now - lastEmitTime > 200) { // throttle UI updates
                        emit(ModelState.Downloading(downloaded, totalBytes))
                        lastEmitTime = now
                    }
                }
                emit(ModelState.Downloading(downloaded, totalBytes))
            }
        }
        connection.disconnect()

        emit(ModelState.Verifying)
        if (!part.renameTo(destination)) {
            emit(ModelState.Error("Downloaded file could not be moved into place."))
            return@flow
        }
        emit(ModelState.Downloaded)
    }.flowOn(Dispatchers.IO)

    suspend fun deleteModel(fileName: String) = withContext(Dispatchers.IO) {
        modelFile(fileName).delete()
        partFile(fileName).delete()
    }
}
