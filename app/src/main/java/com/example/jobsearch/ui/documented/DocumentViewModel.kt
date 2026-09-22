package com.example.jobsearch.ui.documented

import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.jobsearch.ai.GenerationRepository
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
    private val generationRepository: GenerationRepository,
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
        val resumeData: ResumeData? = null,
        val showCheatSheetOptionsDialog: Boolean = false
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val _showCheatSheetOptionsDialog = MutableStateFlow(false)
    val showCheatSheetOptionsDialog: StateFlow<Boolean> = _showCheatSheetOptionsDialog.asStateFlow()

    val generationState: StateFlow<GenerationRepository.State> = generationRepository.state

    init {
        viewModelScope.launch {
            repository.observeJob(jobId).collect { job ->
                if (job != null) {
                    val rawText = when (type) {
                        "resume" -> job.resumeText
                        "cheat" -> job.cheatSheetText
                        "followup" -> job.followUpEmailText
                        "initial" -> job.initialEmailText
                        "thankyou" -> job.thankYouEmailText
                        "external_resume" -> job.externalResumeText
                        "external_cover" -> job.externalCoverLetterText
                        else -> job.coverLetterText
                    }
                    
                    val displayLines = if (rawText != null) {
                        when (type) {
                            "resume", "external_resume" -> ResumeData.fromJson(rawText)?.toHumanReadableText() ?: rawText
                            "cheat" -> CheatSheetData.fromJson(rawText)?.toHumanReadableText() ?: rawText
                            "followup", "initial", "thankyou" -> rawText
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
                            jobTitle = job.title,
                            company = job.company,
                            resumeData = resumeData
                        )
                    }
                }
            }
        }
        viewModelScope.launch {
            _showCheatSheetOptionsDialog.collect { show ->
                _state.update { it.copy(showCheatSheetOptionsDialog = show) }
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

    fun updateSkillCategoryName(index: Int, name: String) {
        val current = _state.value.resumeData ?: return
        val newSkills = current.skills.mapIndexed { i, cat ->
            if (i == index) cat.copy(name = name) else cat
        }
        updateResumeData(current.copy(skills = newSkills))
    }

    fun updateSkillCategorySkills(index: Int, rawSkills: String) {
        val current = _state.value.resumeData ?: return
        val skillList = rawSkills.split(",").map { it.trim() }.filter { it.isNotBlank() }
        val newSkills = current.skills.mapIndexed { i, cat ->
            if (i == index) cat.copy(skills = skillList) else cat
        }
        updateResumeData(current.copy(skills = newSkills))
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
                "thankyou" -> job.copy(thankYouEmailText = textToSave)
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
            "initial" -> "initial email"
            "thankyou" -> "thank you email"
            else -> "cover letter"
        }
        exporter.shareText("$title $label", _state.value.text)
    }

    fun exportPdf(uri: android.net.Uri): Boolean {
        val currentText = _state.value.text
        val textToExport = prepareTextForExportOrPreview(currentText)
        return exporter.writePdf(
            uri,
            textToExport,
            resumeLayout = isResume,
            coverLetterLayout = !isResume && !isCheat,
            cheatSheetLayout = isCheat,
            date = todayFormatted(),
            companyName = _state.value.company
        )
    }

    /** Renders the document as PDF pages for the on-screen preview. */
    suspend fun previewPages(): List<Bitmap>? = withContext(Dispatchers.IO) {
        val currentText = _state.value.text
        val textToRender = prepareTextForExportOrPreview(currentText)
        exporter.renderDocumentPages(
            textToRender,
            resumeLayout = isResume,
            coverLetterLayout = !isResume && !isCheat,
            cheatSheetLayout = isCheat,
            date = todayFormatted(),
            companyName = _state.value.company
        )
    }

    private fun prepareTextForExportOrPreview(currentText: String): String {
        return when (type) {
            "resume", "external_resume" -> ResumeData.fromJson(currentText)?.toHumanReadableText() ?: currentText
            "cheat" -> CheatSheetData.fromJson(currentText)?.toHumanReadableText() ?: currentText
            "followup", "initial", "thankyou" -> currentText
            else -> CoverLetterData.fromJson(currentText)?.toHumanReadableText() ?: currentText
        }
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
        return CheatSheetData.fromText(text) ?: CheatSheetData()
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

    fun showCheatSheetOptions(show: Boolean) {
        _showCheatSheetOptionsDialog.value = show
    }

    fun generateCheatSheetWithOptions(
        includeOverview: Boolean,
        includeChallenges: Boolean,
        includeDayToDay: Boolean,
        includeHighlights: Boolean,
        customQuestions: String,
        strengths: String,
        weaknesses: String
    ) {
        _showCheatSheetOptionsDialog.value = false
        viewModelScope.launch {
            generationRepository.generateCheatSheet(
                jobId = jobId,
                includeOverview = includeOverview,
                includeChallenges = includeChallenges,
                includeDayToDay = includeDayToDay,
                includeHighlights = includeHighlights,
                customQuestions = customQuestions,
                strengths = strengths,
                weaknesses = weaknesses
            )
        }
    }

    fun regenerateDocument() {
        if (type == "cheat") {
            _showCheatSheetOptionsDialog.value = true
        } else {
            viewModelScope.launch {
                when (type) {
                    "resume", "external_resume" -> generationRepository.generate(jobId, GenerationRepository.Type.RESUME)
                    "cover", "external_cover" -> generationRepository.generate(jobId, GenerationRepository.Type.COVER)
                    "followup" -> generationRepository.generate(jobId, GenerationRepository.Type.FOLLOW_UP)
                    "initial" -> generationRepository.generate(jobId, GenerationRepository.Type.INITIAL_EMAIL)
                    "thankyou" -> generationRepository.generate(jobId, GenerationRepository.Type.THANK_YOU)
                }
            }
        }
    }

    private fun sanitizeFileNamePart(value: String): String =
        value.replace(Regex("[\\\\/:*?\"<>|]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    companion object {
        private const val FALLBACK_NAME = "Elizabeth Kale Whaley"
    }
}
