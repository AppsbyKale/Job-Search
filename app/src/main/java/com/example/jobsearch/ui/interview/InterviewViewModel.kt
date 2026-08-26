package com.example.jobsearch.ui.interview

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.jobsearch.ai.InterviewGenerator
import com.example.jobsearch.ai.IModelManager
import com.example.jobsearch.ai.ModelManager
import com.example.jobsearch.data.InterviewAnswer
import com.example.jobsearch.data.InterviewQuestion
import com.example.jobsearch.data.InterviewReport
import com.example.jobsearch.data.InterviewRepository
import com.example.jobsearch.data.Job
import com.example.jobsearch.data.JobRepository
import com.example.jobsearch.data.SettingsRepository
import com.example.jobsearch.di.ApplicationScope
import com.example.jobsearch.speech.AudioRecorder
import com.example.jobsearch.speech.SpeechToText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

/**
 * ViewModel for the interview practice screen.
 * Orchestrates question generation, voice recording, and AI evaluation.
 */
@HiltViewModel
class InterviewViewModel @Inject constructor(
    private val jobRepository: JobRepository,
    private val interviewRepository: InterviewRepository,
    private val modelManager: IModelManager,
    private val settingsRepository: SettingsRepository,
    private val generator: InterviewGenerator,
    private val speechToText: SpeechToText,
    private val audioRecorder: AudioRecorder,
    private val audioDir: File,
    @ApplicationScope private val appScope: CoroutineScope,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val jobId: Long = savedStateHandle.get<Long>("jobId") ?: -1L

    data class UiState(
        val job: Job? = null,
        val loading: Boolean = true,
        val questions: List<InterviewQuestion> = emptyList(),
        val answers: List<InterviewAnswer> = emptyList(),
        val report: InterviewReport? = null,
        val modelReady: Boolean = false,
        val resumeLoaded: Boolean = false,
        val generating: String? = null,
        val generatingError: String? = null,
        val recordingQuestionId: Long? = null,
        val transcribingQuestionId: Long? = null,
        val partialTranscript: String = "",
        val notice: String? = null,
        val manualAnswer: String? = null,
        val manualLoading: Boolean = false
    )

    private val _notice = MutableStateFlow<String?>(null)
    private val _recording = MutableStateFlow<Long?>(null)
    private val _transcribing = MutableStateFlow<Long?>(null)
    private val _questionCount = MutableStateFlow(3)
    private val _manualAnswer = MutableStateFlow<String?>(null)
    private val _manualLoading = MutableStateFlow(false)

    val questionCount: StateFlow<Int> = _questionCount.asStateFlow()

    private data class GenPart(
        val job: Job?,
        val questions: List<InterviewQuestion>,
        val answers: List<InterviewAnswer>,
        val report: InterviewReport?,
        val generating: String?,
        val generatingError: String?
    )

    private data class LocalPart(
        val resume: String,
        val partialTranscript: String,
        val notice: String?,
        val recording: Long?,
        val transcribing: Long?,
        val manualAnswer: String?,
        val manualLoading: Boolean
    )

    val state: StateFlow<UiState> = combine(
        combine(
            jobRepository.observeJob(jobId),
            interviewRepository.observeQuestions(jobId),
            interviewRepository.observeAnswers(jobId),
            interviewRepository.observeReport(jobId),
            generator.state
        ) { job, questions, answers, report, gen ->
            GenPart(
                job = job,
                questions = questions,
                answers = answers,
                report = report,
                generating = if (gen.running && gen.jobId == jobId) gen.label else null,
                generatingError = if (gen.jobId == jobId) gen.error else null
            )
        },
        combine(
            settingsRepository.resumeText,
            speechToText.partialText,
            _notice,
            _recording,
            _transcribing,
            _manualAnswer,
            _manualLoading
        ) { args ->
            LocalPart(
                resume = args[0] as String,
                partialTranscript = args[1] as String,
                notice = args[2] as String?,
                recording = args[3] as Long?,
                transcribing = args[4] as Long?,
                manualAnswer = args[5] as String?,
                manualLoading = args[6] as Boolean
            )
        }
    ) { g, l ->
        UiState(
            job = g.job,
            loading = false,
            questions = g.questions,
            answers = g.answers,
            report = g.report,
            modelReady = modelManager.isModelDownloaded(),
            resumeLoaded = l.resume.isNotBlank(),
            generating = g.generating,
            generatingError = g.generatingError,
            recordingQuestionId = l.recording,
            transcribingQuestionId = l.transcribing,
            partialTranscript = l.partialTranscript,
            notice = l.notice,
            manualAnswer = l.manualAnswer,
            manualLoading = l.manualLoading
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())

    private fun audioFileFor(questionId: Long): File = File(audioDir, "question_$questionId.wav")

    fun setQuestionCount(count: Int) {
        _questionCount.value = count
    }

    fun generateQuestions() {
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
        generator.generateQuestions(jobId, _questionCount.value)
    }

    fun startRecording(questionId: Long) {
        if (_recording.value != null) return
        audioDir.mkdirs()
        try {
            audioRecorder.start(audioFileFor(questionId))
            speechToText.startListening(
                onResult = { text ->
                    saveAnswer(questionId, text)
                },
                onError = { code ->
                    _notice.value = "Speech recognition error: $code"
                }
            )
            _recording.value = questionId
        } catch (e: Exception) {
            _notice.value = "Could not start recording: ${e.message ?: "unknown error"}"
        }
    }

    fun stopRecording(questionId: Long) {
        if (_recording.value != questionId) return
        audioRecorder.stop()
        speechToText.stopListening()
        _recording.value = null
    }

    private fun saveAnswer(questionId: Long, text: String) {
        if (text.isBlank()) return
        appScope.launch {
            try {
                val existing = interviewRepository.getAnswer(questionId)
                val answer = existing ?: InterviewAnswer(jobId = jobId, questionId = questionId)
                interviewRepository.upsertAnswer(
                    answer.copy(text = text, audioPath = audioFileFor(questionId).absolutePath)
                )
            } catch (e: Exception) {
                _notice.value = "Failed to save answer: ${e.message ?: "unknown error"}"
            }
        }
    }

    fun saveTypedAnswer(questionId: Long, text: String) {
        viewModelScope.launch {
            val existing = interviewRepository.getAnswer(questionId)
            val answer = existing ?: InterviewAnswer(jobId = jobId, questionId = questionId)
            interviewRepository.upsertAnswer(answer.copy(text = text.trim()))
        }
    }

    fun evaluate() {
        val s = state.value
        if (s.generating != null) return
        if (s.answers.none { it.hasAnswer }) {
            _notice.value = "Answer at least one question first."
            return
        }
        generator.evaluate(jobId)
    }

    fun dismissNotice() {
        _notice.value = null
        generator.dismissError()
    }

    fun showNotice(message: String) {
        _notice.value = message
    }

    fun askManualQuestion(question: String) {
        if (question.isBlank() || _manualLoading.value) return
        viewModelScope.launch {
            try {
                _manualLoading.value = true
                _manualAnswer.value = null
                val job = jobRepository.getJob(jobId) ?: return@launch
                val resume = settingsRepository.resumeText.first()
                val prompt = com.example.jobsearch.ai.PromptBuilder.manualQuestionPrompt(job, resume, question)
                val result = modelManager.generate(prompt, source = "Interview Practice")
                _manualAnswer.value = result.trim()
            } catch (e: Exception) {
                _notice.value = "Failed to get answer: ${e.message}"
            } finally {
                _manualLoading.value = false
            }
        }
    }

    fun clearManualAnswer() {
        _manualAnswer.value = null
    }

    fun answerFor(questionId: Long): InterviewAnswer? =
        state.value.answers.firstOrNull { it.questionId == questionId }

    override fun onCleared() {
        super.onCleared()
        audioRecorder.stop()
        speechToText.release()
    }
}
