package com.example.jobsearch.testing

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
import com.example.jobsearch.data.TrainingRepository
import com.example.jobsearch.document.DocumentExporter
import com.example.jobsearch.network.LangSearchClient
import com.example.jobsearch.ui.jobdetail.JobDetailViewModel
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
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.argThat
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class JobDetailViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val repository: JobRepository = mock()
    private val interviewRepository: InterviewRepository = mock()
    private val modelManager: IModelManager = mock()
    private val settingsRepository: SettingsRepository = mock()
    private val generationRepository: GenerationRepository = mock()
    private val trainingRepository: TrainingRepository = mock()
    private val exporter: DocumentExporter = mock()
    private val langSearchClient: LangSearchClient = mock()
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
            trainingRepository,
            exporter,
            langSearchClient,
            savedStateHandle
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun givenJobState_whenSetStatusCalled_updatesRepositoryWithNewStatus() = runTest {
        val job = Job(id = 1, title = "Senior Android Engineer", company = "Google", status = JobStatus.SAVED.name)
        jobFlow.value = job

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.state.collect {}
        }
        advanceUntilIdle()

        viewModel.setStatus(JobStatus.APPLIED)
        advanceUntilIdle()

        verify(repository).updateJob(argThat { status == JobStatus.APPLIED.name })
    }

    @Test
    fun givenActiveNotice_whenDismissNoticeCalled_clearsNoticeState() = runTest {
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
