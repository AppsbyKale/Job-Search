package com.example.jobsearch.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Handles full portable backups of the application data, including the Room database
 * and DataStore settings. Backups are stored as ZIP files with a .jsbackup extension.
 */
class BackupRepository(
    private val context: Context,
    private val database: JobDatabase,
    private val settingsRepository: SettingsRepository,
    private val systemLog: SystemLogRepository
) {
    /**
     * Exports all data to a ZIP file at the given URI.
     */
    suspend fun exportBackup(uri: Uri) = withContext(Dispatchers.IO) {
        systemLog.log("Starting backup export...")
        // 1. Ensure DB is flushed to disk
        try {
            database.query("PRAGMA wal_checkpoint(FULL)", null).use { it.moveToFirst() }
        } catch (e: Exception) {
            systemLog.log("Warning: WAL checkpoint failed, backup might be slightly stale: ${e.message}")
        }

        val dbFile = context.getDatabasePath("jobsearch.db")
        if (!dbFile.exists()) {
            systemLog.log("ERROR: Database file not found at ${dbFile.path}")
            throw IllegalStateException("Database file not found.")
        }

        // 2. Gather settings
        val settings = JSONObject().apply {
            put("resume_text", settingsRepository.resumeText.first())
            put("resume_file_name", settingsRepository.resumeFileName.first())
            put("model_url", settingsRepository.modelUrl.first())
            put("gemini_api_key", settingsRepository.geminiApiKey.first())
        }

        // 3. Create ZIP
        context.contentResolver.openOutputStream(uri)?.use { output ->
            ZipOutputStream(output).use { zos ->
                // Add Database
                zos.putNextEntry(ZipEntry("jobsearch.db"))
                FileInputStream(dbFile).use { it.copyTo(zos) }
                zos.closeEntry()

                // Add Settings
                zos.putNextEntry(ZipEntry("settings.json"))
                zos.write(settings.toString().toByteArray(Charsets.UTF_8))
                zos.closeEntry()
            }
            systemLog.log("Backup exported successfully to ${uri.path}")
        } ?: throw IllegalStateException("Could not open output stream for backup.")
    }

    /**
     * Imports data from a ZIP file at the given URI.
     * Replaces the current database and updates settings.
     */
    suspend fun importBackup(uri: Uri) = withContext(Dispatchers.IO) {
        systemLog.log("Starting backup restore from ${uri.path}...")
        var dbRestored = false
        var settingsRestored = false
        
        context.contentResolver.openInputStream(uri)?.use { input ->
            ZipInputStream(input).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    systemLog.log("Found zip entry: ${entry.name}")
                    when (entry.name) {
                        "jobsearch.db" -> {
                            systemLog.log("Restoring database file...")
                            val dbFile = context.getDatabasePath("jobsearch.db")
                            
                            try {
                                database.close()
                                systemLog.log("Database connection closed.")
                            } catch (e: Exception) {
                                systemLog.log("Notice: Error closing DB: ${e.message}")
                            }
                            
                            // Delete WAL/SHM files
                            File(dbFile.path + "-wal").delete()
                            File(dbFile.path + "-shm").delete()
                            systemLog.log("WAL/SHM files cleared.")
                            
                            FileOutputStream(dbFile).use { fos ->
                                zis.copyTo(fos)
                            }
                            dbRestored = true
                            systemLog.log("Database file overwritten.")
                        }
                        "settings.json" -> {
                            systemLog.log("Restoring settings...")
                            try {
                                val json = zis.bufferedReader().readText()
                                val obj = JSONObject(json)
                                settingsRepository.setResumeText(obj.optString("resume_text"))
                                settingsRepository.setResumeFileName(obj.optString("resume_file_name"))
                                settingsRepository.setModelUrl(obj.optString("model_url"))
                                settingsRepository.setGeminiApiKey(obj.optString("gemini_api_key"))
                                settingsRestored = true
                                systemLog.log("Settings updated.")
                            } catch (e: Exception) {
                                systemLog.log("ERROR: Failed to parse settings.json: ${e.message}")
                            }
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
        } ?: throw IllegalStateException("Could not open input stream for restore.")
        
        if (!dbRestored) systemLog.log("WARNING: No 'jobsearch.db' found in the backup file.")
        if (!settingsRestored) systemLog.log("WARNING: No 'settings.json' found in the backup file.")
        
        if (dbRestored || settingsRestored) {
            systemLog.log("RESTORE COMPLETE. Please RESTART THE APP NOW.")
        } else {
            systemLog.log("RESTORE FAILED: No valid data found in ZIP.")
        }
    }
}
