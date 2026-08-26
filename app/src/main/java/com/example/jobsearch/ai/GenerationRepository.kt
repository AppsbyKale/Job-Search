package com.example.jobsearch.ai

import android.util.Log
import com.example.jobsearch.data.CoverLetterData
import com.example.jobsearch.data.InterviewRepository
import com.example.jobsearch.data.JobRepository
import com.example.jobsearch.data.ResumeData
import com.example.jobsearch.data.SettingsRepository
import com.example.jobsearch.data.TrainingRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.io.IOException
import kotlin.time.Duration.Companion.seconds

/**
 * Orchestrates the multi-phase document generation process.
 */
class GenerationRepository(
    private val scope: CoroutineScope,
    private val modelManager: IModelManager,
    private val cloudModelManager: CloudModelManager,
    private val repository: JobRepository,
    private val interviewRepository: InterviewRepository,
    private val settings: SettingsRepository,
    private val trainingRepository: TrainingRepository,
    private val systemLog: com.example.jobsearch.data.SystemLogRepository
) {
    enum class Type(val label: String) {
        RESUME("Tailoring resume..."),
        COVER("Generating cover letter..."),
        BOTH("Generating documents..."),
        FOLLOW_UP("Drafting follow-up email..."),
        CHEAT_SHEET("Generating cheat sheet...")
    }

    sealed class Event {
        data class GenerationFinished(val jobId: Long, val type: Type) : Event()
    }

    data class State(
        val running: Boolean = false,
        val jobId: Long? = null,
        val type: Type? = null,
        val label: String? = null,
        val progress: Float = 0f,
        val progressText: String? = null,
        val indeterminate: Boolean = true,
        val error: String? = null
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private val _events = MutableSharedFlow<Event>(extraBufferCapacity = 1)
    val events = _events.asSharedFlow()

    @Volatile
    private var lastRequest: Type? = null

    @Volatile
    private var activeJob: Job? = null

    private var crawlJob: Job? = null

    private fun startCrawl(max: Float) {
        crawlJob?.cancel()
        crawlJob = scope.launch {
            while (true) {
                delay(1.seconds)
                val current = _state.value.progress
                if ((current < max) && (current < 0.99f)) {
                    val next = current + 0.005f
                    val nextPercent = (next * 100).toInt()
                    _state.value = _state.value.copy(
                        progress = next,
                        progressText = "${_state.value.label}: $nextPercent%"
                    )
                } else {
                    break
                }
            }
        }
    }

    fun generate(jobId: Long, type: Type, includeQa: Boolean = false, steeringPrompt: String? = null) {
        if (_state.value.running) return
        if (!modelManager.isModelDownloaded()) {
            _state.value = State(jobId = jobId, error = "Local AI model not downloaded. Visit Settings.")
            return
        }
        lastRequest = type
        _state.value = State(running = true, jobId = jobId, type = type, label = type.label)
        var launched: Job? = null
        launched = scope.launch {
            try {
                val job = repository.getJob(jobId)
                    ?: throw IllegalStateException("Job not found.")
                val resume = settings.resumeText.first()
                if (resume.isBlank()) {
                    throw IllegalStateException("No resume on file. Add your resume in Settings first.")
                }

                var updated = job

                // Pre-collect Q&A if selected
                val qaPairs = if (includeQa) {
                    val questions = interviewRepository.getQuestions(jobId)
                    val answers = interviewRepository.observeAnswers(jobId).first()
                    questions.mapNotNull { q ->
                        val a = answers.find { it.questionId == q.id }
                        if (a?.hasAnswer == true) q.question to a.text else null
                    }
                } else emptyList()

                // Phase 1: Distillation
                updateProgress("Analyzing job...", 0.01f)
                val distillPrompt = PromptBuilder.distillationPrompt(updated, resume, qaPairs)
                systemLog.log("Starting Phase 1 (Distillation) with Cloud AI (Gemini)...")
                Log.d(TAG, "Phase 1: Starting Job Distillation with Cloud AI (Gemini)...")
                
                val distilledFacts = try {
                    withTimeout(120.seconds) {
                        Log.d(TAG, "Calling cloudModelManager.generate for distillation...")
                        cloudModelManager.generate(distillPrompt).trim()
                    }
                } catch (e: TimeoutCancellationException) {
                    systemLog.log("ERROR: Phase 1 timed out after 2 minutes.")
                    Log.e(TAG, "Job analysis timed out after 120s", e)
                    throw IOException("Job analysis timed out. Cloud AI model might be struggling or rate-limited.")
                }
                
                systemLog.log("Phase 1 Complete. Facts extracted.")
                
                trainingRepository.logExample(
                    appName = "task",
                    feature = "job_distillation",
                    inputPrompt = distillPrompt,
                    modelOutput = distilledFacts
                )
                delay(500)

                // Step 1.5: Q&A Enrichment (Fallback to Cloud if local failed to include it well)
                var finalDistilled = distilledFacts
                if (includeQa && qaPairs.isNotEmpty() && !distilledFacts.contains(qaPairs.first().second.take(10))) {
                    updateProgress("Integrating Q&A facts...", 0.25f)
                    Log.d(TAG, "Phase 1.5: Weaving ${qaPairs.size} Q&A answers into distilled facts using Cloud AI...")
                    val enrichPrompt = "Incorporate these interview Q&A facts into the distilled resume facts. Maintain JSON structure.\n\nQ&A:\n" +
                        qaPairs.joinToString("\n") { "Q: ${it.first}\nA: ${it.second}" } +
                        "\n\nDistilled Facts:\n$distilledFacts"
                    finalDistilled = cloudModelManager.generate(enrichPrompt).trim()
                    delay(500)
                }

                // Phase 2: Resume
                if (type == Type.RESUME || type == Type.BOTH) {
                    updateProgress("Tailoring experience...", 0.45f)
                    val resumePrompt = PromptBuilder.resumePrompt(updated, resume, finalDistilled, emptyList(), steeringPrompt)
                    Log.d(TAG, "Phase 2: Sending resume tailoring prompt to Cloud AI...")
                    val resumeResult = cloudModelManager.generate(resumePrompt).trim()

                    if (resumeResult.isBlank()) throw IllegalStateException("Cloud AI returned an empty resume.")
                    
                    trainingRepository.logExample(
                        appName = "task",
                        feature = "resume_tailoring",
                        inputPrompt = resumePrompt,
                        modelOutput = resumeResult
                    )

                    val cleanResume = ResumeData.fromJson(resumeResult)?.toJson() ?: ResumeData.extractJson(resumeResult)
                    updated = updated.copy(resumeText = cleanResume)
                    repository.updateJob(updated)
                    Log.d(TAG, "Phase 2 Complete. Resume tailored and saved.")
                    delay(500)
                }

                // Phase 3: Cover Letter
                if (type == Type.COVER || type == Type.BOTH) {
                    updateProgress("Finalizing documents...", if (type == Type.BOTH) 0.85f else 0.6f)
                    
                    val resumeSource = updated.resumeText.ifBlank { resume }
                    val coverPrompt = PromptBuilder.coverLetterPrompt(updated, resumeSource)
                    Log.d(TAG, "Sending cover letter prompt to Cloud AI...")
                    val coverResult = cloudModelManager.generate(coverPrompt).trim()

                    if (coverResult.isBlank()) throw IllegalStateException("Failed to generate cover letter.")
                    
                    trainingRepository.logExample(
                        appName = "task",
                        feature = "cover_letter_generation",
                        inputPrompt = coverPrompt,
                        modelOutput = coverResult
                    )

                    val cleanCover = CoverLetterData.fromJson(coverResult)?.toJson() ?: CoverLetterData.extractJson(coverResult)
                    val headerSource = if (updated.resumeText.isNotBlank()) updated.resumeText else resume
                    updated = updated.copy(
                        coverLetterText = CoverLetterComposer.compose(cleanCover, headerSource, updated)
                    )
                    repository.updateJob(updated)
                }

                // Phase 4: Follow-up Email
                if (type == Type.FOLLOW_UP) {
                    updateProgress("Drafting follow-up...", 0.7f)
                    val resumeSource = updated.resumeText.ifBlank { resume }
                    val prompt = PromptBuilder.followUpEmailPrompt(updated, resumeSource)
                    Log.d(TAG, "Sending follow-up prompt to Local AI...")
                    val result = withTimeout(120.seconds) {
                        modelManager.generate(prompt, source = "Follow-up Email").trim()
                    }
                    if (result.isBlank()) throw IllegalStateException("Failed to generate follow-up email.")
                    
                    trainingRepository.logExample(
                        appName = "task",
                        feature = "follow_up_email",
                        inputPrompt = prompt,
                        modelOutput = result
                    )

                    val headerSource = updated.resumeText.ifBlank { resume }
                    val followUpJson = CoverLetterComposer.compose(result, headerSource, updated)
                    repository.updateJob(updated.copy(followUpEmailText = followUpJson))
                }

                _state.value = State()
                crawlJob?.cancel()
                _events.emit(Event.GenerationFinished(jobId, type))
            } catch (e: CancellationException) {
                crawlJob?.cancel()
                throw e
            } catch (e: Throwable) {
                crawlJob?.cancel()
                Log.e(TAG, "Generation failed for job $jobId", e)
                val msg = when {
                    e.message?.contains("PERMISSION_DENIED", ignoreCase = true) == true -> 
                        "Permission denied. Please grant 'All files access' in Settings."
                    e.message?.contains("initialize engine", ignoreCase = true) == true ->
                        "Failed to load AI model. Check model file in Settings."
                    else -> e.message ?: "Unexpected error"
                }
                _state.value = State(jobId = jobId, error = "$msg. Please retry.")
            } finally {
                if (activeJob === launched) activeJob = null
            }
        }
        activeJob = launched
    }

    private fun updateProgress(label: String, progress: Float) {
        val percent = (progress * 100).toInt()
        _state.value = _state.value.copy(
            label = label,
            progress = progress,
            progressText = "$label: $percent%",
            indeterminate = false
        )
        startCrawl(progress + 0.15f)
    }

    fun generateCheatSheet(jobId: Long) {
        if (_state.value.running) return
        if (!modelManager.isModelDownloaded()) {
            _state.value = State(jobId = jobId, error = "Local AI model not downloaded. Visit Settings.")
            return
        }
        _state.value = State(running = true, jobId = jobId, type = Type.CHEAT_SHEET, label = "Generating cheat sheet...")
        var launched: Job? = null
        launched = scope.launch {
            try {
                val job = repository.getJob(jobId) ?: throw IllegalStateException("Job not found.")
                val resume = settings.resumeText.first()
                
                updateProgress("Analyzing for cheat sheet...", 0.3f)
                val prompt = PromptBuilder.cheatSheetPrompt(job, resume)
                Log.d(TAG, "Sending cheat sheet prompt to Local AI...")
                val result = withTimeout(180.seconds) {
                    modelManager.generate(prompt, source = "Cheat Sheet").trim()
                }
                
                trainingRepository.logExample(
                    appName = "task",
                    feature = "interview_cheat_sheet",
                    inputPrompt = prompt,
                    modelOutput = result
                )

                updateProgress("Formatting results...", 0.8f)
                val clean = com.example.jobsearch.data.CheatSheetData.fromJson(result)?.toJson() 
                    ?: com.example.jobsearch.data.CheatSheetData.extractJson(result)
                
                repository.updateJob(job.copy(cheatSheetText = clean))
                _state.value = State()
                crawlJob?.cancel()
            } catch (e: CancellationException) {
                crawlJob?.cancel()
                throw e
            } catch (e: Throwable) {
                crawlJob?.cancel()
                Log.e(TAG, "Cheat sheet generation failed", e)
                _state.value = State(jobId = jobId, error = e.message ?: "Failed to generate cheat sheet.")
            } finally {
                if (activeJob === launched) activeJob = null
            }
        }
        activeJob = launched
    }

    fun cancel() {
        if (!_state.value.running) return
        _state.value = State()
        crawlJob?.cancel()
        modelManager.cancel()
        activeJob?.cancel()
        activeJob = null
    }

    fun retryLast() {
        val request = lastRequest ?: return
        val jobId = _state.value.jobId ?: return
        generate(jobId, request)
    }

    fun dismissError() {
        _state.value = _state.value.copy(error = null, jobId = null)
    }

    companion object {
        private const val TAG = "GenerationRepository"
    }
}
