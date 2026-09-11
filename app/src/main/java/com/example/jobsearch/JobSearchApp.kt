package com.example.jobsearch

import android.app.Application
import com.example.jobsearch.ai.IModelManager
import com.example.jobsearch.di.ApplicationScope
import com.example.jobsearch.data.SettingsRepository
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Main Application class for the JobSearch app.
 * Responsible for preloading AI models and initializing the Sync Server.
 */
@HiltAndroidApp
class JobSearchApp : Application() {

    @Inject
    lateinit var modelManager: IModelManager

    @Inject
    @ApplicationScope
    lateinit var generationScope: CoroutineScope

    @Inject
    lateinit var settingsRepository: SettingsRepository

    override fun onCreate() {
        super.onCreate()

        // Preload the AI model in the background on startup
        generationScope.launch {
            if (!modelManager.isModelDownloaded()) {
                try {
                    modelManager.download { _, _ -> }
                } catch (e: Exception) {
                    android.util.Log.e("JobSearchApp", "Auto-download failed", e)
                }
            }
            modelManager.preload()

            // Start sync server if enabled AND configured to run on startup
            if (settingsRepository.desktopSyncEnabled.first() && settingsRepository.runSyncOnStartup.first()) {
                com.example.jobsearch.data.SyncService.start(this@JobSearchApp)
            }
        }
    }
}
