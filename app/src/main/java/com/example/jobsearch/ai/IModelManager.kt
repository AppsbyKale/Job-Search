package com.example.jobsearch.ai

import kotlinx.coroutines.flow.StateFlow

interface IModelManager {
    val downloadProgress: StateFlow<ModelManager.DownloadProgress>
    fun isModelDownloaded(): Boolean
    suspend fun generate(prompt: String, source: String = "Unknown", onProgress: ((chunk: String) -> Unit)? = null): String
    fun cancel()
    fun isBusy(): Boolean
    fun isEngineLoaded(): Boolean
    suspend fun preload()
    fun modelFileSize(): Long
    fun partialSize(): Long
    suspend fun download(onProgress: suspend (downloaded: Long, total: Long) -> Unit)
    suspend fun deleteModel()
}
