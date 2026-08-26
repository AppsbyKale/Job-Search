package com.example.jobsearch.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {

    private val keyResumeText = stringPreferencesKey("resume_text")
    private val keyResumeFileName = stringPreferencesKey("resume_file_name")
    private val keyModelUrl = stringPreferencesKey("model_url")
    private val keyGeminiApiKey = stringPreferencesKey("gemini_api_key")
    private val keyDesktopSyncEnabled = androidx.datastore.preferences.core.booleanPreferencesKey("desktop_sync_enabled")
    private val keyDesktopSyncPort = androidx.datastore.preferences.core.intPreferencesKey("desktop_sync_port")
    private val keyRunSyncOnStartup = androidx.datastore.preferences.core.booleanPreferencesKey("run_sync_on_startup")
    private val keyTrainingLoggingEnabled = androidx.datastore.preferences.core.booleanPreferencesKey("training_logging_enabled")

    val resumeText: Flow<String> = context.dataStore.data.map { it[keyResumeText] ?: "" }
    val resumeFileName: Flow<String> = context.dataStore.data.map { it[keyResumeFileName] ?: "" }
    val modelUrl: Flow<String> = context.dataStore.data.map {
        it[keyModelUrl] ?: DEFAULT_MODEL_URL
    }
    val geminiApiKey: Flow<String> = context.dataStore.data.map { it[keyGeminiApiKey] ?: "" }
    val desktopSyncEnabled: Flow<Boolean> = context.dataStore.data.map { it[keyDesktopSyncEnabled] ?: false }
    val desktopSyncPort: Flow<Int> = context.dataStore.data.map { it[keyDesktopSyncPort] ?: DEFAULT_SYNC_PORT }
    val runSyncOnStartup: Flow<Boolean> = context.dataStore.data.map { it[keyRunSyncOnStartup] ?: false }
    val trainingLoggingEnabled: Flow<Boolean> = context.dataStore.data.map { it[keyTrainingLoggingEnabled] ?: false }

    suspend fun setResumeText(text: String) {
        context.dataStore.edit { it[keyResumeText] = text }
    }

    suspend fun setResumeFileName(name: String) {
        context.dataStore.edit { it[keyResumeFileName] = name }
    }

    suspend fun setModelUrl(url: String) {
        context.dataStore.edit { it[keyModelUrl] = url }
    }

    suspend fun setGeminiApiKey(key: String) {
        context.dataStore.edit { it[keyGeminiApiKey] = key }
    }

    suspend fun setDesktopSyncEnabled(enabled: Boolean) {
        context.dataStore.edit { it[keyDesktopSyncEnabled] = enabled }
    }

    suspend fun setDesktopSyncPort(port: Int) {
        context.dataStore.edit { it[keyDesktopSyncPort] = port }
    }

    suspend fun setRunSyncOnStartup(enabled: Boolean) {
        context.dataStore.edit { it[keyRunSyncOnStartup] = enabled }
    }

    suspend fun setTrainingLoggingEnabled(enabled: Boolean) {
        context.dataStore.edit { it[keyTrainingLoggingEnabled] = enabled }
    }

    suspend fun getGeminiApiKey(): String = context.dataStore.data.first()[keyGeminiApiKey] ?: GEMINI_API_KEY

    suspend fun getModelUrl(): String = context.dataStore.data.first()[keyModelUrl] ?: DEFAULT_MODEL_URL

    val modelFile: java.io.File
        get() {
            val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
            val aiFolder = java.io.File(downloadsDir, "AI_Models")
            if (!aiFolder.exists()) aiFolder.mkdirs()
            return java.io.File(aiFolder, "Gemma-4-E2B-it.litertlm")
        }

    companion object {
        const val GEMINI_API_KEY = "AQ.Ab8RN6I5KoMqHwgQbnX61bON7ZbfDB8Z7AIc8Fxa_15Mjd5Eiw"
        const val DEFAULT_MODEL_URL =
            "https://huggingface.co/google/gemma-4-E2B-it-litertlm/resolve/main/gemma-4-E2B-it.litertlm"
        const val EXPECTED_MODEL_SIZE_BYTES = 2_780_000_000L // ~2.59 GB decimal
        const val MIN_MODEL_SIZE_BYTES = 2_000_000_000L
        const val DEFAULT_SYNC_PORT = 8080
    }
}
