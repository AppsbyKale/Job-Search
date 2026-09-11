package com.example.jobsearch.ai

import com.example.jobsearch.data.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ModelDownloader @Inject constructor(
    private val settings: SettingsRepository
) {
    private val downloadMutex = Mutex()

    private val _downloadProgress = MutableStateFlow(ModelManager.DownloadProgress())
    val downloadProgress: StateFlow<ModelManager.DownloadProgress> = _downloadProgress.asStateFlow()

    @Volatile
    var isDownloading: Boolean = false
        private set

    val modelFile: File get() = settings.modelFile
    val partialFile: File
        get() = File(modelFile.parentFile, "${modelFile.name}.part")

    suspend fun download(onProgress: suspend (downloaded: Long, total: Long) -> Unit) {
        downloadMutex.withLock {
            if (isDownloading) throw IOException("A model download is already in progress.")
            isDownloading = true
            _downloadProgress.value = ModelManager.DownloadProgress(
                active = true,
                bytes = if (partialFile.exists()) partialFile.length() else 0L
            )
            val progressCallback: suspend (Long, Long) -> Unit = { downloaded, total ->
                _downloadProgress.value = ModelManager.DownloadProgress(active = true, bytes = downloaded, total = total)
                onProgress(downloaded, total)
            }
            try {
                var attempts = 0
                while (true) {
                    attempts++
                    when (val result = tryDownloadOnce(progressCallback)) {
                        DownloadResult.Ok -> {
                            finishDownload()
                            break
                        }
                        DownloadResult.Restart -> {
                            partialFile.delete()
                            if (attempts >= 2) {
                                throw IOException("Download failed repeatedly. Please try again later.")
                            }
                        }
                        is DownloadResult.Failed -> throw result.error
                    }
                }
            } finally {
                isDownloading = false
                _downloadProgress.value = ModelManager.DownloadProgress()
            }
        }
    }

    private fun finishDownload() {
        val dest = modelFile
        if (dest.exists()) dest.delete()
        if (!partialFile.renameTo(dest)) {
            throw IOException("Could not save the model file.")
        }
    }

    private suspend fun tryDownloadOnce(
        onProgress: suspend (downloaded: Long, total: Long) -> Unit
    ): DownloadResult {
        val url = settings.getModelUrl()
        val partial = partialFile
        partial.parentFile?.mkdirs()
        val existing = if (partial.exists()) partial.length() else 0L

        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 30_000
            conn.readTimeout = 60_000
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) JobSearch/1.0")
            conn.setRequestProperty("Accept", "*/*")
            conn.instanceFollowRedirects = true
            if (existing > 0L) {
                conn.setRequestProperty("Range", "bytes=$existing-")
            }

            val code = conn.responseCode
            if (code == 416) return DownloadResult.Restart
            if (code !in 200..299) {
                return DownloadResult.Failed(
                    IOException("Download failed (HTTP $code). Check the model URL in Settings.")
                )
            }

            val resumed = code == 206
            val total = if (resumed) {
                totalFromContentRange(conn.getHeaderField("Content-Range"), existing)
            } else {
                conn.contentLengthLong
            }
            var downloaded = if (resumed) existing else 0L

            val input = conn.inputStream
            val out = if (resumed) FileOutputStream(partial, true) else FileOutputStream(partial)
            val buffer = ByteArray(256 * 1024)
            var lastEmit = 0L
            try {
                while (true) {
                    val read = input.read(buffer)
                    if (read == -1) break
                    out.write(buffer, 0, read)
                    downloaded += read
                    val now = System.currentTimeMillis()
                    if (now - lastEmit >= 250) {
                        lastEmit = now
                        onProgress(downloaded, total)
                    }
                }
                out.flush()
            } finally {
                out.close()
                input.close()
            }

            onProgress(downloaded, total)
            if (total > 0 && downloaded < total - 1024) {
                return DownloadResult.Failed(
                    IOException("Download incomplete ($downloaded / $total bytes). Tap Download again to resume where it left off.")
                )
            }
            if (downloaded < SettingsRepository.MIN_MODEL_SIZE_BYTES) {
                return DownloadResult.Failed(
                    IOException("Download incomplete ($downloaded bytes). Please try again on Wi-Fi.")
                )
            }
            return DownloadResult.Ok
        } catch (e: SocketTimeoutException) {
            return DownloadResult.Failed(
                IOException("Connection stalled. Tap Download to resume where it left off.")
            )
        } catch (e: Exception) {
            return DownloadResult.Failed(IOException(e.message ?: "Download failed."))
        } finally {
            conn.disconnect()
        }
    }

    private fun totalFromContentRange(header: String?, existing: Long): Long {
        if (header.isNullOrBlank()) return existing
        val match = Regex("/(\\d+)\\s*$").find(header)
        return match?.groupValues?.get(1)?.toLongOrNull() ?: existing
    }

    private sealed class DownloadResult {
        object Ok : DownloadResult()
        object Restart : DownloadResult()
        data class Failed(val error: IOException) : DownloadResult()
    }
}
