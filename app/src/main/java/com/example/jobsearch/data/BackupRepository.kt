package com.example.jobsearch.data

import android.content.Context
import android.net.Uri
import com.example.jobsearch.document.DocumentExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Handles full portable backups of the application data, including jobs, interview data,
 * and settings. Backups are stored as clean JSON files with a .jsbackup extension.
 */
class BackupRepository(
    private val context: Context,
    private val database: JobDatabase,
    private val settingsRepository: SettingsRepository,
    private val systemLog: SystemLogRepository,
    private val exporter: DocumentExporter
) {
    /**
     * Exports all data to a JSON file at the given URI.
     */
    suspend fun exportBackup(uri: Uri) = withContext(Dispatchers.IO) {
        systemLog.log("Starting backup export...")
        val jobs = database.jobDao().observeAll().first()

        val settings = JSONObject().apply {
            put("resume_text", settingsRepository.resumeText.first())
            put("resume_file_name", settingsRepository.resumeFileName.first())
            put("model_url", settingsRepository.modelUrl.first())
            put("gemini_api_key", settingsRepository.geminiApiKey.first())
            put("lang_search_api_key", settingsRepository.langSearchApiKey.first())
        }

        val root = JSONObject().apply {
            put("version", 10)
            put("settings", settings)
            put("jobs", JSONArray().apply {
                jobs.forEach { job ->
                    put(JSONObject().apply {
                        put("id", job.id)
                        put("title", job.title)
                        put("company", job.company)
                        put("url", job.url)
                        put("description", job.description)
                        put("dateAdded", job.dateAdded)
                        put("dateApplied", if (job.dateApplied != null) job.dateApplied else JSONObject.NULL)
                        put("status", job.status)
                        put("resumeText", job.resumeText)
                        put("coverLetterText", job.coverLetterText)
                        put("initialEmailText", job.initialEmailText)
                        put("cheatSheetText", job.cheatSheetText)
                        put("followUpEmailText", job.followUpEmailText)
                        put("notes", job.notes)
                        put("externalResumeText", job.externalResumeText)
                        put("externalCoverLetterText", job.externalCoverLetterText)
                        put("tags", job.tags)
                        put("companyInfo", job.companyInfo)
                        put("cheatSheetCustomQuestions", job.cheatSheetCustomQuestions)
                    })
                }
            })
        }

        context.contentResolver.openOutputStream(uri)?.use { output ->
            output.write(root.toString(2).toByteArray(Charsets.UTF_8))
            systemLog.log("Backup exported successfully.")
        } ?: throw IllegalStateException("Could not open output stream for backup.")
    }

    /**
     * Imports data from a JSON file at the given URI.
     * Upserts jobs and updates settings without triggering Room schema version mismatches.
     */
    suspend fun importBackup(uri: Uri) = withContext(Dispatchers.IO) {
        systemLog.log("Starting backup restore from ${uri.path}...")
        
        context.contentResolver.openInputStream(uri)?.use { input ->
            val jsonStr = input.bufferedReader().readText()
            val root = JSONObject(jsonStr)

            // 1. Restore settings
            val settingsObj = root.optJSONObject("settings")
            if (settingsObj != null) {
                settingsRepository.setResumeText(settingsObj.optString("resume_text"))
                settingsRepository.setResumeFileName(settingsObj.optString("resume_file_name"))
                settingsRepository.setModelUrl(settingsObj.optString("model_url"))
                settingsRepository.setGeminiApiKey(settingsObj.optString("gemini_api_key"))
                settingsRepository.setLangSearchApiKey(settingsObj.optString("lang_search_api_key"))
                systemLog.log("Settings restored.")
            }

            // 2. Restore jobs
            val jobsArray = root.optJSONArray("jobs")
            if (jobsArray != null) {
                for (i in 0 until jobsArray.length()) {
                    val jObj = jobsArray.getJSONObject(i)
                    val job = Job(
                        id = jObj.optLong("id", 0L),
                        title = jObj.optString("title"),
                        company = jObj.optString("company"),
                        url = jObj.optString("url"),
                        description = jObj.optString("description"),
                        dateAdded = jObj.optLong("dateAdded", System.currentTimeMillis()),
                        dateApplied = if (jObj.has("dateApplied") && !jObj.isNull("dateApplied")) jObj.optLong("dateApplied") else null,
                        status = jObj.optString("status", JobStatus.SAVED.name),
                        resumeText = jObj.optString("resumeText"),
                        coverLetterText = jObj.optString("coverLetterText"),
                        initialEmailText = jObj.optString("initialEmailText"),
                        cheatSheetText = jObj.optString("cheatSheetText"),
                        followUpEmailText = jObj.optString("followUpEmailText"),
                        notes = jObj.optString("notes"),
                        externalResumeText = jObj.optString("externalResumeText"),
                        externalCoverLetterText = jObj.optString("externalCoverLetterText"),
                        tags = jObj.optString("tags"),
                        companyInfo = jObj.optString("companyInfo"),
                        cheatSheetCustomQuestions = jObj.optString("cheatSheetCustomQuestions")
                    )
                    val existing = database.jobDao().getById(job.id)
                    if (existing == null) {
                        database.jobDao().insert(job)
                    } else {
                        database.jobDao().update(job)
                    }
                }
                systemLog.log("Jobs restored successfully (${jobsArray.length()} jobs).")
            }

            systemLog.log("RESTORE COMPLETE.")
        } ?: throw IllegalStateException("Could not open input stream for restore.")
    }

    suspend fun importCsv(uri: Uri) = withContext(Dispatchers.IO) {
        systemLog.log("Starting CSV import...")
        context.contentResolver.openInputStream(uri)?.use { input ->
            val text = input.bufferedReader().readText()
            val jobs = exporter.parseCsv(text)
            for (job in jobs) {
                val existing = database.jobDao().findByTitleAndCompany(job.title, job.company)
                if (existing == null) {
                    database.jobDao().insert(job)
                }
            }
            systemLog.log("CSV imported successfully (${jobs.size} jobs).")
        } ?: throw IllegalStateException("Could not open input stream for CSV import.")
    }
}
