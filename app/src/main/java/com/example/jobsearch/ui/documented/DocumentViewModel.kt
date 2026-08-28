package com.example.jobsearch.ui.documented

import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.jobsearch.data.CheatSheetData
import com.example.jobsearch.data.CoverLetterData
import com.example.jobsearch.data.JobRepository
import com.example.jobsearch.data.ResumeData
import com.example.jobsearch.data.ToughQuestion
import com.example.jobsearch.document.DocumentExporter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * ViewModel for the document viewing and editing screen.
 * Handles PDF rendering, document export, and human-readable text transformation.
 */
@HiltViewModel
class DocumentViewModel @Inject constructor(
    private val repository: JobRepository,
    private val exporter: DocumentExporter,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val jobId: Long = savedStateHandle.get<Long>("jobId") ?: -1L
    val type: String = savedStateHandle.get<String>("type") ?: "resume"
    private val isResume: Boolean get() = type == "resume"
    private val isCheat: Boolean get() = type == "cheat"

    data class UiState(
        val text: String = "",
        val original: String = "",
        val loaded: Boolean = false,
        val jobTitle: String = "",
        val company: String = "",
        val resumeData: ResumeData? = null
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val job = repository.getJob(jobId)
            val rawText = when (type) {
                "resume" -> job?.resumeText
                "cheat" -> job?.cheatSheetText
                "followup" -> job?.followUpEmailText
                "initial" -> job?.initialEmailText
                "external_resume" -> job?.externalResumeText
                "external_cover" -> job?.externalCoverLetterText
                else -> job?.coverLetterText
            }
            
            val displayLines = if (rawText != null) {
                when (type) {
                    "resume", "external_resume" -> ResumeData.fromJson(rawText)?.toHumanReadableText() ?: rawText
                    "cheat" -> {
                        CheatSheetData.fromJson(rawText)?.toHumanReadableText() ?: rawText
                    }
                    "followup", "initial" -> rawText
                    else -> CoverLetterData.fromJson(rawText)?.toHumanReadableText() ?: rawText
                }
            } else {
                null
            }

            val resumeData = if ((type == "resume" || type == "external_resume") && rawText != null) {
                ResumeData.fromJson(rawText) ?: ResumeData.fromText(rawText)
            } else null

            _state.update {
                it.copy(
                    text = displayLines.orEmpty(),
                    original = displayLines.orEmpty(),
                    loaded = true,
                    jobTitle = job?.title.orEmpty(),
                    company = job?.company.orEmpty(),
                    resumeData = resumeData
                )
            }
        }
    }

    fun onTextChange(value: String) {
        _state.update { it.copy(text = value) }
    }

    fun updateResumeData(data: ResumeData) {
        _state.update { it.copy(resumeData = data, text = data.toHumanReadableText()) }
    }

    fun updateResumeName(name: String) {
        val current = _state.value.resumeData ?: return
        updateResumeData(current.copy(name = name))
    }

    fun updateResumeContact(contact: String) {
        val current = _state.value.resumeData ?: return
        updateResumeData(current.copy(contact = contact))
    }

    fun updateResumeSummary(summary: String) {
        val current = _state.value.resumeData ?: return
        updateResumeData(current.copy(summary = summary))
    }

    fun updateExperienceTitle(index: Int, title: String) {
        val current = _state.value.resumeData ?: return
        val newExp = current.experience.mapIndexed { i, exp ->
            if (i == index) exp.copy(title = title) else exp
        }
        updateResumeData(current.copy(experience = newExp))
    }

    fun updateExperienceCompany(index: Int, company: String) {
        val current = _state.value.resumeData ?: return
        val newExp = current.experience.mapIndexed { i, exp ->
            if (i == index) exp.copy(company = company) else exp
        }
        updateResumeData(current.copy(experience = newExp))
    }

    fun updateJobBullet(index: Int, bulletIndex: Int, text: String) {
        val current = _state.value.resumeData ?: return
        val newExp = current.experience.mapIndexed { i, exp ->
            if (i == index) {
                val newBullets = exp.bullets.toMutableList()
                if (bulletIndex < newBullets.size) {
                    if (text.isEmpty()) {
                        newBullets.removeAt(bulletIndex)
                    } else {
                        newBullets[bulletIndex] = text
                    }
                }
                exp.copy(bullets = newBullets)
            } else exp
        }
        updateResumeData(current.copy(experience = newExp))
    }

    fun updateEducationDegree(index: Int, text: String) {
        val current = _state.value.resumeData ?: return
        val newEdu = current.education.mapIndexed { i, edu ->
            if (i == index) edu.copy(degree = text) else edu
        }
        updateResumeData(current.copy(education = newEdu))
    }

    fun updateEducationSchool(index: Int, text: String) {
        val current = _state.value.resumeData ?: return
        val newEdu = current.education.mapIndexed { i, edu ->
            if (i == index) edu.copy(school = text) else edu
        }
        updateResumeData(current.copy(education = newEdu))
    }

    fun updateEducationDates(index: Int, text: String) {
        val current = _state.value.resumeData ?: return
        val newEdu = current.education.mapIndexed { i, edu ->
            if (i == index) edu.copy(dates = text) else edu
        }
        updateResumeData(current.copy(education = newEdu))
    }

    fun updateProjectName(index: Int, text: String) {
        val current = _state.value.resumeData ?: return
        val newProj = current.projects.mapIndexed { i, proj ->
            if (i == index) proj.copy(name = text) else proj
        }
        updateResumeData(current.copy(projects = newProj))
    }

    fun updateProjectBullet(index: Int, bulletIndex: Int, text: String) {
        val current = _state.value.resumeData ?: return
        val newProj = current.projects.mapIndexed { i, proj ->
            if (i == index) {
                val newBullets = proj.bullets.toMutableList()
                if (bulletIndex < newBullets.size) {
                    if (text.isEmpty()) {
                        newBullets.removeAt(bulletIndex)
                    } else {
                        newBullets[bulletIndex] = text
                    }
                }
                proj.copy(bullets = newBullets)
            } else proj
        }
        updateResumeData(current.copy(projects = newProj))
    }

    fun save() {
        viewModelScope.launch {
            val job = repository.getJob(jobId) ?: return@launch
            val currentText = _state.value.text
            val textToSave = prepareTextForProcessing(currentText, "save")
            val saved = when (type) {
                "resume" -> job.copy(resumeText = textToSave)
                "cheat" -> job.copy(cheatSheetText = textToSave)
                "followup" -> job.copy(followUpEmailText = textToSave)
                "initial" -> job.copy(initialEmailText = textToSave)
                "external_resume" -> job.copy(externalResumeText = textToSave)
                "external_cover" -> job.copy(externalCoverLetterText = textToSave)
                else -> job.copy(coverLetterText = textToSave)
            }
            repository.updateJob(saved)
            _state.update { it.copy(original = currentText) }
        }
    }

    fun hasChanges(): Boolean = _state.value.text != _state.value.original

    fun copyToClipboard() {
        exporter.copyToClipboard(_state.value.text)
    }

    fun share() {
        val title = _state.value.jobTitle.ifBlank { "job" }
        val label = when (type) {
            "resume" -> "resume"
            "cheat" -> "cheat sheet"
            "followup" -> "follow-up email"
            else -> "cover letter"
        }
        exporter.shareText("$title $label", _state.value.text)
    }

    fun exportPdf(uri: android.net.Uri): Boolean {
        val currentText = _state.value.text
        val textToExport = prepareTextForProcessing(currentText, "exportPdf")
        return exporter.writePdf(
            uri,
            textToExport,
            resumeLayout = isResume,
            coverLetterLayout = !isResume && !isCheat,
            cheatSheetLayout = isCheat,
            date = todayFormatted()
        )
    }

    /** Renders the document as PDF pages for the on-screen preview. */
    suspend fun previewPages(): List<Bitmap>? = withContext(Dispatchers.IO) {
        val currentText = _state.value.text
        val textToRender = prepareTextForProcessing(currentText, "previewPages")
        exporter.renderDocumentPages(
            textToRender,
            resumeLayout = isResume,
            coverLetterLayout = !isResume && !isCheat,
            cheatSheetLayout = isCheat,
            date = todayFormatted()
        )
    }

    private fun prepareTextForProcessing(currentText: String, tag: String): String {
        return when (type) {
            "resume", "external_resume" -> {
                val data = ResumeData.fromText(currentText)
                val substantial = data.isSubstantial()
                Log.d("JobSearch", "DocumentViewModel.$tag: type=$type, substantial=$substantial")
                if (substantial) data.toJson() else currentText
            }
            "cheat" -> {
                val data = parseCheatSheetFromText(currentText)
                Log.d("JobSearch", "DocumentViewModel.$tag: type=cheat")
                data.toJson()
            }
            "followup", "initial" -> currentText
            else -> {
                val data = CoverLetterData.fromText(currentText)
                val substantial = data.isSubstantial()
                Log.d("JobSearch", "DocumentViewModel.$tag: type=$type, substantial=$substantial")
                if (substantial) data.toJson() else currentText
            }
        }
    }

    private fun parseCheatSheetFromText(text: String): CheatSheetData {
        val lines = text.lineSequence().map { it.trim() }.toList()
        val highlights = mutableListOf<String>()
        val toughQuestions = mutableListOf<ToughQuestion>()
        
        var section = 0 // 1: Highlights, 2: Questions
        var currentQ = ""
        var currentS = ""
        var collectingAnswer = false
        var currentAnswer = StringBuilder()
        
        for (line in lines) {
            if (line.isEmpty()) {
                if (collectingAnswer) currentAnswer.append("\n")
                continue
            }
            if (line.contains("KEY HIGHLIGHTS", ignoreCase = true)) {
                section = 1
                collectingAnswer = false
                continue
            }
            if (line.contains("TOUGH QUESTIONS", ignoreCase = true)) {
                section = 2
                collectingAnswer = false
                continue
            }
            
            if (section == 1) {
                highlights.add(line.removePrefix("•").trim())
            } else if (section == 2) {
                if (line.startsWith("Q:", ignoreCase = true)) {
                    if (currentQ.isNotEmpty()) {
                        toughQuestions.add(ToughQuestion(currentQ, currentS, currentAnswer.toString().trim()))
                    }
                    currentQ = line.removePrefix("Q:").trim()
                    currentS = ""
                    currentAnswer = StringBuilder()
                    collectingAnswer = false
                } else if (line.startsWith("STRATEGY:", ignoreCase = true)) {
                    currentS = line.removePrefix("STRATEGY:").trim()
                    collectingAnswer = false
                } else if (line.startsWith("EXAMPLE ANSWER:", ignoreCase = true)) {
                    val ex = line.removePrefix("EXAMPLE ANSWER:").trim()
                    currentAnswer.append(ex)
                    collectingAnswer = true
                } else if (collectingAnswer) {
                    currentAnswer.append(line).append("\n")
                }
            }
        }
        if (currentQ.isNotEmpty()) {
            toughQuestions.add(ToughQuestion(currentQ, currentS, currentAnswer.toString().trim()))
        }
        return CheatSheetData(highlights, toughQuestions)
    }

    private fun todayFormatted(): String {
        return java.text.SimpleDateFormat("MMMM d, yyyy", java.util.Locale.US).format(java.util.Date())
    }

    /** Suggested PDF file name: "Candidate Name - Company - Position.pdf". */
    fun pdfFileName(): String {
        val parts = listOf(candidateName(), state.value.company, state.value.jobTitle)
            .map { sanitizeFileNamePart(it) }
            .filter { it.isNotBlank() }
        val base = if (parts.isEmpty()) FALLBACK_NAME else parts.joinToString(" - ")
        return "$base.pdf"
    }

    private fun candidateName(): String {
        val resume = _state.value.original.ifBlank { _state.value.text }
        val data = ResumeData.fromJson(resume)
        if (data != null && data.name.isNotBlank()) return data.name

        val headers = setOf(
            "personal info", "contact", "contact info", "resume",
            "skills", "core skills", "relevant work experience", "work experience",
            "experience", "education", "projects", "professional summary", "summary"
        )
        for (line in resume.lineSequence()) {
            val t = line.trim()
            if (t.isBlank()) continue
            val lower = t.lowercase().trimEnd(':')
            if (lower in headers) continue
            if (t.length > 60) continue
            if (Regex("^\\d{3}[-.)\\s]").containsMatchIn(t)) continue
            if (lower.startsWith("phone") || lower.startsWith("email") || lower.startsWith("address") ||
                lower.startsWith("linkedin") || lower.startsWith("location") || lower.startsWith("city") ||
                lower.startsWith("name:")
            ) continue
            return t
        }
        return FALLBACK_NAME
    }

    private fun sanitizeFileNamePart(value: String): String =
        value.replace(Regex("[\\\\/:*?\"<>|]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    companion object {
        private const val FALLBACK_NAME = "Elizabeth Kale Whaley"
    }
}
