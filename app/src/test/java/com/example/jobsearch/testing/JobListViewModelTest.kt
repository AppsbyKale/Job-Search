package com.example.jobsearch.testing

import com.example.jobsearch.data.InterviewRepository
import com.example.jobsearch.data.Job
import com.example.jobsearch.data.JobRepository
import com.example.jobsearch.data.JobStatus
import com.example.jobsearch.data.SettingsRepository
import com.example.jobsearch.ui.joblist.JobListViewModel
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
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class JobListViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val repository: JobRepository = mock()
    private val interviewRepository: InterviewRepository = mock()
    private val settingsRepository: SettingsRepository = mock()

    private lateinit var viewModel: JobListViewModel

    private val allJobsFlow = MutableStateFlow<List<Job>>(emptyList())
    private val syncedJobsFlow = MutableStateFlow<List<Job>>(emptyList())
    private val followupIntervalsFlow = MutableStateFlow("7, 14, 30")

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        whenever(repository.observeJobs()).thenReturn(allJobsFlow)
        whenever(repository.observeByStatus(JobStatus.SYNCED.name)).thenReturn(syncedJobsFlow)
        whenever(settingsRepository.followupIntervals).thenReturn(followupIntervalsFlow)

        viewModel = JobListViewModel(repository, interviewRepository, settingsRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun givenSearchQuery_whenEntered_filtersJobListByTitleAndCompany() = runTest {
        val job1 = Job(id = 1, title = "Android Developer", company = "Google", status = JobStatus.SAVED.name)
        val job2 = Job(id = 2, title = "iOS Developer", company = "Apple", status = JobStatus.SAVED.name)
        allJobsFlow.value = listOf(job1, job2)

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.jobs.collect {}
        }
        advanceUntilIdle()

        viewModel.onSearchQueryChange("Google")
        advanceUntilIdle()

        val filtered = viewModel.jobs.value
        assertEquals(1, filtered.size)
        assertEquals("Android Developer", filtered[0].job.title)
    }

    @Test
    fun givenJobId_whenDeleteJobCalled_deletesFromInterviewAndJobRepositories() = runTest {
        viewModel.deleteJob(1L)
        advanceUntilIdle()

        verify(interviewRepository).deleteForJob(1L)
        verify(repository).deleteJob(1L)
    }
}
