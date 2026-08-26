package com.example.jobsearch.ui.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.jobsearch.ai.IModelManager
import com.example.jobsearch.ai.ModelManager
import com.example.jobsearch.data.BackupRepository
import com.example.jobsearch.data.JobRepository
import com.example.jobsearch.data.SettingsRepository
import com.example.jobsearch.data.TrainingRepository
import com.example.jobsearch.di.ApplicationScope
import com.example.jobsearch.document.DocumentExporter
import com.example.jobsearch.resume.ResumeImporter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the settings screen.
 * Manages resume storage, model downloads, and API keys.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val modelManager: IModelManager,
    private val resumeImporter: ResumeImporter,
    private val exporter: DocumentExporter,
    private val jobRepository: JobRepository,
    private val syncRepository: com.example.jobsearch.data.SyncRepository,
    private val backupRepository: BackupRepository,
    private val trainingRepository: TrainingRepository,
    private val systemLogRepository: com.example.jobsearch.data.SystemLogRepository,
    @param:ApplicationScope private val appScope: CoroutineScope
) : ViewModel() {

    data class UiState(
        val resumeText: String = "",
        val resumeFileName: String = "",
        val modelUrl: String = SettingsRepository.DEFAULT_MODEL_URL,
        val geminiApiKey: String = "",
        val modelDownloaded: Boolean = false,
        val modelFileSize: Long = 0,
        val partialBytes: Long = 0,
        val downloading: Boolean = false,
        val progressBytes: Long = 0,
        val progressTotal: Long = 0,
        val busy: Boolean = false,
        val desktopSyncEnabled: Boolean = false,
        val desktopSyncPort: Int = 8080,
        val runSyncOnStartup: Boolean = false,
        val trainingLoggingEnabled: Boolean = false,
        val allFilesAccess: Boolean = true,
        val isServerRunning: Boolean = false,
        val recentSyncs: List<String> = emptyList(),
        val localIp: String? = null,
        val systemLogs: List<String> = emptyList(),
        val message: String? = null,
        val error: String? = null
    )

    private val _state = MutableStateFlow(UiState())

    val state: StateFlow<UiState> = combine(
        _state,
        settingsRepository.resumeFileName,
        modelManager.downloadProgress,
        settingsRepository.desktopSyncEnabled,
        settingsRepository.desktopSyncPort,
        settingsRepository.runSyncOnStartup,
        settingsRepository.trainingLoggingEnabled,
        syncRepository.isServerRunning,
        syncRepository.recentSyncs,
        systemLogRepository.logs
    ) { array ->
        val current = array[0] as UiState
        val name = array[1] as String
        val progress = array[2] as ModelManager.DownloadProgress
        val syncEnabled = array[3] as Boolean
        val syncPort = array[4] as Int
        val startup = array[5] as Boolean
        val trainingEnabled = array[6] as Boolean
        val isRunning = array[7] as Boolean
        @Suppress("UNCHECKED_CAST")
        val recents = array[8] as List<String>
        @Suppress("UNCHECKED_CAST")
        val logs = array[9] as List<String>

        val hasAllFilesAccess = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            android.os.Environment.isExternalStorageManager()
        } else {
            true
        }

        current.copy(
            resumeFileName = name,
            modelDownloaded = modelManager.isModelDownloaded(),
            modelFileSize = modelManager.modelFileSize(),
            partialBytes = modelManager.partialSize(),
            downloading = progress.active,
            progressBytes = progress.bytes,
            progressTotal = progress.total,
            desktopSyncEnabled = syncEnabled,
            desktopSyncPort = syncPort,
            runSyncOnStartup = startup,
            trainingLoggingEnabled = trainingEnabled,
            allFilesAccess = hasAllFilesAccess,
            isServerRunning = isRunning,
            recentSyncs = recents,
            localIp = if (isRunning) syncRepository.getLocalIpAddress() else null,
            systemLogs = logs
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())

    init {
        checkPermissionState()
        viewModelScope.launch {
            val resume = settingsRepository.resumeText.first()
            val name = settingsRepository.resumeFileName.first()
            val url = settingsRepository.modelUrl.first()
            val apiKey = settingsRepository.geminiApiKey.first()
            _state.update {
                it.copy(resumeText = resume, resumeFileName = name, modelUrl = url, geminiApiKey = apiKey)
            }
        }
    }

    fun checkPermissionState() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            val hasAccess = android.os.Environment.isExternalStorageManager()
            _state.update { it.copy(allFilesAccess = hasAccess) }
        }
    }

    fun onGeminiApiKeyChange(value: String) {
        _state.update { it.copy(geminiApiKey = value) }
    }

    fun saveGeminiApiKey() {
        viewModelScope.launch {
            val key = _state.value.geminiApiKey.trim()
            android.util.Log.d("SettingsViewModel", "Saving Gemini API Key (length: ${key.length})")
            systemLogRepository.log("Saved Gemini API Key")
            settingsRepository.setGeminiApiKey(key)
            _state.update { it.copy(message = "Gemini API key saved.") }
        }
    }

    fun onResumeTextChange(value: String) {
        _state.update { it.copy(resumeText = value) }
    }

    fun saveResumeText() {
        viewModelScope.launch {
            val text = _state.value.resumeText.trim()
            android.util.Log.d("SettingsViewModel", "Saving resume text (length: ${text.length})")
            _state.update { it.copy(busy = true, message = null, error = null) }
            settingsRepository.setResumeText(text)
            _state.update { it.copy(busy = false, message = "Resume saved.") }
        }
    }

    fun importResume(context: Context, uri: Uri, fileName: String) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null, message = null) }
            try {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    val text = resumeImporter.parse(context, fileName, input)
                    settingsRepository.setResumeText(text)
                    settingsRepository.setResumeFileName(fileName)
                    _state.update { it.copy(busy = false, message = "Resume imported from $fileName.") }
                } ?: throw IllegalStateException("Could not open the selected file.")
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, error = e.message ?: "Import failed.") }
            }
        }
    }

    fun onModelUrlChange(value: String) {
        _state.update { it.copy(modelUrl = value) }
    }

    fun saveModelUrl() {
        viewModelScope.launch {
            val url = _state.value.modelUrl.trim()
            android.util.Log.d("SettingsViewModel", "Saving model URL: $url")
            if (!url.startsWith("http")) {
                _state.update { it.copy(error = "Model URL must start with http(s)://") }
                return@launch
            }
            settingsRepository.setModelUrl(url)
            _state.update { it.copy(message = "Model URL saved.") }
        }
    }

    fun downloadModel() {
        if (_state.value.downloading) return
        android.util.Log.d("SettingsViewModel", "Starting model download...")
        _state.update {
            it.copy(
                downloading = true,
                progressBytes = 0,
                progressTotal = 0,
                error = null,
                message = null
            )
        }
        appScope.launch {
            try {
                modelManager.download { downloaded, total ->
                    _state.update { it.copy(progressBytes = downloaded, progressTotal = total) }
                }
                android.util.Log.i("SettingsViewModel", "Model download complete.")
                _state.update {
                    it.copy(
                        downloading = false,
                        modelDownloaded = true,
                        modelFileSize = modelManager.modelFileSize(),
                        message = "Model downloaded and ready."
                    )
                }
            } catch (e: Exception) {
                android.util.Log.e("SettingsViewModel", "Model download failed", e)
                val partial = modelManager.partialSize()
                _state.update {
                    it.copy(
                        downloading = false,
                        modelDownloaded = false,
                        error = if (partial > 0) {
                            "${e.message ?: "Download failed."} ($partial bytes saved — tap Download again to resume.)"
                        } else {
                            e.message ?: "Download failed."
                        }
                    )
                }
            }
        }
    }

    fun deleteModel() {
        viewModelScope.launch {
            android.util.Log.d("SettingsViewModel", "Deleting model...")
            _state.update { it.copy(busy = true, error = null, message = null) }
            modelManager.deleteModel()
            _state.update {
                it.copy(
                    busy = false,
                    modelDownloaded = false,
                    modelFileSize = 0,
                    progressBytes = 0,
                    progressTotal = 0,
                    message = "Model deleted."
                )
            }
        }
    }

    fun toggleRunSyncOnStartup(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setRunSyncOnStartup(enabled)
        }
    }

    fun toggleTrainingLogging(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setTrainingLoggingEnabled(enabled)
        }
    }

    fun toggleServerManually(context: Context) {
        viewModelScope.launch {
            val currentlyRunning = syncRepository.isServerRunning.value
            if (currentlyRunning) {
                com.example.jobsearch.data.SyncService.stop(context)
                settingsRepository.setDesktopSyncEnabled(enabled = false)
            } else {
                com.example.jobsearch.data.SyncService.start(context)
                settingsRepository.setDesktopSyncEnabled(enabled = true)
            }
        }
    }


    fun exportCsv(uri: Uri) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null, message = null) }
            try {
                val jobs = jobRepository.observeJobs().first()
                val ok = exporter.saveDocumentUri(uri, exporter.csvFor(jobs))
                _state.update {
                    it.copy(
                        busy = false,
                        message = if (ok) "CSV exported." else "Could not write the CSV file."
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, error = e.message ?: "CSV export failed.") }
            }
        }
    }

    fun exportTrainingData(uri: Uri) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null, message = null) }
            try {
                val json = trainingRepository.exportAsJson()
                val ok = exporter.saveDocumentUri(uri, json)
                _state.update {
                    it.copy(
                        busy = false,
                        message = if (ok) "Training data exported (JSONL)." else "Could not write the export file."
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, error = "Export failed: ${e.message}") }
            }
        }
    }

    fun exportBackup(uri: Uri) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null, message = null) }
            try {
                backupRepository.exportBackup(uri)
                _state.update { it.copy(busy = false, message = "Backup exported successfully.") }
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, error = "Export failed: ${e.message}") }
            }
        }
    }

    fun importBackup(uri: Uri) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null, message = null) }
            try {
                backupRepository.importBackup(uri)
                _state.update { 
                    it.copy(
                        busy = false, 
                        message = "Data restored. Please CLOSE and RESTART the app manually to apply changes."
                    ) 
                }
                systemLogRepository.log("Restore successful. User prompted to restart.")
            } catch (e: Exception) {
                android.util.Log.e("SettingsViewModel", "Restore failed", e)
                _state.update { it.copy(busy = false, error = "Import failed: ${e.message}") }
                systemLogRepository.log("ERROR: Restore failed: ${e.message}")
            }
        }
    }

    fun clearLogs() {
        systemLogRepository.clear()
    }

    fun dismissMessage() {
        _state.update { it.copy(message = null, error = null) }
    }
}
