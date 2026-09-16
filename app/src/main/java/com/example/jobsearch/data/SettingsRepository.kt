package com.example.jobsearch.data

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.jobsearch.BuildConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import java.io.File

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {

    private val securePrefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            context,
            "secure_settings",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    private val keyResumeText = stringPreferencesKey("resume_text")
    private val keyResumeFileName = stringPreferencesKey("resume_file_name")
    private val keyModelUrl = stringPreferencesKey("model_url")
    private val keyGeminiApiKey = stringPreferencesKey("gemini_api_key")
    private val keyUseCustomApiKey = booleanPreferencesKey("use_custom_api_key")
    private val keyDesktopSyncEnabled = booleanPreferencesKey("desktop_sync_enabled")
    private val keyDesktopSyncPort = intPreferencesKey("desktop_sync_port")
    private val keyRunSyncOnStartup = booleanPreferencesKey("run_sync_on_startup")
    private val keyTrainingLoggingEnabled = booleanPreferencesKey("training_logging_enabled")
    private val keySyncPin = stringPreferencesKey("sync_pin")
    private val keySyncToken = stringPreferencesKey("sync_token")
    private val keySyncTokenExpiry = longPreferencesKey("sync_token_expiry")

    val resumeText: Flow<String> = context.dataStore.data.map { it[keyResumeText] ?: "" }
    val resumeFileName: Flow<String> = context.dataStore.data.map { it[keyResumeFileName] ?: "" }
    val modelUrl: Flow<String> = context.dataStore.data.map {
        val current = it[keyModelUrl] ?: DEFAULT_MODEL_URL
        if (isOldUrl(current)) DEFAULT_MODEL_URL else current
    }
    val geminiApiKey: Flow<String> = context.dataStore.data.map {
        securePrefs.getString("secure_gemini_api_key", null) ?: it[keyGeminiApiKey] ?: ""
    }
    private val keyLangSearchApiKey = stringPreferencesKey("lang_search_api_key")
    val langSearchApiKey: Flow<String> = context.dataStore.data.map {
        securePrefs.getString("secure_lang_search_api_key", null) ?: it[keyLangSearchApiKey] ?: ""
    }
    val useCustomApiKey: Flow<Boolean> = context.dataStore.data.map { it[keyUseCustomApiKey] ?: false }
    val desktopSyncEnabled: Flow<Boolean> = context.dataStore.data.map { it[keyDesktopSyncEnabled] ?: true }
    val desktopSyncPort: Flow<Int> = context.dataStore.data.map { it[keyDesktopSyncPort] ?: DEFAULT_SYNC_PORT }
    val runSyncOnStartup: Flow<Boolean> = context.dataStore.data.map { it[keyRunSyncOnStartup] ?: false }
    val trainingLoggingEnabled: Flow<Boolean> = context.dataStore.data.map { it[keyTrainingLoggingEnabled] ?: false }
    val syncPin: Flow<String> = context.dataStore.data.map { it[keySyncPin] ?: "" }
    val syncToken: Flow<String> = context.dataStore.data.map { it[keySyncToken] ?: "" }
    val syncTokenExpiry: Flow<Long> = context.dataStore.data.map { it[keySyncTokenExpiry] ?: 0L }

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
        securePrefs.edit().putString("secure_gemini_api_key", key).apply()
        context.dataStore.edit { it[keyGeminiApiKey] = key }
    }

    suspend fun setLangSearchApiKey(key: String) {
        securePrefs.edit().putString("secure_lang_search_api_key", key).apply()
        context.dataStore.edit { it[keyLangSearchApiKey] = key }
    }

    suspend fun setUseCustomApiKey(use: Boolean) {
        context.dataStore.edit { it[keyUseCustomApiKey] = use }
    }

    suspend fun setDesktopSyncEnabled(enabled: Boolean) {
        context.dataStore.edit { it[keyDesktopSyncEnabled] = enabled }
    }

    suspend fun setRunSyncOnStartup(enabled: Boolean) {
        context.dataStore.edit { it[keyRunSyncOnStartup] = enabled }
    }

    suspend fun setTrainingLoggingEnabled(enabled: Boolean) {
        context.dataStore.edit { it[keyTrainingLoggingEnabled] = enabled }
    }

    suspend fun saveSyncPairing(pin: String, token: String, expiryTime: Long) {
        context.dataStore.edit {
            it[keySyncPin] = pin
            it[keySyncToken] = token
            it[keySyncTokenExpiry] = expiryTime
        }
    }

    suspend fun saveSyncPin(pin: String) {
        context.dataStore.edit { it[keySyncPin] = pin }
    }

    suspend fun getGeminiApiKey(): String {
        val prefs = context.dataStore.data.first()
        val useCustom = prefs[keyUseCustomApiKey] ?: false
        return if (useCustom) {
            val custom = securePrefs.getString("secure_gemini_api_key", null)
                ?: prefs[keyGeminiApiKey]
                ?: ""
            if (custom.isNotBlank()) custom else BuildConfig.GEMINI_API_KEY
        } else {
            BuildConfig.GEMINI_API_KEY
        }
    }

    suspend fun getModelUrl(): String {
        val current = context.dataStore.data.first()[keyModelUrl] ?: DEFAULT_MODEL_URL
        return if (isOldUrl(current)) DEFAULT_MODEL_URL else current
    }

    private fun isOldUrl(url: String): Boolean {
        return url.contains("google/") || url.contains("gemma-3") || url.contains("E4B")
    }

    fun setCustomModelPath(path: String) {
        securePrefs.edit().putString("custom_model_path", path).apply()
    }

    fun getCustomModelPath(): String {
        return securePrefs.getString("custom_model_path", "") ?: ""
    }

    val modelFile: File
        get() {
            val custom = getCustomModelPath()
            if (custom.isNotBlank()) {
                val f = File(custom)
                if (f.exists() && f.length() >= MIN_MODEL_SIZE_BYTES) {
                    return f
                }
            }
            val aiFolder = File(context.filesDir, "AI_Models")
            if (!aiFolder.exists()) aiFolder.mkdirs()
            val defaultFile = File(aiFolder, "Gemma-4-E2B-it.litertlm")
            if (defaultFile.exists() && defaultFile.length() >= MIN_MODEL_SIZE_BYTES) return defaultFile

            val anyModel = aiFolder.listFiles()?.firstOrNull { it.name.endsWith(".litertlm") && it.length() >= MIN_MODEL_SIZE_BYTES }
            return anyModel ?: defaultFile
        }

    companion object {
        const val DEFAULT_MODEL_URL =
            "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm"
        const val MIN_MODEL_SIZE_BYTES = 1_500_000_000L // 1.5GB
        const val DEFAULT_SYNC_PORT = 8080
    }
}
