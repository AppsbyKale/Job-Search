package com.example.jobsearch.ui.jobdetail

import androidx.lifecycle.SavedStateHandle
import com.example.jobsearch.ai.GenerationRepository
import com.example.jobsearch.ai.IModelManager
import com.example.jobsearch.ai.ModelManager
import com.example.jobsearch.data.InterviewAnswer
import com.example.jobsearch.data.InterviewQuestion
import com.example.jobsearch.data.InterviewRepository
import com.example.jobsearch.data.Job
import com.example.jobsearch.data.JobRepository
import com.example.jobsearch.data.JobStatus
import com.example.jobsearch.data.SettingsRepository
import com.example.jobsearch.document.DocumentExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.*

@OptIn(ExperimentalCoroutinesApi::class)
class JobDetailViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val repository: JobRepository = mock()
    private val interviewRepository: InterviewRepository = mock()
    private val modelManager: IModelManager = mock()
    private val settingsRepository: SettingsRepository = mock()
    private val generationRepository: GenerationRepository = mock()
    private val exporter: DocumentExporter = mock()
    private val savedStateHandle: SavedStateHandle = SavedStateHandle(mapOf("jobId" to 1L))

    private lateinit var viewModel: JobDetailViewModel

    private val jobFlow = MutableStateFlow<Job?>(null)
    private val genStateFlow = MutableStateFlow(GenerationRepository.State())
    private val resumeTextFlow = MutableStateFlow("")
    private val questionsFlow = MutableStateFlow(emptyList<InterviewQuestion>())
    private val answersFlow = MutableStateFlow(emptyList<InterviewAnswer>())
    private val downloadProgressFlow = MutableStateFlow(ModelManager.DownloadProgress())

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        whenever(repository.observeJob(1L)).thenReturn(jobFlow)
        whenever(generationRepository.state).thenReturn(genStateFlow)
        whenever(settingsRepository.resumeText).thenReturn(resumeTextFlow)
        whenever(interviewRepository.observeQuestions(1L)).thenReturn(questionsFlow)
        whenever(interviewRepository.observeAnswers(1L)).thenReturn(answersFlow)
        whenever(modelManager.downloadProgress).thenReturn(downloadProgressFlow)
        whenever(modelManager.isModelDownloaded()).thenReturn(true)

        viewModel = JobDetailViewModel(
            repository,
            interviewRepository,
            modelManager,
            settingsRepository,
            generationRepository,
            exporter,
            savedStateHandle
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `setStatus updates repository`() = runTest {
        val job = Job(id = 1, title = "Engineer", company = "Google", status = JobStatus.SAVED.name)
        jobFlow.value = job
        
        // Use backgroundScope to keep the state flow active
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.state.collect {}
        }
        
        advanceUntilIdle()
        
        val newStatus = JobStatus.APPLIED
        viewModel.setStatus(newStatus)
        
        advanceUntilIdle()
        
        verify(repository).updateJob(argThat { status == newStatus.name })
    }

    @Test
    fun `dismissNotice clears notice state and calls generationRepository`() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.state.collect {}
        }
        advanceUntilIdle()
        
        viewModel.dismissNotice()
        
        advanceUntilIdle()
        
        verify(generationRepository).dismissError()
        assertNull(viewModel.state.value.notice)
    }
}
