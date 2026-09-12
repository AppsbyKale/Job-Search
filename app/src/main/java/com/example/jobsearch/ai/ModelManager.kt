package com.example.jobsearch.ai

import android.content.Context
import android.util.Log
import com.example.jobsearch.data.SettingsRepository
import com.example.jobsearch.data.SystemLogRepository
import com.google.ai.edge.litertlm.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import kotlin.time.Duration.Companion.milliseconds

/**
 * Manages the local AI model (Gemma) lifecycle using LiteRT-LM.
 * Delegates model download streaming to ModelDownloader.
 */
class ModelManager(
    private val context: Context,
    private val settings: SettingsRepository,
    private val systemLog: SystemLogRepository,
    private val downloader: ModelDownloader = ModelDownloader(settings)
) : IModelManager {
    private val inferenceMutex = Mutex()
    private val generateMutex = Mutex()
    
    @Volatile private var engine: Engine? = null
    @Volatile private var cancelRequested = false
    @Volatile private var preloading = false

    data class DownloadProgress(
        val active: Boolean = false,
        val bytes: Long = 0L,
        val total: Long = 0L
    )

    override val downloadProgress: StateFlow<DownloadProgress>
        get() = downloader.downloadProgress

    val isDownloading: Boolean get() = downloader.isDownloading

    val modelFile: File get() = settings.modelFile
    val partialFile: File get() = downloader.partialFile

    override fun isModelDownloaded(): Boolean {
        val file = modelFile
        return file.exists() && (file.length() > 100_000_000L)
    }

    override fun isEngineLoaded(): Boolean = engine != null
    override fun isBusy(): Boolean = preloading || generateMutex.isLocked

    override suspend fun preload() {
        if (engine != null) return
        if (!isModelDownloaded()) return
        AiModelService.start(context)
        preloading = true
        try {
            ensureLoaded()
            Log.i(TAG, "LLM Model preloaded")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Log.w(TAG, "Model preload failed", e)
        } finally {
            preloading = false
        }
    }

    override fun modelFileSize(): Long = if (modelFile.exists()) modelFile.length() else 0L
    override fun partialSize(): Long = if (partialFile.exists()) partialFile.length() else 0L

    override suspend fun download(onProgress: suspend (downloaded: Long, total: Long) -> Unit) {
        downloader.download(onProgress)
    }

    override suspend fun deleteModel() {
        generateMutex.withLock {
            inferenceMutex.withLock {
                releaseEngine()
            }
        }
        modelFile.delete()
        partialFile.delete()
    }

    private fun releaseEngine() {
        runCatching { engine?.close() }
        engine = null
    }

    override suspend fun generate(
        prompt: String,
        source: String,
        onProgress: ((chunk: String) -> Unit)?
    ): String = withContext(Dispatchers.Default) {
        Log.d(TAG, "generate() called by $source with prompt length: ${prompt.length}")
        systemLog.log("Requesting local AI generation ($source)...")
        AiModelService.start(context)
        generateMutex.withLock {
            Log.d(TAG, "generate() acquired lock, ensuring model is loaded...")
            ensureLoaded()
            var lastError: Throwable? = null
            repeat(MAX_GENERATE_ATTEMPTS) { attempt ->
                Log.d(TAG, "Generation attempt ${attempt + 1} of $MAX_GENERATE_ATTEMPTS")
                systemLog.log("Generation attempt ${attempt + 1}...")
                
                val currentEngine = engine ?: throw IllegalStateException("Engine not loaded")
                cancelRequested = false
                
                // One-shot conversation config
                val conversationConfig = ConversationConfig(
                    systemInstruction = Contents.of("You are a helpful assistant. Use ONLY raw JSON if requested."),
                    samplerConfig = SamplerConfig(topK = 40, topP = 0.95, temperature = 0.4),
                    maxOutputToken = MAX_OUTPUT_TOKENS
                )
                
                val conversation = currentEngine.createConversation(conversationConfig)
                
                try {
                    Log.d(TAG, "Calling sendMessageAsync...")
                    systemLog.log("Generating response (this may take 1-3 mins)...")
                    
                    val output = StringBuilder()
                    conversation.sendMessageAsync(Contents.of(prompt))
                        .map { message ->
                            message.contents.contents
                                .asSequence()
                                .filterIsInstance<Content.Text>()
                                .joinToString("") { it.text }
                        }
                        .collect { chunk ->
                            if (cancelRequested) throw CancellationException("Generation cancelled.")
                            output.append(chunk)
                            onProgress?.invoke(chunk)
                        }

                    val result = output.toString().trim()
                    Log.d(TAG, "sendMessageAsync collected. Result length: ${result.length}")
                    systemLog.log("Response received (${result.length} chars).")

                    if (result.isNotBlank()) return@withContext result
                    else throw IOException("Model returned empty response.")
                } catch (e: CancellationException) {
                    Log.i(TAG, "Generation cancelled by user.")
                    throw e
                } catch (e: Throwable) {
                    if (cancelRequested) throw CancellationException("Generation cancelled.")
                    lastError = e
                    Log.e(TAG, "generate attempt ${attempt + 1} failed", e)
                    systemLog.log("ERROR: Attempt ${attempt + 1} failed: ${e.message}")
                    
                    // On failure, reload engine if it's the first attempt
                    if (attempt == 0) {
                        inferenceMutex.withLock {
                            releaseEngine()
                            delay(100.milliseconds)
                            ensureLoaded()
                        }
                    }
                } finally {
                    runCatching { conversation.close() }
                }
            }
            throw lastError ?: IllegalStateException("Generation failed.")
        }
    }

    override fun cancel() {
        cancelRequested = true
    }

    @OptIn(ExperimentalApi::class)
    private suspend fun ensureLoaded(): Engine = inferenceMutex.withLock {
        engine?.let { 
            Log.d(TAG, "ensureLoaded: Model already loaded.")
            return it 
        }
        val started = System.currentTimeMillis()
        Log.i(TAG, "Loading LLM Model from ${modelFile.absolutePath}...")
        systemLog.log("Loading local AI model into memory...")
        
        // MTP = Multi-Token Prediction (speculative decoding).
        ExperimentalFlags.enableSpeculativeDecoding = true

        val loadedEngine = try {
            Log.d(TAG, "Attempting to initialize Engine with GPU...")
            initializeWith(modelFile, Backend.GPU())
        } catch (e: Exception) {
            Log.w(TAG, "GPU initialization failed, falling back to CPU", e)
            systemLog.log("GPU load failed, falling back to CPU...")
            releaseEngine() // Clean up failed GPU attempt
            try {
                Log.d(TAG, "Attempting to initialize Engine with CPU...")
                initializeWith(modelFile, Backend.CPU())
            } catch (e2: Exception) {
                Log.e(TAG, "CPU initialization also failed!", e2)
                systemLog.log("CRITICAL ERROR: AI Model failed to load.")
                throw e2
            }
        }
        
        Log.i(TAG, "LLM Model loaded in ${System.currentTimeMillis() - started} ms")
        engine = loadedEngine
        loadedEngine
    }

    private fun initializeWith(model: File, backend: Backend): Engine {
        val config = EngineConfig(
            modelPath = model.absolutePath,
            backend = backend,
            cacheDir = context.cacheDir.path,
        )
        return Engine(config).also { created ->
            created.initialize()
            systemLog.log("AI Model loaded (${if (backend is Backend.GPU) "GPU" else "CPU"} backend)")
        }
    }

    companion object {
        private const val TAG = "ModelManager"
        private const val MAX_GENERATE_ATTEMPTS = 2
        private const val MAX_OUTPUT_TOKENS = 1024
    }
}
