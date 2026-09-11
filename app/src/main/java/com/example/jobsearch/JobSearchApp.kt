package com.example.jobsearch

import android.app.Application
import android.util.Log
import com.example.jobsearch.ai.IModelManager
import com.example.jobsearch.di.ApplicationScope
import com.example.jobsearch.data.SettingsRepository
import com.example.jobsearch.data.SyncService
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Main Application class for the JobSearch app.
 * Responsible for initializing the Desktop Sync Server and preloading AI models.
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

        // 1. Immediately start Desktop Sync Service if enabled (defaults to true)
        generationScope.launch {
            val syncEnabled = settingsRepository.desktopSyncEnabled.first()
            if (syncEnabled) {
                SyncService.start(this@JobSearchApp)
            }
        }

        // 2. Preload the AI model in the background
        generationScope.launch {
            try {
                if (modelManager.isModelDownloaded()) {
                    modelManager.preload()
                }
            } catch (e: Exception) {
                Log.e("JobSearchApp", "Model preload failed", e)
            }
        }
    }
}
