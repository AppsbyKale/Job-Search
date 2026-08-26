package com.example.jobsearch.ui.addjob

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.jobsearch.ai.IModelManager
import com.example.jobsearch.ai.PromptBuilder
import com.example.jobsearch.data.InterviewAnswer
import com.example.jobsearch.data.InterviewQuestion
import com.example.jobsearch.data.InterviewRepository
import com.example.jobsearch.data.Job
import com.example.jobsearch.data.JobRepository
import com.example.jobsearch.data.SettingsRepository
import com.example.jobsearch.parsing.JobParser
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * ViewModel for the "Add Job" screen.
 * Handles job parsing from URLs and auto-sweeping job descriptions.
 */
@HiltViewModel
class AddJobViewModel @Inject constructor(
    private val jobs: JobRepository,
    private val interviewRepository: InterviewRepository,
    private val trainingRepository: com.example.jobsearch.data.TrainingRepository,
    private val parser: JobParser,
    private val modelManager: IModelManager,
    private val settings: SettingsRepository
) : ViewModel() {

    data class UiState(
        val jobId: Long? = null,
        val url: String = "",
        val parsing: Boolean = false,
        val parseError: String? = null,
        val webViewOpen: Boolean = false,
        val title: String = "",
        val company: String = "",
        val description: String = "",
        val parsed: Boolean = false,
        val saving: Boolean = false,
        val savedJobId: Long? = null,
        val saveError: String? = null,
        val generateChoice: String? = null,
        val modelReady: Boolean = false,
        val resumeLoaded: Boolean = false,
        val questionsRunning: Boolean = false,
        val improveQuestions: List<String> = emptyList(),
        val improveAnswers: List<String> = emptyList(),
        val improving: Boolean = false,
        val improvedResume: String = "",
        val smartCleaning: Boolean = false,
        val trainingLoggingEnabled: Boolean = false
    )

    private val _state = MutableStateFlow(UiState())

    val state: StateFlow<UiState> = combine(
        _state,
        settings.resumeText,
        settings.trainingLoggingEnabled
    ) { s, resume, trainingEnabled ->
        s.copy(
            modelReady = modelManager.isModelDownloaded(),
            resumeLoaded = resume.isNotBlank(),
            trainingLoggingEnabled = trainingEnabled
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())

    fun onUrlChange(value: String) {
        _state.update { it.copy(url = value) }
    }

    fun onTitleChange(value: String) {
        _state.update { it.copy(title = value) }
    }

    fun onCompanyChange(value: String) {
        _state.update { it.copy(company = value) }
    }

    fun onDescriptionChange(value: String) {
        _state.update { it.copy(description = value) }
    }

    fun trimDescriptionFluff() {
        _state.update {
            val trimmed = parser.trimFluff(it.description)
            it.copy(description = trimmed)
        }
    }

    fun smartCleanDescription() {
        autoSweep(_state.value.description)
    }

    fun loadJob(id: Long) {
        if (_state.value.jobId == id) return
        viewModelScope.launch {
            val job = jobs.getJob(id)
            if (job != null) {
                _state.update {
                    it.copy(
                        jobId = id,
                        url = job.url,
                        title = job.title,
                        company = job.company,
                        description = job.description,
                        parsed = true
                    )
                }
            }
        }
    }

    fun openWebView() {
        _state.update { it.copy(webViewOpen = true, parseError = null) }
    }

    fun closeWebView() {
        _state.update { it.copy(webViewOpen = false) }
    }

    fun onWebViewHtml(html: String?) {
        val url = _state.value.url.trim()
        if (html.isNullOrBlank()) {
            _state.update {
                it.copy(parseError = "The page didn't finish loading yet. Wait a moment and tap \"Done\" again.")
            }
            return
        }
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) { parser.parseFromHtml(url, html) }
                val cleanedDesc = parser.trimFluff(result.description)
                _state.update {
                    it.copy(
                        webViewOpen = false,
                        parsed = true,
                        title = result.title,
                        company = result.company,
                        description = cleanedDesc
                    )
                }
                autoSweep(cleanedDesc)
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        webViewOpen = false,
                        parseError = e.message ?: "Could not read the page. Try again."
                    )
                }
            }
        }
    }

    fun setGenerateChoice(value: String?) {
        _state.update { it.copy(generateChoice = value) }
    }

    fun parse() {
        val url = _state.value.url.trim()
        if (url.isEmpty()) {
            _state.update { it.copy(parseError = "Enter a job posting URL.") }
            return
        }
        _state.update {
            it.copy(
                parsing = true,
                parseError = null,
                parsed = false,
                questionsRunning = false,
                improveQuestions = emptyList(),
                improveAnswers = emptyList(),
                improving = false,
                improvedResume = ""
            )
        }
        viewModelScope.launch {
            try {
                val result = parser.parse(url)
                val cleanedDesc = parser.trimFluff(result.description)
                _state.update {
                    it.copy(
                        parsing = false,
                        parsed = true,
                        title = result.title,
                        company = result.company,
                        description = cleanedDesc
                    )
                }
                autoSweep(cleanedDesc)
            } catch (e: Exception) {
                val currentUrl = _state.value.url.trim()
                _state.update {
                    it.copy(
                        parsing = false,
                        parseError = e.message
                            ?: "Could not fetch $currentUrl. Check the URL and your connection."
                    )
                }
            }
        }
    }

    fun setImproveAnswer(index: Int, text: String) {
        _state.update {
            val answers = it.improveAnswers.toMutableList()
            if (index in answers.indices) answers[index] = text
            it.copy(improveAnswers = answers)
        }
    }

    private fun autoSweep(rawDesc: String) {
        if (rawDesc.isBlank() || _state.value.smartCleaning) return
        
        if (!modelManager.isModelDownloaded()) return

        _state.update { it.copy(smartCleaning = true) }
        viewModelScope.launch {
            try {
                Log.d(TAG, "Auto-sweeping job description with Local AI...")
                val prompt = PromptBuilder.smartCleanPrompt(rawDesc)
                val cleaned = modelManager.generate(prompt, source = "Manual Auto-Sweep").trim()
                if (cleaned.isNotBlank()) {
                    _state.update { it.copy(description = cleaned) }
                    trainingRepository.logExample(
                        appName = "task",
                        feature = "auto_sweep",
                        inputPrompt = prompt,
                        modelOutput = cleaned
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Auto-sweep failed: ${e.message}")
            } finally {
                _state.update { it.copy(smartCleaning = false) }
            }
        }
    }

    fun save(generateChoice: String? = null) {
        val s = _state.value
        val title = s.title.trim()
        val company = s.company.trim()
        if (title.isEmpty() && company.isEmpty()) {
            _state.update { it.copy(saveError = "Add at least a job title or company.") }
            return
        }
        _state.update { it.copy(saving = true, saveError = null, generateChoice = generateChoice) }
        viewModelScope.launch {
            try {
                val jobId = s.jobId
                if (jobId != null) {
                    val existing = jobs.getJob(jobId)
                    if (existing != null) {
                        val updated = existing.copy(
                            title = title.ifEmpty { "Untitled job" },
                            company = company,
                            url = s.url.trim(),
                            description = s.description.trim(),
                            resumeText = s.improvedResume.trim(),
                            status = com.example.jobsearch.data.JobStatus.SAVED.name
                        )
                        jobs.updateJob(updated)
                        saveQuestionsAndAnswers(jobId)
                        _state.update { it.copy(saving = false, savedJobId = jobId) }
                        return@launch
                    }
                }

                val job = Job(
                    title = title.ifEmpty { "Untitled job" },
                    company = company,
                    url = s.url.trim(),
                    description = s.description.trim(),
                    resumeText = s.improvedResume.trim(),
                    dateAdded = System.currentTimeMillis()
                )
                val id = jobs.addJob(job)
                saveQuestionsAndAnswers(id)
                _state.update { it.copy(saving = false, savedJobId = id) }
            } catch (e: Exception) {
                _state.update { it.copy(saving = false, saveError = e.message ?: "Failed to save.") }
            }
        }
    }

    fun deleteJob() {
        val jobId = _state.value.jobId ?: return
        viewModelScope.launch {
            try {
                interviewRepository.deleteForJob(jobId)
                jobs.deleteJob(jobId)
                _state.update { it.copy(savedJobId = -2L) } // Use -2 to signify deleted
            } catch (e: Exception) {
                _state.update { it.copy(saveError = "Failed to delete: ${e.message}") }
            }
        }
    }

    private suspend fun saveQuestionsAndAnswers(jobId: Long) {
        val s = _state.value
        if (s.improveQuestions.isEmpty()) return

        val questions = s.improveQuestions.mapIndexed { index, q ->
            InterviewQuestion(jobId = jobId, question = q, position = index)
        }
        val questionIds = interviewRepository.replaceQuestions(jobId, questions)

        s.improveAnswers.forEachIndexed { index, answerText ->
            if (answerText.isNotBlank() && (index in questionIds.indices)) {
                interviewRepository.upsertAnswer(
                    InterviewAnswer(
                        jobId = jobId,
                        questionId = questionIds[index],
                        text = answerText
                    )
                )
            }
        }
    }

    fun markLastActionAsGood(feature: String) {
        viewModelScope.launch {
            trainingRepository.observeExamples().first()
                .asSequence()
                .filter { it.feature == feature }
                .firstOrNull()?.let {
                    trainingRepository.markAsGood(it.id, isGood = true)
                }
        }
    }

    companion object {
        private const val TAG = "AddJobViewModel"
    }
}
