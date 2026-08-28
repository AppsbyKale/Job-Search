package com.example.jobsearch.ui.jobdetail

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.jobsearch.ai.GenerationRepository
import com.example.jobsearch.ai.IModelManager
import com.example.jobsearch.ai.ModelManager
import com.example.jobsearch.ai.PromptBuilder
import com.example.jobsearch.data.InterviewAnswer
import com.example.jobsearch.data.InterviewQuestion
import com.example.jobsearch.data.InterviewRepository
import com.example.jobsearch.data.Job
import com.example.jobsearch.data.JobRepository
import com.example.jobsearch.data.JobStatus
import com.example.jobsearch.data.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds
import org.json.JSONObject
import org.json.JSONTokener
import javax.inject.Inject

/**
 * ViewModel for the job details screen.
 * Manages job data, document generation state, and interview Q&A.
 */
@HiltViewModel
class JobDetailViewModel @Inject constructor(
    private val repository: JobRepository,
    private val interviewRepository: InterviewRepository,
    private val modelManager: IModelManager,
    private val settingsRepository: SettingsRepository,
    private val generationRepository: GenerationRepository,
    private val trainingRepository: com.example.jobsearch.data.TrainingRepository,
    private val exporter: com.example.jobsearch.document.DocumentExporter,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val jobId: Long = savedStateHandle.get<Long>("jobId") ?: -1L
    private var generateProcessed = false

    init {
        // Model preloading is handled globally in JobSearchApp
    }

    data class MatchResult(
        val score: Int = 0,
        val foundKeywords: List<String> = emptyList(),
        val missingKeywords: List<String> = emptyList()
    )

    data class UiState(
        val job: Job? = null,
        val loading: Boolean = true,
        val generating: String? = null,
        val generationType: GenerationRepository.Type? = null,
        val generationProgress: Float = 0f,
        val generationProgressText: String? = null,
        val generationIndeterminate: Boolean = true,
        val generationError: String? = null,
        val modelReady: Boolean = false,
        val resumeLoaded: Boolean = false,
        val useQaAnswers: Boolean = false,
        val notice: String? = null,
        val showSupplementalDialog: Boolean = false,
        val showInitialEmailDialog: Boolean = false,
        val showCheatSheetDialog: Boolean = false,
        val showFollowUpDialog: Boolean = false,
        val showMatchAnalysisDialog: Boolean = false,
        val showJobDescriptionDialog: Boolean = false,
        val showInterviewDialog: Boolean = false,
        val showAskAiQuestionDialog: Boolean = false,
        val showNotesDialog: Boolean = false,
        val showExternalUploadDialog: Boolean = false,
        val questions: List<InterviewQuestion> = emptyList(),
        val answers: List<InterviewAnswer> = emptyList(),
        val questionsRunning: Boolean = false,
        val statusLabel: String = "",
        val statusHint: String = "",
        val matchResult: MatchResult? = null,
        val matchRunning: Boolean = false,
        val showResumeSteeringDialog: Boolean = false,
        val resumeSteeringPrompt: String = "",
        val coverSteeringPrompt: String = ""
    )

    private val _notice = MutableStateFlow<String?>(null)
    private val _useQaAnswers = MutableStateFlow(false)
    private val _showSupplementalDialog = MutableStateFlow(false)
    private val _showInitialEmailDialog = MutableStateFlow(false)
    private val _showCheatSheetDialog = MutableStateFlow(false)
    private val _showFollowUpDialog = MutableStateFlow(false)
    private val _showMatchAnalysisDialog = MutableStateFlow(false)
    private val _showJobDescriptionDialog = MutableStateFlow(false)
    private val _showInterviewDialog = MutableStateFlow(false)
    private val _showAskAiQuestionDialog = MutableStateFlow(false)
    private val _showNotesDialog = MutableStateFlow(false)
    private val _showExternalUploadDialog = MutableStateFlow(false)
    private val _questionsRunning = MutableStateFlow(false)
    private val _matchResult = MutableStateFlow<MatchResult?>(null)
    private val _matchRunning = MutableStateFlow(false)
    private val _showResumeSteeringDialog = MutableStateFlow(false)
    private val _resumeSteeringPrompt = MutableStateFlow("")
    private val _coverSteeringPrompt = MutableStateFlow("")

    init {
        // Model preloading is handled globally in JobSearchApp
    }

    val state: StateFlow<UiState> = combine(
        repository.observeJob(jobId),
        generationRepository.state,
        settingsRepository.resumeText,
        _useQaAnswers,
        _notice,
        _showSupplementalDialog,
        _showInitialEmailDialog,
        _showCheatSheetDialog,
        _showFollowUpDialog,
        _showMatchAnalysisDialog,
        _showJobDescriptionDialog,
        _showInterviewDialog,
        _showAskAiQuestionDialog,
        _showNotesDialog,
        _showExternalUploadDialog,
        interviewRepository.observeQuestions(jobId),
        interviewRepository.observeAnswers(jobId),
        _questionsRunning,
        modelManager.downloadProgress,
        _matchResult,
        _matchRunning,
        _showResumeSteeringDialog,
        _resumeSteeringPrompt,
        _coverSteeringPrompt
    ) { args ->
        val job = args[0] as Job?
        val gen = args[1] as GenerationRepository.State
        val resume = args[2] as String
        val useQa = args[3] as Boolean
        val notice = args[4] as String?
        val supp = args[5] as Boolean
        val initialEmailShow = args[6] as Boolean
        val cheat = args[7] as Boolean
        val follow = args[8] as Boolean
        val matchAnalysis = args[9] as Boolean
        val jobDesc = args[10] as Boolean
        val showInterview = args[11] as Boolean
        val showAskAi = args[12] as Boolean
        val showNotes = args[13] as Boolean
        val externalUploadShow = args[14] as Boolean
        @Suppress("UNCHECKED_CAST")
        val questions = args[15] as List<InterviewQuestion>
        @Suppress("UNCHECKED_CAST")
        val answers = args[16] as List<InterviewAnswer>
        val qRunning = args[17] as Boolean
        // args[18] is downloadProgress
        val mResult = args[19] as MatchResult?
        val mRunning = args[20] as Boolean
        val steeringShow = args[21] as Boolean
        val steeringPrompt = args[22] as String
        val coverSteeringPrompt = args[23] as String

        val statusObj = job?.let { JobStatus.fromName(it.status) } ?: JobStatus.SAVED
        val hint = when (statusObj) {
            JobStatus.SYNCED -> "Needs review"
            JobStatus.SAVED -> "Ready to apply"
            JobStatus.APPLIED -> "Waiting for feedback"
            JobStatus.INTERVIEWING -> "Good luck!"
            JobStatus.OFFER -> "Congratulations!"
            JobStatus.REJECTED -> "Keep going!"
            JobStatus.ARCHIVED -> "Stored"
        }

        UiState(
            job = job,
            loading = false,
            generating = if (gen.running && gen.jobId == jobId) gen.label else null,
            generationType = if (gen.running && gen.jobId == jobId) gen.type else null,
            generationProgress = gen.progress,
            generationProgressText = gen.progressText,
            generationIndeterminate = gen.indeterminate,
            generationError = if (gen.jobId == jobId) gen.error else null,
            modelReady = modelManager.isModelDownloaded(),
            resumeLoaded = resume.isNotBlank(),
            useQaAnswers = useQa,
            notice = notice,
            showSupplementalDialog = supp,
            showInitialEmailDialog = initialEmailShow,
            showCheatSheetDialog = cheat,
            showFollowUpDialog = follow,
            showMatchAnalysisDialog = matchAnalysis,
            showJobDescriptionDialog = jobDesc,
            showInterviewDialog = showInterview,
            showAskAiQuestionDialog = showAskAi,
            showNotesDialog = showNotes,
            showExternalUploadDialog = externalUploadShow,
            questions = questions,
            answers = answers,
            questionsRunning = qRunning,
            statusLabel = statusObj.label,
            statusHint = hint,
            matchResult = mResult,
            matchRunning = mRunning,
            showResumeSteeringDialog = steeringShow,
            resumeSteeringPrompt = steeringPrompt,
            coverSteeringPrompt = coverSteeringPrompt
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())

    fun setStatus(status: JobStatus) {
        state.value.job?.let { job ->
            viewModelScope.launch { repository.updateJob(job.copy(status = status.name)) }
        }
    }

    private var pendingGenType: GenerationRepository.Type? = null

    fun generateResume() {
        pendingGenType = GenerationRepository.Type.RESUME
        _showResumeSteeringDialog.value = true
    }

    fun generateCoverLetter() {
        generate(GenerationRepository.Type.COVER)
    }

    fun generateBoth() {
        pendingGenType = GenerationRepository.Type.BOTH
        _showResumeSteeringDialog.value = true
    }

    fun confirmSteering(resumePrompt: String, coverPrompt: String) {
        _resumeSteeringPrompt.value = resumePrompt
        _coverSteeringPrompt.value = coverPrompt
        _showResumeSteeringDialog.value = false
        val type = pendingGenType
        if (type != null) {
            generationRepository.generate(jobId, type, state.value.useQaAnswers, resumePrompt, coverPrompt)
        }
        pendingGenType = null
    }

    fun dismissSteering() {
        _showResumeSteeringDialog.value = false
        pendingGenType = null
    }

    fun generateFollowUpEmail() {
        val s = state.value
        if (s.generating != null) return
        if (!s.modelReady) {
            _notice.value = "The AI model is not downloaded yet. Go to Settings and download it first."
            return
        }
        generationRepository.generate(jobId, GenerationRepository.Type.FOLLOW_UP)
    }

    fun deleteDocument(type: String) {
        val job = state.value.job ?: return
        val updated = when (type) {
            "resume" -> job.copy(resumeText = "")
            "cheat" -> job.copy(cheatSheetText = "")
            "followup" -> job.copy(followUpEmailText = "")
            "initial" -> job.copy(initialEmailText = "")
            else -> job.copy(coverLetterText = "")
        }
        viewModelScope.launch { repository.updateJob(updated) }
    }

    fun generateInitialEmail() {
        val s = state.value
        if (s.generating != null) return
        if (!s.modelReady) {
            _notice.value = "The AI model is not downloaded yet. Go to Settings and download it first."
            return
        }
        generationRepository.generate(jobId, GenerationRepository.Type.INITIAL_EMAIL)
    }

    fun showInitialEmail(show: Boolean) { _showInitialEmailDialog.value = show }
    fun showSupplemental(show: Boolean) { _showSupplementalDialog.value = show }
    fun showCheatSheet(show: Boolean) { _showCheatSheetDialog.value = show }
    fun showFollowUp(show: Boolean) { _showFollowUpDialog.value = show }
    fun showMatchAnalysis(show: Boolean) { _showMatchAnalysisDialog.value = show }
    fun showJobDescription(show: Boolean) { _showJobDescriptionDialog.value = show }
    fun showInterview(show: Boolean) { _showInterviewDialog.value = show }
    fun showAskAiQuestion(show: Boolean) { _showAskAiQuestionDialog.value = show }
    fun showNotes(show: Boolean) { _showNotesDialog.value = show }
    fun showExternalUpload(show: Boolean) { _showExternalUploadDialog.value = show }

    fun updateNotes(text: String) {
        state.value.job?.let { job ->
            viewModelScope.launch { repository.updateJob(job.copy(notes = text)) }
        }
    }

    fun saveExternalDocument(type: String, text: String) {
        val job = state.value.job ?: return
        val updated = when (type) {
            "resume" -> job.copy(externalResumeText = text)
            "cover" -> job.copy(externalCoverLetterText = text)
            else -> job
        }
        viewModelScope.launch {
            repository.updateJob(updated)
            
            // Log as training example for learning
            if (text.isNotBlank()) {
                trainingRepository.logExample(
                    appName = "task",
                    feature = "external_${type}_upload",
                    inputPrompt = "External $type uploaded for job: ${job.title} with tags: ${job.tags}",
                    modelOutput = text
                )
            }
        }
    }

    fun updateTags(tags: String) {
        val job = state.value.job ?: return
        viewModelScope.launch {
            repository.updateJob(job.copy(tags = tags))
        }
    }

    fun generateQuestions() {
        if (state.value.questionsRunning) return
        _questionsRunning.value = true
        viewModelScope.launch {
            try {
                val job = repository.getJob(jobId) ?: return@launch
                val resume = settingsRepository.resumeText.first()
                val prompt = com.example.jobsearch.ai.PromptBuilder.improveQuestionsPrompt(job, resume, 50)
                val questions = com.example.jobsearch.ai.PromptBuilder.parseQuestions(modelManager.generate(prompt, source = "Improve Questions"))
                interviewRepository.replaceQuestions(jobId, questions.mapIndexed { i, q ->
                    InterviewQuestion(jobId = jobId, question = q, position = i)
                })
            } catch (e: Exception) {
                _notice.value = "Failed to generate questions: ${e.message}"
            } finally {
                _questionsRunning.value = false
            }
        }
    }

    fun askAiQuestion(question: String) {
        if (question.isBlank()) return
        viewModelScope.launch {
            try {
                val job = repository.getJob(jobId) ?: return@launch
                val resume = settingsRepository.resumeText.first()
                val prompt = PromptBuilder.manualQuestionPrompt(job, resume, question)
                
                // Set generating state
                _notice.value = "AI is thinking..."
                val result = modelManager.generate(prompt, source = "Ask AI Question").trim()
                
                val toughQ = com.example.jobsearch.data.ToughQuestion.fromJson(result)
                    ?: com.example.jobsearch.data.ToughQuestion(question, "Failed to parse strategy.", result)
                
                val currentCheat = com.example.jobsearch.data.CheatSheetData.fromJson(job.cheatSheetText) ?: com.example.jobsearch.data.CheatSheetData()
                val updatedCheat = currentCheat.copy(toughQuestions = currentCheat.toughQuestions + toughQ)
                
                repository.updateJob(job.copy(cheatSheetText = updatedCheat.toJson()))
                _notice.value = null
            } catch (e: Exception) {
                _notice.value = "Failed to get AI answer: ${e.message}"
            }
        }
    }

    fun setAnswer(questionId: Long, text: String) {
        viewModelScope.launch {
            interviewRepository.upsertAnswer(InterviewAnswer(jobId = jobId, questionId = questionId, text = text))
        }
    }

    fun dismissNotice() {
        _notice.value = null
        generationRepository.dismissError()
    }

    fun retry() {
        generationRepository.retryLast()
    }

    fun cancelGeneration() {
        generationRepository.cancel()
    }

    fun exportPdf(uri: android.net.Uri, text: String, isCheat: Boolean): Boolean {
        return exporter.writePdf(
            uri,
            text,
            resumeLayout = false,
            coverLetterLayout = !isCheat,
            cheatSheetLayout = isCheat,
            date = java.text.SimpleDateFormat("MMMM d, yyyy", java.util.Locale.US).format(java.util.Date())
        )
    }

    fun onGenerateFromArgs(choice: String) {
        if (generateProcessed || choice.isBlank()) return
        generateProcessed = true
        generate(choice)
    }

    fun generate(choice: String) {
        val type = when (choice) {
            "resume" -> GenerationRepository.Type.RESUME
            "cover" -> GenerationRepository.Type.COVER
            "followup" -> GenerationRepository.Type.FOLLOW_UP
            else -> GenerationRepository.Type.BOTH
        }
        generate(type)
    }

    fun generateCheatSheet() {
        val s = state.value
        if (s.generating != null) return
        if (!s.modelReady) {
            _notice.value = "The AI model is not downloaded yet. Go to Settings and download it first."
            return
        }
        generationRepository.generateCheatSheet(jobId)
    }

    fun toggleQaAnswers(enabled: Boolean) {
        _useQaAnswers.value = enabled
    }

    fun checkMatch(specificResume: String? = null) {
        if (_matchRunning.value) return
        if (!modelManager.isModelDownloaded()) return

        viewModelScope.launch {
            try {
                val job = repository.getJob(jobId) ?: return@launch
                val resume = specificResume ?: job.resumeText.ifBlank { settingsRepository.resumeText.first() }
                if (resume.isBlank()) return@launch

                _matchRunning.value = true
                val prompt = PromptBuilder.matchPercentPrompt(job, resume)
                val output = kotlinx.coroutines.withTimeout(60.seconds) {
                    modelManager.generate(prompt, source = "Match Analysis")
                }
                val result = parseMatchResult(output)
                _matchResult.value = result
            } catch (e: Exception) {
                Log.e("JobDetailViewModel", "Match check failed", e)
            } finally {
                _matchRunning.value = false
            }
        }
    }

    private fun parseMatchResult(text: String): MatchResult {
        return try {
            val tokener = JSONTokener(text)
            while (tokener.more()) {
                val c = tokener.next()
                if (c == '{') {
                    tokener.back()
                    break
                }
            }
            val json = JSONObject(tokener)
            MatchResult(
                score = json.optInt("score", 0).coerceIn(0, 100),
                foundKeywords = json.optJSONArray("foundKeywords")?.let { arr ->
                    List(arr.length()) { arr.getString(it) }
                } ?: emptyList(),
                missingKeywords = json.optJSONArray("missingKeywords")?.let { arr ->
                    List(arr.length()) { arr.getString(it) }
                } ?: emptyList()
            )
        } catch (e: Exception) {
            Log.e("JobDetailViewModel", "Failed to parse match result: $text", e)
            val match = Regex("\\d{1,3}").find(text)
            MatchResult(score = (match?.value?.toIntOrNull() ?: 0).coerceIn(0, 100))
        }
    }

    private fun generate(type: GenerationRepository.Type, steeringPrompt: String? = null) {
        val s = state.value
        if (s.generating != null) return
        if (!s.modelReady) {
            _notice.value = "The AI model is not downloaded yet. Go to Settings and download it first."
            return
        }
        if (!s.resumeLoaded) {
            _notice.value = "No resume on file. Add your resume in Settings first."
            return
        }
        generationRepository.generate(jobId, type, s.useQaAnswers, steeringPrompt)
    }
}
