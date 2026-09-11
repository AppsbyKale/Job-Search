package com.example.jobsearch.ai

import com.example.jobsearch.data.InterviewQuestion
import com.example.jobsearch.data.InterviewReport
import com.example.jobsearch.data.InterviewRepository
import com.example.jobsearch.data.JobRepository
import com.example.jobsearch.data.SettingsRepository
import com.example.jobsearch.data.TrainingRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration.Companion.seconds

class InterviewGenerator(
    private val scope: CoroutineScope,
    private val modelManager: IModelManager,
    private val settings: SettingsRepository,
    private val jobRepository: JobRepository,
    private val interviewRepository: InterviewRepository,
    private val trainingRepository: TrainingRepository
) {

    data class State(
        val running: Boolean = false,
        val jobId: Long? = null,
        val label: String? = null,
        val error: String? = null
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    fun generateQuestions(jobId: Long, count: Int) {
        if (_state.value.running) return
        _state.value = State(running = true, jobId = jobId, label = "Generating questions...")
        scope.launch {
            try {
                val job = jobRepository.getJob(jobId)
                    ?: throw IllegalStateException("Job not found.")
                val resume = settings.resumeText.first()
                if (resume.isBlank()) {
                    throw IllegalStateException("No resume on file. Add your resume in Settings first.")
                }
                val prompt = PromptBuilder.interviewQuestionsPrompt(job, resume, count)
                val output = withTimeout(180.seconds) {
                    modelManager.generate(prompt, source = "Interview Questions")
                }
                val questions = PromptBuilder.parseQuestions(output)
                if (questions.isEmpty()) {
                    throw IllegalStateException("The model did not return any questions. Please try again.")
                }

                trainingRepository.logExample(
                    appName = "task",
                    feature = "interview_questions",
                    inputPrompt = prompt,
                    modelOutput = output
                )

                interviewRepository.resetForJob(jobId)
                interviewRepository.replaceQuestions(
                    jobId,
                    questions.mapIndexed { index, question ->
                        InterviewQuestion(jobId = jobId, question = question, position = index)
                    }
                )
                _state.value = State()
            } catch (e: Exception) {
                _state.value = State(jobId = jobId, error = e.message ?: "Question generation failed.")
            }
        }
    }

    fun evaluate(jobId: Long) {
        if (_state.value.running) return
        _state.value = State(running = true, jobId = jobId, label = "Evaluating...")
        scope.launch {
            try {
                val job = jobRepository.getJob(jobId)
                    ?: throw IllegalStateException("Job not found.")
                val resume = settings.resumeText.first()
                if (resume.isBlank()) {
                    throw IllegalStateException("No resume on file. Add your resume in Settings first.")
                }
                val pairs = interviewRepository.getQuestions(jobId).mapNotNull { question ->
                    val answer = interviewRepository.getAnswer(question.id)
                    if (answer?.hasAnswer == true) question to answer else null
                }
                if (pairs.isEmpty()) {
                    throw IllegalStateException("Answer at least one question first.")
                }

                pairs.forEachIndexed { index, (question, answer) ->
                    _state.value = _state.value.copy(
                        label = "Evaluating question ${index + 1} of ${pairs.size}..."
                    )
                    val prompt = PromptBuilder.interviewFeedbackPrompt(job, resume, question.question, answer.text)
                    val raw = withTimeout(180.seconds) {
                        modelManager.generate(prompt, source = "Interview Feedback").trim()
                    }
                    
                    trainingRepository.logExample(
                        appName = "task",
                        feature = "interview_feedback",
                        inputPrompt = prompt,
                        modelOutput = raw
                    )

                    val parsed = PromptBuilder.parseFeedback(raw)
                    val (feedback, modelAnswer) = PromptBuilder.splitModelAnswer(parsed.feedback)
                    interviewRepository.upsertAnswer(
                        answer.copy(score = parsed.score, feedback = feedback, modelAnswer = modelAnswer)
                    )
                }

                _state.value = _state.value.copy(label = "Writing overall report...")
                val overallPrompt = PromptBuilder.interviewOverallPrompt(
                    job, resume, pairs.map { it.first.question to it.second.text }
                )
                val overall = withTimeout(180.seconds) {
                    modelManager.generate(overallPrompt, source = "Interview Report").trim()
                }
                
                trainingRepository.logExample(
                    appName = "task",
                    feature = "interview_report",
                    inputPrompt = overallPrompt,
                    modelOutput = overall
                )

                interviewRepository.upsertReport(
                    InterviewReport(jobId = jobId, overall = overall)
                )
                _state.value = State()
            } catch (e: Exception) {
                _state.value = State(jobId = jobId, error = e.message ?: "Evaluation failed.")
            }
        }
    }

    fun dismissError() {
        _state.value = _state.value.copy(error = null, jobId = null)
    }
}
